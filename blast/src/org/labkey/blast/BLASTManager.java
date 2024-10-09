/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.blast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.blast.model.BlastJob;
import org.labkey.blast.pipeline.BlastDatabasePipelineJob;
import org.labkey.blast.pipeline.BlastPipelineJob;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;

public class BLASTManager
{
    private static final BLASTManager _instance = new BLASTManager();
    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.blast.settings";
    public final static String BLAST_BIN_DIR = "blastBinDir";

    public final static String SEQUENCE_ANALYSIS = "sequenceanalysis";

    private BLASTManager()
    {
        // prevent external construction with a private default constructor
    }

    public static BLASTManager get()
    {
        return _instance;
    }

    public void saveSettings(Map<String, String> props) throws IllegalArgumentException
    {
        WritablePropertyMap configMap = PropertyManager.getWritableProperties(BLASTManager.CONFIG_PROPERTY_DOMAIN, true);

        //validate bin dir
        String binDir = StringUtils.trimToNull(props.get(BLAST_BIN_DIR));
        if (binDir != null)
        {
            File binFolder = new File(binDir);
            if (!binFolder.exists())
            {
                throw new IllegalArgumentException("bin dir does not exist or is not accessible: " + binFolder.getPath());
            }

            if (!binFolder.canRead())
            {
                throw new IllegalArgumentException("The user running tomcat must have read access to the bin dir: " + binFolder.getPath());
            }
        }
        configMap.put(BLAST_BIN_DIR, binDir);

        configMap.save();
    }

    public File getBinDir()
    {
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            Map<String, String> props = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN);
            if (props.containsKey(BLAST_BIN_DIR))
            {
                return new File(props.get(BLAST_BIN_DIR));
            }
        }

        return null;
    }

    public File getDatabaseDir(Container c, boolean createIfDoesntExist)
    {
        FileContentService fileService = FileContentService.get();
        Path fileRoot = fileService == null ? null : fileService.getFileRootPath(c, FileContentService.ContentType.files);
        if (fileRoot == null || fileRoot.getFileSystem() != FileSystems.getDefault())
        {
            return null;
        }

        File ret = new File(fileRoot.toFile(), ".blastDB");
        if (createIfDoesntExist && !ret.exists())
        {
            ret.mkdirs();
        }

        return ret;
    }

    public Container getContainerForDatabase(String databaseId)
    {
        TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
        String containerId = new TableSelector(databases, PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), databaseId), null).getObject(String.class);
        if (containerId != null)
        {
            return ContainerManager.getForId(containerId);
        }

        return null;
    }

    public void createDatabase(Container c, User u, Integer libraryId) throws IllegalArgumentException, IOException
    {
        //only create once per library
        TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
        TableSelector dbTs = new TableSelector(databases, PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("libraryid"), libraryId), null);
        if (dbTs.exists())
        {
            return;
        }

        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            PipelineService.get().queueJob(new BlastDatabasePipelineJob(c, u, null, root, libraryId));
        }
        catch (PipelineValidationException | PipelineJobException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public String runBLASTN(Container c, User u, String blastDb, File input, String task, String title, boolean saveResults, Map<String, String> params, boolean async) throws IllegalArgumentException, IOException
    {
        TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
        TableSelector ts = new TableSelector(databases, new SimpleFilter(FieldKey.fromString("objectid"), blastDb), null);
        if (!ts.exists())
        {
            throw new IllegalArgumentException("Unable to find BLAST DB: " + blastDb);
        }

        TableInfo jobs = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_BLAST_JOBS);
        BlastJob databaseRecord = new BlastJob();
        databaseRecord.setDatabaseId(blastDb);
        databaseRecord.setTitle(title);
        databaseRecord.setSaveResults(saveResults);
        databaseRecord.setParams(params == null ? null : new JSONObject(params).toString());
        databaseRecord.setObjectid(new GUID().toString().toUpperCase());
        databaseRecord.setContainer(c.getId());
        databaseRecord.setCreated(new Date());
        databaseRecord.setCreatedBy(u.getUserId());
        databaseRecord.setModified(new Date());
        databaseRecord.setModifiedBy(u.getUserId());
        databaseRecord.setHasRun(false);
        if (task != null)
            databaseRecord.addParam("task", task);
        Table.insert(u, jobs, databaseRecord);

        //move input
        File movedInput = databaseRecord.getExpectedInputFile();
        FileUtils.moveFile(input, movedInput);

        return runBLASTN(u, c, databaseRecord, async);
    }

    public File getBlastRoot(Container c, boolean throwOnError)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root == null)
        {
            if (throwOnError)
                throw new IllegalArgumentException("No pipeline directory set for folder: " + c.getPath());
            else
                return null;
        }

        return  new File(root.getRootPath(), ".blast");
    }

    public String runBLASTN(User u, Container c, BlastJob job, boolean async) throws IllegalArgumentException, IOException
    {
        File outDir = getBlastRoot(c, true);
        if (!outDir.exists())
        {
            outDir.mkdirs();
        }

        if (!async)
        {
            Container dbContainer = getContainerForDatabase(job.getDatabaseId());
            try
            {
                BLASTWrapper wrapper = new BLASTWrapper(LogManager.getLogger(BLASTManager.class));
                wrapper.runBlastN(job.getDatabaseId(), job.getExpectedInputFile(), job.getExpectedOutputFile(), job.getParamMap(), null, BLASTManager.get().getDatabaseDir(dbContainer, false));
                job.setComplete(u, null);
            }
            catch (PipelineJobException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
        else
        {
            try
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
                Container dbContainer = getContainerForDatabase(job.getDatabaseId());
                PipelineService.get().queueJob(new BlastPipelineJob(c, u, null, root, job, BLASTManager.get().getDatabaseDir(dbContainer, false), null));
            }
            catch (PipelineValidationException e)
            {
                throw new IllegalArgumentException(e);
            }
        }

        return job.getObjectid();
    }

    public BlastJob getBlastResults(Container c, User u, String jobId)
    {
        TableInfo blastJobs = QueryService.get().getUserSchema(u, c, BLASTSchema.NAME).getTable(BLASTSchema.TABLE_BLAST_JOBS);
        TableSelector ts = new TableSelector(blastJobs, new SimpleFilter(FieldKey.fromString("objectid"), jobId), null);

        return ts.getObject(BlastJob.class);
    }
}
