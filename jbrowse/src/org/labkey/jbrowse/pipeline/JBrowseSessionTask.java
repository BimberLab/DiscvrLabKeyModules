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

import org.apache.commons.io.FileUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
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
import org.labkey.jbrowse.JBrowseRoot;
import org.labkey.jbrowse.JBrowseSchema;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            return Arrays.asList("JBrowse");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            JBrowseSessionTask task = new JBrowseSessionTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

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
        else if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.RecreateDatabase)
        {
            recreateDatabase(getPipelineJob().getDatabaseGuid());
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("JBrowse")));
    }

    private void recreateDatabase(String databaseGuid) throws PipelineJobException
    {
        JBrowseRoot root = JBrowseRoot.getRoot();
        root.setLogger(getJob().getLogger());

        try
        {
            getJob().getLogger().info("preparing session JSON files");
            root.prepareDatabase(getJob().getContainer(), getJob().getUser(), databaseGuid);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void reprocessResources() throws PipelineJobException
    {
        TableInfo ti = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("objectid"), getPipelineJob().getJsonFiles(), CompareType.IN), null);
        List<JsonFile> jsonFiles = ts.getArrayList(JsonFile.class);
        try
        {
            for (JsonFile f : jsonFiles)
            {
                if (f.getSequenceId() != null)
                {
                    JBrowseManager.get().preprareReferenceJson(getJob().getContainer(), getJob().getUser(), f.getSequenceId(), getJob().getLogger(), true);
                }
                else if (f.getTrackId() != null)
                {
                    JBrowseManager.get().preprareFeatureTrackJson(getJob().getContainer(), getJob().getUser(), f.getTrackId(), getJob().getLogger(), true);
                }
                else
                {
                    //nothing to do?
                }
            }

            //next update any DBs using these resources
            TableInfo databaseMembers = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
            TableSelector ts2 = new TableSelector(databaseMembers, PageFlowUtil.set("database"), new SimpleFilter(FieldKey.fromString("jsonfile"), getPipelineJob().getJsonFiles(), CompareType.IN), null);
            Set<String> databaseGuids = new HashSet<>(ts2.getArrayList(String.class));
            getJob().getLogger().info("recreating " + databaseGuids.size() + " sessions because resources may have changed");
            for (String databaseGuid : databaseGuids)
            {
                recreateDatabase(databaseGuid);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void createOrAddToSession() throws PipelineJobException
    {
        boolean success = false;
        TableInfo databases = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES);
        TableInfo databaseMembers = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        String databaseGuid = getPipelineJob().getDatabaseGuid();
        List<Integer> databaseMemberRecordsCreated = new ArrayList<>();

        try
        {
            //first create the database record
            if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.CreateNew)
            {
                CaseInsensitiveHashMap databaseRecord = new CaseInsensitiveHashMap();
                databaseRecord.put("name", getPipelineJob().getName());
                databaseRecord.put("description", getPipelineJob().getDatabaseDescription());
                databaseRecord.put("libraryid", getPipelineJob().getLibraryId());
                databaseRecord.put("objectid", databaseGuid);
                databaseRecord.put("jobid", getJob().getJobGUID());
                databaseRecord.put("container", getJob().getContainer().getId());
                databaseRecord.put("created", new Date());
                databaseRecord.put("createdby", getJob().getUser().getUserId());
                databaseRecord.put("modified", new Date());
                databaseRecord.put("modifiedby", getJob().getUser().getUserId());

                Table.insert(getJob().getUser(), databases, databaseRecord);
            }

            List<Integer> trackIdList = new ArrayList<>();
            if (getPipelineJob().getTrackIds() != null)
                trackIdList.addAll(getPipelineJob().getTrackIds());

            List<Integer> dataIdList = new ArrayList<>();
            if (getPipelineJob().getDataIds() != null)
                dataIdList.addAll(getPipelineJob().getDataIds());

            List<Integer> ntIdList = new ArrayList<>();
            if (getPipelineJob().getLibraryId() != null)
            {
                TableInfo refLibMembers = DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_members");
                TableSelector ts = new TableSelector(refLibMembers, Collections.singleton("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryId(), CompareType.EQUAL), null);
                if (ts.exists())
                {
                    ntIdList.addAll(ts.getArrayList(Integer.class));
                }

                TableInfo refLibTracks = DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_tracks");
                TableSelector ts2 = new TableSelector(refLibTracks, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryId(), CompareType.EQUAL), null);
                if (ts2.exists())
                {
                    trackIdList.addAll(ts2.getArrayList(Integer.class));
                }
            }

            if (!ntIdList.isEmpty())
            {
                getJob().getLogger().info("processing: " + ntIdList.size() + " reference sequences");

                //find any existing database_member records for these sequences so we dont duplicate
                for (Integer ntId : ntIdList)
                {
                    JsonFile json = JBrowseManager.get().preprareReferenceJson(getJob().getContainer(), getJob().getUser(), ntId, getJob().getLogger(), false);
                    if (json == null || !json.getBaseDir().exists() || json.getBaseDir().listFiles().length == 0)
                    {
                        getJob().getLogger().error("sequence did not appear to process correctly, skipping: " + ntId);
                    }
                }
            }
            else
            {
                getJob().getLogger().error("there are no reference sequences to process");
            }

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
                    JsonFile json = JBrowseManager.get().preprareFeatureTrackJson(getJob().getContainer(), getJob().getUser(), trackId, getJob().getLogger(), false);
                    //this could indicate a non-supported filetype
                    if (json == null)
                        continue;


                    if (!json.getBaseDir().exists() || json.getBaseDir().listFiles().length == 0)
                    {
                        getJob().getLogger().error("track did not appear to process correctly, skipping: " + trackId);
                    }
                    else
                    {
                        if (existingTrackIds.contains(trackId))
                        {
                            getJob().getLogger().info("track is already a member of this database, skipping");
                            continue;
                        }

                        CaseInsensitiveHashMap trackRecord = new CaseInsensitiveHashMap();
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
            }
            else
            {
                getJob().getLogger().info("no tracks to process");
            }

            //handle ad hoc files
            if (!dataIdList.isEmpty())
            {
                getJob().getLogger().info("processing: " + dataIdList.size() + " files");

                //find any existing database_member records for these sequences so we dont duplicate
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(databaseMembers, PageFlowUtil.set(FieldKey.fromString("jsonfile/dataid")));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), getPipelineJob().getDatabaseGuid(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("jsonfile/dataid"), null, CompareType.NONBLANK);
                TableSelector ts = new TableSelector(databaseMembers, cols.values(), filter, null);
                List<Integer> existingDataIds = ts.getArrayList(Integer.class);

                for (Integer dataId : dataIdList)
                {
                    JBrowseRoot root = JBrowseRoot.getRoot();
                    root.setLogger(getJob().getLogger());
                    JsonFile json = root.prepareFile(getJob().getContainer(), getJob().getUser(), dataId, false);
                    //this could indicate a non-supported filetype
                    if (json == null)
                        continue;

                    if (existingDataIds.contains(dataId))
                    {
                        getJob().getLogger().info("data file is already a member of this database, skipping");
                        continue;
                    }

                    CaseInsensitiveHashMap trackRecord = new CaseInsensitiveHashMap();
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

            JBrowseRoot root = JBrowseRoot.getRoot();
            root.setLogger(getJob().getLogger());

            getJob().getLogger().info("preparing session JSON files");
            root.prepareDatabase(getJob().getContainer(), getJob().getUser(), databaseGuid);
            success = true;
        }
        catch (IOException e)
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

                    File expectedDir = new File(JBrowseManager.get().getJBrowseRoot(), "databases");
                    if (expectedDir.exists())
                    {
                        try
                        {
                            FileUtils.deleteDirectory(expectedDir);
                        }
                        catch (IOException e)
                        {
                            //ignore
                            getJob().getLogger().info("unable to delete directory: " + expectedDir.getPath());
                        }
                    }
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
