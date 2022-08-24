package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.MarkDuplicatesWithMateCigarWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */
public class MarkDuplicatesWithMateCigarStep extends AbstractCommandPipelineStep<MarkDuplicatesWithMateCigarWrapper> implements BamProcessingStep
{
    public MarkDuplicatesWithMateCigarStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new MarkDuplicatesWithMateCigarWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<MarkDuplicatesWithMateCigarStep>
    {
        public Provider()
        {
            super("MarkDuplicatesWithMateCigarStep", "Mark Duplicates With Mate", "Picard", "This runs Picard tools MarkDuplicatesWithMateCigarStep command in order to mark and/or remove duplicate reads.  Unlike the original MarkDuplicates, this uses paired read information to more accurately detect true duplicates.  Please note this can have implications for downstream analysis, because reads marked as duplicates are frequently omitted.  This is often desired, but can be a problem for sequencing of PCR products.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--REMOVE_DUPLICATES"), "removeDuplicates", "Remove Duplicates", "If selected, duplicate reads will be removed, as opposed to flagged as duplicates.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--MINIMUM_DISTANCE"), "minimumDistance", "Minimum Distance", "The minimum distance to buffer records to account for clipping on the 5' end of the records.", "ldk-integerfield", null, 200),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--SORTING_COLLECTION_SIZE_RATIO"), "sortingCollectionSizeRatio", "Sorting Collection Size Ratio", "This number, plus the maximum RAM available to the JVM, determine the memory footprint used by some of the sorting collections. If you are running out of memory, try reducing this number. Default is 0.25", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, null)
            ), null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public MarkDuplicatesWithMateCigarStep create(PipelineContext ctx)
        {
            return new MarkDuplicatesWithMateCigarStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".markduplicateswithmatecigar.bam");
        output.addIntermediateFile(outputBam);

        output.setBAM(getWrapper().executeCommand(inputBam, outputBam, getClientCommandArgs()));
        MarkDuplicatesStep.addStepOutputs(getWrapper(), rs, inputBam, outputDirectory, output);

        return output;
    }
}
