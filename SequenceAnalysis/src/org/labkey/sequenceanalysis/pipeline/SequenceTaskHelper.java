/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequencePipelineServiceImpl;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: bbimber
 * Date: 4/21/12
 * Time: 5:40 PM
 */
public class SequenceTaskHelper implements PipelineContext
{
    private final SequenceJob _job;
    private final WorkDirectory _wd;
    private final SequencePipelineSettings _settings;
    private TaskFileManager _fileManager;
    private final File _workLocation;
    public static final String FASTQ_DATA_INPUT_NAME = "Input FASTQ File";
    public static final String BAM_INPUT_NAME = "Input BAM File";
    public static final String SEQUENCE_DATA_INPUT_NAME = "Input Sequence File";
    public static final String NORMALIZED_FASTQ_OUTPUTNAME = "Normalized FASTQ File";
    public static final String BARCODED_FASTQ_OUTPUTNAME = "Barcoded FASTQ File";
    public static final String NORMALIZATION_SUBFOLDER_NAME = "Normalization";
    public static final String PREPROCESSING_SUBFOLDER_NAME = "Preprocessing";
    public static final String SHARED_SUBFOLDER_NAME = "Shared";  //the subfolder within which the Reference DB and aligner index files will be created

    public static final int CORES = Runtime.getRuntime().availableProcessors();

    public SequenceTaskHelper(SequenceJob job, WorkDirectory wd)
    {
        this(job, wd, null);
    }

    public SequenceTaskHelper(SequenceJob job, File workLocation)
    {
        this(job, null, workLocation);
    }

    private SequenceTaskHelper(SequenceJob job, WorkDirectory wd, @Nullable File workLocation)
    {
        _job = job;
        _wd = wd;
        _workLocation = workLocation == null ? wd.getDir() : workLocation;  //TODO: is this the right behavior??
        _fileManager = new TaskFileManagerImpl(_job, getWorkingDirectory(), wd);
        _settings = new SequencePipelineSettings(_job.getParameters());
    }

    public TaskFileManager getFileManager()
    {
        return _fileManager;
    }

    public void setFileManager(TaskFileManager fileManager)
    {
        _fileManager = fileManager;
    }

    @Override
    public Logger getLogger()
    {
        return getJob().getLogger();
    }

    public ExpData createExpData(File f)
    {
        return createExpData(f, _job);
    }

    public static ExpData createExpData(File f, PipelineJob job)
    {
        job.getLogger().debug("Creating Exp data for file: " + f.getName());
        ExpData d = ExperimentService.get().createData(job.getContainer(), new DataType("SequenceFile"));

        f = FileUtil.getAbsoluteCaseSensitiveFile(f);

        d.setName(f.getName());
        d.setDataFileURI(f.toURI());
        job.getLogger().debug("The saved filepath is: " + f.getPath());
        d.save(job.getUser());
        return d;
    }

    @Override
    public SequenceJob getJob()
    {
        return _job;
    }

    @Override
    public @Nullable WorkDirectory getWorkDir()
    {
        return _wd;
    }

    @Override
    public File getWorkingDirectory()
    {
        return _workLocation;
    }

    @Override
    public File getSourceDirectory()
    {
        return getSourceDirectory(false);
    }

    @Override
    public File getSourceDirectory(boolean forceParent)
    {
        return getJob().getWebserverDir(forceParent);
    }

    public <StepType extends PipelineStep> PipelineStepProvider<StepType> getSingleStep(Class<StepType> stepType) throws PipelineJobException
    {
        List<PipelineStepCtx<StepType>> steps = SequencePipelineService.get().getSteps(getJob(), stepType);
        if (steps.isEmpty())
        {
            throw new PipelineJobException("No steps found for type: " + stepType.getName());
        }
        else if (steps.size() > 1)
        {
            throw new PipelineJobException("More than 1 step was supplied of type: " + stepType.getName());
        }

        return steps.get(0).getProvider();
    }

    public SequencePipelineSettings getSettings()
    {
        return _settings;
    }

    public static Integer getExpRunIdForJob(PipelineJob job) throws PipelineJobException
    {
        return getExpRunIdForJob(job, true);
    }

