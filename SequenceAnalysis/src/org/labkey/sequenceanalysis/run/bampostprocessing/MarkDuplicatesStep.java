package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.run.util.MarkDuplicatesWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */
public class MarkDuplicatesStep extends AbstractCommandPipelineStep<MarkDuplicatesWrapper> implements BamProcessingStep
{
    public MarkDuplicatesStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new MarkDuplicatesWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<MarkDuplicatesStep>
    {
        public Provider()
        {
            super("MarkDuplicates", "Mark Duplicates", "Picard", "This runs Picard tools MarkDuplicates command in order to mark and/or remove duplicate reads.  Please note this can have implications for downstream analysis, because reads marked as duplicates are frequently omitted.  This is often desired, but can be a problem for sequencing of PCR products.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--REMOVE_DUPLICATES"), "removeDuplicates", "Remove Duplicates", "If selected, duplicate reads will be removed, as opposed to flagged as duplicates.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--MAX_RECORDS_IN_RAM"), "maxRecordsInRam", "Max Records in RAM", "When writing files that need to be sorted, this will specify the number of records stored in RAM before spilling to disk. Increasing this number reduces the number of file handles needed to sort the file, and increases the amount of RAM needed.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--SORTING_COLLECTION_SIZE_RATIO"), "sortingCollectionSizeRatio", "Sorting Collection Size Ratio", "This number, plus the maximum RAM available to the JVM, determine the memory footprint used by some of the sorting collections. If you are running out of memory, try reducing this number. Default is 0.25", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, null)
            ), null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public MarkDuplicatesStep create(PipelineContext ctx)
        {
            return new MarkDuplicatesStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".markduplicates.bam");
        output.addIntermediateFile(outputBam);

        output.setBAM(getWrapper().executeCommand(inputBam, outputBam, getClientCommandArgs()));
        addStepOutputs(getWrapper(), rs, inputBam, outputDirectory, output);

        return output;
    }

    public static void addStepOutputs(MarkDuplicatesWrapper wrapper, Readset rs, File inputBam, File outputDirectory, BamProcessingOutputImpl output)
    {
        //Note:
        File sortedBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".sorted.bam");
        if (sortedBam.exists() && !inputBam.equals(sortedBam))
        {
            wrapper.getLogger().debug("Adding sorted BAM as intermediate file: " + sortedBam.getPath());
            output.addIntermediateFile(sortedBam);
            output.addIntermediateFile(new File(sortedBam.getPath() + ".bai"));
        }

        //NOTE: depending on whether the BAM is sorted by the wrapper, the metrics file name will differ
        if (wrapper.getMetricsFile(sortedBam).exists())
        {
            output.addPicardMetricsFile(rs, wrapper.getMetricsFile(sortedBam), PipelineStepOutput.PicardMetricsOutput.TYPE.bam);
            output.addOutput(wrapper.getMetricsFile(sortedBam), "MarkDuplicateMetrics");
        }
        else if (wrapper.getMetricsFile(inputBam).exists())
        {
            output.addPicardMetricsFile(rs, wrapper.getMetricsFile(inputBam), PipelineStepOutput.PicardMetricsOutput.TYPE.bam);
            output.addOutput(wrapper.getMetricsFile(inputBam), "MarkDuplicateMetrics");
        }
    }
}
