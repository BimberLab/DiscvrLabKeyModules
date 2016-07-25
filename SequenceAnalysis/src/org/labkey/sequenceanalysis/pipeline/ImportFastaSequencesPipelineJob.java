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
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
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
    private List<File> _fastas;
    private Map<String, String> _params;
    private boolean _splitWhitespace;
    private boolean _createLibrary;
    private boolean _deleteInputs = false;
    private Map<String, String> _libraryParams;

    public ImportFastaSequencesPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, List<File> fastas, Map<String, String> params, boolean splitWhitespace, boolean createLibrary, Map<String, String> libraryParams) throws IOException
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _fastas = fastas;
        _params = params;
        _splitWhitespace = splitWhitespace;
        _createLibrary = createLibrary;
        _libraryParams = libraryParams;

        File outputDir = RefNtSequenceModel.getReferenceSequenceDir(c);
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("fastaImport", "log")));
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

    public Map<String, String> getLibraryParams()
    {
        return _libraryParams;
    }

    public void setLibraryParams(Map<String, String> libraryParams)
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
            addAction(actionId, DetailsURL.fromString("sequenceanalysis/importFasta.view", context.getContainer()).getActionURL(), "Import Reference Sequences", directory, directory.listFiles(new UploadFileFilter()), true, true, includeAll);
        }

        public static class UploadFileFilter extends PipelineProvider.FileEntryFilter
        {
            private static FileType _fasta = new FileType(Arrays.asList("fa", "fasta"), "fasta", FileType.gzSupportLevel.SUPPORT_GZ);

            public boolean accept(File file)
            {
                return fileExists(file) && _fasta.isType(file);
            }
        }
    }
}
