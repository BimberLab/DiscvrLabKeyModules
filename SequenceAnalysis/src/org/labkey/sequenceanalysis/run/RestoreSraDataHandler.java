package org.labkey.sequenceanalysis.run;

import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
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
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RestoreSraDataHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public RestoreSraDataHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Restore SRA Data", "This will run SRA fasterq-dump to re-download the original FASTQ(s), based on SRA accession. This will fail for any job without an archived read data or archived read data without an SRA accession", null, Arrays.asList(
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
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
            for (ReadData rd : rs.getReadData())
            {
                if (rd.isArchived())
                {
                    String accession = rd.getSra_accession();
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
        }

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

                    File sraLog = new File(expectedFile1.getParentFile(), FileUtil.makeLegalName("sraDownload_" + timestamp + ".txt"));
                    try (PrintWriter writer = PrintWriters.getPrintWriter(IOUtil.openFileForWriting(sraLog, true)))
                    {
                        ctx.getLogger().info("Copying file to: " + expectedFile1.getPath());
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
            files.remove(file1);
            file1 = doGzip(file1);

            File file2 = new File(outDir, dataset + "_2.fastq");
            if (!file2.exists())
            {
                file2 = null;
            }
            else
            {
                files.remove(file2);
                file2 = doGzip(file2);
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
