package org.labkey.sequenceanalysis.run;

import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.pipeline.ReadsetCreationTask;
import org.labkey.sequenceanalysis.pipeline.SequenceNormalizationTask;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class RestoreSraDataHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public RestoreSraDataHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Restore SRA Data", "This will run SRA fasterq-dump to re-download the original FASTQ(s), based on SRA accession. This will fail for any job without an archived read data or archived read data without an SRA accession", null, List.of(
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject()
                {{
                    put("checked", true);
                }}, false)
        ));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
    }

    @Override
    public boolean supportsSraArchivedData()
    {
        return true;
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
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceReadsetProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (readsets.size() != 1)
            {
                throw new PipelineJobException("Expected jobs to be split and have a single readset as input");
            }

            Readset rs = readsets.get(0);
            job.getLogger().info("Restoring readset: " + rs.getName());
            int totalArchivedPairs = 0;

            Map<String, List<ReadData>> readdataToSra = new HashMap<>();
            for (ReadData rd : rs.getReadData())
            {
                String accession = rd.getSra_accession();
                if (accession != null)
                {
                    if (!readdataToSra.containsKey(accession))
                    {
                        readdataToSra.put(accession, new ArrayList<>());
                    }

                    readdataToSra.get(accession).add(rd);
                }

                if (rd.isArchived())
                {
                    if (accession == null)
                    {
                        throw new PipelineJobException("Missing accession for archived readdata: " + rd.getRowid());
                    }

                    totalArchivedPairs++;
                    support.cacheExpData(ExperimentService.get().getExpData(rd.getFileId1()));
                    if (rd.getFileId2() != null)
                    {
                        support.cacheExpData(ExperimentService.get().getExpData(rd.getFileId2()));
                    }
                }
            }

            if (totalArchivedPairs == 0)
            {
                throw new PipelineJobException("There are no readdata marked as archived");
            }

            // Determine if we need to merge any readdata:
            Set<String> updatedAccessions = new HashSet<>();
            HashMap<String, Integer> accessionToReads = new HashMap<>();
            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                for (String accession : readdataToSra.keySet())
                {
                    List<ReadData> toMerge = readdataToSra.get(accession);
                    if (toMerge.stream().map(ReadData::isArchived).collect(Collectors.toSet()).size() > 1)
                    {
                        throw new PipelineJobException("SRA group contains a mix of archived and non-archived readdata: " + accession);
                    }

                    if (!toMerge.get(0).isArchived())
                    {
                        continue;
                    }

                    if (readdataToSra.get(accession).size() > 1)
                    {
                        job.getLogger().debug("Consolidating multiple readdata for: " + accession);

                        ReadDataImpl rd = new ReadDataImpl();
                        rd.setReadset(rs.getReadsetId());
                        rd.setArchived(true);
                        rd.setSra_accession(accession);

                        ExpData data1 = ExperimentService.get().getExpData(toMerge.get(0).getFileId1());
                        File expectedFastq1 = new File(data1.getFile().getParentFile(), accession + "_1.fastq.gz");
                        ExpData expData1 = ExperimentService.get().getExpDataByURL(expectedFastq1, ContainerManager.getForId(rs.getContainer()));
                        if (expData1 == null)
                        {
                            expData1 = ExperimentService.get().createData(ContainerManager.getForId(rs.getContainer()), new DataType("SequenceData"), expectedFastq1.getName());
                        }
                        expData1.setDataFileURI(expectedFastq1.toURI());
                        expData1.save(job.getUser());
                        rd.setFileId1(expData1.getRowId());
                        support.cacheExpData(expData1);

                        if (toMerge.get(0).getFileId2() != null)
                        {
                            ExpData data2 = ExperimentService.get().getExpData(toMerge.get(0).getFileId2());
                            File expectedFastq2 = new File(data2.getFile().getParentFile(), accession + "_2.fastq.gz");
                            ExpData expData2 = ExperimentService.get().getExpDataByURL(expectedFastq2, ContainerManager.getForId(rs.getContainer()));
                            if (expData2 == null)
                            {
                                expData2 = ExperimentService.get().createData(ContainerManager.getForId(rs.getContainer()), new DataType("SequenceData"), expectedFastq2.getName());
                            }
                            expData2.setDataFileURI(expectedFastq2.toURI());
                            expData2.save(job.getUser());
                            rd.setFileId2(expData2.getRowId());

                            support.cacheExpData(expData2);
                        }

                        rd.setContainer(rs.getContainer());
                        rd.setCreated(new Date());
                        rd.setModified(new Date());
                        rd.setCreatedBy(job.getUser().getUserId());
                        rd.setModifiedBy(job.getUser().getUserId());
                        rd.setPlatformUnit(accession);

                        job.getLogger().debug("Merging readdata for accession: " + accession);
                        File sraLog = new File(data1.getFile().getParentFile(), FileUtil.makeLegalName("sraDownload.txt"));
                        try (PrintWriter writer = PrintWriters.getPrintWriter(IOUtil.openFileForWriting(sraLog, sraLog.exists())))
                        {
                            for (ReadData r : toMerge)
                            {
                                ExpData d1 = ExperimentService.get().getExpData(r.getFileId1());
                                ExpData d2 = r.getFileId2() == null ? null : ExperimentService.get().getExpData(r.getFileId2());
                                writer.println("Condensing/merging readdata: " + r.getRowid() + ", " + r.getFileId1() + ", " + d1.getFile().getPath() + ", " + (r.getFileId2() == null ? "N/A" : r.getFileId2()) + ", " + (r.getFileId2() == null ? "N/A" : d2.getFile().getPath()));

                                List<Map<String, Object>> toDelete = List.of(Map.of("rowid", r.getRowid()));
                                QueryService.get().getUserSchema(job.getUser(), ContainerManager.getForId(r.getContainer()), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_READ_DATA).getUpdateService().deleteRows(job.getUser(), ContainerManager.getForId(r.getContainer()), toDelete, null, null);
                            }

                            rd = Table.insert(job.getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA), rd);
                            writer.println("Adding merged readdata: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + ExperimentService.get().getExpData(rd.getFileId1()).getFile().getPath() + ", " + (rd.getFileId2() == null ? "N/A" : rd.getFileId2()) + ", " + (rd.getFileId2() == null ? "N/A" : ExperimentService.get().getExpData(rd.getFileId2()).getFile().getPath()));
                        }
                        catch (QueryUpdateServiceException | SQLException | InvalidKeyException | BatchValidationException e)
                        {
                            throw new PipelineJobException(e);
                        }
                        updatedAccessions.add(accession);
                    }

                    int totalReads = toMerge.stream().map(ReadData::getTotalReads).reduce(0, Integer::sum);
                    job.getLogger().debug("Total reads from prior data: " + totalReads);
                    accessionToReads.put(accession, totalReads);
                }

                transaction.commit();
            }

            support.cacheReadset(rs.getReadsetId(), job.getUser(), true);
            support.cacheObject(UPDATED_ACCESSIONS, StringUtils.join(updatedAccessions, ";"));
            support.cacheObject(ACCESSION_TO_READS, accessionToReads);
        }

        private Map<String, Integer> getCachedReadCounts(SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            return support.getCachedObject(ACCESSION_TO_READS, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, String.class, Integer.class));
        }

        private static final String UPDATED_ACCESSIONS = "updatedAccessions";
        private static final String ACCESSION_TO_READS = "accessionToReads";

        @Override
        public void complete(PipelineJob job, List<Readset> readsets, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            Readset rs = readsets.get(0);
            List<Map<String, Object>> rows = new ArrayList<>();

            for (ReadData rd : rs.getReadData())
            {
                if (rd.isArchived())
                {
                    ExpData d1 = ExperimentService.get().getExpData(rd.getFileId1());
                    if (!d1.getFile().exists())
                    {
                        throw new PipelineJobException("Missing file: " + d1.getFile());
                    }

                    if (rd.getFileId2() != null)
                    {
                        ExpData d2 = ExperimentService.get().getExpData(rd.getFileId2());
                        if (!d2.getFile().exists())
                        {
                            throw new PipelineJobException("Missing file: " + d2.getFile());
                        }
                    }

                    Map<String, Object> toUpdate = new HashMap<>();
                    toUpdate.put("rowid", rd.getRowid());
                    toUpdate.put("archived", false);
                    toUpdate.put("container", rd.getContainer());

                    rows.add(toUpdate);

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
                    filter.addCondition(FieldKey.fromString("category"), "Readset");
                    filter.addCondition(FieldKey.fromString("container"), rs.getContainer());
                    filter.addCondition(FieldKey.fromString("dataId"), rd.getFileId1());
                    boolean hasMetrics = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), PageFlowUtil.set("RowId"), filter, null).exists();
                    if (!hasMetrics)
                    {
                        List<Integer> toAdd = new ArrayList<>(rd.getFileId1());
                        if (rd.getFileId2() != null)
                        {
                            toAdd.add(rd.getFileId2());
                        }

                        for (int dataId : toAdd)
                        {
                            //then delete/add:
                            ReadsetCreationTask.addQualityMetricsForReadset(rs, dataId, job, true);
                        }
                    }
                    else
                    {
                        job.getLogger().info("Existing metrics found, will not re-import");
                    }

                    Map<String, Object> rsUpdate = new HashMap<>();
                    rsUpdate.put("rowid", rs.getRowId());
                    rsUpdate.put("modified", new Date());
                    rsUpdate.put("modifiedby", job.getUser().getUserId());
                    Table.update(job.getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS), rsUpdate, rs.getRowId());
                }
            }

            Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
            TableInfo ti = QueryService.get().getUserSchema(job.getUser(), target, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_READ_DATA);
            try
            {
                ti.getUpdateService().updateRows(job.getUser(), target, rows, rows, null, null);
            }
            catch (InvalidKeyException | BatchValidationException | QueryUpdateServiceException | SQLException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<String> accessionsNeedingMetrics = Arrays.asList(ctx.getSequenceSupport().getCachedObject(UPDATED_ACCESSIONS, String.class).split(";"));
            Map<String, Integer> accessionToReads = getCachedReadCounts(ctx.getSequenceSupport());

            Readset rs = readsets.get(0);
            final String timestamp = FileUtil.getTimestamp();
            for (ReadData rd : rs.getReadData())
            {
                if (rd.isArchived())
                {
                    String accession = rd.getSra_accession();
                    if (accession == null)
                    {
                        throw new PipelineJobException("Missing accession for archived readdata: " + rd.getRowid());
                    }

                    File expectedFile1 = ctx.getSequenceSupport().getCachedData(rd.getFileId1());
                    File expectedFile2 = rd.getFileId2() == null ? null : ctx.getSequenceSupport().getCachedData(rd.getFileId2());

                    FastqDumpWrapper wrapper = new FastqDumpWrapper(ctx.getLogger());
                    Pair<File, File> files = wrapper.downloadSra(accession, ctx.getOutputDir());

                    long lines1 = SequenceUtil.getLineCount(files.first) / 4;
                    ctx.getJob().getLogger().debug("Reads in " + files.first.getName() + ": " + lines1);
                    if (lines1 != accessionToReads.get(accession))
                    {
                        throw new PipelineJobException("Reads found in file, " + lines1 + ", does not match expected: " + accessionToReads.get(accession) + " for file: " + files.first.getPath());
                    }

                    if (files.second != null)
                    {
                        long lines2 = SequenceUtil.getLineCount(files.second) / 4;
                        ctx.getJob().getLogger().debug("Reads in " + files.second.getName() + ": " + lines2);
                        if (lines2 != accessionToReads.get(accession))
                        {
                            throw new PipelineJobException("Reads found in file, " + lines2 + ", does not match expected: " + accessionToReads.get(accession) + " for file: " + files.second.getPath());
                        }
                    }

                    if (accessionsNeedingMetrics.contains(accession))
                    {
                        SequenceNormalizationTask.generateAndWriteMetrics(ctx.getJob(), files.first);
                        if (files.second != null)
                        {
                            SequenceNormalizationTask.generateAndWriteMetrics(ctx.getJob(), files.second);
                        }
                    }

                    File sraLog = new File(expectedFile1.getParentFile(), FileUtil.makeLegalName("sraDownload.txt"));
                    try (PrintWriter writer = PrintWriters.getPrintWriter(IOUtil.openFileForWriting(sraLog, sraLog.exists())))
                    {
                        ctx.getLogger().info("Copying file to: " + expectedFile1.getPath());
                        if (expectedFile1.exists())
                        {
                            ctx.getLogger().debug("Deleting pre-existing file: " + expectedFile1.getPath());
                            expectedFile1.delete();
                        }
                        Files.copy(files.first.toPath(), expectedFile1.toPath());
                        ctx.getFileManager().addIntermediateFile(files.first);
                        writer.println("Downloaded " + expectedFile1.getName() + " from " + accession + " on " + timestamp + ", size: " + FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(expectedFile1)));

                        if (expectedFile2 != null)
                        {
                            if (files.second == null)
                            {
                                throw new PipelineJobException("Missing expected second-mate file");
                            }

                            ctx.getLogger().info("Copying file to: " + expectedFile2.getPath());
                            if (expectedFile2.exists())
                            {
                                ctx.getLogger().debug("Deleting pre-existing file: " + expectedFile2.getPath());
                                expectedFile2.delete();
                            }
                            Files.copy(files.second.toPath(), expectedFile2.toPath());
                            ctx.getFileManager().addIntermediateFile(files.second);
                            writer.println("Downloaded " + expectedFile2.getName() + " from SRA " + accession + " on " + timestamp + ", size: " + FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(expectedFile2)));
                        }
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }
    }

    public static class FastqDumpWrapper extends AbstractCommandWrapper
    {
        public FastqDumpWrapper(@Nullable Logger logger)
        {
            super(logger);
        }

        public Pair<File, File> downloadSra(String dataset, File outDir) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());

            args.add("-S");
            args.add("--include-technical");

            Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (threads != null)
            {
                args.add("--threads");
                args.add(threads.toString());
            }

            args.add("--temp");
            args.add(SequencePipelineService.get().getJavaTempDir());

            args.add("-f"); //force-overwrite

            args.add("-O");
            args.add(outDir.getPath());

            args.add(dataset);

            //NOTE: sratoolkit requires this to be set:
            addToEnvironment("HOME", System.getProperty("user.home"));

            execute(args);

            List<File> files = new ArrayList<>(Arrays.asList(Objects.requireNonNull(outDir.listFiles((dir, name) -> name.startsWith(dataset)))));

            File file1 = new File(outDir, dataset + "_1.fastq");
            if (!file1.exists())
            {
                throw new PipelineJobException("Missing file: " + file1.getPath());
            }
            file1 = doGzip(file1);
            files.remove(file1);

            File file2 = new File(outDir, dataset + "_2.fastq");
            if (!file2.exists())
            {
                file2 = null;
            }
            else
            {
                file2 = doGzip(file2);
                files.remove(file2);
            }

            if (!files.isEmpty())
            {
                getLogger().info("Deleting extra files: ");
                files.forEach(f -> {
                    getLogger().info(f.getName());
                    f.delete();
                });
            }

            return Pair.of(file1, file2);
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("SRATOOLKIT_PATH", "fasterq-dump");
        }

        private File doGzip(File input) throws PipelineJobException
        {
            getLogger().info("gzipping file: " + input.getPath());
            if (SystemUtils.IS_OS_WINDOWS)
            {
                return Compress.compressGzip(input);
            }
            else
            {
                // run gzip directly for speed:
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getLogger());
                wrapper.execute(Arrays.asList("/bin/bash", "-c", "gzip -f '" + input.getPath() + "'"));

                return new File(input.getPath() + ".gz");
            }
        }
    }
}