    public static Integer getExpRunIdForJob(PipelineJob job, boolean throwUnlessFound) throws PipelineJobException
    {
        Integer jobId = PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getJobGUID());
        Integer parentJobId = PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getParentGUID());

        TableInfo runs = ExperimentService.get().getSchema().getTable("ExperimentRun");
        TableSelector ts = new TableSelector(runs, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromString("JobId"), jobId), null);
        Map<String, Object>[] rows = ts.getMapArray();

        if (rows.length == 0)
        {
            ts = new TableSelector(runs, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromString("JobId"), parentJobId), null);
            rows = ts.getMapArray();
        }

        if (rows.length != 1)
        {
            if (throwUnlessFound)
                throw new PipelineJobException("Incorrect row count when querying ExpRuns for: " + jobId + ".  Found: " + rows.length);
            else
                return null;
        }
        Map<String, Object> row = rows[0];
        return (Integer)row.get("rowid");
    }

    public static String getUnzippedBaseName(File file)
    {
        return getUnzippedBaseName(file.getName());
    }

    //returns the basename of the file, automatically removing .gz, if present
    public static String getUnzippedBaseName(String filename)
    {
        filename = filename.replaceAll("\\.gz$", "");
        return FilenameUtils.getBaseName(filename);
    }

    public static String getMinimalBaseName(String filename)
    {
        while (filename.contains("."))
        {
            filename = FilenameUtils.getBaseName(filename);
        }

        return filename;
    }

    @Override
    public SequenceAnalysisJobSupport getSequenceSupport()
    {
        return _job.getSequenceSupport();
    }

    public FileAnalysisJobSupport getSupport()
    {
        return _job.getJobSupport(FileAnalysisJobSupport.class);
    }

    public static boolean isAlignmentUsed(PipelineJob job)
    {
        return !StringUtils.isEmpty(job.getParameters().get(PipelineStep.CorePipelineStepTypes.alignment.name()));
    }

    public static void logModuleVersions(Logger log)
    {
        String vcs1 = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getVcsRevision();
        log.info("SequenceAnalysis Module Version: " + ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getReleaseVersion() + (StringUtils.isEmpty(vcs1) ? "" : " (r" + vcs1 + ")"));

        String vcs2 = ModuleLoader.getInstance().getModule("pipeline").getVcsRevision();
        log.info("Pipeline Module Version: " + ModuleLoader.getInstance().getModule("pipeline").getReleaseVersion() + (StringUtils.isEmpty(vcs2) ? "" : " (r" + vcs2 + ")"));
        log.debug("java.io.tmpDir: " + System.getProperty("java.io.tmpdir"));
        try
        {
            File tmp = FileUtil.createTempFile("sa-tmp", "tmp");
            log.debug("temp file location: " + tmp.getParent());
            tmp.delete();
        }
        catch (IOException e)
        {
            log.error(e);
        }

        try
        {
            log.debug("hostname: " + InetAddress.getLocalHost().getHostName());
        }
        catch (UnknownHostException e)
        {
            log.debug("unable to determine hostname: " + e.getMessage(), e);
        }
    }

    public static Integer getMaxThreads(PipelineJob job)
    {
        return getMaxThreads(job.getLogger());
    }

    private static final String THREAD_PROP_NAME = "SEQUENCEANALYSIS_MAX_THREADS";

    public static Integer getMaxThreads(Logger log)
    {

        //read environment
        String threads = StringUtils.trimToNull(System.getenv(THREAD_PROP_NAME));
        if (NumberUtils.isCreatable(threads))
        {
            try
            {
                return Integer.parseInt(threads);
            }
            catch (NumberFormatException e)
            {
                log.error("Non-integer value for SEQUENCEANALYSIS_MAX_THREADS: " + threads, new Exception());
            }
        }

        threads = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(THREAD_PROP_NAME);
        if (StringUtils.trimToNull(threads) != null && NumberUtils.isCreatable(threads))
        {
            try
            {
                return Integer.parseInt(threads);
            }
            catch (NumberFormatException e)
            {
                log.error("Non-integer value for SEQUENCEANALYSIS_MAX_THREADS: " + threads, new Exception());
            }
        }
        else
        {
            return CORES;
        }

        return null;
    }

    public void cacheExpDatasForParams() throws PipelineJobException
    {
        Map<Class<? extends  PipelineStep>, String> map = SequencePipelineServiceImpl.get().getPipelineStepTypes();
        //cache params, as needed:
        for (Class<? extends PipelineStep> stepType : map.keySet())
        {
            for (PipelineStepProvider<?> fact : SequencePipelineService.get().getProviders(stepType))
            {
                for (ToolParameterDescriptor pd : fact.getParameters())
                {
                    if (pd instanceof ToolParameterDescriptor.CachableParam p)
                    {
                        for (Object o : pd.extractAllValues(getJob(), fact))
                        {
                            p.doCache(getJob(), o, getSequenceSupport());
                        }
                    }
                }
            }
        }
    }
}
