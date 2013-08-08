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
select
rowid,
(select count(*) from sequenceanalysis.sequence_alignments t where t.analysis_id = s.rowid) as Total_Alignments,
(select count(*) from sequenceanalysis.alignment_summary_grouped t where t.analysis_id = s.rowid) as Total_Alignment_Summaries,
(select count(*) from sequenceanalysis.nt_snps t where t.analysis_id = s.rowid) as Total_NT_SNPs,
(select count(*) from sequenceanalysis.aa_snps t where t.analysis_id = s.rowid) as Total_AA_SNPs,

from sequenceanalysis.sequence_analyses s
