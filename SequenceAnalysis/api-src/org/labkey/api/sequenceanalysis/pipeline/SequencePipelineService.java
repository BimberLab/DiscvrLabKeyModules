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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
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

    abstract public void registerPipelineStepType(Class<? extends PipelineStep> clazz, String paramName);

    abstract public void registerPipelineStep(PipelineStepProvider provider);

    abstract public Set<PipelineStepProvider> getAllProviders();

    abstract public <StepType extends PipelineStep> Set<PipelineStepProvider<StepType>> getProviders(Class<StepType> stepType);

    abstract public <StepType extends PipelineStep> PipelineStepProvider<StepType> getProviderByName(String name, Class<StepType> stepType);

    abstract public <StepType extends PipelineStep> List<PipelineStepCtx<StepType>> getSteps(PipelineJob job, Class<StepType> type);

    abstract public @Nullable String getParamNameForStepType(Class<? extends PipelineStep> stepType);

    abstract public File getExeForPackage(String packageName, String exe);

    abstract public void ensureSequenceDictionaryExists(File referenceFasta, Logger log, boolean forceRecreate) throws PipelineJobException;

    abstract public String getUnzippedBaseName(String filename);

    abstract public String getJavaFilepath();

    abstract public String getJava8FilePath();

    abstract public String getJavaTempDir();

    abstract public List<String> getJavaOpts();

    abstract public List<String> getJavaOpts(@Nullable Integer maxRamOverride);

    abstract public boolean isRemoteGenomeCacheUsed();

    @Nullable
    abstract public File getRemoteGenomeCacheDirectory();

    abstract public Integer getMaxThreads(Logger log);

    abstract public Integer getMaxRam();

    abstract public CommandWrapper getCommandWrapper(Logger log);

    /**
     * This allows instances to override the default docker executable. If DOCKER_EXE is provided in pipelineConfig.xml, this
     * will be used. Otherwise this defaults to 'docker'
     */
    abstract public String getDockerCommand();

    /**
     * This allows instances to supply a user that will be passed to 'docker login'. This is rarely needed. It can be set using DOCKER_USER in pipelineConfig.xml
     */
    abstract public String getDockerUser();

    abstract public List<File> getSequenceJobInputFiles(PipelineJob job);

    /**
     * Throws exception if no run is found
     */
    abstract public Integer getExpRunIdForJob(PipelineJob job) throws PipelineJobException;

    abstract public long getLineCount(File f) throws PipelineJobException;

    abstract public File ensureBamIndex(File f, Logger log, boolean forceDeleteExisting) throws PipelineJobException;

    abstract public SAMFileHeader.SortOrder getBamSortOrder(File bam) throws IOException;

    abstract public File sortVcf(File inputVcf, @Nullable File outputVcf, File sequenceDictionary, Logger log) throws PipelineJobException;

    /**
     *
     * @param input
     * @param log
     * @param startColumnIdx The 1-based column on which to sort.  BED files are 2 and GTF/GFF are 4
     * @throws IOException
     * @throws PipelineJobException
     */
    abstract public void sortROD(File input, Logger log, Integer startColumnIdx) throws IOException, PipelineJobException;

    abstract public String inferRPath(Logger log);

    abstract public void registerResourceSettings(JobResourceSettings settings);

    abstract public Map<String, Object> getQualityMetrics(File fastq, Logger log);

    abstract public boolean hasMinLineCount(File f, long minLines) throws PipelineJobException;

    abstract public void updateOutputFile(SequenceOutputFile o, PipelineJob job, Integer runId, Integer analysisId);

    abstract public PreprocessingStep.Output simpleTrimFastqPair(File fq1, File fq2, List<String> params, File outDir, Logger log) throws PipelineJobException;

    // Note: this primarily exists for testing. The returned TaskFileManager does not have key values set: SequenceJob, WorkingDir (File) and WorkDirectory
    abstract public TaskFileManager getTaskFileManager();
}
