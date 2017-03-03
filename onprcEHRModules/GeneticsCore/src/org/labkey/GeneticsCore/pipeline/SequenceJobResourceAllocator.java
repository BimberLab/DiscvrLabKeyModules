package org.labkey.GeneticsCore.pipeline;

import org.apache.commons.io.FileUtils;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by bbimber
 *
 */
public class SequenceJobResourceAllocator implements HTCondorJobResourceAllocator
{
    public static class Factory implements HTCondorJobResourceAllocator.Factory
    {
        @Override
        public HTCondorJobResourceAllocator getAllocator()
        {
            return new SequenceJobResourceAllocator();
        }

        @Override
        public Integer getPriority(TaskId taskId)
        {
            return (taskId.getNamespaceClass() != null && (
                    taskId.getNamespaceClass().getName().startsWith("org.labkey.sequenceanalysis.pipeline")
            )) ? 50 : null;
        }
    }

    private boolean isSequenceNormalizationTask(PipelineJob job)
    {
        return (job.getActiveTaskId() != null && job.getActiveTaskId().getNamespaceClass().getName().endsWith("SequenceNormalizationTask"));
    }

    private boolean isSequenceAlignmentTask(PipelineJob job)
    {
        return (job.getActiveTaskId() != null && job.getActiveTaskId().getNamespaceClass().getName().endsWith("SequenceAlignmentTask"));
    }

    private boolean isSequenceSequenceOutputHandlerTask(PipelineJob job)
    {
        return (job.getActiveTaskId() != null && job.getActiveTaskId().getNamespaceClass().getName().endsWith("SequenceOutputHandlerRemoteTask"));
    }

    private Long _totalFileSize = null;
    private static final Long UNABLE_TO_DETERMINE = -1L;

    @Override
    public Integer getMaxRequestCpus(PipelineJob job)
    {
        if (job instanceof HasJobParams)
        {
            Map<String, String> params = ((HasJobParams)job).getJobParams();
            if (params.get("resourceSettings.resourceSettings.cpus") != null)
            {
                Integer cpus = ConvertHelper.convert(params.get("resourceSettings.resourceSettings.cpus"), Integer.class);
                job.getLogger().debug("using CPUs supplied by job: " + cpus);
                return cpus;
            }
        }

        if (isSequenceNormalizationTask(job))
        {
            job.getLogger().debug("setting max CPUs to 4");
            return 4;
        }

        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        if (isSequenceAlignmentTask(job))
        {
            //10gb
            if (totalFileSize < 10e9)
            {
                job.getLogger().debug("file size less than 10gb, lowering CPUs to 8");

                return 8;
            }
            else if (totalFileSize < 20e9)
            {
                job.getLogger().debug("file size less than 20gb, lowering CPUs to 16");

                return 16;
            }

            job.getLogger().debug("file size greater than 20gb, using 24 CPUs");

            return 24;
        }

        return null;
    }

    @Override
    public Integer getMaxRequestMemory(PipelineJob job)
    {
        Integer ret = null;
        if (job instanceof HasJobParams)
        {
            Map<String, String> params = ((HasJobParams) job).getJobParams();
            if (params.get("resourceSettings.resourceSettings.ram") != null)
            {
                Integer ram = ConvertHelper.convert(params.get("resourceSettings.resourceSettings.ram"), Integer.class);
                job.getLogger().debug("using RAM supplied by job: " + ram);
                ret = ram;
            }
        }

        if (isSequenceNormalizationTask(job))
        {
            job.getLogger().debug("setting memory to 24");
            return 24;
        }

        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        boolean hasHaplotypeCaller = false;
        boolean hasStar = false;

        if (isSequenceSequenceOutputHandlerTask(job))
        {
            File jobXml = new File(job.getLogFile().getParentFile(), FileUtil.getBaseName(job.getLogFile()) + ".job.xml");
            if (jobXml.exists())
            {
                try (BufferedReader reader = Readers.getReader(jobXml))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        if (line.contains("HaplotypeCallerHandler"))
                        {
                            hasHaplotypeCaller = true;
                            break;
                        }
                    }
                }
                catch (IOException e)
                {
                    job.getLogger().error(e.getMessage(), e);
                }
            }
        }

        if (isSequenceAlignmentTask(job))
        {
            if (totalFileSize <= 10e9)
            {
                job.getLogger().debug("file size less than 10gb, setting memory to 16");

                ret = 16;
            }
            else if (totalFileSize <= 30e9)
            {
                job.getLogger().debug("file size less than 30gb, setting memory to 24");

                ret = 24;
            }
            else
            {
                job.getLogger().debug("file size greater than 30gb, setting memory to 48");

                ret = 48;
            }

            Map<String, String> params = job.getParameters();
            if (params != null)
            {
                if (params.containsKey(PipelineStep.StepType.analysis.name()) && params.get(PipelineStep.StepType.analysis.name()).contains("HaplotypeCallerAnalysis"))
                {
                    hasHaplotypeCaller = true;
                }

                if (params.containsKey(PipelineStep.StepType.alignment.name()) && params.get(PipelineStep.StepType.alignment.name()).contains("STAR"))
                {
                    hasStar = true;
                }
            }
        }

        if (hasHaplotypeCaller)
        {
            Integer orig = ret;
            ret = ret == null ? 48 : Math.max(ret, 48);
            if (!ret.equals(orig))
            {
                job.getLogger().debug("adjusting RAM for HaplotypeCaller to: " + ret);
            }
        }

        if (hasStar)
        {
            Integer orig = ret;
            ret = ret == null ? 48 : Math.max(ret, 48);
            if (!ret.equals(orig))
            {
                job.getLogger().debug("adjusting RAM for STAR to: " + ret);
            }
        }

        return ret;
    }

    @Override
    public List<String> getExtraSubmitScriptLines(PipelineJob job)
    {
        if (job instanceof HasJobParams)
        {
            Map<String, String> params = ((HasJobParams)job).getJobParams();
            if (params.get("resourceSettings.resourceSettings.weekLongJob") != null)
            {
                Boolean weekLongJob = ConvertHelper.convert(params.get("resourceSettings.resourceSettings.weekLongJob"), Boolean.class);
                if (weekLongJob)
                {
                    job.getLogger().debug("adding WEEK_LONG_JOB as supplied by job");
                    return Arrays.asList("concurrency_limits = WEEK_LONG_JOBS");
                }
            }
        }

        Long totalFileSize = getFileSize(job);
        if (UNABLE_TO_DETERMINE.equals(totalFileSize))
        {
            return null;
        }

        //25gb alignment
        if (isSequenceAlignmentTask(job) && totalFileSize > 25e9)
        {
            return Arrays.asList("concurrency_limits = WEEK_LONG_JOBS");
        }

        return null;
    }

    private Long getFileSize(PipelineJob job)
    {
        if (_totalFileSize != null)
        {
            return _totalFileSize;
        }

        List<File> files = SequencePipelineService.get().getSequenceJobInputFiles(job);
        if (files != null && !files.isEmpty())
        {
            long total = 0;
            for (File f : files)
            {
                if (f.exists())
                {
                    total += f.length();
                }
            }

            job.getLogger().info("total input files: " + files.size());
            job.getLogger().info("total size of input files: " + FileUtils.byteCountToDisplaySize(total));

            _totalFileSize = total;
        }
        else
        {
            _totalFileSize = UNABLE_TO_DETERMINE;
        }

        return _totalFileSize;
    }
}