package org.labkey.sequenceanalysis.run.alignment;

import htsjdk.samtools.ValidationStringency;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 8:35 AM
 */
public class BWASWWrapper extends BWAWrapper
{
    public BWASWWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BWASWAlignmentStep extends BWAAlignmentStep<BWASWWrapper>
    {
        public BWASWAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BWASWWrapper(ctx.getLogger()));
        }

        @Override
        protected void doPerformAlignment(AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, Readset rs, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            getWrapper().performSWAlignment(getPipelineCtx().getJob(), output, inputFastq1, inputFastq2, outputDirectory, referenceGenome, basename, getClientCommandArgs());
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BWA-SW", "BWA-SW uses a different algorithm than BWA that is better suited for longer reads. By design it will only return a single hit for each read. It it currently recommended for viral analysis and other applications that align longer reads, but do not require retaining multiple hits.", null, null, "http://bio-bwa.sourceforge.net/", true, true);
        }

        public BWASWAlignmentStep create(PipelineContext context)
        {
            return new BWASWAlignmentStep(this, context);
        }
    }

    private void performSWAlignment(PipelineJob job, AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, List<String> additionalArgs) throws PipelineJobException
    {
        setOutputDir(outputDirectory);

        getLogger().info("Running BWA-SW");

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("bwasw");
        if (additionalArgs != null)
            args.addAll(additionalArgs);
        appendThreads(job, args);

        args.add(new File(referenceGenome.getAlignerIndexDir("bwa"), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".bwa.index").getPath());
        args.add(inputFastq1.getPath());

        if (inputFastq2 != null)
        {
            args.add(inputFastq2.getPath());
        }

        File sam = new File(outputDirectory, basename + ".sam");
        execute(args, sam);
        if (!sam.exists() || !SequenceUtil.hasMinLineCount(sam, 2))
        {
            throw new PipelineJobException("SAM file doesn't exist or has too few lines: " + sam.getPath());
        }

        //convert to BAM
        File bam = new File(outputDirectory, basename + ".bam");
        SamFormatConverterWrapper converter = new SamFormatConverterWrapper(getLogger());
        converter.setStringency(ValidationStringency.SILENT);
        bam = converter.execute(sam, bam, true);
        if (!bam.exists())
        {
            throw new PipelineJobException("Unable to find output file: " + bam.getPath());
        }

        output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
    }
}
