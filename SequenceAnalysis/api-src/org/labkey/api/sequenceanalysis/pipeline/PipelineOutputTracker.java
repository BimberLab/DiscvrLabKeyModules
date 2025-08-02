package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public interface PipelineOutputTracker
{
    /**
     * Add an intermediate file.  If the user selected 'delete intermediates', this will be deleted on job success.
     */
    void addIntermediateFile(File file);

    void addIntermediateFiles(Collection<File> files);

    /**
     * Add a SequenceOutputFile for this job.  These files are tracked and displayed through the browser UI.
     */
    void addSequenceOutput(File file, String label, String category, @Nullable Long readsetId, @Nullable Long analysisId, @Nullable Integer genomeId, @Nullable String description);

    /**
     * Remove a previously added intermediate file
     * @param toRemove The file to remove
     */
    void removeIntermediateFile(File toRemove);

}
