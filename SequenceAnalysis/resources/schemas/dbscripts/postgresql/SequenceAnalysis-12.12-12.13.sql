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

ALTER table sequenceanalysis.quality_metrics
  add column readset integer;

--delete orphan rows in non-existant containers.  the container listener should handle future cases
DELETE from sequenceanalysis.sequence_readsets t WHERE (select entityid from core.containers c where c.entityid = t.container) is null;
DELETE from sequenceanalysis.sequence_analyses t WHERE (select entityid from core.containers c where c.entityid = t.container) is null;

--then cleanup orphans lacking an analysis row
DELETE from sequenceanalysis.aa_snps t WHERE (select rowid from sequenceanalysis.sequence_analyses a where a.rowid = t.analysis_id) is null;
DELETE from sequenceanalysis.nt_snps t WHERE (select rowid from sequenceanalysis.sequence_analyses a where a.rowid = t.analysis_id) is null;
DELETE from sequenceanalysis.sequence_coverage t WHERE (select rowid from sequenceanalysis.sequence_analyses a where a.rowid = t.analysis_id) is null;
DELETE from sequenceanalysis.sequence_reads t WHERE (select rowid from sequenceanalysis.sequence_analyses a where a.rowid = t.analysis_id) is null;
DELETE from sequenceanalysis.sequence_alignments t WHERE (select rowid from sequenceanalysis.sequence_analyses a where a.rowid = t.analysis_id) is null;

--update virus genbank IDs
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'AB231898' WHERE name = 'GHNJ196';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'NC_001802' WHERE name = 'HXB2';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'NC_001722' WHERE name = 'NC_001722';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'SHIV-1157ipd3N4' WHERE name = 'DQ779174';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'SHIV89.6P' WHERE name = 'U89134';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'SIVmac239' WHERE name = 'M33262';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'SIVmac251' WHERE name = 'M19499';
UPDATE sequenceanalysis.ref_nt_sequences set genbank = 'SIVsmE543' WHERE name = 'U72748.2';

INSERT INTO sequenceanalysis.sequence_platforms (platform) VALUES ('MIXED');

--delete duplicate epitopes accidentally entered
update sequenceanalysis.ref_aa_features set comment = null where comment = '';

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence, r.comment
    having count(*) > 1
);

--also catch duplicates where the second one has a comment, but the first is blank
delete from sequenceanalysis.ref_aa_features
where rowid in (
    select min(rowid) from sequenceanalysis.ref_aa_features r
    group by r.ref_aa_id, r.aa_start, r.aa_stop, r.name, r.aa_sequence
    having count(*) > 1
) and comment is null;