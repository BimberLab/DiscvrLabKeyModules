package org.labkey.sequenceanalysis.run.analysis;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAnalysisStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class HaplotypeCallerAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public HaplotypeCallerAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<HaplotypeCallerAnalysis>
    {
        public Provider()
        {
            super("HaplotypeCallerAnalysis", "Haplotype Caller", "This will run GATK's HaplotypeCaller on the selected data.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-recoverDanglingHeads"), "recoverDanglingHeads", "Recover Dangling Heads", "Should we enable dangling head recovery in the read threading assembler? This mode is currently experimental and should only be used in the RNA-seq calling pipeline.", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-dontUseSoftClippedBases"), "dontUseSoftClippedBases", "Don't Use Soft Clipped Bases", "If specified, we will not analyze soft clipped bases in the reads", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-stand_emit_conf"), "stand_emit_conf", "Threshold For Emitting Variants", "The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-stand_emit_conf"), "stand_emit_conf", "Threshold For Emitting Variants", "The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--emitRefConfidence"), "emitRefConfidence", "Emit Reference Confidence Scores", "Mode for emitting experimental reference confidence scores", "checkbox", null, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_type"), "variant_index_type", "Variant Index Type", "Type of IndexCreator to use for VCF/BCF indices", "textfield", null, "LINEAR"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--variant_index_parameter"), "variant_index_parameter", "Variant Index Parameter", "Parameter to pass to the VCF/BCF IndexCreator", "ldk-integerfield", null, 128000)
            ), null, null);
        }

        @Override
        public HaplotypeCallerAnalysis create(PipelineContext ctx)
        {
            return new HaplotypeCallerAnalysis(this, ctx);
        }
    }

    @Override
    public void performAnalysis(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {

    }
}
