package org.labkey.sequenceanalysis.run.variant;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.DepthOfCoverageWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 4/24/2017.
 */
public class DepthOfCoverageHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public DepthOfCoverageHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Depth of Coverage", "This will run GATK's DepthOfCoverage tool on one or more BAM files", new LinkedHashSet<>(PageFlowUtil.set("/sequenceanalysis/field/IntervalField.js")), Arrays.asList(
                ToolParameterDescriptor.create("basename", "File Basename", "This will be used as the basename for the output table", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("outputDescription", "Description", "This will be used as the description for output files", "textarea", new JSONObject(){{
                    put("height", 150);
                }}, null),
//                ToolParameterDescriptor.create("outputBed", "Output As BED File", "If selected, the output will be converted to a BED file", "checkbox", new JSONObject(){{
//                    put("checked", true);
//                }}, true),
                ToolParameterDescriptor.create("omitZero", "Omit Positions of Zero Coverage", "If selected, any positions with zero coverage will be omitted", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, true),
                ToolParameterDescriptor.create("mbq", "Min Base Quality", "The minimum quality required for a base to be included.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("mmq", "Min Mapping Quality", "The minimum mapping quality required for an alignment to be included.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("intervals", "Intervals", "The intervals over which to merge the data.  They should be in the form: chr01:102-20394", "sequenceanalysis-intervalfield", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bam.getFileType().isType(o.getFile());
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

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<String> extraArgs = new ArrayList<>();
            String basename = ctx.getParams().getString("basename");
            if (StringUtils.trimToNull(basename) == null)
            {
                throw new PipelineJobException("No basename was provided");
            }

            List<File> inputBams = new ArrayList<>();
            Set<Integer> libraryIds = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                inputBams.add(so.getFile());
                libraryIds.add(so.getLibrary_id());
            }

            if (libraryIds.size() != 1)
            {
                throw new PipelineJobException("Not all files use the same reference library");
            }
            ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(libraryIds.iterator().next());

            String intervalString = StringUtils.trimToNull(ctx.getParams().optString("intervals"));
            if (intervalString != null)
            {
                String[] intervals = intervalString.split(";");
                validateIntervals(intervals);
                for (String i : intervals)
                {
                    extraArgs.add("-L");
                    extraArgs.add(i);
                }
            }
            else
            {
                //GATK4 now requires intervals:
                File intervalList = new File(ctx.getOutputDir(), "depthOfCoverageIntervals.intervals");
                ctx.getFileManager().addIntermediateFile(intervalList);
                extraArgs.addAll(DepthOfCoverageWrapper.generateIntervalArgsForFullGenome(rg, intervalList));
            }

            Integer mmq = ctx.getParams().optInt("mmq");
            if (mmq > 0)
            {
                extraArgs.add("--read-filter");
                extraArgs.add("MappingQualityReadFilter");
                extraArgs.add("--minimum-mapping-quality");
                extraArgs.add(mmq.toString());
            }

            Integer mbq = ctx.getParams().optInt("mbq");
            if (mbq > 0)
            {
                extraArgs.add("--min-base-quality");
                extraArgs.add(mbq.toString());
            }

            extraArgs.add("--omit-locus-table");
            extraArgs.add("--omit-interval-statistics");

            File outputFile = new File(ctx.getOutputDir(), basename + ".coverage");

            DepthOfCoverageWrapper wrapper = new DepthOfCoverageWrapper(ctx.getLogger());
            wrapper.run(inputBams, outputFile.getPath(), rg.getWorkingFastaFile(), extraArgs);
            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find file: " + outputFile.getPath());
            }

            for (String suffix : Arrays.asList("_summary", "_statistics", "_interval_summary", "_interval_statistics", "_gene_summary", "_gene_statistics", "_cumulative_coverage_counts", "_cumulative_coverage_proportions"))
            {
                File f = new File(outputFile.getPath() + suffix);
                if (f.exists())
                {
                    f.delete();
                }
            }

            boolean omitZero = ctx.getParams().optBoolean("omitZero", false);

            SequenceOutputFile so = new SequenceOutputFile();
            so.setCategory("Depth of Coverage");
            so.setName(basename);
            so.setLibrary_id(libraryIds.iterator().next());
            String outputDescription = StringUtils.trimToNull(ctx.getParams().optString("outputDescription"));
            so.setDescription(outputDescription);

            if (omitZero)
            {
                int zeroCoverage = 0;
                File outputFile2 = new File(ctx.getOutputDir(), basename + ".coverage." + (omitZero ? "gt0." : "") + ("txt"));
                try (CSVReader reader = new CSVReader(Readers.getReader(outputFile), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile2), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    String[] line;
                    int lineNo = 0;
                    while ((line = reader.readNext()) != null)
                    {
                        lineNo++;

                        if (lineNo == 1)
                        {
                            writer.writeNext(line);
                            continue;
                        }

                        if (omitZero && "0".equals(line[1]))
                        {
                            zeroCoverage++;
                            continue;
                        }

                        writer.writeNext(line);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                if (zeroCoverage > 0)
                {
                    ctx.getLogger().info("total positions skipped due to zero coverage: " + zeroCoverage);
                }

                so.setFile(outputFile2);
                outputFile.delete();
            }
            else
            {
                so.setFile(outputFile);
            }

            ctx.getFileManager().addSequenceOutput(so);
        }
    }

    public static void validateIntervals(String[] intervals) throws PipelineJobException
    {
        for (String i : intervals)
        {
            //NOTE: the contig name can contain hyphen..
            String[] tokens = i.split(":");
            if (tokens.length > 2)
            {
                throw new PipelineJobException("Invalid interval: " + i);
            }
            else if (tokens.length == 2)
            {
                String[] coords = tokens[1].split("-");
                if (coords.length != 2)
                {
                    throw new PipelineJobException("Invalid interval: " + i);
                }
            }
        }
    }
}
