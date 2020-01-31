package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class MendelianViolationReportStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public MendelianViolationReportStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<MendelianViolationReportStep> implements VariantProcessingStep.RequiresPedigree
    {
        public Provider()
        {
            super("MendelianViolationReport", "Mendelian Violation Report", "", "This will generate a table summarizing Mendelian violations from the VCF", Arrays.asList(
                    ToolParameterDescriptor.create("violationReportThreshold", "Violation Report Threshold ", "Only subject with at least this many MVs will be reported.", "ldk-integerfield", null, 500),
                    ToolParameterDescriptor.create("excludeFiltered", "Exclude Filtered", "If selected, filtered sites will be ignored.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, null)
                ), null, "");
        }

        public MendelianViolationReportStep create(PipelineContext ctx)
        {
            return new MendelianViolationReportStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();
        Integer maxThreads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            options.add("-nt");
            options.add(maxThreads.toString());
        }

        if (getProvider().getParameterByName("excludeFiltered").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
        {
            options.add("--excludeFiltered");
        }

        Integer violationReportThreshold = getProvider().getParameterByName("violationReportThreshold").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
        if (violationReportThreshold > 0)
        {
            options.add("--violationReportThreshold");
            options.add(String.valueOf(violationReportThreshold));
        }

        File pedFile = ProcessVariantsHandler.getPedigreeFile(getPipelineCtx().getSourceDirectory(true));
        if (!pedFile.exists())
        {
            throw new PipelineJobException("Unable to find pedigree file: " + pedFile.getPath());
        }

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File outputTable = new File(outputDirectory, SequencePipelineService.get().getUnzippedBaseName(inputVCF.getName()) + ".mv.txt");
        MendelianViolationReportWrapper wrapper = new MendelianViolationReportWrapper(getPipelineCtx().getLogger());
        wrapper.execute(inputVCF, genome.getWorkingFastaFile(), outputTable, pedFile, options);

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addInput(pedFile, "Pedigree File");
        output.addOutput(outputTable, "Mendelian Violation Report");

        output.addSequenceOutput(outputTable, "Mendelian Violation Summary for: " + inputVCF.getName(), "MV Report", null, null, genome.getGenomeId(), null);

        return output;
    }
}
