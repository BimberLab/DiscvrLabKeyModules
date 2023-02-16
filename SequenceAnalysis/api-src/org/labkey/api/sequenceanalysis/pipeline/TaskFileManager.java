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
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class helps manage the inputs and outputs created during a pipeline job.  It will gather inputs, outputs and intermediate files.
 * On complete, it will handle translation of filepaths from the working directory to the final location.
 *
 * User: bimber
 * Date: 6/20/2014
 * Time: 5:38 PM
 */
public interface TaskFileManager extends PipelineOutputTracker
{
    void addSequenceOutput(SequenceOutputFile o);

    void addOutput(RecordedAction action, String role, File file);

    void addInput(RecordedAction action, String role, File file);

    void addStepOutputs(RecordedAction action, PipelineStepOutput output);

    /**
     * Registers a file that will be deleted only at the very end of the protocol
     */
    void addDeferredIntermediateFile(File file);

    void deleteDeferredIntermediateFiles();

    boolean isDeleteIntermediateFiles();

    boolean isCopyInputsLocally();

    void addPicardMetricsFiles(List<PipelineStepOutput.PicardMetricsOutput> files) throws PipelineJobException;

    void writeMetricsToDb(Map<Integer, Integer> readsetMap, Map<Integer, Map<PipelineStepOutput.PicardMetricsOutput.TYPE, File>> typeMap) throws PipelineJobException;

    void deleteIntermediateFiles() throws PipelineJobException;

    Set<SequenceOutputFile> createSequenceOutputRecords(@Nullable Integer analysisId)throws PipelineJobException;

    //should be used for remote jobs or local jobs running in a separate working directory
    void cleanup(Collection<RecordedAction> actions) throws PipelineJobException;

    void cleanup(Collection<RecordedAction> actions, @Nullable AbstractResumer resumer) throws PipelineJobException;

    InputFileTreatment getInputFileTreatment();

    Set<File> getIntermediateFiles();

    void processUnzippedInputs();

    void decompressInputFiles(Pair<File, File> pair, List<RecordedAction> actions);

    Set<SequenceOutputFile> getOutputsToCreate();

    void addCommandsToAction(List<String> commands, RecordedAction action);

    void onResume(PipelineJob job, WorkDirectory wd);

    enum InputFileTreatment
    {
        none(),
        delete(),
        compress(),
        leaveInPlace()
    }
}
