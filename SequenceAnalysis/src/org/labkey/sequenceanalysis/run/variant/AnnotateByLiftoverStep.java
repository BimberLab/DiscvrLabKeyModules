package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.sequenceanalysis.run.LiftoverBcfToolsWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.run.util.VariantAnnotatorWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by bimber on 5/2/2017.
 */
public class AnnotateByLiftoverStep extends AbstractCommandPipelineStep<BcftoolsRunner> implements VariantProcessingStep
{
    public static final String CHAIN_FILE = "chainFileId";
    public static final String TARGET_GENOME = "targetGenomeId";

    final List<String> liftFields = Arrays.asList("LiftedContig", "LiftedStart", "LiftedStop", "ReverseComplementedAlleles");

    public AnnotateByLiftoverStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new BcftoolsRunner(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<AnnotateByLiftoverStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("AnnotateByLiftoverStep", "Annotate Using LiftOver", "LiftOver/BackportLiftedVcf", "This will lift a VCF to the target genome, and then use this result to annotate annotate an input NHP VCF using human annotations including funcotator.  This jobs will automatically look for chain files based on the source VCF genome and GRCh37/38 targets and will fail if these are not found.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(CHAIN_FILE, "Target Genome", "The target genome for liftover", "sequenceanalysis-chainfileselectorfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null)
            ), new LinkedHashSet<String>(List.of("sequenceanalysis/field/ChainFileSelectorField.js")), null);
        }

        @Override
        public AnnotateByLiftoverStep create(PipelineContext context)
        {
            return new AnnotateByLiftoverStep(this, context);
        }
    }

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws UnsupportedOperationException, PipelineJobException
    {
        Set<Integer> genomeIds = new HashSet<>();
        for (SequenceOutputFile so : inputFiles)
        {
            genomeIds.add(so.getLibrary_id());
        }

        if (genomeIds.size() != 1)
        {
            throw new PipelineJobException("All inputs must be from the same genome");
        }

        Integer chainFileId = getProvider().getParameterByName(CHAIN_FILE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        if  (chainFileId == null)
        {
            throw new PipelineJobException("Chain file not provided");
        }

        //find chain files:
        job.getLogger().debug("Caching chain file");
        ExpData data = ExperimentService.get().getExpData(chainFileId);
        if (data == null)
        {
            throw new PipelineJobException("Unable to find ExpData chain file: " + chainFileId);
        }

        support.cacheExpData(data);

        // Target Genome:
        TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer().getContainerFor(ContainerType.DataType.tabParent), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES);
        Integer targetGenomeId = new TableSelector(ti, PageFlowUtil.set("genomeId2"), new SimpleFilter(FieldKey.fromString("chainFile"), chainFileId), null).getObject(Integer.class);
        support.cacheObject(TARGET_GENOME, targetGenomeId);

        ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(targetGenomeId, getPipelineCtx().getJob().getUser());
        support.cacheGenome(rg);
    }

    private boolean indexExists(File vcf)
    {
        return new File(vcf.getPath() + ".tbi").exists();
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        Integer chainFileId = getProvider().getParameterByName(CHAIN_FILE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File chainFile = getPipelineCtx().getSequenceSupport().getCachedData(chainFileId);
        if (!chainFile.exists())
        {
            throw new PipelineJobException("Unable to find chain file: " + chainFileId);
        }

        int targetGenomeId = getPipelineCtx().getSequenceSupport().getCachedObject(TARGET_GENOME, Integer.class);
        ReferenceGenome targetGenome = getPipelineCtx().getSequenceSupport().getCachedGenome(targetGenomeId);

        // Subset, drop annotations and genotypes:
        File subsetVcf = FileUtil.appendName(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".subset.vcf.gz");
        if (!indexExists(subsetVcf))
        {
            BcftoolsRunner br = new  BcftoolsRunner(getPipelineCtx().getLogger());

            List<String> selectArgs = new ArrayList<>();
            selectArgs.add(BcftoolsRunner.getBcfToolsPath().getPath());
            selectArgs.add("annotate");
            selectArgs.add("-x");
            selectArgs.add("INFO,FORMAT");

            selectArgs.add("-o");
            selectArgs.add(subsetVcf.getPath());

            if (intervals != null)
            {
                selectArgs.add("-r");
                selectArgs.add(intervals.stream().map(interval -> interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd()).collect(Collectors.joining(",")));
            }

            selectArgs.add(inputVCF.getPath());

            br.execute(selectArgs);

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(subsetVcf, getPipelineCtx().getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getPipelineCtx().getLogger().debug("VCF index exists, skipping subset: " + subsetVcf.getPath());
        }
        output.addIntermediateFile(subsetVcf);
        output.addIntermediateFile(new File(subsetVcf.getPath() + ".tbi"));

        // Lift
        File liftedVcf = FileUtil.appendName(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".lifted.vcf.gz");
        if (!indexExists(liftedVcf))
        {
            LiftoverBcfToolsWrapper lbw = new LiftoverBcfToolsWrapper(getPipelineCtx().getLogger());
            lbw.doLiftover(subsetVcf, chainFile, genome.getWorkingFastaFile(), targetGenome.getWorkingFastaFile(), null, liftedVcf);

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(liftedVcf, getPipelineCtx().getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            getPipelineCtx().getLogger().debug("VCF index exists, skipping liftOver: " + liftedVcf.getPath());
        }
        output.addIntermediateFile(liftedVcf);
        output.addIntermediateFile(new File(liftedVcf.getPath() + ".tbi"));

        // Backport
        File liftedVcfBackport = FileUtil.appendName(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".lifted.bp.vcf.gz");
        if (!indexExists(liftedVcfBackport))
        {
            DISCVRSeqRunner bpRunner = new DISCVRSeqRunner(getPipelineCtx().getLogger());
            List<String> args = bpRunner.getBaseArgs("BackportLiftedVcf");

            args.add("-V");
            args.add(liftedVcf.getPath());

            args.add("-R");
            args.add(targetGenome.getWorkingFastaFile().getPath());

            args.add("--targetFasta");
            args.add(genome.getWorkingFastaFile().getPath());

            args.add("-O");
            args.add(liftedVcfBackport.getPath());

            bpRunner.execute(args);
            if (!liftedVcfBackport.exists())
            {
                throw new PipelineJobException("Unable to find file: " + liftedVcfBackport.getPath());
            }
        }
        else
        {
            getPipelineCtx().getLogger().debug("VCF index exists, skipping liftOver/backport: " + liftedVcfBackport.getPath());
        }
        output.addIntermediateFile(liftedVcfBackport);
        output.addIntermediateFile(new File(liftedVcfBackport.getPath() + ".tbi"));

        // Annotate original:
        File vcfAnnotated = FileUtil.appendName(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".lifted.bp.annot.vcf.gz");
        if (!indexExists(vcfAnnotated))
        {
            VariantAnnotatorWrapper bpRunner = new VariantAnnotatorWrapper(getPipelineCtx().getLogger());
            List<String> args = new ArrayList<>();
            args.add("-resource:lifted");
            args.add(liftedVcfBackport.getPath());

            args.add("--resource-allele-concordance");

            args.add("-E");
            args.add("lifted.LiftedContig");

            args.add("-E");
            args.add("lifted.LiftedStart");

            args.add("-E");
            args.add("lifted.LiftedStop");

            bpRunner.execute(genome.getWorkingFastaFile(), liftedVcfBackport, vcfAnnotated, args);
            if (!liftedVcfBackport.exists())
            {
                throw new PipelineJobException("Unable to find file: " + vcfAnnotated.getPath());
            }
        }
        else
        {
            getPipelineCtx().getLogger().debug("VCF index exists, skipping liftOver/backport: " + vcfAnnotated.getPath());
        }

        output.setVcf(vcfAnnotated);

        return output;
    }

}
