package org.labkey.sequenceanalysis.run.analysis;

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
            super("HaplotypeCallerAnalysis", "Haplotype Caller", "GATK", "This will run GATK's HaplotypeCaller on the selected data.  The typical purpose of this step is to create per-sample genotype likelihoods (ie. gVCF file).  gVCFs from many samples can be used it a later step for joint genotyping, which should produce more accurate results.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-stand_emit_conf"), "stand_emit_conf", "Threshold For Emitting Variants", "The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--emitRefConfidence"), "emitRefConfidence", "Emit Reference Confidence Scores", "Mode for emitting experimental reference confidence scores.  Allowable values are: NONE, BP_RESOLUTION, GVCF", "textfield", null, "GVCF"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-dontUseSoftClippedBases"), "dontUseSoftClippedBases", "Don't Use Soft Clipped Bases", "If specified, we will not analyze soft clipped bases in the reads", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_type"), "variant_index_type", "Variant Index Type", "Type of IndexCreator to use for VCF/BCF indices", "textfield", null, "LINEAR"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_parameter"), "variant_index_parameter", "Variant Index Parameter", "Parameter to pass to the VCF/BCF IndexCreator", "ldk-integerfield", null, 128000),
                    ToolParameterDescriptor.create("multithreaded", "Multithreaded?", "If checked, this tool will attempt to run in multi-threaded mode.  There are sometimes issues with this.", "checkbox", null, null),
                    ToolParameterDescriptor.create("useQueue", "Use Queue?", "If checked, this tool will attempt to run using GATK queue.  This is the preferred way to multi-thread this tool.", "checkbox", null, null)
            ), null, null);
        }

        @Override
        public HaplotypeCallerAnalysis create(PipelineContext ctx)
        {
            return new HaplotypeCallerAnalysis(this, ctx);
        }
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

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".vcf.gz");
        File idxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".vcf.gz.idx");

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

            getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, args);
        }

        output.addOutput(outputFile, "gVCF File");
        output.addSequenceOutput(outputFile, rs.getName() + ": HaplotypeCaller Variants", "gVCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId());
        if (idxFile.exists())
        {
            output.addOutput(idxFile, "VCF Index");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }
}
