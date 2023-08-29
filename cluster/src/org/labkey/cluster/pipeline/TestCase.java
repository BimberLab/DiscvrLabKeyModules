package org.labkey.cluster.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.cluster.ClusterModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 7/12/2017.
 */
public class TestCase extends Assert
{
    protected static final Logger _log = LogManager.getLogger(TestCase.class);
    private static final String PROJECT_NAME = "ClusterTestProject";

    @BeforeClass
    public static void initialSetUp() throws Exception
    {
        //pre-clean
        doCleanup(PROJECT_NAME);

        Container project = ContainerManager.getForPath(PROJECT_NAME);
        if (project == null)
        {
            project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, TestContext.get().getUser());
            Set<Module> modules = new HashSet<>();
            modules.addAll(project.getActiveModules());
            modules.add(ModuleLoader.getInstance().getModule(ClusterModule.class));
            project.setActiveModules(modules);
        }
    }

    @AfterClass
    public static void cleanup()
    {
        doCleanup(PROJECT_NAME);
    }

    protected static void doCleanup(String projectName)
    {
        Container project = ContainerManager.getForPath(projectName);
        if (project != null)
        {
            File pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
            try
            {
                if (pipelineRoot.exists())
                {
                    File[] contents = pipelineRoot.listFiles();
                    for (File f : contents)
                    {
                        if (f.exists())
                        {
                            if (f.isDirectory())
                                FileUtils.deleteDirectory(f);
                            else
                                f.delete();
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            ContainerManager.deleteAll(project, TestContext.get().getUser());
        }
    }

    @Test
    public void basicTest() throws Exception
    {
        Container c = ContainerManager.getForPath(PROJECT_NAME);
        for (RemoteExecutionEngine<?> engine : PipelineJobService.get().getRemoteExecutionEngines())
        {
            _log.info("testing engine: " + engine.getType());
            if (engine instanceof AbstractClusterExecutionEngine<?> acee)
            {
                runTestJob(c, TestContext.get().getUser(), acee);
            }
        }
    }

    public static class TestRunner implements ClusterService.ClusterRemoteTask
    {
        public long _sleep = 0l;

        public TestRunner(long sleep)
        {
            _sleep = sleep;
        }

        @Override
        public void run(Logger log)
        {
            log.info("Running!");

            if (_sleep > 0)
            {
                try
                {
                    Thread.sleep(_sleep);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void runTestJob(Container c, User u, AbstractClusterExecutionEngine engine) throws PipelineJobException
    {
        boolean orig = engine.isDebug();
        try
        {
            engine.setDebug(true);

            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            File workDir = new File(root.getRootPath(), "ClusterPipelineTest");
            if (workDir.exists())
            {
                if (workDir.isDirectory())
                    FileUtils.deleteDirectory(workDir);
                else
                    workDir.delete();
            }
            workDir.mkdirs();

            //submit job
            File log1 = new File(workDir, "pipeline1." + engine.getType() + ".log");
            PipelineJob job1 = ClusterService.get().createClusterRemotePipelineJob(c, u, engine.getType() + "_" + engine.getConfig().getLocation() + "TestJob1", engine, new TestRunner(100000), log1);
            PipelineService.get().queueJob(job1);
            Thread.sleep(5000);
            engine.updateStatusForAll(PageFlowUtil.set(job1.getJobGUID()));
            Thread.sleep(5000);
            engine.cancelJob(job1.getJobGUID());

            long start = System.currentTimeMillis();
            long timeout = 30 * 1000; //30 secs
            while (!isJobDone(job1))
            {
                Thread.sleep(1000);

                long duration = System.currentTimeMillis() - start;
                if (duration > timeout)
                {
                    //NOTE: it's possible a job could time out on a busy cluster.  rather than fail, continue in case there's a second engine to test
                    _log.warn("timed out waiting for job: " + job1.getDescription());
                    break;
                }
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            engine.setDebug(orig);
        }
    }

    private static boolean isJobDone(PipelineJob job) throws Exception
    {
        TableInfo ti = PipelineService.get().getJobsTable(job.getUser(), job.getContainer());
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("job"), job.getJobGUID()), null);
        Map<String, Object> map = ts.getMap();

        if (PipelineJob.TaskStatus.complete.matches((String)map.get("status")))
            return true;

        //look for errors
        boolean error = PipelineJob.TaskStatus.error.matches((String)map.get("status"));
        if (error)
        {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = Readers.getReader(job.getLogFile()))
            {
                sb.append("*******************\n");
                sb.append("Error running cluster integration tests.  Pipeline log:\n");
                String line;
                while ((line = reader.readLine()) != null)
                {
                    sb.append(line).append('\n');
                }

                sb.append("*******************\n");
            }

            _log.error(sb.toString());

            throw new Exception("There was an error running job: " + (job == null ? "PipelineJob was null" : job.getDescription()));
        }

        return false;
    }
}
