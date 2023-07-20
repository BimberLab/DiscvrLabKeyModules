package org.labkey.studies;

import org.apache.logging.log4j.Logger;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class StudiesServiceImpl extends StudiesService
{
    private static final StudiesServiceImpl _instance = new StudiesServiceImpl();
    private static final Logger _log = LogHelper.getLogger(StudiesServiceImpl.class, "StudiesService messages");

    public static StudiesServiceImpl get()
    {
        return _instance;
    }

    private StudiesServiceImpl()
    {

    }

    @Override
    public void importFolderDefinition(Container container, User user, Module m, Path sourceFolderDirPath) throws IOException
    {
        Resource root = m.getModuleResource(sourceFolderDirPath);
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
        java.nio.file.Path pipeRootPath = pipeRoot.getRootNioPath();

        java.nio.file.Path folderXmlPath;

        if (root instanceof DirectoryResource && ((DirectoryResource)root).getDir().equals(pipeRootPath.toFile()))
        {
            // The pipeline root is already pointed at the folder definition, like it might be on a dev machine.
            // No need to copy, especially since copying can cause infinite recursion when the paths are nested
            folderXmlPath = pipeRootPath.resolve("folder.xml");
        }
        else
        {
            java.nio.file.Path folderPath = pipeRootPath.resolve("moduleFolderImport");
            folderXmlPath = folderPath.resolve("folder.xml");
            if (Files.exists(folderPath))
            {
                FileUtil.deleteDir(folderPath);
            }
            copyResourceToPath(root, folderPath);
        }

        if (!Files.exists(folderXmlPath))
        {
            throw new FileNotFoundException("Couldn't find an extracted " + folderXmlPath);
        }
        ImportOptions options = new ImportOptions(container.getId(), user.getUserId());
        options.setSkipQueryValidation(true);

        PipelineService.get().runFolderImportJob(container, user, null, folderXmlPath, "folder.xml", pipeRoot, options);
    }

    private void copyResourceToPath(Resource resource, java.nio.file.Path target) throws IOException
    {
        if (resource.isCollection())
        {
            Files.createDirectory(target);
            for (Resource child : resource.list())
            {
                java.nio.file.Path childTarget = target.resolve(child.getName());
                copyResourceToPath(child, childTarget);
            }
        }
        else
        {
            try (InputStream in = resource.getInputStream();
                 OutputStream out = Files.newOutputStream(target))
            {
                FileUtil.copyData(in, out);
            }
        }
    }
}
