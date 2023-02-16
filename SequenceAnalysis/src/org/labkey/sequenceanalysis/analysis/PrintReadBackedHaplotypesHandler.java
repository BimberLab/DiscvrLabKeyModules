package org.labkey.sequenceanalysis.analysis;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 2/3/2016.
 */
public class PrintReadBackedHaplotypesHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _bamFileType = new FileType("bam", false);

    public PrintReadBackedHaplotypesHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Print Read-Backed Haplotypes", "This scans the alignments over the provided interval(s), and reports all unique haplotypes.", new LinkedHashSet<>(PageFlowUtil.set("/sequenceanalysis/field/IntervalField.js")), Arrays.asList(
                ToolParameterDescriptor.create("intervals", "Intervals", "The intervals over which to merge the data.  They should be in the form: chr01:102-20394", "sequenceanalysis-intervalfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mq"), "minQual", "Min Base Quality", "Nucleotides with quality scores below this value will be converted to N", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 10),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-rc"), "requiredCoverageFraction", "Required Coverage Fraction", "A haplotype must have coverage over this fraction of the interval to be reported", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                    put("maxValue", 1.0);
                    put("decimalPrecision", 2);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mr"), "minReadsToReport", "Min Reads To Report", "If specified, only haplotypes with at least this many reads will be reported", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mrf"), "minReadFractionToReport", "Min Read Fraction To Report", "If specified, only haplotypes representing at least this fraction of total haplotypes will be reported", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                    put("maxValue", 1.0);
                    put("decimalPrecision", 2);
                }}, 0.01)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() != null)
                {
                    ctx.getSequenceSupport().cacheReadset(so.getReadset(), ctx.getJob().getUser());
                }
                else
                {
                    ctx.getJob().getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            if (inputFiles.isEmpty())
            {
                job.getLogger().warn("no input files");
            }

            for (SequenceOutputFile so : inputFiles)
            {
                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());
                action.addInput(so.getFile(), "Input BAM");

                File input = so.getFile();

                String intervalText = StringUtils.trimToNull(ctx.getParams().optString("intervals"));
                if (intervalText == null)
                {
                    throw new PipelineJobException("Must provide a list of intervals");
                }

                List<String> args = new ArrayList<>();
                List<Interval> il = SequenceUtil.parseAndSortIntervals(intervalText);
                if (il != null)
                {
                    for (Interval i : il)
                    {
                        args.add("-L");
                        args.add(i.getContig() + ":" + i.getStart() + "-" + i.getEnd());
                    }
                }

                List<String> extraArgs = getClientCommandArgs(ctx.getParams());
                if (extraArgs != null)
                {
                    args.addAll(extraArgs);
                }

                File output = new File(ctx.getWorkingDirectory(), FileUtil.getBaseName(input) + ".txt");
                Wrapper wrapper = new Wrapper(ctx.getLogger());
                wrapper.execute(input, ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()).getWorkingFastaFile(), output, args);

                action.addOutput(output, "Local Haplotypes", false);
                ctx.addActions(action);

                SequenceOutputFile o = new SequenceOutputFile();
                o.setName(output.getName());
                o.setFile(output);
                o.setLibrary_id(so.getLibrary_id());
                o.setCategory("Local Haplotypes");
                o.setReadset(so.getReadset());
                ctx.addSequenceOutput(o);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }

    public static class Wrapper extends DISCVRSeqRunner
    {
        public Wrapper(Logger log)
        {
            super(log);
        }

        public File execute(File bam, File fasta, File output, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = getBaseArgs("PrintReadBackedHaplotypes");
            args.add("-I");
            args.add(bam.getPath());

            args.add("-R");
            args.add(fasta.getPath());

            args.add("-O");
            args.add(output.getPath());

            args.addAll(extraArgs);

            execute(args);

            if (!output.exists())
            {
                throw new PipelineJobException("Unable to find file: " + output.getPath());
            }

            return output;
        }
    }
}