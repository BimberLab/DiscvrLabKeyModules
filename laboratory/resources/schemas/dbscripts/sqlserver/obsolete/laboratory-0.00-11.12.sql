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


CREATE SCHEMA laboratory;
GO

CREATE TABLE laboratory.samples (

RowID int IDENTITY(1,1) NOT NULL,
sampleName varchar(255),
vialId varchar(255),
sampleDate datetime,
collectDate datetime,

sampleType varchar(255),
sampleSource varchar(255),
species varchar(255),
comments text default null,

Container ENTITYID NOT NULL,
CreatedBy USERID,
Created datetime,
ModifiedBy USERID,
Modified datetime,

CONSTRAINT PK_samples PRIMARY KEY (rowId)
)



CREATE TABLE laboratory.inventory (
RowID int IDENTITY(1,1) NOT NULL,
sampleName varchar(255),
subjectId varchar(255),

sampleDate datetime,
collectDate datetime,

sampleType varchar(255),
sampleSource varchar(255),
sampleSpecies varchar(255),

dateRemoved datetime,
removedBy integer,

additive varchar(255),
concentration float,
concentration_units varchar(255),

--related to location
location varchar(255),
freezer integer,
cane integer,
box integer,
box_row integer,
box_column integer,

--DNA-specific
molecule_type varchar(255),
dna_vector varchar(255),
dna_insert varchar(255),
sequence text,

comment text default null,

Container ENTITYID NOT NULL,
CreatedBy USERID,
Created datetime,
ModifiedBy USERID,
Modified datetime,

CONSTRAINT PK_inventory PRIMARY KEY (rowId)
)


-- ----------------------------
-- Table structure for laboratory.sample_source
-- ----------------------------
CREATE TABLE laboratory.sample_source (
  source varchar(105) NOT NULL,

  CONSTRAINT PK_sample_source PRIMARY KEY (source)
)


-- ----------------------------
-- Records of sample_source
-- ----------------------------
INSERT INTO laboratory.sample_source VALUES ('CD4');
INSERT INTO laboratory.sample_source VALUES ('CD4-resting');
INSERT INTO laboratory.sample_source VALUES ('CD4-resting Memory');
INSERT INTO laboratory.sample_source VALUES ('Nasal swab');
INSERT INTO laboratory.sample_source VALUES ('PBMC');
INSERT INTO laboratory.sample_source VALUES ('Plasma');
INSERT INTO laboratory.sample_source VALUES ('Supernatant');
INSERT INTO laboratory.sample_source VALUES ('Whole Blood');
INSERT INTO laboratory.sample_source VALUES ('Total RNA');
INSERT INTO laboratory.sample_source VALUES ('mRNA');
INSERT INTO laboratory.sample_source VALUES ('vRNA');
INSERT INTO laboratory.sample_source VALUES ('RNA');


-- ----------------------------
-- Table structure for laboratory.module_properties
-- ----------------------------
CREATE TABLE laboratory.module_properties (
   RowID int IDENTITY(1,1) NOT NULL,

    prop_name varchar(255) DEFAULT NULL,
    stringvalue varchar(255) DEFAULT NULL,
    floatvalue float DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created datetime,
    ModifiedBy USERID,
    Modified datetime,

    CONSTRAINT PK_module_properties PRIMARY KEY (rowId)
)


-- ----------------------------
-- Table structure for laboratory.site_module_properties
-- ----------------------------
CREATE TABLE laboratory.site_module_properties (
    prop_name varchar(255) DEFAULT NULL,
    stringvalue varchar(255) DEFAULT NULL,
    floatvalue float DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created datetime,
    ModifiedBy USERID,
    Modified datetime,

    CONSTRAINT PK_site_module_properties PRIMARY KEY (prop_name)
)


--primers
CREATE TABLE laboratory.dna_oligos(
    RowID int IDENTITY(1,1) NOT NULL,
    name varchar(255) not null,
    sequence text not null,
    oligo_type varchar(255),
    modifications varchar(255),
    target varchar(255),
    cognate_primer integer,
    melting_temp float,
    purification varchar(255),

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created datetime,
    ModifiedBy USERID,
    Modified datetime,

    CONSTRAINT PK_dna_oligos PRIMARY KEY (rowid)
)


--peptides
CREATE TABLE laboratory.peptides(
    RowID int IDENTITY(1,1) NOT NULL,
    sequence varchar(4000),
    concentration float,
    alias varchar(255),
    vendor varchar(255),
    mw float,
    ref_aa integer,
    ref_aa_name varchar(255),
    ref_aa_start integer,
    ref_aa_stop integer,
    mhc_restriction varchar(255),
    mhc_nt_id integer,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created datetime,
    ModifiedBy USERID,
    Modified datetime,

    CONSTRAINT PK_peptides PRIMARY KEY (rowid)
)


-- ----------------------------
-- Table structure for laboratory.dna_mol_type
-- ----------------------------
CREATE TABLE laboratory.dna_mol_type (
  mol_type varchar(45) NOT NULL,

  CONSTRAINT PK_dna_mol_type PRIMARY KEY (mol_type)
)


-- ----------------------------
-- Records of laboratory.dna_mol_type
-- ----------------------------
INSERT INTO laboratory.dna_mol_type VALUES ('mRNA');
INSERT INTO laboratory.dna_mol_type VALUES ('vRNA');
INSERT INTO laboratory.dna_mol_type VALUES ('gDNA');
INSERT INTO laboratory.dna_mol_type VALUES ('Plasmid');
INSERT INTO laboratory.dna_mol_type VALUES ('RNA');


-- ----------------------------
-- Table structure for laboratory.sample_type
-- ----------------------------
CREATE TABLE laboratory.sample_type (
  type varchar(105) NOT NULL,

  CONSTRAINT PK_sample_type PRIMARY KEY (type)
)


-- ----------------------------
-- Records of sample_type
-- ----------------------------
INSERT INTO laboratory.sample_type VALUES ('Nasal swab');
INSERT INTO laboratory.sample_type VALUES ('PBMC');
INSERT INTO laboratory.sample_type VALUES ('Plasma');
INSERT INTO laboratory.sample_type VALUES ('Cells - Primary');
INSERT INTO laboratory.sample_type VALUES ('Cells');
INSERT INTO laboratory.sample_type VALUES ('Total RNA');
INSERT INTO laboratory.sample_type VALUES ('mRNA');
INSERT INTO laboratory.sample_type VALUES ('vRNA');
INSERT INTO laboratory.sample_type VALUES ('RNA');
INSERT INTO laboratory.sample_type VALUES ('Cell Line');


-- ----------------------------
-- Table structure for laboratory.sample_additive
-- ----------------------------
CREATE TABLE laboratory.sample_additive (
  additive varchar(255) NOT NULL,

  CONSTRAINT PK_sample_additive PRIMARY KEY (additive)
)

INSERT into laboratory.sample_additive (additive) VALUES ('EDTA');
INSERT into laboratory.sample_additive (additive) VALUES ('Heparin');
INSERT into laboratory.sample_additive (additive) VALUES ('RNA Later');
INSERT into laboratory.sample_additive (additive) VALUES ('Sodium citrate');


GO
;