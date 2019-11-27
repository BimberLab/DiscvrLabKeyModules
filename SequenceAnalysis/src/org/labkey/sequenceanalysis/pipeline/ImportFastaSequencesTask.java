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
package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class ImportFastaSequencesTask extends PipelineJob.Task<ImportFastaSequencesTask.Factory>
{
    private static final String ACTION_NAME = "Import FASTA Sequences";

    protected ImportFastaSequencesTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ImportFastaSequencesTask.class);
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
            return Arrays.asList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ImportFastaSequencesTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info("Importing sequences from file(s): ");
        for (File f : getPipelineJob().getFastas())
        {
            getJob().getLogger().info(f.getPath());
        }

        try
        {
            List<Integer> sequenceIds = new ArrayList<>();
            for (File f : getPipelineJob().getFastas())
            {
                Integer jobId = PipelineService.get().getJobId(getJob().getUser(), getJob().getContainer(), getJob().getJobGUID());
                sequenceIds.addAll(SequenceAnalysisManager.get().importRefSequencesFromFasta(getJob().getContainer(), getJob().getUser(), f, getPipelineJob().isSplitWhitespace(), getPipelineJob().getParams(), getJob().getLogger(), getPipelineJob().getOutDir(), jobId));
            }

            if (getPipelineJob().isCreateLibrary())
            {
                getJob().getLogger().info("Creating reference library");
                if (getPipelineJob().getLibraryParams() == null || StringUtils.trimToNull((String)getPipelineJob().getLibraryParams().get("name")) == null)
                {
                    throw new PipelineJobException("No name provided for reference genome");
                }

                String libraryName = (String)getPipelineJob().getLibraryParams().get("name");
                String libraryDescription = (String)getPipelineJob().getLibraryParams().get("description");
                String assemblyId = (String)getPipelineJob().getLibraryParams().get("assemblyId");
                boolean skipCacheIndexes = (boolean)getPipelineJob().getLibraryParams().get("skipCacheIndexes");
                boolean skipTriggers = (boolean)getPipelineJob().getLibraryParams().get("skipTriggers");

                SequenceAnalysisManager.get().createReferenceLibrary(sequenceIds, getJob().getContainer(), getJob().getUser(), libraryName, assemblyId, libraryDescription, skipCacheIndexes, skipTriggers, null, null);
            }

        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet(new RecordedAction(ACTION_NAME));
    }

    private ImportFastaSequencesPipelineJob getPipelineJob()
    {
        return (ImportFastaSequencesPipelineJob)getJob();
    }
}
