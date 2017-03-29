package org.labkey.GeneticsCore.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.PeekableIterator;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.GeneticsCore.pipeline.CombpRunner;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/26/2014.
 */
public class MethylationRateComparisonHandler implements SequenceOutputHandler
{
    public static final FileType METHYLATION_TYPE = new FileType(".CpG_Site_Summary.gff");
    private static final String METHYLATION_RATE_COMPARISON = "Methylation Rate Comparison";

    public MethylationRateComparisonHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Methylation Rate Comparison";
    }

    @Override
    public String getDescription()
    {
        return "This will take a set of site methylation rates (calculated separately through this module), allow you to divide them into 2 groups, and then calculate p-values for each site";
    }

    @Nullable
    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Nullable
    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/GeneticsCore/methylationRateComparison.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(GeneticsCoreModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList("/LDK/field/ExpDataField.js"));
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (METHYLATION_TYPE.isType(f.getFile()));
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getFile().getName().endsWith(".bed") && METHYLATION_RATE_COMPARISON.equals(so.getCategory()))
                {
                    job.getLogger().debug("preparing JBrowse files: " + so.getFile().getName());

                    JSONObject json = new JSONObject();
                    json.put("type", "JBrowse/View/Track/Wiggle/XYPlot");
                    //json.put("max_score", 1);

                    try
                    {
                        JBrowseService.get().prepareOutputFile(job.getUser(), job.getLogger(), so.getRowid(), true, json);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());
            ctx.addActions(action);

            JSONArray groupNames = ctx.getParams().getJSONArray("groupNames");
            ctx.getLogger().info("Group names:");
            for (int i = 0;i<groupNames.length();i++)
            {
                ctx.getLogger().info(groupNames.getString(i));
            }

            Map<Integer, Integer> fileToGroupMap = new HashMap<>();
            JSONObject map = ctx.getParams().getJSONObject("fileToGroupMap");
            for (String id : map.keySet())
            {
                fileToGroupMap.put(Integer.parseInt(id), map.getInt(id));
            }

            Integer minDatapointsPerGroup = ctx.getParams().optInt("minDatapointsPerGroup", 0);
            Integer minDepthPerSite = ctx.getParams().optInt("minDepthPerSite", 0);
            String jobDescription = ctx.getParams().optString("jobDescription");
            String statisticalMethod = ctx.getParams().optString("statisticalMethod");

            //build map of site rates
            Map<String, Map<Integer, Map<Integer, List<Double>>>> rateMap = new HashMap<>();

            int i = 0;
            for (SequenceOutputFile o : inputFiles)
            {
                i++;
                ctx.getJob().getLogger().info("processing: " + o.getName() + ", " + i + " of " + inputFiles.size());

                if (!fileToGroupMap.containsKey(o.getRowid()))
                {
                    ctx.getLogger().warn("sample is not part of a group, skipping: " + o.getName());
                    continue;
                }

                Integer groupNum = fileToGroupMap.get(o.getRowid());
                ctx.getJob().getLogger().debug("group #: " + groupNum);

                action.addInputIfNotPresent(o.getFile(), "Site Methylation Rates");

                try (BufferedReader reader = Readers.getReader(o.getFile()))
                {
                    int lineNo = 0;
                    String line;
                    int skippedForDepth = 0;
                    LINE: while ((line = reader.readLine()) != null)
                    {
                        lineNo++;

                        if (line.startsWith("#"))
                        {
                            continue;
                        }

                        if (StringUtils.trimToNull(line) == null)
                        {
                            continue;
                        }

                        String[] tokens = line.split("\t");
                        if (tokens.length < 9)
                        {
                            throw new PipelineJobException("Fewer than 9 fields found on line: " + lineNo + ", line was: [" + line + "]");
                        }

                        String chr = tokens[0];
                        Integer pos = Integer.parseInt(tokens[3]);
                        Double rate = Double.parseDouble(tokens[5]);
                        String attrs = StringUtils.trimToNull(tokens[8]);
                        if (attrs != null)
                        {
                            for (String attr : attrs.split(";"))
                            {
                                String[] pieces = attr.split("=");
                                if (pieces[0].equals("Depth"))
                                {
                                    Integer depth = Integer.parseInt(pieces[1]);
                                    if (depth < minDepthPerSite)
                                    {
                                        skippedForDepth++;
                                        continue LINE;
                                    }
                                }
                            }
                        }

                        if (!rateMap.containsKey(chr))
                        {
                            rateMap.put(chr, new HashMap<>());
                        }

                        if (!rateMap.get(chr).containsKey(pos))
                        {
                            rateMap.get(chr).put(pos, new HashMap<>());
                        }

                        if (!rateMap.get(chr).get(pos).containsKey(groupNum))
                        {
                            rateMap.get(chr).get(pos).put(groupNum, new ArrayList<>());
                        }

                        rateMap.get(chr).get(pos).get(groupNum).add(rate);
                    }

                    ctx.getLogger().info("total sites skipped due to low depth: " + skippedForDepth);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            List<File> outputs = new ArrayList<>();
            for (String chr : rateMap.keySet())
            {
                ctx.getLogger().info("processing chr: " + chr);
                Map<Integer, Map<Integer, List<Double>>> posMap = rateMap.get(chr);
                if (posMap.isEmpty())
                {
                    ctx.getLogger().info("no data for chromosome, skipping");
                    continue;
                }
                ctx.getLogger().info("total positions with data in at least one subject: " + posMap.size());

                File tsvOut = new File(ctx.getOutputDir(), "chrCombined." + chr + ".txt");
                int totalLines = 0;
                int totalPassingPositions = 0;
                int group1Skipped = 0;
                int group2Skipped = 0;
                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(tsvOut), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    for (Integer pos : posMap.keySet())
                    {
                        List<Double> group1 = posMap.get(pos).get(0);
                        if (group1 == null)
                        {
                            group1Skipped++;
                            continue;
                        }

                        List<Double> group2 = posMap.get(pos).get(1);
                        if (group2 == null)
                        {
                            group2Skipped++;
                            continue;
                        }

                        if (minDatapointsPerGroup != null)
                        {
                            boolean doSkip = false;
                            if (group1.size() < minDatapointsPerGroup)
                            {
                                group1Skipped++;
                                doSkip = true;
                            }

                            if (group2.size() < minDatapointsPerGroup)
                            {
                                group2Skipped++;
                                doSkip = true;
                            }

                            if (doSkip)
                            {
                                continue;
                            }
                        }

                        totalPassingPositions++;

                        for (Double d : group1)
                        {
                            totalLines++;
                            writer.writeNext(new String[]{chr, String.valueOf(pos), "Group1", String.valueOf(d)});
                        }

                        for (Double d : group2)
                        {
                            totalLines++;
                            writer.writeNext(new String[]{chr, String.valueOf(pos), "Group2", String.valueOf(d)});
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                ctx.getLogger().info("total skipped due to insufficient group1 datapoints: " + group1Skipped);
                ctx.getLogger().info("total skipped due to insufficient group2 datapoints: " + group2Skipped);
                ctx.getLogger().info("total passing positions: " + totalPassingPositions);

                if (totalLines == 0)
                {
                    ctx.getLogger().info("no passing positions, skipping chr: " + chr);
                    tsvOut.delete();
                    continue;
                }

                //now calculate pvalues
                ctx.getFileManager().addIntermediateFile(tsvOut);
                action.addOutput(tsvOut, "Combined Methylation Rates", false);

                AbstractCommandWrapper wrapper = new AbstractCommandWrapper(ctx.getJob().getLogger()){};
                wrapper.setOutputDir(ctx.getOutputDir());
                wrapper.setWorkingDir(ctx.getOutputDir());

                List<String> args = new ArrayList<>();
                args.add(getRPath(ctx.getLogger()));
                args.add(getScriptPath());
                args.add(tsvOut.getPath());
                args.add(statisticalMethod);

                File pvalOut = new File(ctx.getOutputDir(), "chrCombined." + chr + ".pval.txt");
                args.add(pvalOut.getPath());
                ctx.getFileManager().addIntermediateFile(pvalOut);

                wrapper.execute(args);

                outputs.add(pvalOut);
            }

            if (!outputs.isEmpty())
            {
                //now combine
                File finalOut = new File(ctx.getOutputDir(), "MethylationComparison.bed");
                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(finalOut), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    for (File f : outputs)
                    {
                        ctx.getLogger().info("processing: " + f.getName());
                        int skippedLines = 0;
                        int passingLines = 0;
                        try (CSVReader reader = new CSVReader(Readers.getReader(f), '\t'))
                        {
                            String[] line;
                            while ((line = reader.readNext()) != null)
                            {
                                if (line.length < 3)
                                {
                                    throw new PipelineJobException("invalid line: " + StringUtils.join(line, ";"));
                                }

                                if ("NA".equals(line[2]))
                                {
                                    skippedLines++;
                                }
                                else
                                {
                                    writer.writeNext(new String[]{
                                            line[0],  //sequence name
                                            String.valueOf(Integer.parseInt(line[1]) - 1),  //start, 0-based
                                            line[1],  //end, 1-based
                                            "Methlyation p-value", //name
                                            line[2],  //pval
                                            "+",      //strand
                                    });

                                    passingLines++;
                                }
                            }
                        }

                        ctx.getLogger().info("total lines skipped due to NA p-values: " + skippedLines);
                        ctx.getLogger().info("total lines accepted: " + passingLines);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                try
                {
                    //end sorted
                    ctx.getLogger().info("sorting output");
                    File sorted = new File(ctx.getOutputDir(), "tmp.bed");
                    CommandWrapper wrapper = SequencePipelineService.get().getCommandWrapper(ctx.getLogger());
                    wrapper.execute(Arrays.asList("/bin/sh", "-c", "cat '" + finalOut.getPath() + "' | grep -v '^#' | sort -V -k1,1 -k2,2n"), ProcessBuilder.Redirect.appendTo(sorted));
                    finalOut.delete();
                    FileUtils.moveFile(sorted, finalOut);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                if (!SequencePipelineService.get().hasMinLineCount(finalOut, 1))
                {
                    ctx.getLogger().warn("no lines in final BED file, skipping other steps: " + finalOut.getName());
                }
                else
                {
                    action.addOutput(finalOut, "Methylation Rate Comparison", false);

                    SequenceOutputFile so = new SequenceOutputFile();
                    so.setName(ctx.getJob().getDescription());
                    so.setDescription(jobDescription);
                    so.setFile(finalOut);
                    so.setCategory(METHYLATION_RATE_COMPARISON);
                    ctx.addSequenceOutput(so);

                    //now run comb-p
                    boolean runCombp = ctx.getParams().optBoolean("combp", false);
                    if (runCombp)
                    {
                        ctx.getLogger().info("running comb-p");
                        Integer dist = ctx.getParams().optInt("distance", 300);
                        Double seed = ctx.getParams().optDouble("seed", 0.05);
                        Integer step = ctx.getParams().optInt("step", 100);

                        CombpRunner combp = new CombpRunner(ctx.getLogger());
                        File outBed = combp.runCompP(finalOut, ctx.getOutputDir(), dist, seed, step);
                        SequenceOutputFile so2 = new SequenceOutputFile();
                        so2.setName("Comb-p: " + ctx.getJob().getDescription());
                        so2.setDescription("Comb-p: " + jobDescription);
                        so2.setFile(outBed);
                        so2.setCategory("Comb-p Sites");
                        ctx.addSequenceOutput(so2);

                        //build new combined human-readable table with all data
                        //# APPEND WILCOX AND SIDAK-P PVALUES TO DATATABLE
                        if (outBed.exists())
                        {
                            File table = new File(ctx.getOutputDir(), "MethylationComparison.txt");
                            List<Pair<Interval, String>> intervals = new ArrayList<>();
                            try (FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(outBed.getAbsolutePath(), new BEDCodec(), false))
                            {
                                try (CloseableTribbleIterator<BEDFeature> it = reader.iterator())
                                {
                                    while (it.hasNext())
                                    {
                                        BEDFeature f = it.next();

                                        //NOTE: name is the field holding p-val
                                        intervals.add(Pair.of(new Interval(f.getContig(), f.getStart(), f.getEnd()), f.getName()));
                                    }
                                }
                            }
                            catch (IOException e)
                            {
                                throw new PipelineJobException(e);
                            }

                            buildCombinedTable(table, intervals, inputFiles, ctx, true);

                            SequenceOutputFile soTable = new SequenceOutputFile();
                            soTable.setName("Comb-p Table: " + ctx.getJob().getDescription());
                            soTable.setDescription("Comb-p Raw Data: " + jobDescription);
                            soTable.setFile(table);
                            soTable.setCategory("Comb-p Combined Data");
                            ctx.addSequenceOutput(soTable);
                        }

                        File plot = new File(outBed.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(outBed.getName())) + ".manhattan.png");
                        if (plot.exists())
                        {
                            SequenceOutputFile so3 = new SequenceOutputFile();
                            so3.setName("Comb-p Plot: " + ctx.getJob().getDescription());
                            so3.setDescription("Comb-p Plot: " + jobDescription);
                            so3.setFile(plot);
                            so3.setCategory("Comb-p Manhattan Plot");
                            ctx.addSequenceOutput(so3);
                        }
                        else
                        {
                            ctx.getLogger().debug("no plot found, expected: " + plot.getPath());
                        }
                    }
                    else
                    {
                        ctx.getLogger().info("skipping comb-p");
                    }
                }
            }
            else
            {
                ctx.getLogger().info("no outputs, cannot create final BED file");
            }

            action.setEndTime(new Date());
        }

        private String getScriptPath() throws PipelineJobException
        {
            String path = "/external/methylationComparison.R";
            Module module = ModuleLoader.getInstance().getModule(GeneticsCoreModule.NAME);
            Resource script = module.getModuleResource(path);
            if (script == null || !script.exists())
                throw new PipelineJobException("Unable to find file: " + script.getPath() + " in module: " + GeneticsCoreModule.NAME);

            File f = ((FileResource) script).getFile();
            if (!f.exists())
                throw new PipelineJobException("Unable to find file: " + f.getPath());

            return f.getPath();
        }

        private String getRPath(Logger log)
        {
            String exePath = "Rscript";

            String packagePath = SequencePipelineService.get().inferRPath(log);
            if (StringUtils.trimToNull(packagePath) != null)
            {
                exePath = (new File(packagePath, exePath)).getPath();
            }

            return exePath;
        }
    }

    public static void buildCombinedTable(File table, List<Pair<Interval, String>> intervals, List<SequenceOutputFile> inputFiles, PipelineContext ctx, boolean includePValCol) throws PipelineJobException
    {
        ctx.getLogger().info("building combined data table");

        List<FeatureReader<GFF3Feature>> gffReaders = new ArrayList<>();
        List<PeekableIterator<GFF3Feature>> iterators = new ArrayList<>();
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(table), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            List<String> header = new ArrayList<>();
            header.add("IntervalName");
            header.add("Chr");
            header.add("Position");
            if (includePValCol)
                header.add("Pval");
            for (SequenceOutputFile in : inputFiles)
            {
                header.add(in.getName());
            }
            writer.writeNext(header.toArray(new String[header.size()]));

            int totalBedLines = 0;
            for (SequenceOutputFile so : inputFiles)
            {
                FeatureReader<GFF3Feature> gffReader = AbstractFeatureReader.getFeatureReader(so.getFile().getAbsolutePath(), new GFF3Codec(), false);
                gffReaders.add(gffReader);
                iterators.add(new PeekableIterator(gffReader.iterator()));
            }

            for (Pair<Interval, String> pair : intervals)
            {
                Interval il = pair.first;
                String intervalName = pair.second;

                ctx.getLogger().info("inspecting interval: " + il.getContig() + ": " + il.getStart() + "-" + il.getEnd());
                int positionsInspected = 0;
                int positionsWithData = 0;

                totalBedLines++;

                int position = il.getStart();
                while (position <= il.getEnd())
                {
                    positionsInspected++;

                    boolean hasData = false;
                    List<String> data = new ArrayList<>(inputFiles.size());
                    for (PeekableIterator<GFF3Feature> it : iterators)
                    {
                        //scan until we hit this position or exceed
                        GFF3Feature f = null;
                        while (it.hasNext())
                        {
                            GFF3Feature toInspect = it.peek();
                            Interval i2 = new Interval(toInspect.getContig(), toInspect.getStart(), toInspect.getEnd());

                            if (toInspect.getContig().equals(il.getContig()) && toInspect.getStart() == position)
                            {
                                //this is a match, select and increment iterator
                                f = it.next();
                                break;
                            }
                            else if (i2.compareTo(il) > 0)
                            {
                                //if this interval is beyond the target position, abort but dont increment iterator
                                break;
                            }

                            //scan to next
                            it.next();
                        }

                        if (f != null)
                        {
                            hasData = true;
                            data.add(f.getScore() == null ? "" :String.valueOf(f.getScore()));
                        }
                        else
                        {
                            data.add("");
                        }
                    }

                    if (hasData)
                    {
                        List<String> row = new ArrayList<>();
                        row.add(il.getContig() + ":" + il.getStart() + "-" + il.getEnd());
                        row.add(il.getContig());
                        row.add(String.valueOf(position));

                        if (includePValCol)
                            row.add(intervalName); //this is the column holding score
                        row.addAll(data);

                        writer.writeNext(row.toArray(new String[row.size()]));

                        positionsWithData++;
                    }

                    position++;
                }

                ctx.getLogger().info("positions inspected: " + positionsInspected);
                ctx.getLogger().info("positions with data: " + positionsWithData);
            }

            ctx.getLogger().info("total BED lines inspected: " + totalBedLines);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            for (PeekableIterator it : iterators)
            {
                try
                {
                    it.close();
                }
                catch (Throwable e)
                {
                    //ignore
                }
            }

            for (FeatureReader r : gffReaders)
            {
                try
                {
                    r.close();
                }
                catch (Throwable e)
                {
                    //ignore
                }
            }
        }
    }
}
