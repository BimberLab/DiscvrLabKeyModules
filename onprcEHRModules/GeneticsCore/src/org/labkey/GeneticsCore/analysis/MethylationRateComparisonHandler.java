package org.labkey.GeneticsCore.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.data.Container;
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
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 8/26/2014.
 */
public class MethylationRateComparisonHandler implements SequenceOutputHandler
{
    private static final FileType _methylationType = new FileType(".CpG_Site_Summary.gff");

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
        return f.getFile() != null && (_methylationType.isType(f.getFile()));
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

            Integer minDatapointsPerGroup = ctx.getParams().optInt("minDatapointsPerGroup");
            Integer minDepthPerSite = ctx.getParams().optInt("minDepthPerSite");
            String jobDescription = ctx.getParams().optString("jobDescription");
            String statisticalMethod = ctx.getParams().optString("statisticalMethod");

            //build map of site rates
            Map<String, Map<Integer, Map<Integer, Set<Double>>>> rateMap = new HashMap<>();

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

                action.addInput(o.getFile(), "Site Methylation Rates");

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

                        String[] tokens = line.split("\t");
                        if (tokens.length < 9)
                        {
                            throw new PipelineJobException("Invalid entry on line: " + lineNo);
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
                            rateMap.get(chr).get(pos).put(groupNum, new HashSet<>());
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
                Map<Integer, Map<Integer, Set<Double>>> posMap = rateMap.get(chr);
                if (posMap.isEmpty())
                {
                    ctx.getLogger().info("no data for chromosome, skipping");
                    continue;
                }
                ctx.getLogger().info("total positions: " + posMap.size());

                File tsvOut = new File(ctx.getOutputDir(), "chrCombined." + chr + ".txt");
                int totalLines = 0;
                int group1Skipped = 0;
                int group2Skipped = 0;
                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(tsvOut), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    for (Integer pos : posMap.keySet())
                    {
                        Set<Double> group1 = posMap.get(pos).get(0);
                        if (group1 == null)
                        {
                            group1Skipped++;
                            continue;
                        }

                        Set<Double> group2 = posMap.get(pos).get(1);
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
                File finalOut = new File(ctx.getOutputDir(), "MethylationComparison.gff");
                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(finalOut), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    for (File f : outputs)
                    {
                        try (CSVReader reader = new CSVReader(Readers.getReader(f), '\t'))
                        {
                            writer.writeNext(new String[]{"##gff-version 3"});

                            String[] line;
                            while ((line = reader.readNext()) != null)
                            {
                                if (line.length < 3)
                                {
                                    throw new PipelineJobException("invalid line: " + StringUtils.join(line, ";"));
                                }

                                writer.writeNext(new String[]{
                                        line[0],  //sequence name
                                        ".",      //source
                                        "methylation_rate_comparison",  //type
                                        line[1],  //start, 1-based
                                        line[1],  //end
                                        line[2],  //pval
                                        "+",      //strand
                                        "0",      //phase
                                        ""        //attributes
                                });
                            }
                        }
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
                    File sorted = new File(ctx.getOutputDir(), "tmp.gff");
                    CommandWrapper wrapper = SequencePipelineService.get().getCommandWrapper(ctx.getLogger());
                    wrapper.execute(Arrays.asList("/bin/sh", "-c", "cat '" + finalOut.getPath() + "' | grep -v '^#' | sort -k1,1 -k2,2n"), ProcessBuilder.Redirect.appendTo(sorted));
                    finalOut.delete();
                    FileUtils.moveFile(sorted, finalOut);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                action.addOutput(finalOut, "Methylation Rate Comparison", false);

                SequenceOutputFile so = new SequenceOutputFile();
                so.setName(ctx.getJob().getDescription());
                so.setDescription(jobDescription);
                so.setFile(finalOut);
                so.setCategory("Methylation Rate Comparison");
                ctx.addSequenceOutput(so);
            }
            else
            {
                ctx.getLogger().info("no outputs, cannot create final GFF file");
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
}
