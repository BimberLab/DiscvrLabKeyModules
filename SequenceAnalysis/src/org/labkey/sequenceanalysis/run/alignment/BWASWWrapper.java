package org.labkey.sequenceanalysis.run.alignment;

import net.sf.samtools.SAMFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisController;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAlignmentStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.run.util.FixMateInformationWrapper;
import org.labkey.sequenceanalysis.run.util.PicardWrapper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
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

    public static class BWASWAlignmentStep extends BWAAlignmentStep
    {
        public BWASWAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx);
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, File refFasta, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            getWrapper().setOutputDir(outputDirectory);

            getPipelineCtx().getLogger().info("Running BWA-SW");

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("bwasw");
            args.addAll(getClientCommandArgs());
            getWrapper().appendThreads(getPipelineCtx().getJob(), args);

            args.add(new File(refFasta.getParentFile(), FileUtil.getBaseName(refFasta.getName()) + ".bwa.index").getPath());
            args.add(inputFastq1.getPath());

            if (inputFastq2 != null)
            {
                args.add(inputFastq2.getPath());
            }

            File sam = new File(outputDirectory, basename + ".sam");
            getWrapper().execute(args, sam);
            if (!sam.exists() || SequenceUtil.getLineCount(sam) < 2)
            {
                throw new PipelineJobException("SAM file doesnt exist or has too few lines: " + sam.getPath());
            }

            //convert to BAM
            File bam = new File(outputDirectory, basename + ".bam");
            SamFormatConverterWrapper converter = new SamFormatConverterWrapper(getPipelineCtx().getLogger());
            converter.setStringency(SAMFileReader.ValidationStringency.SILENT);
            bam = converter.execute(sam, bam, true);
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + bam.getPath());
            }

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BWA-SW", "BWA-SW uses a different algorithm than BWA that is better suited for longer reads. By design it will only return a single hit for each read. It it currently recommended for viral analysis and other applications that align longer reads, but do not require retaining multiple hits.", null, null, "http://bio-bwa.sourceforge.net/", false);
        }

        public BWASWAlignmentStep create(PipelineContext context)
        {
            return new BWASWAlignmentStep(this, context);
        }
    }
}
