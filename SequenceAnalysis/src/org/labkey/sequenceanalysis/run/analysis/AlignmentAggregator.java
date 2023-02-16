/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 3:23 PM
 *
 * An interface describing a class that is designed to iterate a BAM alignments, compute a metric and save this to the DB
 */
public interface AlignmentAggregator
{
    void writeOutput(User u, Container c, AnalysisModel model);

    void inspectAlignment(SAMRecord record, @Nullable ReferenceSequence ref, Map<Integer, List<NTSnp>> snps) throws PipelineJobException;

    String getSynopsis();
}
