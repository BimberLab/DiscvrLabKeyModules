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
import org.labkey.api.sequenceanalysis.model.Readset;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:10 PM
 */
public interface BamProcessingStep extends PipelineStep
{
    BamProcessingStep.Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException;

    /**
     * The BamProcessingStep must call Output.setBam() to explicitly specify the modified file.  This method exists as a check
     * to prevent the developer from forgetting this.  Unless this is overridden, the pipeline will error if the original BAM is returned
     * instead.
     */
    default boolean expectToCreateNewBam()
    {
        return true;
    }

    interface Output extends PipelineStepOutput
    {
        /**
         * Returns the processed BAM
         */
        File getBAM();
    }
}
