package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.reader.Readers;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.pipeline.AlignmentInitTask;
import org.labkey.sequenceanalysis.pipeline.PrepareAlignerIndexesTask;
import org.labkey.sequenceanalysis.pipeline.SequenceAlignmentJob;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService.SEQUENCE_TOOLS_PARAM;

public class SequenceRemoteIntegrationTests extends SequenceIntegrationTests.AbstractAnalysisPipelineTestCase
{
    private static final String PROJECT_NAME = "SequenceRemoteIntegrationTests";

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    @BeforeClass
    public static void initialSetUp() throws Exception
    {
        doInitialSetUp(PROJECT_NAME);
    }

    private File setupConfigDir(File outDir) throws IOException
    {
        File baseDir = new File(outDir, "config");
        if (baseDir.exists())
        {
            FileUtils.deleteDirectory(baseDir);
        }

        baseDir.mkdirs();

        if (_sampleData == null)
        {
            throw new IOException("_sampleData was null");
        }

        File source = new File(_sampleData, "remotePipeline");
        if (!source.exists())
        {
            throw new IOException("Unable to find file: " + source.getPath());
        }

        FileUtils.copyFile(new File(source, "sequenceanalysisConfig.xml"), new File(baseDir, "sequenceanalysisConfig.xml"));

        try (PrintWriter writer = PrintWriters.getPrintWriter(new File(baseDir, "pipelineConfig.xml")); BufferedReader reader = Readers.getReader(new File(source, "pipelineConfig.xml")))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.contains("@@SEQUENCEANALYSIS_TOOLS@@"))
                {
                    String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SEQUENCE_TOOLS_PARAM);
                    if (StringUtils.trimToNull(path) == null)
                    {
                        path = PipelineJobService.get().getAppProperties().getToolsDirectory();
                    }

                    path = path.replaceAll("\\\\", "/");
                    line = line.replaceAll("@@SEQUENCEANALYSIS_TOOLS@@", path);
                    _log.info("Writing to pipelineConfig.xml: " + line);
                }
                else if (line.contains("@@WORK_DIR@@"))
                {
                    line = line.replaceAll("@@WORK_DIR@@", outDir.getPath().replaceAll("\\\\", "/"));
                    _log.info("Writing to pipelineConfig.xml: " + line);
                }

                writer.println(line);
            }
        }

        return baseDir;
    }

    @AfterClass
    public static void cleanup()
    {
        doCleanup(PROJECT_NAME);
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Test
    public void BasicRemoteJob() throws Exception
    {
        File outDir = new File(_pipelineRoot, "clusterBootstrap");
        if (outDir.exists())
        {
            FileUtils.deleteDirectory(outDir);
        }

        outDir.mkdirs();

        executeJobRemote(outDir, null);

        try
        {
            // Not ideal.  This job runs extremely quickly.  When the folder cleanup happens, it seems that some process is holding on
            // to the sequence file created in setup, so delete fails on that file.  Search should be disabled, but there might be another FileWatcher.
            // This delay is designed to let that thread catch up.
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void RunBwaRemote() throws Exception
    {
        if (!isExternalPipelineEnabled())
            return;

        String jobName = "TestBWAMem_" + System.currentTimeMillis();
        JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
        config.put("alignment", "BWA-Mem");
        appendSamplesForAlignment(config, _readsets);

        SequenceAlignmentJob job = SequenceAlignmentJob.createForReadsets(_project, _context.getUser(), "RemoteJob1", "Test of remote pipeline", config, config.getJSONArray("readsetIds"), false).get(0);
        File outDir = new File(_pipelineRoot, "remoteBwa");
        if (outDir.exists())
        {
            FileUtils.deleteDirectory(outDir);
        }

        outDir.mkdirs();
        job.getLogFile().getParentFile().mkdirs();

        _readsets.forEach(rs -> job.getSequenceSupport().cacheReadset(rs));

        //Force the init task to run
        job.setActiveTaskId(new TaskId(AlignmentInitTask.class));
        AlignmentInitTask task = (AlignmentInitTask)job.getActiveTaskFactory().createTask(job);
        WorkDirectory wd = job.getActiveTaskFactory().createWorkDirectory(job.getJobGUID(), job, job.getLogger());
        assertNotNull("Sequence support is null", job.getSequenceSupport());
        assertEquals("Readsets not cached", _readsets.size(), job.getSequenceSupport().getCachedReadsets().size());
        task.setWorkDirectory(wd);
        task.run();

        //Now move to remote tasks
        job.setActiveTaskId(new TaskId(PrepareAlignerIndexesTask.class));

        File jobFile = new File(outDir, "bwaRemote.job.json.txt");
        job.writeToFile(jobFile);

        executeJobRemote(outDir, jobFile);

        //check outputs
        try
        {
            PipelineJob job2 = PipelineJob.readFromFile(jobFile);
            Assert.assertEquals("Incorrect status", PipelineJob.TaskStatus.complete, job2.getActiveTaskStatus());
            File workingFasta = job.getTargetGenome().getWorkingFastaFile();
            Assert.assertNotNull("Genome FASTA not set", workingFasta);
            File idx = new File(workingFasta.getPath() + ".fai");
            Assert.assertTrue("FASTA index not created, expected: " + idx.getPath(), idx.exists());
        }
        catch (AssertionError e)
        {
            writeJobLogToLog(job);

            _log.info("Files in job folder: " + job.getLogFile().getParentFile().getPath());
            for (File f : job.getLogFile().getParentFile().listFiles())
            {
                _log.info(f.getName());
            }

            throw e;
        }
    }

    protected void executeJobRemote(File workDir, @Nullable File jobJson) throws IOException
    {
        List<String> args = PipelineService.get().getClusterStartupArguments();

        File configDir = setupConfigDir(workDir);
        args.add("-configdir=" + configDir.getPath());

        if (jobJson != null)
        {
            args.add(jobJson.toURI().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(workDir);

        _log.info("Executing job in '" + pb.directory().getAbsolutePath() + "': " + String.join(" ", pb.command()));

        Process proc;
        try
        {
            pb.redirectErrorStream(true);
            proc = pb.start();
            File logFile = new File(workDir, "clusterBootstrap.txt");
            try (BufferedReader procReader = Readers.getReader(proc.getInputStream());PrintWriter writer = PrintWriters.getPrintWriter(logFile))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    writer.println(line);
                }
            }

            proc.waitFor();

            if (proc.exitValue() != 0)
            {
                try (BufferedReader reader = Readers.getReader(logFile))
                {
                    _log.error("Output from ClusterBootstrap:");
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        _log.error(line);
                    }
                }
            }

            Assert.assertEquals("Non-zero exit from ClusterBootstrap", 0, proc.exitValue());
        }
        catch (InterruptedException | IOException e)
        {
            throw new RuntimeException(e);
        }
    }

}
