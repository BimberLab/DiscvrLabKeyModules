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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.BLASTSchema;
import org.labkey.blast.BLASTWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
            return Arrays.asList("Creating BLAST Database");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            BlastDatabaseTask task = new BlastDatabaseTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info("creating BLAST database for library: " + getPipelineJob().getLibraryId());

        TableInfo referenceLibraries = DbSchema.get(BLASTManager.SEQUENCE_ANALYSIS).getTable("reference_libraries");
        TableSelector ts = new TableSelector(referenceLibraries, PageFlowUtil.set("name", "description", "fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), getPipelineJob().getLibraryId()), null);
        Map<String, Object> libraryMap = ts.getObject(Map.class);
        Integer dataId = (Integer)libraryMap.get("fasta_file");
        if (dataId == null)
        {
            throw new IllegalArgumentException("Unable to find reference library: " + getPipelineJob().getLibraryId());
        }

        //first create the database record
        CaseInsensitiveHashMap databaseRecord = new CaseInsensitiveHashMap();
        databaseRecord.put("name", libraryMap.get("name"));
        databaseRecord.put("description", libraryMap.get("description"));
        databaseRecord.put("libraryid", getPipelineJob().getLibraryId());

        String databaseGuid = new GUID().toString().toUpperCase();
        databaseRecord.put("objectid", databaseGuid);

        databaseRecord.put("container", getJob().getContainer().getId());
        databaseRecord.put("created", new Date());
        databaseRecord.put("createdby", getJob().getUser().getUserId());
        databaseRecord.put("modified", new Date());
        databaseRecord.put("modifiedby", getJob().getUser().getUserId());

        TableInfo databases = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_DATABASES);
        Table.insert(getJob().getUser(), databases, databaseRecord);

        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null || !data.getFile().exists())
        {
            throw new IllegalArgumentException("Unable to find FASTA File for reference library: " + getPipelineJob().getLibraryId());
        }

        File unzipped = null;
        boolean success = false;
        try
        {
            unzipped = new File(BLASTManager.get().getDatabaseDir(), databaseGuid + ".fasta");
            Compress.decompressGzip(data.getFile(), unzipped);

            BLASTWrapper wrapper = new BLASTWrapper();
            wrapper.setLog(getJob().getLogger());
            wrapper.createDatabase(databaseGuid, null, unzipped);
            success = true;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (unzipped != null && unzipped.exists())
            {
                unzipped.delete();
            }

            if (!success)
            {
                getJob().getLogger().info("deleting database record because of job failure");
                Table.delete(databases, databaseGuid);
            }
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("Creating BLAST Database")));
    }

    private BlastDatabasePipelineJob getPipelineJob()
    {
        return (BlastDatabasePipelineJob )getJob();
    }
}
