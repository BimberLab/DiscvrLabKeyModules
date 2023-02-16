package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PangolinHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public static final String[] PANGO_FIELDS = new String[]{"PangolinLineage", "PangolinConflicts", "PangolinAmbiguity", "PangolinVersions"};

    public enum PANGO_MODE
    {
        pangoLEARN(),
        usher(),
        both();

        PANGO_MODE()
        {

        }

        public static List<PANGO_MODE> getModes(PANGO_MODE mode)
        {
            return switch (mode)
                    {
                        case pangoLEARN -> Collections.singletonList(pangoLEARN);
                        case usher -> Collections.singletonList(usher);
                        case both -> Arrays.asList(pangoLEARN, usher);
                    };
        }
    }

    public PangolinHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Pangolin", "Runs pangolin, a tool for assigning SARS-CoV-2 data to lineage", null, List.of(
                getPangoModeOption()
        ));
    }

    public static ToolParameterDescriptor getPangoModeOption()
    {
        return ToolParameterDescriptor.create(PANGO_MODE.class.getSimpleName(), "Pangolin Mode", "Pangolin can run in either pangoLEARN mode, Usher, or both. If the latter is selected, both are run and the unique values returned.", "ldk-simplecombo", new JSONObject()
        {{
            put("multiSelect", false);
            put("allowBlank", false);
            put("storeValues", StringUtils.join(Arrays.stream(PANGO_MODE.values()).map(Enum::name).collect(Collectors.toList()), ";"));
            put("initialValues", PANGO_MODE.both.name());
        }}, null);
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && SequenceUtil.FILETYPE.fasta.getFileType().isType(o.getFile());
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Map<Integer, SequenceOutputFile> fileMap = inputFiles.stream().collect(Collectors.toMap(SequenceOutputFile::getRowid, x -> x));

            job.getLogger().info("Importing metrics:");
            List<Map<String, Object>> toInsert = new ArrayList<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(getMetricsFile(outputDir)), '\t'))
            {
                //Delete existing metrics:
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    SequenceOutputFile so = fileMap.get(Integer.parseInt(line[0]));

                    Map<String, Object> row1 = new CaseInsensitiveHashMap<>();
                    row1.put("dataid", so.getDataId());
                    row1.put("readset", so.getReadset());
                    row1.put("analysis_id", so.getAnalysis_id());
                    row1.put("category", "Pangolin");
                    row1.put("metricName", "PangolinLineage");
                    row1.put("qualvalue", line[1]);
                    row1.put("container", so.getContainer());
                    if (StringUtils.trimToNull(line[4]) != null)
                    {
                        row1.put("comment", line[4]);
                    }
                    toInsert.add(row1);

                    if (StringUtils.trimToNull(line[2]) != null)
                    {
                        Map<String, Object> row2 = new CaseInsensitiveHashMap<>();
                        row2.put("dataid", so.getDataId());
                        row2.put("readset", so.getReadset());
                        row2.put("analysis_id", so.getAnalysis_id());
                        row2.put("category", "Pangolin");
                        row2.put("metricName", "PangolinConflicts");
                        row2.put("qualvalue", line[2]);
                        row2.put("container", so.getContainer());
                        if (StringUtils.trimToNull(line[4]) != null)
                        {
                            row2.put("comment", line[4]);
                        }
                        toInsert.add(row2);
                    }

                    if (StringUtils.trimToNull(line[3]) != null)
                    {
                        Map<String, Object> row = new CaseInsensitiveHashMap<>();
                        row.put("dataid", so.getDataId());
                        row.put("readset", so.getReadset());
                        row.put("analysis_id", so.getAnalysis_id());
                        row.put("category", "Pangolin");
                        row.put("metricName", "PangolinAmbiguity");
                        row.put("qualvalue", line[3]);
                        row.put("container", so.getContainer());
                        if (StringUtils.trimToNull(line[4]) != null)
                        {
                            row.put("comment", line[4]);
                        }
                        toInsert.add(row);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
            TableInfo ti = QueryService.get().getUserSchema(job.getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
            Set<Integer> uniqueAnalyses = inputFiles.stream().map(SequenceOutputFile::getAnalysis_id).collect(Collectors.toSet());
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), "Pangolin");
            filter.addCondition(FieldKey.fromString("analysis_id"), uniqueAnalyses, CompareType.IN);

            try
            {
                final List<Map<String, Object>> toDelete = new ArrayList<>();
                new TableSelector(ti, PageFlowUtil.set("rowid", "container"), filter, null).forEachResults(rs -> {
                    toDelete.add(Map.of("rowid", rs.getInt(FieldKey.fromString("rowid")), "container", rs.getString(FieldKey.fromString("container"))));
                });

                if (!toDelete.isEmpty())
                {
                    job.getLogger().info("Deleting " + toDelete.size() + " existing metric rows");
                    ti.getUpdateService().deleteRows(job.getUser(), targetContainer, toDelete, null, null);
                }

                BatchValidationException bve = new BatchValidationException();
                ti.getUpdateService().insertRows(job.getUser(), targetContainer, toInsert, bve, null, null);

                if (bve.hasErrors())
                {
                    throw bve;
                }
            }
            catch (SQLException | BatchValidationException | InvalidKeyException | QueryUpdateServiceException | DuplicateKeyException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            //write metrics:
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(getMetricsFile(ctx.getSourceDirectory())), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (SequenceOutputFile so : inputFiles)
                {
                    PangolinHandler.PANGO_MODE pangoMode = PangolinHandler.PANGO_MODE.valueOf(ctx.getParams().optString(PangolinHandler.PANGO_MODE.class.getSimpleName(), PANGO_MODE.both.name()));
                    Map<String, String> pangolinData = runPangolin(ctx.getWorkingDirectory(), so.getFile(), ctx.getFileManager(), ctx.getLogger(), pangoMode);
                    List<String> vals = new ArrayList<>();
                    vals.add(String.valueOf(so.getRowid()));
                    for (String key : PANGO_FIELDS)
                    {
                        vals.add(pangolinData.getOrDefault(key, ""));
                    }

                    writer.writeNext(vals.toArray(new String[0]));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File getMetricsFile(File webserverDir)
        {
            return new File(webserverDir, "metrics.txt");
        }
    }

    public static File getRenamedPangolinOutput(File consensusFasta, PANGO_MODE mode)
    {
        return new File(consensusFasta.getParentFile(), FileUtil.getBaseName(consensusFasta) + "." + mode.name() + ".pangolin.csv");
    }

    private static File runUsingDocker(File outputDir, Logger log, File consensusFasta, PipelineOutputTracker tracker, List<String> extraArgs) throws PipelineJobException
    {
        if (!consensusFasta.getParentFile().equals(outputDir))
        {
            try
            {
                File consensusFastaLocal = new File(outputDir, consensusFasta.getName());
                log.info("Copying FASTA locally: " + consensusFastaLocal.getPath());
                FileUtils.copyFile(consensusFasta, consensusFastaLocal);
                tracker.addIntermediateFile(consensusFastaLocal);
                consensusFasta = consensusFastaLocal;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        File localBashScript = new File(outputDir, "dockerWrapper.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull ghcr.io/bimberlabinternal/pangolin:latest");
            writer.println("sudo $DOCKER run --rm=true \\");

            if (SequencePipelineService.get().getMaxThreads(log) != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_THREADS \\");
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            String extraArgString = extraArgs == null ? "" : " " + StringUtils.join(extraArgs, " ");
            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-u $UID \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t-w /work \\");
            writer.println("\tghcr.io/bimberlabinternal/pangolin:latest \\");
            writer.println("\tpangolin" + extraArgString + " '/work/" + consensusFasta.getName() + "'");
            writer.println("");
            writer.println("echo 'Bash script complete'");
            writer.println("");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(log);
        rWrapper.setWorkingDir(outputDir);
        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));
        tracker.addIntermediateFile(localBashScript);

        File output = new File(outputDir, "lineage_report.csv");
        if (!output.exists())
        {
            throw new PipelineJobException("Pangolin output not found: " + output.getPath());
        }

        return output;
    }

    public static Map<String, String> runPangolin(File workDir, File consensusFasta, PipelineOutputTracker tracker, Logger log, PANGO_MODE pangoMode) throws PipelineJobException
    {
        List<PANGO_MODE> modes = PANGO_MODE.getModes(pangoMode);

        Map<String, Set<String>> ret = new HashMap<>();

        for (PANGO_MODE mode : modes)
        {
            List<String> extraArgs = mode == PANGO_MODE.usher ? Collections.singletonList("--usher") : null;
            File output = runUsingDocker(workDir, log, consensusFasta, tracker, extraArgs);

            try
            {
                File outputMoved = getRenamedPangolinOutput(consensusFasta, mode);
                if (outputMoved.exists())
                {
                    outputMoved.delete();
                }

                FileUtils.moveFile(output, outputMoved);
                try (CSVReader reader = new CSVReader(Readers.getReader(outputMoved)))
                {
                    reader.readNext(); //header
                    String[] pangolinData = reader.readNext();

                    List<String> versions = new ArrayList<>();
                    if (pangolinData != null)
                    {
                        if (StringUtils.trimToNull(pangolinData[8]) != null)
                        {
                            versions.add("Pangolin: " + pangolinData[8]);
                        }

                        if (StringUtils.trimToNull(pangolinData[9]) != null)
                        {
                            versions.add("pangoLEARN: " + pangolinData[9]);
                        }

                        if (StringUtils.trimToNull(pangolinData[10]) != null)
                        {
                            versions.add("pango: " + pangolinData[10]);
                        }
                    }

                    if (pangolinData != null)
                    {
                        Set<String> vals;

                        vals = ret.containsKey("PangolinLineage") ? ret.get("PangolinLineage") : new LinkedHashSet<>();
                        vals.add(pangolinData[1]);
                        ret.put("PangolinLineage", vals);

                        vals = ret.containsKey("PangolinConflicts") ? ret.get("PangolinConflicts") : new LinkedHashSet<>();
                        vals.add(pangolinData[2]);
                        ret.put("PangolinConflicts", vals);

                        vals = ret.containsKey("PangolinAmbiguity") ? ret.get("PangolinAmbiguity") : new LinkedHashSet<>();
                        vals.add(pangolinData[3]);
                        ret.put("PangolinAmbiguity", vals);

                        vals = ret.containsKey("PangolinVersions") ? ret.get("PangolinVersions") : new LinkedHashSet<>();
                        vals.add(StringUtils.join(versions, ","));
                        ret.put("PangolinVersions", vals);
                    }
                    else
                    {
                        Set<String> vals = ret.containsKey("PangolinLineage") ? ret.get("PangolinLineage") : new LinkedHashSet<>();
                        vals.add("QC Fail");
                        ret.put("PangolinLineage", vals);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        Map<String, String> map = new HashMap<>();
        for (String key : ret.keySet())
        {
            Set<String> vals = ret.get(key);
            vals.remove("");
            String val = StringUtils.join(vals, ",");
            if ("PangolinVersions".equals(key))
            {
                val = StringUtils.join(new HashSet<>(Arrays.asList(val.split(","))), ",");
            }

            map.put(key, val);
        }

        return map;
    }
}
