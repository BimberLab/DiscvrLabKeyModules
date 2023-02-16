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
package org.labkey.jbrowse.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.JBrowseManager;
import org.labkey.jbrowse.JBrowseSchema;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.util.ArrayList;
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
public class JBrowseSessionTask extends PipelineJob.Task<JBrowseSessionTask.Factory>
{
    protected JBrowseSessionTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(JBrowseSessionTask.class);
            setLocation("webserver-high-priority");
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
            return List.of("JBrowse");
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new JBrowseSessionTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info(getPipelineJob().getMode().getDescription());

        if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.AddToExisting || getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.CreateNew)
        {
            createOrAddToSession();
        }
        else if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.ReprocessResources)
        {
            reprocessResources();
        }
        else if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.PrepareGenome)
        {
            prepareGenome(true);
        }
        else if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.ReprocessSession)
        {
            prepareGenome(false);
            reprocessResources();
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("JBrowse")));
    }

    private void prepareGenome(boolean forceReprocess) throws PipelineJobException
    {
        if (getPipelineJob().getLibraryId() == null)
        {
            throw new PipelineJobException("No genome ID provided, this is likely an upstream problem");
        }

        JBrowseSession session = JBrowseSession.getGenericGenomeSession(getPipelineJob().getLibraryId());
        List<JsonFile> jsonFiles = session.getJsonFiles(getJob().getUser(), true);
        getJob().getLogger().info("total files to reprocess: " + jsonFiles.size());
        for (JsonFile f : jsonFiles)
        {
            f.prepareResource(getJob().getLogger(), false, forceReprocess);
        }
    }

    private void reprocessResources() throws PipelineJobException
    {
        if (getPipelineJob().getJsonFiles() == null || getPipelineJob().getJsonFiles().isEmpty())
        {
            throw new PipelineJobException("No JsonFiles provided, this is likely an upstream problem");
        }

        TableInfo ti = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("objectid"), getPipelineJob().getJsonFiles(), CompareType.IN), null);
        List<JsonFile> jsonFiles = ts.getArrayList(JsonFile.class);
        getJob().getLogger().info("total files to reprocess: " + jsonFiles.size());
        for (JsonFile f : jsonFiles)
        {
            f.prepareResource(getJob().getLogger(), false, true);
        }
    }

    private void createOrAddToSession() throws PipelineJobException
    {
        boolean success = false;
        TableInfo databases = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES);
        TableInfo databaseMembers = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        String databaseGuid = getPipelineJob().getDatabaseGuid();
        List<Integer> databaseMemberRecordsCreated = new ArrayList<>();

        try
        {
            //first create the database record
            if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.CreateNew)
            {
                //Note: if you restart a failed job, this record might already exist
                TableSelector ts = new TableSelector(databases, new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
                if (!ts.exists())
                {
                    CaseInsensitiveHashMap<Object> databaseRecord = new CaseInsensitiveHashMap<>();
                    databaseRecord.put("name", getPipelineJob().getName());
                    databaseRecord.put("description", getPipelineJob().getDatabaseDescription());
                    databaseRecord.put("libraryid", getPipelineJob().getLibraryId());
                    databaseRecord.put("objectid", databaseGuid);
                    databaseRecord.put("jobid", getJob().getJobGUID());
                    databaseRecord.put("temporary", getPipelineJob().isTemporarySession());
                    databaseRecord.put("container", getJob().getContainer().getId());
                    databaseRecord.put("created", new Date());
                    databaseRecord.put("createdby", getJob().getUser().getUserId());
                    databaseRecord.put("modified", new Date());
                    databaseRecord.put("modifiedby", getJob().getUser().getUserId());

                    getJob().getLogger().debug("creating database record");
                    Table.insert(getJob().getUser(), databases, databaseRecord);
                }
                else
                {
                    getJob().getLogger().info("Existing session record found for " + databaseGuid + ", re-using");
                }
            }

            TableSelector dbTs = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
            JBrowseSession db = dbTs.getObject(JBrowseSession.class);
            if (db == null)
            {
                throw new IllegalArgumentException("Unknown database: " + databaseGuid);
            }

            getJob().getLogger().info("Preparing default genome tracks:");
            JBrowseSession session = JBrowseSession.getGenericGenomeSession(db.getLibraryId());
            session.ensureJsonFilesPrepared(getJob().getUser(), getJob().getLogger());

            List<Integer> trackIdList = new ArrayList<>();
            if (getPipelineJob().getTrackIds() != null)
                trackIdList.addAll(getPipelineJob().getTrackIds());

            List<Integer> outputFileIdList = new ArrayList<>();
            if (getPipelineJob().getOutputFileIds() != null)
                outputFileIdList.addAll(getPipelineJob().getOutputFileIds());

            if (!trackIdList.isEmpty())
            {
                getJob().getLogger().info("processing: " + trackIdList.size() + " tracks");

                //find any existing database_member records for these sequences so we dont duplicate
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(databaseMembers, PageFlowUtil.set(FieldKey.fromString("jsonfile/trackid")));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), getPipelineJob().getDatabaseGuid(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("jsonfile/trackid"), null, CompareType.NONBLANK);
                TableSelector ts = new TableSelector(databaseMembers, cols.values(), filter, null);
                List<Integer> existingTrackIds = ts.getArrayList(Integer.class);

                for (Integer trackId : trackIdList)
                {
                    JsonFile json = JsonFile.prepareJsonFileForGenomeTrack(getJob().getUser(), trackId);
                    //this could indicate a non-supported filetype
                    if (json == null)
                    {
                        getJob().getLogger().debug("No JsonFile created for track: " + trackId);
                        continue;
                    }

                    if (json.getBaseDir() == null)
                    {
                        continue;
                    }

                    if (existingTrackIds.contains(trackId))
                    {
                        getJob().getLogger().info("track is already a member of this database, skipping");
                        continue;
                    }

                    CaseInsensitiveHashMap<Object> trackRecord = new CaseInsensitiveHashMap<>();
                    trackRecord.put("database", databaseGuid);
                    trackRecord.put("jsonfile", json.getObjectId());
                    trackRecord.put("container", getJob().getContainer().getId());
                    trackRecord.put("created", new Date());
                    trackRecord.put("createdby", getJob().getUser().getUserId());
                    trackRecord.put("modified", new Date());
                    trackRecord.put("modifiedby", getJob().getUser().getUserId());
                    trackRecord = Table.insert(getJob().getUser(), databaseMembers, trackRecord);
                    databaseMemberRecordsCreated.add((Integer)trackRecord.get("rowid"));
                }
            }
            else
            {
                getJob().getLogger().info("no tracks to process");
            }

            //handle ad hoc files
            if (!outputFileIdList.isEmpty())
            {
                getJob().getLogger().info("processing: " + outputFileIdList.size() + " files");

                //find any existing database_member records for these sequences so we dont duplicate
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(databaseMembers, PageFlowUtil.set(FieldKey.fromString("jsonfile/outputfile")));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), getPipelineJob().getDatabaseGuid(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("jsonfile/outputfile"), null, CompareType.NONBLANK);
                TableSelector ts = new TableSelector(databaseMembers, cols.values(), filter, null);
                List<Integer> existingOutputFileIds = ts.getArrayList(Integer.class);

                for (Integer outputFileId : outputFileIdList)
                {
                    Integer dataId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("dataid"), new SimpleFilter(FieldKey.fromString("rowid"), outputFileId), null).getObject(Integer.class);
                    if (dataId == null)
                    {
                        getJob().getLogger().error("Unable to find dataId for output file: " + outputFileId);
                        continue;
                    }

                    JsonFile json = JsonFile.prepareJsonFileRecordForOutputFile(getJob().getUser(), outputFileId, null, getPipelineJob().getLogger());
                    //this could indicate a non-supported filetype
                    if (json == null)
                        continue;

                    if (existingOutputFileIds.contains(outputFileId))
                    {
                        getJob().getLogger().info("data file is already a member of this database, skipping");
                        continue;
                    }

                    CaseInsensitiveHashMap<Object> trackRecord = new CaseInsensitiveHashMap<>();
                    trackRecord.put("database", databaseGuid);
                    trackRecord.put("jsonfile", json.getObjectId());
                    trackRecord.put("container", getJob().getContainer().getId());
                    trackRecord.put("created", new Date());
                    trackRecord.put("createdby", getJob().getUser().getUserId());
                    trackRecord.put("modified", new Date());
                    trackRecord.put("modifiedby", getJob().getUser().getUserId());
                    trackRecord = Table.insert(getJob().getUser(), databaseMembers, trackRecord);
                    databaseMemberRecordsCreated.add((Integer) trackRecord.get("rowid"));
                }
            }

            getJob().getLogger().info("Processing resources complete");
            success = true;
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (!success)
            {
                getJob().getLogger().error("job did not appear to succeed, rolling back database inserts and files");

                if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.CreateNew)
                {
                    Table.delete(databases, databaseGuid);
                    Table.delete(databaseMembers, new SimpleFilter(FieldKey.fromString("database"), databaseGuid));
                }
                else
                {
                    if (!databaseMemberRecordsCreated.isEmpty())
                    {
                        getJob().getLogger().error("deleting database members created in this pipeline job.  the database itself will not be deleted");
                        Table.delete(databaseMembers, new SimpleFilter(FieldKey.fromString("rowid"), databaseMemberRecordsCreated, CompareType.IN));
                    }
                }
            }
        }
    }

    private JBrowseSessionPipelineJob getPipelineJob()
    {
        return (JBrowseSessionPipelineJob)getJob();
    }
}
