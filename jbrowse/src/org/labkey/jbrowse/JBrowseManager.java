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

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JBrowseManager
{
    private static final JBrowseManager _instance = new JBrowseManager();
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

    public void createDatabase(Container c, User u, String name, String description, Integer libraryId, List<Integer> trackIds, List<Integer> outputFileIds, boolean isTemporary) throws IOException
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.createNewDatabase(c, u, root, name, description, libraryId, trackIds, outputFileIds, isTemporary));
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public void ensureGenomePrepared(Container c, User u, Integer genomeId, Logger log) throws PipelineJobException
    {
        try
        {
            ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(genomeId, u);
            if (rg == null)
            {
                throw new IllegalArgumentException("Unable to find genome: " + genomeId);
            }

            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);

            JBrowseSession session = JBrowseSession.getGenericGenomeSession(genomeId);
            List<String> jsonfilesProcess = session.getJsonFiles(u, true).stream().map(JsonFile::getObjectId).collect(Collectors.toList());
            if (!jsonfilesProcess.isEmpty())
            {
                PipelineService.get().queueJob(JBrowseSessionPipelineJob.refreshResources(c, u, root, jsonfilesProcess));
            }
            else
            {
                log.info("No JsonFiles exist for genome " + genomeId + " skipping processing");
            }
        }
        catch (PipelineValidationException e)
        {
            log.error("Error preparing genome", e);
            throw new IllegalArgumentException(e);
        }
    }

    public void addDatabaseMember(User u, String databaseGuid, List<Integer> trackIds, List<Integer> outputFileIds) throws IOException
    {
        //make sure this is a valid database
        TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
        JBrowseSession db = ts.getObject(JBrowseSession.class);
        if (db == null)
        {
            throw new IllegalArgumentException("Unknown database: " + databaseGuid);
        }

        Container targetContainer = ContainerManager.getForId(db.getContainer());
        if (!targetContainer.hasPermission(u, InsertPermission.class))
        {
            throw new UnauthorizedException("Insufficient permissions to edit folder: " + targetContainer.getName());
        }

        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(targetContainer);
            PipelineService.get().queueJob(JBrowseSessionPipelineJob.addMembers(targetContainer, u, root, databaseGuid, trackIds, outputFileIds));
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

    public TableInfo getSequenceAnalysisTable(String tableName)
    {
        return DbSchema.get(SEQUENCE_ANALYSIS, DbSchemaType.Module).getTable(tableName);
    }

    public File getJbrowseCli() throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(JBrowseModule.NAME);
        DirectoryResource resource = (DirectoryResource) module.getModuleResolver().lookup(Path.parse("external/jb-cli"));
        File toolDir = resource.getDir();
        if (!toolDir.exists())
        {
            throw new PipelineJobException("Unable to find expected folder: " + toolDir.getPath());
        }

        File exe = null;
        if (SystemUtils.IS_OS_WINDOWS)
        {
            exe = new File(toolDir, "cli-win.exe");
        }
        else if (SystemUtils.IS_OS_LINUX)
        {
            exe = new File(toolDir, "cli-linux");
        }
        else if (SystemUtils.IS_OS_MAC_OSX)
        {
            exe = new File(toolDir, "cli-macos");
        }
        else
        {
            throw new PipelineJobException("Unknown OS: " + SystemUtils.OS_NAME);
        }

        try
        {
            if (exe.toPath().getFileSystem().supportedFileAttributeViews().contains("posix"))
            {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(exe.toPath());
                if (!perms.contains(PosixFilePermission.OWNER_EXECUTE))
                {
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(exe.toPath(), perms);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        if (!exe.exists())
        {
            throw new PipelineJobException("Unable to find file: " + exe.getPath());
        }

        return exe;
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = LogManager.getLogger(JBrowseManager.TestCase.class);

        @Test
        public void testJBrowseCli() throws Exception
        {
            File exe = JBrowseManager.get().getJbrowseCli();
            String output = new SimpleScriptWrapper(_log).executeWithOutput(Arrays.asList(exe.getPath(), "help"));

            assertTrue("Malformed output", output.contains("Add an assembly to a JBrowse 2 configuration"));
        }
    }

    @Nullable
    public File getBaseDir(Container c, boolean doCreate)
    {
        FileContentService fileService = FileContentService.get();
        File fileRoot = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (fileRoot == null || !fileRoot.exists())
        {
            return null;
        }

        if (fileRoot == null || !fileRoot.exists())
        {
            return null;
        }

        File jbrowseDir = new File(fileRoot, ".jbrowse");
        if (!jbrowseDir.exists())
        {
            if (!doCreate)
            {
                return null;
            }

            jbrowseDir.mkdirs();
        }

        return jbrowseDir;
    }
}