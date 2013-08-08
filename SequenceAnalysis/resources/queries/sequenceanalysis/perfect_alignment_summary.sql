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

r.analysis_id,
r.analysis_id || ';' || r.ref_ids as keyField,
r.refs,
r.ref_ids,
count(r.readname) as Total,
sum(r.forward) as TotalForward,
sum(r.reverse) as TotalReverse,

FROM (
select *,
case when orientations = '0' then 1 else 0 end as forward,
case when orientations = '1' then 1 else 0 end as reverse,
case when orientations = '0,1' then 1 else 0 end as both
from sequenceanalysis.perfect_alignments_by_read r
) r

GROUP BY r.analysis_id, r.refs, r.ref_ids

