package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.util.Pair;

import java.io.File;
import java.util.List;

/**
 * Provides information about the outputs of a pipeline step.  This does not act on these files,
 * but will provide the pipeline with information used to track outputs and cleanup intermediate files
 *
 * User: bimber
 * Date: 6/21/2014
 * Time: 7:47 AM
 */
public interface PipelineStepOutput
{
    /**
     * Returns a list of pairs giving the output file and role of this output
     */
    public List<Pair<File, String>> getOutputs();

    /**
     * Returns a list of pairs giving the output file and role of this output
     */
    public List<File> getOutputsOfRole(String role);

    /**
     * Returns a list of intermediate files created during this step.  Intermediate files are files
     * that are deemed non-essential by this step.  If the pipeline has selected deleteIntermediaFiles=true,
     * these files will be deleted during the cleanup step.
     */
    public List<File> getIntermediateFiles();

    /**
     * Returns a list of deferred delete intermediate files created during this step.  These are similar to the files
     * tagged as intermediate files, except that the delete step does not run until the very end of the pipeline.
     * This allows earlier steps to create products that are needed by later steps (such as aligner-specific indexes),
     * but still delete these files at the end of the process.
     */
    public List<File> getDeferredDeleteIntermediateFiles();

}
