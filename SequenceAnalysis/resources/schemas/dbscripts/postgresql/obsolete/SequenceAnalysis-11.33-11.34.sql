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

delete from sequenceanalysis.ref_aa_sequences a where (select name from sequenceanalysis.ref_nt_sequences n where n.rowid = a.ref_nt_id) = 'SIVmac239cy0163';
delete from sequenceanalysis.ref_nt_sequences where name = 'SIVmac239cy0163';
delete from sequenceanalysis.virus_strains where virus_strain = 'SIVmac239cy0163';

alter table sequenceanalysis.sequence_readsets
  add column machine_run_id varchar(200);

alter table sequenceanalysis.sequence_readsets
  add column fileid2 integer;

alter table sequenceanalysis.sequence_readsets
  add column raw_input_file2 integer;

alter table sequenceanalysis.sequence_readsets
  add column qc_file2 integer;

alter table sequenceanalysis.sequence_analyses
  add column inputfile2 integer;

delete from sequenceanalysis.aligners where name = 'bwa';
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa', 'BWA', 'BWA is a commonly used aligner, optimized for shorter reads.  It also supports paired-end reads.', '[{"xtype":"hidden","name":"pairedEnd","value":"true"}]');

delete from sequenceanalysis.aligners where name = 'mosaik';
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('mosaik', 'Mosaik', 'Mosaik is suitable for longer reads and has the option to retain multiple hits per read.  The only downside is that it can be slower.  When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.  It supports paired end reads.  The aligner is still good; however, Lastz also seems to perform well for SBT.', '[{"xtype":"hidden","name":"pairedEnd","value":"true"},{"name":"mosaik.output_multiple","fieldLabel":"Retain All Hits","xtype":"checkbox","renderData":{"helpPopup":"If selected, all hits above thresholds will be reported.  If not, only a single hit will be retained."},"checked":true},{"name":"mosaik.max_mismatch_pct","fieldLabel":"Max Mismatch Pct","renderData":{"helpPopup":"The maximum percent of bases allowed to mismatch per alignment.  Note: Ns are counted as mismatches"},"value":0.02,"minValue":0,"maxValue":1},{"name":"mosaik.hash_size","fieldLabel":"Hash Size","renderData":{"helpPopup":"The hash size used in alignment (see Mosaik documentation).  A large value is preferred for sequences expected to be highly similar to the reference"},"minValue":0,"value":32},{"name":"mosaik.max_hash_positions","fieldLabel":"Max Hash Positions","renderData":{"helpPopup":"The maximum number of hash matches that are passed to local alignment."},"minValue":0,"value":200},{"name":"mosaik.align_threshold","fieldLabel":"Alignment Threshold","renderData":{"helpPopup":"The alignment score required for an alignment to continue to local alignment.  Because the latter is slow, a higher value can improve speed."},"value":55}]');
