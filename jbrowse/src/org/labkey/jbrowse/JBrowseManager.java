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

package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JBrowseManager
{
    private static final JBrowseManager _instance = new JBrowseManager();
    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.jbrowse.settings";
    public final static String JBROWSE_ROOT = "jbrowseRoot";
    public final static String JBROWSE_URL = "jbrowseURL";
    public final static String JBROWSE_DB_PREFIX = "jbrowseDatabasePrefix";
    public final static String JBROWSE_BIN = "jbrowseBinDir";
    public final static String JBROWSE_COMPRESS_JSON = "compressJson";
    public final static String SEQUENCE_ANALYSIS = "sequenceanalysis";


    private JBrowseManager()
    {
        // prevent external construction with a private default constructor
    }

    public static JBrowseManager get()
    {
        return _instance;
    }

    public void saveSettings(Map<String, String> props) throws IllegalArgumentException
    {
        PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN, true);

        //validate URL
        String url = StringUtils.trimToNull(props.get(JBROWSE_URL));
        if (url == null)
        {
            throw new IllegalArgumentException("URL to JBrowse server not provided");
        }

        if (!new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS).isValid(url))
        {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        configMap.put(JBROWSE_URL, url);
        configMap.put(JBROWSE_DB_PREFIX, props.get(JBROWSE_DB_PREFIX));

        //validate file root
        String root = StringUtils.trimToNull(props.get(JBROWSE_ROOT));
        if (root == null)
        {
            throw new IllegalArgumentException("JBrowse file root not provided");
        }

        File fileRoot = new File(root);
        if (!fileRoot.exists())
        {
            throw new IllegalArgumentException("JBrowse file root does not exist or is not acessible: " + fileRoot.getPath());
        }

        if (!fileRoot.canWrite() || !fileRoot.canRead())
        {
            throw new IllegalArgumentException("The user running tomcat must have read/write access to the JBrowse file root: " + fileRoot.getPath());
        }
        configMap.put(JBROWSE_ROOT, root);

        //validate bin
        String binDir = StringUtils.trimToNull(props.get(JBROWSE_BIN));
        if (binDir == null)
        {
            throw new IllegalArgumentException("JBrowse bin folder not provided");
        }

        File binFolder = new File(binDir);
        if (!binFolder.exists())
        {
            throw new IllegalArgumentException("JBrowse bin dir does not exist or is not acessible: " + binFolder.getPath());
        }

        if (!binFolder.canRead())
        {
            throw new IllegalArgumentException("The user running tomcat must have read access to the JBrowse bin dir: " + binFolder.getPath());
        }
        configMap.put(JBROWSE_BIN, binDir);

        Boolean compress = props.containsKey(JBROWSE_COMPRESS_JSON) ? Boolean.parseBoolean(props.get(JBROWSE_COMPRESS_JSON)) : false;
        configMap.put(JBROWSE_COMPRESS_JSON, compress.toString());

        PropertyManager.saveProperties(configMap);
    }

    /**
     * Note: does not check whether path is valid
     */
    public File getJBrowseRoot()
    {
        Map<String, String> props = PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(JBROWSE_ROOT))
        {
            return new File(props.get(JBROWSE_ROOT));
        }

        return null;
    }

    public String getJBrowseDbPrefix()
    {
        Map<String, String> props = PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(JBROWSE_DB_PREFIX) && props.get(JBROWSE_DB_PREFIX) != null)
        {
            String prefix = props.get(JBROWSE_DB_PREFIX);
            if (prefix.startsWith("/"))
                prefix = prefix.substring(1);

            return  prefix + (props.get(JBROWSE_DB_PREFIX).endsWith("/") ? "" : "/");
        }

        return null;
    }

    public String getJBrowseBaseUrl()
    {
        Map<String, String> props = PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(JBROWSE_URL) && props.get(JBROWSE_URL) != null)
        {
            return props.get(JBROWSE_URL) + (props.get(JBROWSE_URL).endsWith("/") ? "" : "/");
        }

        return null;
    }

    public File getJBrowseBinDir()
    {
        Map<String, String> props = PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(JBROWSE_BIN))
        {
            return new File(props.get(JBROWSE_BIN));
        }

        return null;
    }

    public boolean compressJSON()
    {
        Map<String, String> props = PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(JBROWSE_COMPRESS_JSON))
        {
            return Boolean.parseBoolean(props.get(JBROWSE_COMPRESS_JSON));
        }

        return false;
    }

    public void createDatabase(Container c, User u, String name, String description, List<Integer> libraryIds, List<Integer> ntIds, List<Integer> trackIds) throws IOException
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.createNewDatabase(c, u, root, name, description, libraryIds, ntIds, trackIds));
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public void addDatabaseMember(Container c, User u, String databaseGuid, List<Integer> libraryIds, List<Integer> ntIds, List<Integer> trackIds) throws IOException
    {
        //make sure this is a valid database
        TableSelector ts = new TableSelector(DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
        if (!ts.exists())
        {
            throw new IllegalArgumentException("Unknown database: " + databaseGuid);
        }

        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.addMembers(c, u, root, databaseGuid, libraryIds, ntIds, trackIds));
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public JsonFile preprareTrackJson(Container c, User u, int trackId, @Nullable Logger log) throws IOException
    {
        JBrowseRoot root = JBrowseRoot.getRoot();
        if (log != null)
            root.setLogger(log);

        return root.prepareTrack(c, u, trackId);
    }

    public JsonFile preprareReferenceJson(Container c, User u, int ntId, @Nullable Logger log) throws IOException
    {
        JBrowseRoot root = JBrowseRoot.getRoot();
        if (log != null)
            root.setLogger(log);

        return root.prepareRefSeq(c, u, ntId);
    }
}