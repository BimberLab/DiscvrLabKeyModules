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
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.PrintWriters;
import org.labkey.blast.BLASTWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class BlastDatabaseTask extends PipelineJob.Task<BlastDatabaseTask.Factory>
{
    protected BlastDatabaseTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(BlastDatabaseTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return List.of("Creating BLAST Database");
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            BlastDatabaseTask task = new BlastDatabaseTask(this, job);

            return task;
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info("creating BLAST database for library: " + getPipelineJob().getLibraryId());

        File originalFasta = getPipelineJob().getFasta();
        if (originalFasta == null)
        {
            throw new IllegalArgumentException("Unable to find FASTA for reference genome: " + getPipelineJob().getLibraryId());
        }

        ReferenceLibraryHelper libraryHelper = SequenceAnalysisService.get().getLibraryHelper(originalFasta);
        File fastaCopy = null;
        boolean success = false;
        try
        {
            fastaCopy = new File(getPipelineJob().getDatabaseDir(), getPipelineJob().getDatabaseGuid() + ".fasta");

            //decompress file and also touch up read names to keep BLAST happier
            try (BufferedReader reader = Readers.getReader(originalFasta); PrintWriter writer = PrintWriters.getPrintWriter(fastaCopy))
            {
                getJob().getLogger().info("creating FASTA copy of: " + originalFasta.getPath());
                getJob().getLogger().info("location: " + fastaCopy.getPath());

                String line, refName;
                Integer rowId;
                int sequenceCount = 0;
                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith(">"))
                    {
                        refName = line.substring(1);
                        refName = refName.replaceAll("\\|", "_");  //problematic for BLAST's parsing

                        //accession = libraryHelper.resolveAccession(refName);
                        rowId = libraryHelper.resolveSequenceId(refName);

                        writer.write(">lcl|" + refName + " [" + rowId + "]" + System.getProperty("line.separator"));
                        sequenceCount++;

                        if (sequenceCount % 1000 == 0)
                        {
                            getJob().getLogger().info("processed " + sequenceCount + " sequences");
                        }
                    }
                    else
                    {
                        writer.write(line + System.getProperty("line.separator"));
                    }
                }

                if (sequenceCount == 0)
                {
                    throw new PipelineJobException("There are no sequences in the input FASTA");
                }
                else
                {
                    getJob().getLogger().debug("sequence count: " + sequenceCount);
                }
            }

            BLASTWrapper wrapper = new BLASTWrapper(getJob().getLogger());
            wrapper.createDatabase(getPipelineJob().getDatabaseGuid(), null, fastaCopy, getPipelineJob().getDatabaseDir(), getJob().getLogger());
            success = true;
        }
        catch (IOException | IllegalArgumentException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (success && fastaCopy.exists())
            {
                getJob().getLogger().info("deleting FASTA copy");
                fastaCopy.delete();
            }
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("Creating BLAST Database")));
    }

    private BlastDatabasePipelineJob getPipelineJob()
    {
        return (BlastDatabasePipelineJob )getJob();
    }
}
