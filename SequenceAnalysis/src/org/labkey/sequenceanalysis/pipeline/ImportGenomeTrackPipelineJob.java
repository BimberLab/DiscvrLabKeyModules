package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
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
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class ImportGenomeTrackPipelineJob extends PipelineJob
{
    private int _libraryId;
    private String _trackName;
    private String _trackDescription;
    private File _track;
    private boolean _doChrTranslation;
    private File _outDir;

    // Default constructor for serialization
    protected ImportGenomeTrackPipelineJob()
    {
    }

    public ImportGenomeTrackPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, int libraryId, String trackName, File track, String fileName, String trackDescription, boolean doChrTranslation) throws IOException
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _libraryId = libraryId;
        _trackName = trackName;
        _trackDescription = trackDescription;
        _doChrTranslation = doChrTranslation;

        _outDir = ImportFastaSequencesPipelineJob.createLocalDirectory(pipeRoot);
        File target = new File(_outDir, fileName);
        FileUtils.moveFile(track, target);
        _track = target;

        setLogFile(new File(_outDir, FileUtil.makeFileNameWithTimestamp("trackImport", "log")));
    }

    @Override
    public String getDescription()
    {
        return "Import Tracks";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(ImportGenomeTrackPipelineJob.class));
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
        public static final String NAME = "importGenomeTracks";

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

            String actionId = "SequenceAnalysis.importTrack:ImportTrack";
            addAction(actionId, DetailsURL.fromString("sequenceanalysis/importTracks.view", context.getContainer()).getActionURL(), "Import Genome Tracks", directory, directory.listFiles(new UploadFileFilter()), true, true, includeAll);
        }

        public static class UploadFileFilter extends FileEntryFilter
        {
            @Override
            public boolean accept(File file)
            {
                return fileExists(file) && (
                    SequenceUtil.FILETYPE.gtf.getFileType().isType(file) ||
                    SequenceUtil.FILETYPE.gff.getFileType().isType(file) ||
                    SequenceUtil.FILETYPE.bed.getFileType().isType(file) ||
                    SequenceUtil.FILETYPE.bw.getFileType().isType(file) ||
                    SequenceUtil.FILETYPE.vcf.getFileType().isType(file)
                );
            }
        }
    }

    public int getLibraryId()
    {
        return _libraryId;
    }

    public void setLibraryId(int libraryId)
    {
        _libraryId = libraryId;
    }

    public String getTrackName()
    {
        return _trackName;
    }

    public void setTrackName(String trackName)
    {
        _trackName = trackName;
    }

    public String getTrackDescription()
    {
        return _trackDescription;
    }

    public void setTrackDescription(String trackDescription)
    {
        _trackDescription = trackDescription;
    }

    public File getTrack()
    {
        return _track;
    }

    public void setTrack(File track)
    {
        _track = track;
    }

    public void setOutDir(File outDir)
    {
        _outDir = outDir;
    }

    public boolean doChrTranslation()
    {
        return _doChrTranslation;
    }
}
