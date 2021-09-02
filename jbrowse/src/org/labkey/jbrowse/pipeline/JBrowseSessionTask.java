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
import org.labkey.jbrowse.JBrowseRoot;
import org.labkey.jbrowse.JBrowseSchema;
import org.labkey.jbrowse.model.JBrowseSession;
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
            return Arrays.asList("JBrowse");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new JBrowseSessionTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

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
        else if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.RecreateDatabase)
        {
            recreateDatabase(getPipelineJob().getDatabaseGuid());
        }

        return new RecordedActionSet(Collections.singleton(new RecordedAction("JBrowse")));
    }

    private void recreateDatabase(String databaseGuid) throws PipelineJobException
    {
        JBrowseRoot root = new JBrowseRoot(getJob().getLogger());

        try
        {
            getJob().getLogger().info("preparing session JSON files");
            TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
            JBrowseSession db = ts.getObject(JBrowseSession.class);
            if (db == null)
            {
                throw new IllegalArgumentException("Unknown database: " + databaseGuid);
            }

            root.prepareDatabase(db, getJob().getUser(), getJob());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void reprocessResources() throws PipelineJobException
    {
        TableInfo ti = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("objectid"), getPipelineJob().getJsonFiles(), CompareType.IN), null);
        List<JsonFile> jsonFiles = ts.getArrayList(JsonFile.class);
        try
        {
            JBrowseRoot root = new JBrowseRoot(getJob().getLogger());
            getJob().getLogger().info("total files to reprocess: " + jsonFiles.size());
            Set<Integer> sequenceIds = new HashSet<>();
            Set<Integer> trackIds = new HashSet<>();

            for (JsonFile f : jsonFiles)
            {
                if (f.getSequenceId() != null)
                {
                    root.prepareRefSeq(getJob().getUser(), f.getSequenceId(), true);
                    sequenceIds.add(f.getSequenceId());
                }
                else if (f.getTrackId() != null)
                {
                    root.prepareFeatureTrack(getJob().getUser(), f.getTrackId(), null, true);
                    trackIds.add(f.getTrackId());
                }
                else if (f.getOutputFile() != null)
                {
                    root.prepareOutputFile(getJob().getUser(), f.getOutputFile(), true);
                }
                else
                {
                    getJob().getLogger().info("no need to reprocess jsonfile: " + f.getObjectId());
                }
            }

            //next update any DBs using these resources
            TableInfo databaseMembers = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
            TableSelector ts2 = new TableSelector(databaseMembers, PageFlowUtil.set("database"), new SimpleFilter(FieldKey.fromString("jsonfile"), getPipelineJob().getJsonFiles(), CompareType.IN), null);
            Set<String> databaseGuids = new HashSet<>(ts2.getArrayList(String.class));

            //look for libraries using these resources.  then find any sessions based on these libraries
            Set<Integer> libraryIds = new HashSet<>();
            libraryIds.addAll(new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("library_id"), new SimpleFilter(FieldKey.fromString("rowid"), trackIds, CompareType.IN), null).getArrayList(Integer.class));
            libraryIds.addAll(new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_members"), PageFlowUtil.set("library_id"), new SimpleFilter(FieldKey.fromString("ref_nt_id"), sequenceIds, CompareType.IN), null).getArrayList(Integer.class));
            if (!libraryIds.isEmpty())
            {
                List<String> newIds = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("libraryId"), libraryIds, CompareType.IN), null).getArrayList(String.class);
                if (!newIds.isEmpty())
                {
                    getJob().getLogger().info("re-processing " + new HashSet<>(newIds).size() + " additional sessions because they use reference genomes that were modified");
                    databaseGuids.addAll(newIds);
                }
            }

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
        TableInfo databases = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES);
        TableInfo databaseMembers = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        String databaseGuid = getPipelineJob().getDatabaseGuid();
        List<Integer> databaseMemberRecordsCreated = new ArrayList<>();

        try
        {
            JBrowseRoot root = new JBrowseRoot(getJob().getLogger());

            //first create the database record
            if (getPipelineJob().getMode() == JBrowseSessionPipelineJob.Mode.CreateNew)
            {
                //TODO: if you restart a failed job, this record might already exist

                CaseInsensitiveHashMap<Object> databaseRecord = new CaseInsensitiveHashMap<>();
                databaseRecord.put("name", getPipelineJob().getName());
                databaseRecord.put("description", getPipelineJob().getDatabaseDescription());
                databaseRecord.put("libraryid", getPipelineJob().getLibraryId());
                databaseRecord.put("objectid", databaseGuid);
                databaseRecord.put("jobid", getJob().getJobGUID());
                databaseRecord.put("primarydb", getPipelineJob().isPrimaryDb());
                databaseRecord.put("createOwnIndex", getPipelineJob().isCreateOwnIndex());
                databaseRecord.put("temporary", getPipelineJob().isTemporarySession());
                databaseRecord.put("container", getJob().getContainer().getId());
                databaseRecord.put("created", new Date());
                databaseRecord.put("createdby", getJob().getUser().getUserId());
                databaseRecord.put("modified", new Date());
                databaseRecord.put("modifiedby", getJob().getUser().getUserId());

                getJob().getLogger().debug("creating database record");
                Table.insert(getJob().getUser(), databases, databaseRecord);
            }

            TableSelector dbTs = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
            JBrowseSession db = dbTs.getObject(JBrowseSession.class);
            if (db == null)
            {
                throw new IllegalArgumentException("Unknown database: " + databaseGuid);
            }

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
                    JsonFile json = root.prepareFeatureTrack(getJob().getUser(), trackId, null, false);
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

                    JsonFile json = root.prepareOutputFile(getJob().getUser(), outputFileId, false);
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

            getJob().getLogger().info("preparing session JSON files");
            root.prepareDatabase(db, getJob().getUser(), getJob());

            getJob().getLogger().info("complete");
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

                    JBrowseRoot root = new JBrowseRoot(getJob().getLogger());
                    File expectedDir = root.getDatabaseDir(getJob().getContainer());
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
