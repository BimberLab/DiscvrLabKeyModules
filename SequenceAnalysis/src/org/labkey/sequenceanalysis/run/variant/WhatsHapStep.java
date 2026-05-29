package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.LongArrayList;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhatsHapStep extends AbstractCommandPipelineStep<WhatsHapStep.WhatsHapWrapper> implements VariantProcessingStep
{
    public WhatsHapStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new WhatsHapStep.WhatsHapWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<WhatsHapStep>
    {
        public Provider()
        {
            super("WhatsHap", "WhatsHap", "", "This will run WhatsHap to phase the VCF using BAM/CRAM data", List.of(

            ), null, "https://whatshap.readthedocs.io/en/latest/");
        }

        @Override
        public WhatsHapStep create(PipelineContext ctx)
        {
            return new WhatsHapStep(this, ctx);
        }
    }

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        if (inputFiles.size() != 1)
        {
            throw new PipelineJobException("This step expects a single VCF as input");
        }

        // look up BAM/CRAMs:
        for (SequenceOutputFile so : inputFiles)
        {
            List<String> samples;
            try (VCFFileReader reader = new VCFFileReader(so.getFile()))
            {
                VCFHeader header = reader.getFileHeader();
                samples = header.getSampleNamesInOrder();
            }

            if (samples.isEmpty())
            {
                throw new IllegalStateException("No samples found in VCF file");
            }

            ArrayList<Long> toCache = new LongArrayList();
            Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbookOrTab() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
            TableInfo outputFiles = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
            for (String sample : samples)
            {
                // Find readsets for this genome:
                SimpleFilter filter1 = new SimpleFilter(FieldKey.fromString("readset/name"), sample).
                        addCondition(FieldKey.fromString("library_id"), so.getLibrary_id()).
                        addCondition(FieldKey.fromString("category"), "Alignment");

                List<Integer> alignments = new TableSelector(outputFiles, PageFlowUtil.set("rowid"), filter1, null).getArrayList(Integer.class);
                if (alignments.isEmpty())
                {
                    throw new PipelineJobException("Unable to find alignment for: " + sample);
                }

                SequenceOutputFile alignmentFile = SequenceOutputFile.getForId(Collections.max(alignments));
                toCache.add(alignmentFile.getDataId());
                support.cacheExpData(alignmentFile.getExpData());
            }

            support.cacheObject(CACHE_KEY, toCache);
        }
    }

    private final String CACHE_KEY = "~cached_readsets~";

    private List<File> getCachedBams() throws PipelineJobException
    {
        List<Long> cachedFiles = getPipelineCtx().getSequenceSupport().getCachedObject(CACHE_KEY, PipelineJob.createObjectMapper().getTypeFactory().constructType(LongArrayList.class));

        return cachedFiles.stream().map(x -> getPipelineCtx().getSequenceSupport().getCachedData(x)).toList();
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");

        File vcfOut = FileUtil.appendName(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".phased.vcf.gz");

        List<String> args = new ArrayList<>();
        args.add(getWrapper().getExe().getPath());
        args.add("-o");
        args.add(vcfOut.getPath());
        args.add("--reference");
        args.add(genome.getWorkingFastaFile().getPath());
        args.add(inputVCF.getPath());
        for (File f : getCachedBams())
        {
            args.add(f.getPath());
        }

        getWrapper().execute(args);

        if (!vcfOut.exists())
        {
            throw new PipelineJobException("Missing file: " + vcfOut.getPath());
        }

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(vcfOut, getPipelineCtx().getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addSequenceOutput(vcfOut, "Phased VCF: " + inputVCF.getName(), "Phased VCF", null, null, genome.getGenomeId(), null);

        return output;
    }

    public static class WhatsHapWrapper extends AbstractCommandWrapper
    {
        public WhatsHapWrapper(@Nullable Logger logger)
        {
            super(logger);
        }

        public File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("WHATSHAPPATH", "whatshap");
        }
    }
}
