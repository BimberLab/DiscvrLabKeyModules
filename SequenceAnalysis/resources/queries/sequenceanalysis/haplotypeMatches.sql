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

PARAMETERS(MinimumPercent INTEGER default 1)

select

t.*,
t2.totalRequired as totalLineagesRequiredByHaplotype,
t2.requiredLineages,
t3.lineages as lineagesDefinedInHaplotype,
t3.total as totalLineagesDefinedInHaplotype,
(cast(t.totalLineagesPresent as float) / t3.total) * 100 as pctFound

from (

SELECT
h.haplotype,
asg.analysis_id,
group_concat(distinct asg.lineages, chr(10)) as lineagesPresent,
sum(cast(h.required as integer)) as totalRequiredLineagesPresent,
count(distinct asg.lineages) as totalLineagesPresent,

FROM sequenceanalysis.haplotype_sequences h
inner join sequenceanalysis.alignment_summary_grouped asg
  ON (h.lineage = asg.lineages AND h.present = true)

WHERE (MinimumPercent IS NULL OR asg.percent >= MinimumPercent) and asg.total_reads > 2

group by
h.haplotype,
asg.analysis_id

) t

LEFT JOIN (select haplotype, count(h.required) as totalRequired, group_concat(h.lineage, chr(10)) as requiredLineages FROM sequenceanalysis.haplotype_sequences h WHERE required = true GROUP BY haplotype) t2
ON (t2.haplotype = t.haplotype)

LEFT JOIN (select haplotype, count(*) as total, group_concat(h.lineage, chr(10)) as lineages FROM sequenceanalysis.haplotype_sequences h GROUP BY haplotype) t3
ON (t3.haplotype = t.haplotype)

WHERE totalRequiredLineagesPresent >= totalRequired