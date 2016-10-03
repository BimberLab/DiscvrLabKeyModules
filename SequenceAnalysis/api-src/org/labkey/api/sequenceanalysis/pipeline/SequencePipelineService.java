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


import htsjdk.samtools.SAMFileHeader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.CommandWrapper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:43 PM
 */
abstract public class SequencePipelineService
{
    public static final String SEQUENCE_TOOLS_PARAM = "SEQUENCEANALYSIS_TOOLS";

    static SequencePipelineService _instance;

    public static SequencePipelineService get()
    {
        return _instance;
    }

    static public void setInstance(SequencePipelineService instance)
    {
        _instance = instance;
    }

    abstract public void registerPipelineStep(PipelineStepProvider provider);

    abstract public Set<PipelineStepProvider> getAllProviders();

    abstract public <StepType extends PipelineStep> Set<PipelineStepProvider<StepType>> getProviders(Class<? extends StepType> stepType);

    abstract public <StepType extends PipelineStep> PipelineStepProvider<StepType> getProviderByName(String name, Class<? extends StepType> stepType);

    abstract public <StepType extends PipelineStep> List<PipelineStepProvider<StepType>> getSteps(PipelineJob job, Class<StepType> stepType);

    abstract public File getExeForPackage(String packageName, String exe);

    abstract public void ensureSequenceDictionaryExists(File referenceFasta, Logger log, boolean forceRecreate) throws PipelineJobException;

    abstract public String getUnzippedBaseName(String filename);

    abstract public String getJavaFilepath();

    abstract public List<String> getJavaOpts();

    @Nullable
    abstract public File getRemoteGenomeCacheDirectory();

    abstract public Integer getMaxThreads(PipelineJob job);

    abstract public CommandWrapper getCommandWrapper(Logger log);

    abstract public List<File> getSequenceJobInputFiles(PipelineJob job);

    /**
     * Throws exception if no run is found
     */
    abstract public Integer getExpRunIdForJob(PipelineJob job) throws PipelineJobException;

    abstract public long getLineCount(File f) throws PipelineJobException;

    abstract public File ensureBamIndex(File f, Logger log, boolean forceDeleteExisting) throws PipelineJobException;

    abstract public SAMFileHeader.SortOrder getBamSortOrder(File bam) throws IOException;

    abstract public File sortVcf(File inputVcf, @Nullable File outputVcf, File sequenceDictionary, Logger log) throws PipelineJobException;

    abstract public void sortROD(File input, Logger log) throws IOException, PipelineJobException;

    abstract public String inferRPath(Logger log);

    abstract public void registerResourceSettings(JobResourceSettings settings);

    abstract public Map<String, Object> getQualityMetrics(File fastq, Logger log);
}
