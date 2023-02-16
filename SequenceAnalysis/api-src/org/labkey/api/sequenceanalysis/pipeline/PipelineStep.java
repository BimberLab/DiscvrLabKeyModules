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

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:08 PM
 *
 * This describes one step in the pipeline, which is divided into the steps below.  Subclasses of this interface
 * define methods specific to that type of PipelineStep.  Each step is typically a wrapper around a command line tool or script.
 * Supported step types must be registered with SequencePipelineService
 */
public interface PipelineStep
{
    PipelineContext getPipelineCtx();

    PipelineStepProvider<?> getProvider();

    int getStepIdx();

    void setStepIdx(int stepIdx);

    enum CorePipelineStepTypes
    {
        fastqProcessing(PreprocessingStep.class),
        referenceLibraryCreation(ReferenceLibraryStep.class),
        alignment(AlignmentStep.class),
        bamPostProcessing(BamProcessingStep.class),
        variantProcessing(VariantProcessingStep.class),
        assembly(AssemblyStep.class),
        analysis(AnalysisStep.class);

        private final Class<? extends PipelineStep> _stepClass;

        CorePipelineStepTypes(Class<? extends PipelineStep> clazz)
        {
            _stepClass = clazz;
        }

        public Class<? extends PipelineStep> getStepClass()
        {
            return _stepClass;
        }
    }
}
