/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This interface describes an action that acts upon sequence data.  It is designed to let modules easily register actions that
 * process or visualize data, including those that require background pipeline processing.  If registed, this will appear as an
 * option in the outputfiles dataregion.  When the button is clicked, it will check whether the selected files are available to this
 * handler by calling canProcess().  If the files pass, the handler will do one of two things.  If this handler returns
 * a non-null string for getSuccessUrl(), the window will navigate to this URL.  This would allow the handler to have a secondary page
 * to capture additional user input or show a report.  Alternately,
 *
 * Created by bimber on 8/25/2014.
 */
public interface SequenceOutputHandler<T>
{
    public static enum TYPE
    {
        OutputFile(SequenceOutputProcessor.class),
        Readset(SequenceReadsetProcessor.class);

        private Class processorClass;

        TYPE(Class processorClass)
        {
            this.processorClass = processorClass;
        }

        public Class getProcessorClass()
        {
            return processorClass;
        }
    }

    public String getName();

    default String getAnalysisType(PipelineJob job)
    {
        return getName();
    }

    public String getDescription();

    /**
     * @return Whether this handler requires all inputs to be based on the same genome
     */
    default boolean requiresSingleGenome()
    {
        return true;
    }

    /**
     * @return Whether this handler requires each input to be associated with a genome
     */
    default boolean requiresGenome()
    {
        return true;
    }

    public boolean canProcess(SequenceOutputFile o);

    default boolean supportsSraArchivedData()
    {
        return false;
    }

    /**
     * If false, this handler will not be returned with the list of available handlers for a given set of files.
     * This allows the developer to register handlers that feed into the pipeline, but can only be called through specific code/UI
     * @return Whether to show this handler in user-facing UI
     */
    default boolean isVisible()
    {
        return true;
    }

    /**
     * This should be a JS function that will be called after we have verified that the output files selected
     * can be processed by this handler.  The handler should provide either a JS handler or a successURL.  If both are provided,
     * the URL will be used prferentially.  The JS handler function will be called with the following arguments:
     * dataRegionName: the name of the DataRegion
     * outputFileIds: the RowIDs of the output files selected
     */
    public @Nullable String getButtonJSHandler();

    /**
     * When the user chooses this option,
     */
    public @Nullable ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds);

    /**
     * The module that provides this handler.  If the module is not active in the current container, this handler will not be shown.
     */
    public Module getOwningModule();

    /**
     * An ordered list of ClientDependencies, which allows this handler to declare any client-side resources it depends upon.
     */
    public LinkedHashSet<String> getClientDependencies();

    /**
     * Set whether the user should be prompted for a workbook when this handler is selected
     */
    public boolean useWorkbooks();

    /**
     * Provides the opportunity for the handler to validate parameters prior to running
     * @param params
     * @return List of error messages.  Null or empty list indicates no errors.
     */
    default List<String> validateParameters(List<SequenceOutputFile> outputFiles, JSONObject params)
    {
        return null;
    }

    /**
     * If true, the server will run portions of this handler on the remote server.  This is intended to be a background pipeline
     * server, but in some cases this is also the webserver.
     */
    public boolean doRunRemote();

    /**
     * If true, the server will run portions of this handler on the local webserver.  In general it is a good idea to run intenstive
     * tasks on a remotely; however, some tasks require running SQL or other processes that require the webserver.
     */
    public boolean doRunLocal();

    public T getProcessor();

    /**
     * If true, a separate job will be queued per file.  If not, a single job will run for all files.
     */
    public boolean doSplitJobs();

    public interface SequenceProcessor
    {

    }

    public interface SequenceOutputProcessor extends SequenceProcessor
    {
        /**
         * Allows handlers to perform setup on the webserver prior to remote running.  This will be run in the background as a pipeline job.
         * @param ctx Provides context about the active pipeline job
         * @param inputFiles      The list of input files to process
         * @param actions
         * @param outputsToCreate
         */
        default void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        /**
         * Allows handlers to perform processing on the input SequenceOutputFiles locally.  This will be run in the background as a pipeline job.
         * The intention is to allow handlers to only implement the actual processing code they need, without a separate server-side action, pipeline job, etc.
         * Certain handlers will not use this method, and it is recommended that they throw an IllegalArgumentException
         *
         * @param support Provides context about the active pipeline job
         * @param inputFiles The list of input files to process
         * @param params
         * @param outputDir
         * @param actions
         * @param outputsToCreate
         */
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException;

        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException;

        default void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {

        }
    }

    public interface SequenceReadsetProcessor extends SequenceProcessor
    {
        /**
         * Allows handlers to perform setup on the webserver prior to remote running.  This will be run in the background as a pipeline job.
         * @param job             The pipeline job running this task
         * @param support Provides context about the active pipeline job
         * @param readsets      The list of readsets to process
         * @param params
         * @param outputDir
         * @param actions
         * @param outputsToCreate
         */
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException;

        /**
         *
         * @param support Provides context about the active pipeline job
         * @param readsets The list of readsets to process
         * @param params
         * @param outputDir
         * @param actions
         * @param outputsToCreate
         */
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException;

        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException;

        default void complete(PipelineJob job, List<Readset> readsets, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {

        }
    }

    public interface JobContext extends PipelineContext
    {
        public JSONObject getParams();

        public File getOutputDir();

        public void addActions(RecordedAction... action);

        public TaskFileManager getFileManager();

        public void addSequenceOutput(SequenceOutputFile o);

        public PipeRoot getFolderPipeRoot();
    }

    public interface MutableJobContext extends JobContext
    {
        public void setFileManager(TaskFileManager manager);
    }

    public interface HasActionNames
    {
        public Collection<String> getAllowableActionNames();
    }

    public static interface TracksVCF
    {
        public File getScatterJobOutput(JobContext ctx) throws PipelineJobException;

        default File finalizeScatterJobOutput(JobContext ctx, File primaryOutput) throws PipelineJobException
        {
            return primaryOutput;
        }

        public SequenceOutputFile createFinalSequenceOutput(PipelineJob job, File processed, List<SequenceOutputFile> inputFiles) throws PipelineJobException;
    }

    public static interface HasCustomVariantMerge
    {
        public File performVariantMerge(TaskFileManager manager, RecordedAction action, SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler, PipelineJob job) throws PipelineJobException;
    }
}