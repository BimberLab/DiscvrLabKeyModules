package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;

/**
 * Created by bimber on 1/5/2015.
 */
public class NcbiGenomeImportPipelineJob extends PipelineJob
{
    private String _remoteDirName;
    private String _genomeName;
    private String _genomePrefix;
    private String _species;

    public NcbiGenomeImportPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, String remoteDirName, String genomeName, String genomePrefix, String species)
    {
        super(NcbiGenomeImportPipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _remoteDirName = remoteDirName;
        _genomeName = genomeName;
        _genomePrefix = genomePrefix;
        _species = species;

        File outputDir = new File(pipeRoot.getRootPath(), NcbiGenomeImportPipelineProvider.NAME);
        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("ncbiGenomeImport", "log")));
    }

    @Override
    public String getDescription()
    {
        return "Load genome from NCBI";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return null;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(NcbiGenomeImportPipelineJob.class));
    }

    public String getRemoteDirName()
    {
        return _remoteDirName;
    }

    public String getGenomeName()
    {
        return _genomeName;
    }

    public String getGenomePrefix()
    {
        return _genomePrefix;
    }

    public String getSpecies()
    {
        return _species;
    }
}
