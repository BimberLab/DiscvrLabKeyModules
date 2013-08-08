/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
SELECT
sa.analysis_id as analysis_id,
sa.readname,
group_concat(DISTINCT sa.ref_nt_id.name) as Refs,
group_concat(DISTINCT sa.ref_nt_id) as Ref_Ids,
count(distinct sa.ref_nt_id) as NumHits,
group_concat(distinct sa.orientation) as orientations,
group_concat(distinct sa.rowid) as RowIds,

from sequenceanalysis.sequence_alignments sa

WHERE sa.status=true and sa.num_mismatches = 0

GROUP BY sa.analysis_id, sa.readname