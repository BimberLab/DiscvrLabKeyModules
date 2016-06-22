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
import org.apache.log4j.Logger;
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
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;

import java.io.File;
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
    private PipelineJob _job;
    private WorkDirectory _wd;
    private SequencePipelineSettings _settings;
    private TaskFileManager _fileManager;
    private File _workLocation;
    public static final String FASTQ_DATA_INPUT_NAME = "Input FASTQ File";
    public static final String BAM_INPUT_NAME = "Input BAM File";
    public static final String SEQUENCE_DATA_INPUT_NAME = "Input Sequence File";
    public static final String NORMALIZED_FASTQ_OUTPUTNAME = "Normalized FASTQ File";
    public static final String MERGED_FASTQ_OUTPUTNAME = "Merged FASTQ File";
    public static final String BARCODED_FASTQ_OUTPUTNAME = "Barcoded FASTQ File";
    public static final String NORMALIZATION_SUBFOLDER_NAME = "Normalization";
    public static final String PREPROCESSING_SUBFOLDER_NAME = "Preprocessing";
    public static final String SHARED_SUBFOLDER_NAME = "Shared";  //the subfolder within which the Reference DB and aligner index files will be created
    public static final String ARCHIVED_INPUT_SEQUENCE = "Archived Input Sequence";

    public static final int CORES = Runtime.getRuntime().availableProcessors();

    public SequenceTaskHelper(PipelineJob job, WorkDirectory wd)
    {
        this(job, wd, null);
    }

    public SequenceTaskHelper(PipelineJob job, File workLocation)
    {
        this(job, null, workLocation);
    }

    private SequenceTaskHelper(PipelineJob job, WorkDirectory wd, @Nullable File workLocation)
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

    public Logger getLogger()
    {
        return getJob().getLogger();
    }

    //make sure Exp data objects exist for all input files.
    public void createExpDatasForInputs()
    {
        for (SequenceReadsetImpl rs : _settings.getReadsets(getJob().getJobSupport(SequenceAnalysisJob.class)))
        {
            for (ReadDataImpl rd : rs.getReadData())
            {
                if (rd.getFileId1() == null && rd.getFile1() != null)
                {
                    ExpData d = createExpData(rd.getFile1());
                    if (d != null)
                    {
                        rd.setFileId1(d.getRowId());
                    }
                }

                if (rd.getFileId2() == null && rd.getFile2() != null)
                {
                    ExpData d = createExpData(rd.getFile2());
                    if (d != null)
                    {
                        rd.setFileId2(d.getRowId());
                    }
                }
            }
        }
    }

    public ExpData createExpData(File f)
    {
        _job.getLogger().debug("Creating Exp data for file: " + f.getName());
        ExpData d = ExperimentService.get().createData(_job.getContainer(), new DataType("SequenceFile"));

        f = FileUtil.getAbsoluteCaseSensitiveFile(f);

        d.setName(f.getName());
        d.setDataFileURI(f.toURI());
        _job.getLogger().debug("The saved filepath is: " + f.getPath());
        d.save(_job.getUser());
        return d;
    }

    @Override
    public PipelineJob getJob()
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
        return getSupport().getAnalysisDirectory();
    }

    public <StepType extends PipelineStep> PipelineStepProvider<StepType> getSingleStep(Class<StepType> stepType) throws PipelineJobException
    {
        List<PipelineStepProvider<StepType>> providers = SequencePipelineService.get().getSteps(getJob(), stepType);
        if (providers.isEmpty())
        {
            throw new PipelineJobException("No steps found for type: " + stepType.getName());
        }
        else if (providers.size() > 1)
        {
            throw new PipelineJobException("More than 1 step was supplied of type: " + stepType.getName());
        }

        return providers.get(0);
    }

    public SequencePipelineSettings getSettings()
    {
        return _settings;
    }

    public static String getExpectedNameForInput(String fn)
    {
        FileType gz = new FileType(".gz");
        if (gz.isType(fn))
            return fn.replaceAll(".gz$", "");
        else
            return fn;
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

    public FileAnalysisJobSupport getSupport()
    {
        return (FileAnalysisJobSupport)_job;
    }

    public SequenceAnalysisJobSupport getSequenceSupport()
    {
        return (SequenceAnalysisJobSupport)_job;
    }

    public static boolean isAlignmentUsed(PipelineJob job)
    {
        return !StringUtils.isEmpty(job.getParameters().get(PipelineStep.StepType.alignment.name()));
    }

    public void logModuleVersions()
    {
        getLogger().info("SequenceAnalysis Module Version: " + ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getFormattedVersion() + " (r" + (ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getVcsRevision()) + ")");
        getLogger().info("Pipeline Module Version: " + ModuleLoader.getInstance().getModule("pipeline").getFormattedVersion() + " (r" + (ModuleLoader.getInstance().getModule("pipeline").getVcsRevision()) + ")");
    }

    public static Integer getMaxThreads(PipelineJob job)
    {
        return getMaxThreads(job.getLogger());
    }

    public static Integer getMaxThreads(Logger log)
    {
        String threads = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQUENCEANALYSIS_MAX_THREADS");
        if (StringUtils.trimToNull(threads) != null && NumberUtils.isNumber(threads))
        {
            try
            {
                return Integer.parseInt(threads);
            }
            catch (NumberFormatException e)
            {
                log.error("Non-integer value for SEQUENCEANALYSIS_MAX_THREADS: " + threads);
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
        //cache params, as needed:
        for (PipelineStep.StepType stepType : PipelineStep.StepType.values())
        {
            for (PipelineStepProvider fact : SequencePipelineService.get().getProviders(stepType.getStepClass()))
            {
                for (ToolParameterDescriptor pd : (List<ToolParameterDescriptor>)fact.getParameters())
                {
                    if (pd instanceof ToolParameterDescriptor.CachableParam)
                    {
                        ((ToolParameterDescriptor.CachableParam)pd).doCache(getJob(), pd.extractValue(getJob(), fact), getSequenceSupport());
                    }
                }
            }
        }
    }
}
