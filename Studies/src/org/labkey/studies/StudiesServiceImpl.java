package org.labkey.studies;

import org.apache.logging.log4j.Logger;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.studies.study.EventProvider;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.studies.query.StudiesTableCustomizer;
import org.labkey.vfs.FileLike;
import org.labkey.studies.query.StudiesTriggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public TriggerFactory getStudiesTriggerFactory()
    {
        return new StudiesTriggerFactory();
    }

    @Override
    public void importFolderDefinition(Container container, User user, Module m, Path sourceFolderDirPath) throws IOException
    {
        Resource root = m.getModuleResource(sourceFolderDirPath);
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
        FileLike pipeRootPath = pipeRoot.getRootFileLike();

        FileLike folderXmlPath;

        if (root instanceof DirectoryResource dir && dir.getDir().equals(pipeRootPath.toNioPathForRead().toFile()))
        {
            // The pipeline root is already pointed at the folder definition, like it might be on a dev machine.
            // No need to copy, especially since copying can cause infinite recursion when the paths are nested
            folderXmlPath = pipeRootPath.resolveChild("folder.xml");
        }
        else
        {
            FileLike folderPath = pipeRootPath.resolveChild("moduleFolderImport");
            folderXmlPath = folderPath.resolveChild("folder.xml");
            if (folderPath.exists())
            {
                FileUtil.deleteDir(folderPath);
            }
            copyResourceToPath(root, folderPath);
        }

        if (!folderXmlPath.exists())
        {
            throw new FileNotFoundException("Couldn't find an extracted " + folderXmlPath);
        }
        ImportOptions options = new ImportOptions(container.getId(), user.getUserId());
        options.setSkipQueryValidation(true);

        PipelineService.get().runFolderImportJob(container, user, null, folderXmlPath, "folder.xml", pipeRoot, options);
    }

    private void copyResourceToPath(Resource resource, FileLike target) throws IOException
    {
        if (resource.isCollection())
        {
            FileUtil.createDirectory(target);
            for (Resource child : resource.list())
            {
                FileLike childTarget = target.resolveChild(child.getName());
                copyResourceToPath(child, childTarget);
            }
        }
        else
        {
            try (InputStream in = resource.getInputStream();
                 OutputStream out = target.openOutputStream())
            {
                FileUtil.copyData(in, out);
            }
        }
    }

    @Override
    public void loadTsv(Resource tsv, String schemaName, User u, Container c)
    {
        try (DataLoader loader = DataLoader.get().createLoader(tsv, true, null, TabLoader.TSV_FILE_TYPE))
        {
            TableInfo ti = QueryService.get().getUserSchema(u, c, schemaName).getTable(FileUtil.getBaseName(tsv.getName()));
            if (ti == null)
            {
                throw new IllegalStateException("Missing table: " + tsv.getName());
            }

            List<Map<String, Object>> rows = loader.load();

            QueryUpdateService qus = ti.getUpdateService();
            qus.setBulkLoad(true);

            qus.truncateRows(u, c, null, null);
            BatchValidationException bve = new BatchValidationException();
            qus.insertRows(u, c, rows, bve, null, null);
            if (bve.hasErrors())
            {
                throw bve;
            }
        }
        catch (IOException | SQLException | BatchValidationException | QueryUpdateServiceException |
               DuplicateKeyException e)
        {
            _log.error("Error populating TSV", e);

            throw new RuntimeException(e);
        }
    }

    private final Map<String, EventProvider> _eventProviders = new HashMap<>();

    @Override
    public void registerEventProvider(EventProvider ep)
    {
        if (_eventProviders.containsKey(ep.getName()))
        {
            throw new ConfigurationException("There is already a provider registered with the name: " + ep.getName());
        }

        _eventProviders.put(ep.getName(), ep);
    }

    @Override
    public List<EventProvider> getEventProviders(Container c)
    {
        return _eventProviders.values().stream().filter(ep -> ep.isAvailable(c)).toList();
    }

    @Override
    public TableCustomizer getStudiesTableCustomizer()
    {
        return new StudiesTableCustomizer();
    }

    public static String ASSIGNMENT_DATASET = "assignment";

    public boolean hasAssignmentDataset(Container c)
    {
        Study s = StudyService.get().getStudy(c.isWorkbookOrTab() ? c.getParent() : c);
        if (s == null)
        {
            return false;
        }

        return s.getDatasetByName(ASSIGNMENT_DATASET) != null;
    }
}
