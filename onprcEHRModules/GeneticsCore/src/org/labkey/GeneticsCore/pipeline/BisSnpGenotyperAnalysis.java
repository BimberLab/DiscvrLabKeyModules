package org.labkey.GeneticsCore.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class BisSnpGenotyperAnalysis extends AbstractCommandPipelineStep<BisulfiteGenotyperWrapper> implements AnalysisStep
{
    public BisSnpGenotyperAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new BisulfiteGenotyperWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<BisSnpGenotyperAnalysis>
    {
        public Provider()
        {
            super("BisulfiteGenotyperAnalysis", "BisSNP BisulfiteGenotyper", "BisSNP", "This will run BisSNP's BisulfiteGenotyper on the selected data.", getToolDescriptors(), null, null);
        }

        @Override
        public BisSnpGenotyperAnalysis create(PipelineContext ctx)
        {
            return new BisSnpGenotyperAnalysis(this, ctx);
        }
    }

    public static List<ToolParameterDescriptor> getToolDescriptors()
    {
        return Arrays.asList(
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 20),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_emit_conf"), "stand_emit_conf", "Threshold For Emitting Variants", "The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", "ldk-numberfield", null, 20)
            );
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputBam, "Input BAM File");

        File snpOutputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".snp.vcf");
        File cpgOutputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".cpg.vcf");

        getWrapper().setOutputDir(outputDir);

        List<String> args = new ArrayList<>();
        args.addAll(getClientCommandArgs());

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getJob().getLogger());
        getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), cpgOutputFile, snpOutputFile, maxThreads, args);

        //sort, which will also create indexes
        File dict = new File(referenceGenome.getWorkingFastaFile().getParent(), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile()) + ".dict");
        cpgOutputFile = SequencePipelineService.get().sortVcf(cpgOutputFile, new File(cpgOutputFile.getPath() + ".gz"), dict, getPipelineCtx().getLogger());
        snpOutputFile = SequencePipelineService.get().sortVcf(snpOutputFile, new File(snpOutputFile.getPath() + ".gz"), dict, getPipelineCtx().getLogger());

        (new File(outputDir, FileUtil.getBaseName(inputBam) + ".snp.vcf")).delete();
        (new File(outputDir, FileUtil.getBaseName(inputBam) + ".cpg.vcf")).delete();
        (new File(outputDir, FileUtil.getBaseName(inputBam) + ".snp.vcf.idx")).delete();
        (new File(outputDir, FileUtil.getBaseName(inputBam) + ".cpg.vcf.idx")).delete();

        File snpIdxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".snp.vcf.gz.tbi");
        File cpgIdxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".cpg.vcf.gz.tbi");

        output.addOutput(cpgOutputFile, "Bisulfite VCF File");
        output.addSequenceOutput(cpgOutputFile, cpgOutputFile.getName(), "Bisulfite VCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
        if (cpgIdxFile.exists())
        {
            output.addOutput(cpgIdxFile, "Bisulfite VCF Index");
        }
        else
        {
            getPipelineCtx().getLogger().warn("expected index not found: " + cpgIdxFile.getName());
        }
        output.addOutput(getWrapper().getFilterSummary(cpgOutputFile), "Bisulfite Filter Summary");

        output.addOutput(snpOutputFile, "VCF File");
        output.addSequenceOutput(snpOutputFile, snpOutputFile.getName(), "VCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
        if (snpIdxFile.exists())
        {
            output.addOutput(snpIdxFile, "Bisulfite VCF Index");
        }
        else
        {
            getPipelineCtx().getLogger().warn("expected index not found: " + snpIdxFile.getName());
        }
        output.addOutput(getWrapper().getFilterSummary(snpOutputFile), "Bisulfite Filter Summary");

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
