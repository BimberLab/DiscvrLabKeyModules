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
PARAMETERS(max_mismatch INTEGER);

SELECT t1.*, a.rowid, a.ref_nt_id, a.ref_nt_id.name,a.status,a.container

FROM (
SELECT
sa.analysis_id,
sa.readname,
group_concat(DISTINCT sa.ref_nt_id.name) as Refs,
group_concat(DISTINCT sa.ref_nt_id) as Ref_Ids,
count(distinct sa.ref_nt_id) as NumHits,

avg(sa.num_mismatches) as avg_mismatches,
min(sa.num_mismatches) as min_mismatches,
max(sa.num_mismatches) as max_mismatches,
Max_Mismatch,
group_concat(distinct sa.rowid) as RowIds,

from sequenceanalysis.sequence_alignments sa

WHERE sa.num_mismatches <= Max_Mismatch and sa.status = true

GROUP BY sa.analysis_id, sa.readname;
) t1
LEFT JOIN sequenceanalysis.sequence_alignments a
ON (a.analysis_id = t1.analysis_id AND t1.readname = a.readname);