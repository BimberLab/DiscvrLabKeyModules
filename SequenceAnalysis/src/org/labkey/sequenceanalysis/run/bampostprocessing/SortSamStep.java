package org.labkey.sequenceanalysis.run.bampostprocessing;

import htsjdk.samtools.ValidationStringency;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.run.util.PicardWrapper;
import org.labkey.sequenceanalysis.run.util.SamSorter;
import org.labkey.sequenceanalysis.run.util.SortSamWrapper;

import java.io.File;

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
            super("SortSam", "Sort BAM", "Samtools", "This step uses samtools to coordinate sort the BAM file", null, null, "http://samtools.sourceforge.net/");
        }

        @Override
        public SortSamStep create(PipelineContext ctx)
        {
            return new SortSamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, File referenceFasta, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);
        getWrapper().setStringency(ValidationStringency.SILENT);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".sorted.bam");
        output.addIntermediateFile(outputBam);
        File sorted = getWrapper().execute(inputBam, outputBam, true);
        output.setBAM(sorted);

        return output;
    }
}
