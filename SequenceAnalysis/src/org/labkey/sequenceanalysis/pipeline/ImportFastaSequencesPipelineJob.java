package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class ImportFastaSequencesPipelineJob extends PipelineJob
{
    public static String _folderPrefix = "refSequenceImport";
    private List<File> _fastas;
    private Map<String, String> _params;
    private boolean _splitWhitespace;
    private boolean _createLibrary;
    private boolean _deleteInputs = false;
    private Map<String, Object> _libraryParams;
    private File _outDir;

    // Default constructor for serialization
    protected ImportFastaSequencesPipelineJob()
    {
    }

    public ImportFastaSequencesPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, List<File> fastas, Map<String, String> params, boolean splitWhitespace, boolean createLibrary, Map<String, Object> libraryParams) throws IOException
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _fastas = fastas;
        _params = params;
        _splitWhitespace = splitWhitespace;
        _createLibrary = createLibrary;
        _libraryParams = libraryParams;

        _outDir = createLocalDirectory(pipeRoot);
        setLogFile(new File(_outDir, FileUtil.makeFileNameWithTimestamp("fastaImport", "log")));
    }

    public static File createLocalDirectory(PipeRoot pipeRoot) throws IOException
    {
        File webserverOutDir = new File(pipeRoot.getRootPath(), _folderPrefix);
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdir();
        }

        AssayFileWriter writer = new AssayFileWriter();
        String folderName = "SequenceImport_" + FileUtil.getTimestamp();
        webserverOutDir = AssayFileWriter.findUniqueFileName(folderName, webserverOutDir);
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdirs();
        }

        return webserverOutDir;
    }

    @Override
    public String getDescription()
    {
        return "Import FASTA Sequences";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(ImportFastaSequencesPipelineJob.class));
    }

    public Map<String, String> getParams()
    {
        return _params;
    }

    public void setParams(Map<String, String> params)
    {
        _params = params;
    }

    public boolean isSplitWhitespace()
    {
        return _splitWhitespace;
    }

    public void setSplitWhitespace(boolean splitWhitespace)
    {
        _splitWhitespace = splitWhitespace;
    }

    public boolean isCreateLibrary()
    {
        return _createLibrary;
    }

    public void setCreateLibrary(boolean createLibrary)
    {
        _createLibrary = createLibrary;
    }

    public Map<String, Object> getLibraryParams()
    {
        return _libraryParams;
    }

    public void setLibraryParams(Map<String, Object> libraryParams)
    {
        _libraryParams = libraryParams;
    }

    public List<File> getFastas()
    {
        return _fastas;
    }

    public void setFastas(List<File> fastas)
    {
        _fastas = fastas;
    }

    public boolean isDeleteInputs()
    {
        return _deleteInputs;
    }

    public void setDeleteInputs(boolean deleteInputs)
    {
        _deleteInputs = deleteInputs;
    }

    @Override
    public ActionURL getStatusHref()
    {
        return null;
    }

    public File getOutDir()
    {
        return _outDir;
    }

    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "importFastaSequences";

        public Provider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {
            if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            {
                return;
            }

            String actionId = "SequenceAnalysis.importFastaSequences:ImportReferenceSequences";
            String path = directory.cloneHref().getParameter(Params.path.toString());
            ActionURL url = DetailsURL.fromString("sequenceanalysis/importFasta.view?" + (path == null ? "" : "path=" + path), context.getContainer()).getActionURL();
            addAction(actionId, url, "Import Reference Sequences", directory, directory.listFiles(new UploadFileFilter()), true, true, includeAll);
        }

        public static class UploadFileFilter extends PipelineProvider.FileEntryFilter
        {
            private static final FileType _fasta = new FileType(Arrays.asList("fa", "fasta"), "fasta", FileType.gzSupportLevel.SUPPORT_GZ);

            @Override
            public boolean accept(File file)
            {
                return fileExists(file) && _fasta.isType(file);
            }
        }
    }
}
