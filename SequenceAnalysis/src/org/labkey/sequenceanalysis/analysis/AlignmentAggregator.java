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
package org.labkey.sequenceanalysis.analysis;

import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.SAMRecord;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/22/12
 * Time: 3:23 PM
 */
public interface AlignmentAggregator extends BamAggregator
{
    public void inspectAlignment(SAMRecord record, @Nullable ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, @Nullable CigarPositionIterable cpi);
}
