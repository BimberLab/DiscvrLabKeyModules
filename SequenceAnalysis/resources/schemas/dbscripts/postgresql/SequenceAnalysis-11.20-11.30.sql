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

/* SequenceAnalysis-11.20-11.21.sql */

-- NOTE: due to a naming problem, this script probably never ran on earlier installs

-- alter table sequenceanalysis.virus_strains
--   drop column workbook;

-- alter table sequenceanalysis.samples
--   drop column workbook;

--moving to laboratory module
drop table if exists sequenceanalysis.species;
drop table if exists sequenceanalysis.sample_source;
drop table if exists sequenceanalysis.dna_mol_type;
drop table if exists sequenceanalysis.external_dbs;

/* SequenceAnalysis-11.21-11.22.sql */

alter table sequenceAnalysis.site_module_properties
  drop column container
  ;

DROP TABLE if exists sequenceAnalysis.chemistries;

DROP TABLE IF EXISTS sequenceanalysis.sequence_platforms;
CREATE TABLE sequenceanalysis.sequence_platforms (
  platform varchar(45) NOT NULL,
  aliases varchar(200),

  CONSTRAINT PK_sequence_platforms PRIMARY KEY (platform)
)
WITH (OIDS=FALSE)
;

-- ----------------------------
-- Records of sequenceAnalysis.sequence_platforms
-- ----------------------------
INSERT INTO sequenceanalysis.sequence_platforms
(platform,aliases)
VALUES
('ILLUMINA', 'SLX,SOLEXA'),
('SOLID', null),
('LS454', '454'),
('COMPLETE_GENOMICS', 'COMPLETE'),
('PACBIO', null),
('ION_TORRENT', 'IONTORRENT'),
('SANGER', null)
;

update sequenceAnalysis.sequence_reads set chemistry = 'LS454' where chemistry = 'Pyrosequencing';

delete from sequenceAnalysis.site_module_properties where prop_name = 'contactEmail';
insert into sequenceAnalysis.site_module_properties (prop_name, stringValue) VALUES ('contactEmail', 'bbimber@labkey.com');

DROP TABLE IF EXISTS sequenceAnalysis.sequence_readsets;
CREATE TABLE sequenceAnalysis.sequence_readsets (
RowId serial NOT NULL,
name varchar(220),
subjectid integer,
sampleid integer,
platform varchar(100),
comments text default null,

Container ENTITYID NOT NULL,
CreatedBy USERID,
Created TIMESTAMP,
ModifiedBy USERID,
Modified TIMESTAMP,

CONSTRAINT PK_sequence_readsets PRIMARY KEY (rowId)
);


alter table sequenceAnalysis.sequence_analyses 
  add column readset integer
;

ALTER TABLE sequenceAnalysis.sequence_reads
  add column readset integer
;


--populate readsets based on sequence_reads
insert into sequenceAnalysis.sequence_readsets
(sampleid,container,created,createdby,modified,modifiedby,platform)
(select a.sampleid,container,max(created) as created,max(createdby) as createdby, max(modified) as modified,max(modifiedby) as modifiedby, 'LS454' as platform
FROM sequenceAnalysis.sequence_analyses a
GROUP BY a.sampleid, a.container
);


--then update sequence_reads based on readsets
UPDATE sequenceAnalysis.sequence_reads s
SET readset = (
select rs.rowid
from sequenceAnalysis.sequence_readsets rs
join sequenceAnalysis.sequence_analyses a
on (a.sampleid=rs.sampleid and a.container=rs.container)
WHERE s.analysis_id=a.rowid and s.container=a.container);

/* SequenceAnalysis-11.22-11.23.sql */

--moved to the laboratory module:
drop table if exists sequenceanalysis.species;
drop table if exists sequenceanalysis.sample_source;
drop table if exists sequenceanalysis.external_dbs;
drop table if exists sequenceanalysis.geographic_origins;
drop table if exists sequenceanalysis.dna_mol_type;
--drop table if exists sequenceanalysis.samples;

alter table sequenceAnalysis.sequence_readsets
  add column fileid integer
  ;

/* SequenceAnalysis-11.23-11.24.sql */

