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
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AlignmentStep extends PipelineStep
{
    /**
     * Creates any indexes needed by this aligner if not already present.
     * @throws PipelineJobException
     */
    IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException;

    default String getIndexCachedDirName(PipelineJob job)
    {
        return getProvider().getName();
    }

    /**
     * Performs a reference guided alignment using the provided files.
     * @param inputFastqs1 The forward FASTQ file(s). In most cases this is a single FASTQ. The aligner must return true for canAlignMultiplePairsAtOnce() otherwise.
     * @param inputFastqs2 The second FASTQ(s), if using paired end data
     * @param basename The basename to use as the output
     * @throws PipelineJobException
     */
    AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException;

    default boolean canAlignMultiplePairsAtOnce()
    {
        return false;
    }

    default File assertSingleFile(List<File> inputs)
    {
        if (inputs != null && inputs.size() > 1)
        {
            throw new IllegalArgumentException("This aligner only supports a single pair of input FASTQs");
        }

        return inputs == null ? null : inputs.get(0);
    }

    boolean doAddReadGroups();

    boolean doSortIndexBam();

    default boolean supportsMetrics()
    {
        return true;
    }

    boolean alwaysCopyIndexToWorkingDir();

    boolean supportsGzipFastqs();

    @Override
    AlignmentStepProvider getProvider();

    default String getAlignmentDescription()
    {
        return "Aligner: " + getProvider().getName();
    }

    interface AlignmentOutput extends PipelineStepOutput
    {
        /**
         * If created, returns a pair of FASTQ files containing the unaligned reads
         */
        File getUnalignedReadsFastq();

        File getBAM();
    }

    /**
     * Optional.  Allows this analysis to gather any information from the server required to execute the alignment.  This information needs to be serialized
     * to run remotely, which could be as simple as writing to a text file.
     * @throws PipelineJobException
     */
    default void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {

    }

    /**
     * Optional.  Allows steps to be run on the webserver upon completion.
     */
    default void complete(SequenceAnalysisJobSupport support, AnalysisModel model, Collection<SequenceOutputFile> outputFilesCreated) throws PipelineJobException
    {

    }

    interface IndexOutput extends PipelineStepOutput
    {

    }
}
