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
            return Arrays.asList("Creating JBrowse Session");
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
        if (getPipelineJob().isCreateNew())
        {
            getJob().getLogger().info("Creating JBrowse session");
        }
        else
        {
            getJob().getLogger().info("Adding resources to existing JBrowse session");
        }

        boolean success = false;
        TableInfo databases = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES);
        TableInfo databaseMembers = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        String databaseGuid = getPipelineJob().getDatabaseGuid();

        try
        {
            //first create the database record
            if (getPipelineJob().isCreateNew())
            {
                CaseInsensitiveHashMap databaseRecord = new CaseInsensitiveHashMap();
                databaseRecord.put("name", getPipelineJob().getName());
                databaseRecord.put("description", getPipelineJob().getDatabaseDescription());
                databaseRecord.put("objectid", databaseGuid);
                databaseRecord.put("container", getJob().getContainer().getId());
                databaseRecord.put("created", new Date());
                databaseRecord.put("createdby", getJob().getUser().getUserId());
                databaseRecord.put("modified", new Date());
                databaseRecord.put("modifiedby", getJob().getUser().getUserId());

                Table.insert(getJob().getUser(), databases, databaseRecord);
            }

            List<Integer> ntIdList = new ArrayList<>();
            if (getPipelineJob().getNtIds() != null)
                ntIdList.addAll(getPipelineJob().getNtIds());

            List<Integer> trackIdList = new ArrayList<>();
            if (getPipelineJob().getTrackIds() != null)
                trackIdList.addAll(getPipelineJob().getTrackIds());

            if (getPipelineJob().getLibraryIds() != null)
            {
                TableInfo refLibMembers = DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_members");
                TableSelector ts = new TableSelector(refLibMembers, Collections.singleton("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryIds(), CompareType.IN), null);
                if (ts.exists())
                {
                    ntIdList.addAll(ts.getArrayList(Integer.class));
                }

                TableInfo refLibTracks = DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_tracks");
                TableSelector ts2 = new TableSelector(refLibTracks, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryIds(), CompareType.IN), null);
                if (ts2.exists())
                {
                    trackIdList.addAll(ts2.getArrayList(Integer.class));
                }
            }

            if (!ntIdList.isEmpty())
            {
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(databaseMembers, PageFlowUtil.set(FieldKey.fromString("jsonfile/sequenceid")));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), getPipelineJob().getLibraryIds(), CompareType.IN);
                filter.addCondition(FieldKey.fromString("jsonfile/sequenceid"), null, CompareType.ISBLANK);

                TableSelector ts = new TableSelector(databaseMembers, cols.values(), filter, null);
                List<Integer> existingNtIds = ts.getArrayList(Integer.class);

                for (Integer ntId : ntIdList)
                {
                    JsonFile json = JBrowseManager.get().preprareReferenceJson(getJob().getContainer(), getJob().getUser(), ntId, getJob().getLogger());
                    if (json == null || !json.getBaseDir().exists() || json.getBaseDir().listFiles().length == 0)
                    {
                        getJob().getLogger().error("sequence did not appear to process correctly, skipping: " + ntId);
                    }
                    else
                    {
                        if (existingNtIds.contains(ntId))
                        {
                            getJob().getLogger().info("sequence is already a member of this database, skipping");
                            continue;
                        }

                        CaseInsensitiveHashMap ntRecord = new CaseInsensitiveHashMap();
                        ntRecord.put("database", databaseGuid);
                        ntRecord.put("jsonfile", json.getObjectId());
                        ntRecord.put("container", getJob().getContainer().getId());
                        ntRecord.put("created", new Date());
                        ntRecord.put("createdby", getJob().getUser().getUserId());
                        ntRecord.put("modified", new Date());
                        ntRecord.put("modifiedby", getJob().getUser().getUserId());
                        Table.insert(getJob().getUser(), databaseMembers, ntRecord);
                    }
                }
            }

            if (!trackIdList.isEmpty())
            {
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(databaseMembers, PageFlowUtil.set(FieldKey.fromString("jsonfile/trackid")));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), getPipelineJob().getLibraryIds(), CompareType.IN);
                filter.addCondition(FieldKey.fromString("jsonfile/trackid"), null, CompareType.ISBLANK);

                TableSelector ts = new TableSelector(databaseMembers, cols.values(), filter, null);
                List<Integer> existingTrackIds = ts.getArrayList(Integer.class);

                for (Integer trackId : trackIdList)
                {
                    JsonFile json = JBrowseManager.get().preprareTrackJson(getJob().getContainer(), getJob().getUser(), trackId, getJob().getLogger());
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
                        Table.insert(getJob().getUser(), databaseMembers, trackRecord);
                    }
                }
            }

            JBrowseRoot root = JBrowseRoot.getRoot();
            root.setLogger(getJob().getLogger());

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

                if (getPipelineJob().isCreateNew())
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
                    //TODO
                    getJob().getLogger().error("no automatic cleanup takes place for adding files to a database");
                }
            }
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("Creating JBrowse Session")));
    }

    private JBrowseSessionPipelineJob getPipelineJob()
    {
        return (JBrowseSessionPipelineJob)getJob();
    }
}
