package org.labkey.sequenceanalysis.run.bampostprocessing;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.SortSamWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:46 PM
 */
public class SortSamStep extends AbstractCommandPipelineStep<SortSamWrapper> implements BamProcessingStep
{
    public SortSamStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SortSamWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<SortSamStep>
    {
        public Provider()
        {
            super("SortSam", "Sort BAM (Coordinate)", "Picard", "This step uses picard tools to coordinate sort the BAM file", null, null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public SortSamStep create(PipelineContext ctx)
        {
            return new SortSamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);
        getWrapper().setStringency(ValidationStringency.SILENT);

        try
        {
            File sorted;
            SAMFileHeader.SortOrder order = SequenceUtil.getBamSortOrder(inputBam);
            if (SAMFileHeader.SortOrder.coordinate != order)
            {
                File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".sorted.bam");
                output.addIntermediateFile(outputBam);
                sorted = getWrapper().execute(inputBam, outputBam, SAMFileHeader.SortOrder.coordinate);
            }
            else
            {
                getPipelineCtx().getLogger().info("BAM is already coordinate sorted, no need to re-sort");
                sorted = inputBam;
            }

            output.setBAM(sorted);

            return output;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
