package org.labkey.sequenceanalysis.run.analysis;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAnalysisStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.HaplotypeCallerWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-recoverDanglingHeads"), "recoverDanglingHeads", "Recover Dangling Heads", "Should we enable dangling head recovery in the read threading assembler? This mode is currently experimental and should only be used in the RNA-seq calling pipeline.", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-dontUseSoftClippedBases"), "dontUseSoftClippedBases", "Don't Use Soft Clipped Bases", "If specified, we will not analyze soft clipped bases in the reads", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_type"), "variant_index_type", "Variant Index Type", "Type of IndexCreator to use for VCF/BCF indices", "textfield", null, "LINEAR"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_parameter"), "variant_index_parameter", "Variant Index Parameter", "Parameter to pass to the VCF/BCF IndexCreator", "ldk-integerfield", null, 128000),
                    ToolParameterDescriptor.create("dbsnp", "dbSNP file", "rsIDs from this file are used to populate the ID column of the output. Also, the DB INFO flag will be set when appropriate. dbSNP is not used in any way for the calculations themselves.", "displayfield", null, null),
                    ToolParameterDescriptor.create("comp", "Comparison Track", "If a call overlaps with a record from the provided comp track, the INFO field will be annotated as such in the output with the track name (e.g. -comp:FOO will have 'FOO' in the INFO field). Records that are filtered in the comp track will be ignored. Note that 'dbSNP' has been special-cased (see the --dbsnp argument).", "displayfield", null, null)
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
    public Output performAnalysisPerSampleRemote(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File outputFile = new File(getWrapper().getOutputDir(inputBam), FileUtil.getBaseName(inputBam) + ".gvcf");

        List<String> args = new ArrayList<>();
        Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
        if (threads != null)
        {
            args.add("-nct");
            args.add(threads.toString());
        }

        args.addAll(getClientCommandArgs());

        getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, args);

        output.addSequenceOutput(outputFile, rs.getName() + ": HaplotypeCaller Variants", "GVCF File", rs.getRowId(), null, referenceGenome.getGenomeId());

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }

    @Override
    public void performAnalysisOnAll(List<AnalysisModel> analysisModels) throws PipelineJobException
    {

    }
}
