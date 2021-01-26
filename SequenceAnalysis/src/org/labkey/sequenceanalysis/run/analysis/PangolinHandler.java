package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
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
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PangolinHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public PangolinHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Pangolin", "Runs pangolin, a tool for assigning SARS-CoV-2 data to lineage", null, Collections.emptyList());
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
                    toInsert.add(row1);

                    if (StringUtils.trimToNull(line[2]) != null)
                    {
                        Map<String, Object> row2 = new CaseInsensitiveHashMap<>();
                        row2.put("dataid", so.getDataId());
                        row2.put("readset", so.getReadset());
                        row2.put("analysis_id", so.getAnalysis_id());
                        row2.put("category", "Pangolin");
                        row2.put("metricName", "PangolinLineageConfidence");
                        row2.put("value", Double.parseDouble(line[2]));
                        row2.put("container", so.getContainer());
                        toInsert.add(row2);
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
            PangolinHandler.updatePangolinRefs(ctx.getLogger());

            //write metrics:
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(getMetricsFile(ctx.getSourceDirectory())), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (SequenceOutputFile so : inputFiles)
                {
                    String[] pangolinData = runPangolin(so.getFile(), ctx.getLogger(), ctx.getFileManager());
                    writer.writeNext(new String[]{String.valueOf(so.getRowid()), (pangolinData == null ? "QC Fail" : pangolinData[1]), (pangolinData == null ? "" : pangolinData[2])});
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

    public static void updatePangolinRefs(Logger log) throws PipelineJobException
    {
        log.info("Updating pangolin lineages");

        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(log);

        File pangolin = SequencePipelineService.get().getExeForPackage("PANGOLINPATH", "pangolin-update.sh");
        wrapper.execute(Arrays.asList("/bin/bash", pangolin.getPath()));
    }

    public static String[] runPangolin(File consensusFasta, Logger log, PipelineOutputTracker tracker) throws PipelineJobException
    {
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(log);
        wrapper.setWorkingDir(consensusFasta.getParentFile());

        File pangolin = SequencePipelineService.get().getExeForPackage("PANGOLINPATH", "pangolin");

        List<String> args = new ArrayList<>();
        args.add(pangolin.getPath());
        args.add(consensusFasta.getPath());

        wrapper.execute(args);

        File output = new File(consensusFasta.getParentFile(), "lineage_report.csv");
        if (!output.exists())
        {
            throw new PipelineJobException("Pangolin output not found: " + output.getPath());
        }

        tracker.addIntermediateFile(output);
        try (CSVReader reader = new CSVReader(Readers.getReader(output)))
        {
            reader.readNext(); //header
            String[] line = reader.readNext();

            return line;
        }
        catch (IOException e)
        {
            throw new PipelineJobException();
        }
    }
}
