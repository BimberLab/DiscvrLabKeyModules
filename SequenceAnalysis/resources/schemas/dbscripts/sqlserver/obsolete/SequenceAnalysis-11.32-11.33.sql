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

CREATE TABLE sequenceanalysis.aligners (
    RowId INT IDENTITY(1,1) NOT NULL,

    name varchar(100) not null,
    displayname varchar(100),
    description text,
    jsonconfig text,

    Created DATETIME,
    Modified DATETIME,

    CONSTRAINT PK_aligners PRIMARY KEY (rowId)
);

insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bowtie', 'Bowtie', 'Bowtie is a fast aligner often used for short reads.  Disadvantages are that it does not perform gapped alignment.  It will return a single hit for each read.', '[{"name": "bowtie.max_seed_mismatches","fieldLabel": "Max Seed Mismatches","value": 3},{"name": "bowtie.seed_length","fieldLabel": "Seed Length","value": 20}]');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('lastz', 'Lastz', 'Lastz has performed well for both sequence-based genotyping and viral analysis.  ', '[{"name": "lastz.identity","fieldLabel": "Min Pct Identity","renderData": {"helpPopup": "The minimum percent identity required per alignment for that match to be included"},"value": 98},{"name": "lastz.continuity","fieldLabel": "Percent Continuity","renderData": {"helpPopup": "Continuity is the percentage of alignment columns that are not gaps. Alignment blocks outside the given range are discarded."},"value": 90}]');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa', 'BWA', 'BWA is a commonly used aligner, optimized for shorter reads.  It also supports paired-end reads.', '');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa-sw', 'BWA-SW', 'BWA-SW uses a different algorithm than BWA that is better suited for longer reads.  By design it will only return a single hit for each read.  It it currently recommended for viral analysis and other applications that align longer reads, but do not require retaining multiple hits.', '');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('mosaik', 'Mosaik', 'Mosaik is suitable for longer reads and has the option to retain multiple hits per read.  The only downside is that it can be slower.  When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.  It supports paired end reads.  The aligner is still good; however, Lastz also seems to perform well for SBT.', '[{"name":"mosaik.output_multiple","fieldLabel":"Retain All Hits","xtype":"checkbox","renderData":{"helpPopup":"If selected, all hits above thresholds will be reported.  If not, only a single hit will be retained."},"checked":true},{"name":"mosaik.max_mismatch_pct","fieldLabel":"Max Mismatch Pct","renderData":{"helpPopup":"The maximum percent of bases allowed to mismatch per alignment.  Note: Ns are counted as mismatches"},"value":0.02,"minValue":0,"maxValue":1},{"name":"mosaik.hash_size","fieldLabel":"Hash Size","renderData":{"helpPopup":"The hash size used in alignment (see Mosaik documentation).  A large value is preferred for sequences expected to be highly similar to the reference"},"minValue":0,"value":32},{"name":"mosaik.max_hash_positions","fieldLabel":"Max Hash Positions","renderData":{"helpPopup":"The maximum number of hash matches that are passed to local alignment."},"minValue":0,"value":200},{"name":"mosaik.align_threshold","fieldLabel":"Alignment Threshold","renderData":{"helpPopup":"The alignment score required for an alignment to continue to local alignment.  Because the latter is slow, a higher value can improve speed."},"value":55}]');
