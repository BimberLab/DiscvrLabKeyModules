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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JBrowseManager
{
    private static final JBrowseManager _instance = new JBrowseManager();
    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.jbrowse.settings";
    public final static String JBROWSE_BIN = "jbrowseBinDir";
    public final static String SEQUENCE_ANALYSIS = "sequenceanalysis";
    public static final List<FileType> ALLOWABLE_TRACK_EXTENSIONS = Arrays.asList(
            new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ),
            new FileType("bcf", FileType.gzSupportLevel.NO_GZ),
            new FileType(Arrays.asList("bw", "bigwig", "wig"), "bw", FileType.gzSupportLevel.NO_GZ),
            new FileType("gff", FileType.gzSupportLevel.NO_GZ),
            new FileType("gff3", FileType.gzSupportLevel.NO_GZ),
            new FileType("gtf", FileType.gzSupportLevel.NO_GZ),
            new FileType("bed", FileType.gzSupportLevel.NO_GZ),
            new FileType("bedgraph", FileType.gzSupportLevel.NO_GZ),
            new FileType("bam", FileType.gzSupportLevel.NO_GZ)
    );

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

        PropertyManager.saveProperties(configMap);
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
        //hard coded for now until webdav supports otherwise
        return true;
    }

    public void createDatabase(Container c, User u, String name, String description, Integer libraryId, List<Integer> trackIds, List<Integer> outputFileIds) throws IOException
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.createNewDatabase(c, u, root, name, description, libraryId, trackIds, outputFileIds));
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public void addDatabaseMember(Container c, User u, String databaseGuid, List<Integer> trackIds, List<Integer> outputFileIds) throws IOException
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
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.addMembers(c, u, root, databaseGuid, trackIds, outputFileIds));
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean canDisplayAsTrack(File f)
    {
        String extension = FileUtil.getExtension(f);
        if (extension == null)
        {
            return false;
        }

        for (FileType ft : ALLOWABLE_TRACK_EXTENSIONS)
        {
            if (ft.isType(f))
            {
                return true;
            }
        }

        return false;
    }
}