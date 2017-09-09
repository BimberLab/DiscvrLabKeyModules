package org.labkey.sequenceanalysis.run.bampostprocessing;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.SortSamWrapper;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:46 PM
 */
public class DiscardUnmappedReadsStep extends AbstractCommandPipelineStep<SamtoolsRunner> implements BamProcessingStep
{
    public DiscardUnmappedReadsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SamtoolsRunner(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<DiscardUnmappedReadsStep>
    {
        public Provider()
        {
            super("DiscardUnmappedReads", "Discard Unmapped Reads", "Samtools", "This step uses samtools to create a new BAM omitting any unmapped reads.  This would typically be done to save space if these alignments are not wanted.", null, null, "");
        }

        @Override
        public DiscardUnmappedReadsStep create(PipelineContext ctx)
        {
            return new DiscardUnmappedReadsStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File filtered = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".mapped.bam");
        output.addIntermediateFile(filtered);

        List<String> args = new ArrayList<>();
        args.add(getWrapper().getSamtoolsPath().getPath());
        args.add("view");
        args.add("-b");
        args.add("-F");
        args.add("4");
        args.add("-o");
        args.add(filtered.getPath());
        args.add(inputBam.getPath());

        getWrapper().execute(args);
        if (!filtered.exists())
        {
            throw new PipelineJobException("Unable to find BAM: " + filtered.getPath());
        }

        output.setBAM(filtered);
        output.addCommandsExecuted(getWrapper().getCommandsExecuted());

        return output;
    }
}
