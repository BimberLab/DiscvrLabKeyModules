package org.labkey.sequenceanalysis.run.analysis;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.HaplotypeCallerWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class HaplotypeCallerAnalysis extends AbstractCommandPipelineStep<HaplotypeCallerWrapper> implements AnalysisStep
{
    public HaplotypeCallerAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new HaplotypeCallerWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<HaplotypeCallerAnalysis>
    {
        public Provider()
        {
            super("HaplotypeCallerAnalysis", "Haplotype Caller", "GATK", "This will run GATK's HaplotypeCaller on the selected data.  The typical purpose of this step is to create per-sample genotype likelihoods (ie. gVCF file).  gVCFs from many samples can be used it a later step for joint genotyping, which should produce more accurate results.", getToolDescriptors(), null, null);
        }

        @Override
        public HaplotypeCallerAnalysis create(PipelineContext ctx)
        {
            return new HaplotypeCallerAnalysis(this, ctx);
        }
    }

    public static List<ToolParameterDescriptor> getToolDescriptors()
    {
        return Arrays.asList(
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-dontUseSoftClippedBases"), "dontUseSoftClippedBases", "Don't Use Soft Clipped Bases", "If specified, we will not analyze soft clipped bases in the reads", "checkbox", null, true),
                ToolParameterDescriptor.create("multithreaded", "Multithreaded?", "If checked, this tool will attempt to run in multi-threaded mode.  There are sometimes issues with this.", "checkbox", null, null),
                ToolParameterDescriptor.create("useQueue", "Use Queue?", "If checked, this tool will attempt to run using GATK queue.  This is the preferred way to multi-thread this tool.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        );
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputBam, "Input BAM File");

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz");
        File idxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz.idx");

        if (getProvider().getParameterByName("multithreaded").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getPipelineCtx().getLogger().debug("HaplotypeCaller will run multi-threaded");
            getWrapper().setMultiThreaded(true);
        }

        getWrapper().setOutputDir(outputDir);

        if (getProvider().getParameterByName("useQueue").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getWrapper().executeWithQueue(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, getClientCommandArgs());
        }
        else
        {
            List<String> args = new ArrayList<>();
            args.addAll(getClientCommandArgs());
            args.add("--emitRefConfidence");
            args.add("GVCF");

            getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, args);
        }

        output.addOutput(outputFile, "gVCF File");
        output.addSequenceOutput(outputFile, outputFile.getName(), "gVCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId());
        if (idxFile.exists())
        {
            output.addOutput(idxFile, "VCF Index");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
