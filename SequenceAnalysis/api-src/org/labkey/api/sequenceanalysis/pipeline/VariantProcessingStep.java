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

import htsjdk.samtools.util.Interval;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.laboratory.DemographicsProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:13 PM
 */
public interface VariantProcessingStep extends PipelineStep
{
    Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException;

    default void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {

    }

    default void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
    {

    }

    enum ScatterGatherMethod
    {
        none(false),
        contig(false),
        chunked(true),
        fixedJobs(false);

        private final boolean _mayRequireSort;

        ScatterGatherMethod(boolean mayRequireSort)
        {
            _mayRequireSort = mayRequireSort;
        }

        public boolean mayRequireSort()
        {
            return _mayRequireSort;
        }
    }

    interface Output extends PipelineStepOutput
    {
        File getVCF();
    }

    interface RequiresPedigree
    {
        default String getDemographicsProviderName(PipelineStepProvider<?> provider, PipelineJob job, int stepIdx)
        {
            return provider.getParameterByName(PedigreeToolParameterDescriptor.NAME).extractValue(job, provider, stepIdx, String.class);
        }

        default DemographicsProvider getDemographicsProvider(PipelineStepProvider<?> provider, PipelineJob job, int stepIdx)
        {
            if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
            {
                throw new IllegalStateException("getDemographicsProvider() can only be run from the webserver");
            }

            return LaboratoryService.get().getDemographicsProviderByName(job.getContainer(), job.getUser(), getDemographicsProviderName(provider, job, stepIdx));
        }
    }

    interface SupportsScatterGather
    {
        default void validateScatter(ScatterGatherMethod method, PipelineJob job) throws IllegalArgumentException
        {

        }

        default void performAdditionalMergeTasks(SequenceOutputHandler.JobContext ctx, PipelineJob job, TaskFileManager manager, ReferenceGenome genome, List<File> orderedScatterOutputs, List<String> orderedJobDirs) throws PipelineJobException
        {
            ctx.getLogger().debug("No additional merge tasks are implemented for: " + getClass().getName());
        }

        default boolean doSortAfterMerge()
        {
            return false;
        }
    }

    interface MayRequirePrepareTask
    {
        boolean isRequired(PipelineJob job);

        void doWork(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws PipelineJobException;
    }
}