alter table sequenceAnalysis.nt_snps
  add column quality_score double precision,
  add column avg_qual double precision
  ;

alter table sequenceAnalysis.aa_snps
  add column avg_qual double precision
  ;

alter table sequenceAnalysis.virus_strains
  drop column workbook
  ;

alter table sequenceAnalysis.sequence_analyses
  add column alignmentFile integer,
  add column snpFile integer
;

/* SequenceAnalysis-11.24-11.25.sql */

alter table sequenceanalysis.sequence_readsets
  drop column subjectid,
  add column subjectid varchar(200)
;


alter table sequenceanalysis.ref_nt_sequences
  add column aliases varchar(1000)
  ;

/* SequenceAnalysis-11.25-11.26.sql */

alter table sequenceanalysis.sequence_alignments
  drop column haplotype
;

alter table sequenceanalysis.samples
  drop column workbook
;

alter table sequenceanalysis.sequence_analyses
  drop column sampleid
;

alter table sequenceanalysis.haplotype_types
  drop column container
;
alter table sequenceanalysis.haplotype_types
  drop column created
;
alter table sequenceanalysis.haplotype_types
  drop column createdby
;
alter table sequenceanalysis.haplotype_types
  drop column modified
;
alter table sequenceanalysis.haplotype_types
  drop column modifiedby
;

alter table sequenceanalysis.haplotype_sequences
  drop column container
;

drop table if exists sequenceanalysis.haplotype_mapping;
drop table if exists sequenceanalysis.haplotype_definitions;

drop table if exists sequenceanalysis.haplotypes;
CREATE TABLE sequenceanalysis.haplotypes (
  name varchar(200) NOT NULL,
  type varchar(200),
  comment text,

  CreatedBy USERID,
  Created timestamp,
  ModifiedBy USERID,
  Modified timestamp,

  CONSTRAINT PK_haplotypes PRIMARY KEY (name)

);

alter table sequenceanalysis.ref_nt_sequences
  rename column category4 to lineage;
alter table sequenceanalysis.ref_nt_sequences
  rename column category3 to locus;
alter table sequenceanalysis.ref_nt_sequences
  rename column category2 to subset;
alter table sequenceanalysis.ref_nt_sequences
  rename column category1 to category;

/* SequenceAnalysis-11.26-11.27.sql */

ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD COLUMN avgqual_a double precision,
  ADD COLUMN avgqual_t double precision,
  ADD COLUMN avgqual_g double precision,
  ADD COLUMN avgqual_c double precision,
  ADD COLUMN avgqual_n double precision,
  ADD COLUMN avgqual_del double precision
;

ALTER TABLE sequenceAnalysis.nt_snps
  ADD pvalue double precision
;

ALTER TABLE sequenceAnalysis.aa_snps
  ADD column min_pvalue double precision
;

ALTER TABLE sequenceAnalysis.sequence_analyses
  add column reference_library integer
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add column barcode5 varchar(100)
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add column barcode3 varchar(100)
;

CREATE INDEX aa_snps_ref_aa_position_codon
ON sequenceanalysis.aa_snps (analysis_id, ref_nt_id, ref_aa_id, ref_aa_position, ref_aa_insert_index, ref_aa, q_aa, q_codon);

/* SequenceAnalysis-11.27-11.28.sql */

ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD COLUMN pvalue_a double precision,
  ADD COLUMN pvalue_t double precision,
  ADD COLUMN pvalue_g double precision,
  ADD COLUMN pvalue_c double precision,
  ADD COLUMN pvalue_n double precision,
  ADD COLUMN pvalue_del double precision
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add column raw_input_file integer
;

/* SequenceAnalysis-11.28-11.29.sql */

ALTER TABLE sequenceanalysis.sequence_alignments
   ALTER COLUMN read_id DROP NOT NULL;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_percent TYPE double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_percent TYPE double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_depth TYPE double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_depth TYPE double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_reads TYPE double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_reads TYPE double precision;

/* SequenceAnalysis-11.29-11.30.sql */

ALTER TABLE sequenceanalysis.sequence_readsets
   ADD COLUMN qc_file integer;

ALTER TABLE sequenceanalysis.sequence_analyses
   ADD COLUMN qc_file integer;