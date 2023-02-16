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

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AnalysisStep extends PipelineStep
{
    /**
     * Optional.  Allows this analysis to gather any information from the server required to execute the analysis.  This information needs to be serialized
     * to run remotely, which could be as simple as writing to a text file.
     * @throws PipelineJobException
     */
    default void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {

    }

    /**
     * Will perform analysis steps on the remote pipeline server
     * Note: outputDir is the location where the BAM is expected to be written
     */
    Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException;

    /**
     * Will perform analysis steps on the local webserver
     * Note: outputDir is the location where the BAM was created (if this step creates a BAM)
     */
    Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException;

    interface Output extends PipelineStepOutput
    {

    }
}
