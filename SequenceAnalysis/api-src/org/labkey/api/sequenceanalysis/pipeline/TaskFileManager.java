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
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class helps manage the inputs and outputs created during a pipeline job.  It will gather inputs, outputs and intermediate files.
 * On complete, it will handle translation of filepaths from the working directory to the final location.
 *
 * User: bimber
 * Date: 6/20/2014
 * Time: 5:38 PM
 */
public interface TaskFileManager
{
    public void addSequenceOutput(SequenceOutputFile o);

    public void addSequenceOutput(File file, String label, String category, @Nullable Integer readsetId, @Nullable Integer analysisId, @Nullable Integer genomeId, @Nullable String description);

    public void addOutput(RecordedAction action, String role, File file);

    public void addInput(RecordedAction action, String role, File file);

    public void addStepOutputs(RecordedAction action, PipelineStepOutput output);

    /**
     * Registers a file that will be deleted only at the very end of the protocol
     */
    public void addDeferredIntermediateFile(File file);

    public void deleteDeferredIntermediateFiles();

    public boolean isDeleteIntermediateFiles();

    public boolean isCopyInputsLocally();

    public void addIntermediateFile(File f);

    public void addIntermediateFiles(Collection<File> files);

    public void removeIntermediateFile(File f);

    public void addPicardMetricsFiles(List<PipelineStepOutput.PicardMetricsOutput> files) throws PipelineJobException;

    public void writeMetricsToDb(Map<Integer, Integer> readsetMap, Map<Integer, Map<PipelineStepOutput.PicardMetricsOutput.TYPE, File>> typeMap) throws PipelineJobException;

    public void deleteIntermediateFiles() throws PipelineJobException;

    public void createSequenceOutputRecords(@Nullable Integer analysisId)throws PipelineJobException;

    //should be used for remote jobs or local jobs running in a separate working directory
    public void cleanup(Collection<RecordedAction> actions) throws PipelineJobException;

    public InputFileTreatment getInputFileTreatment();

    public void processUnzippedInputs();

    public void decompressInputFiles(Pair<File, File> pair, List<RecordedAction> actions);

    public enum InputFileTreatment
    {
        none(),
        delete(),
        compress(),
        leaveInPlace();
    }
}
