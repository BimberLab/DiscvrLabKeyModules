/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.blast.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.blast.BLASTWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class BlastTask extends PipelineJob.Task<BlastTask.Factory>
{
    protected BlastTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(BlastTask.class);
            setLocation("webserver-high-priority");
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList("BLAST");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            BlastTask task = new BlastTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            if (!getPipelineJob().getBlastJob().getExpectedInputFile().exists())
            {
                throw new PipelineJobException("Unable to find input file: " + getPipelineJob().getBlastJob().getExpectedInputFile().getPath());
            }

            BLASTWrapper wrapper = new BLASTWrapper();
            wrapper.setLog(getJob().getLogger());
            wrapper.runBlastN(getPipelineJob().getBlastJob().getDatabaseId(), getPipelineJob().getBlastJob().getExpectedInputFile(), getPipelineJob().getBlastJob().getExpectedOutputFile(), getPipelineJob().getBlastJob().getParamMap());
            getPipelineJob().getBlastJob().setComplete(getJob().getUser(), getJob());

            return new RecordedActionSet(Collections.singleton(new RecordedAction()));
        }
        catch (IOException | IllegalArgumentException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private BlastPipelineJob getPipelineJob()
    {
        return (BlastPipelineJob)getJob();
    }
}
