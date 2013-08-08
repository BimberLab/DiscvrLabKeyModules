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

/* laboratory-0.00-11.00.sql */

CREATE SCHEMA laboratory;

CREATE TABLE laboratory.samples
(
    RowId serial NOT NULL,
    sampleName VARCHAR(255),
    vialId VARCHAR(255),
    sampleDate DATE,
    collectDate DATE,

    sampleType VARCHAR(255),
    sampleSource VARCHAR(255),
    species VARCHAR(255),
    comments TEXT DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_samples PRIMARY KEY (rowId)
);

CREATE TABLE laboratory.inventory
(
    RowId serial NOT NULL,
    sampleName VARCHAR(255),
    subjectId VARCHAR(255),

    sampleDate DATE,
    collectDate DATE,

    sampleType VARCHAR(255),
    sampleSource VARCHAR(255),
    sampleSpecies VARCHAR(255),

    dateRemoved DATE,
    removedBy INTEGER,

    additive VARCHAR(255),
    concentration FLOAT,
    concentration_units VARCHAR(255),

    --related to location
    location VARCHAR(255),
    freezer INTEGER,
    cane INTEGER,
    box INTEGER,
    box_row INTEGER,
    box_column INTEGER,

    --DNA-specific
    molecule_type VARCHAR(255),
    dna_vector VARCHAR(255),
    dna_insert VARCHAR(255),
    sequence TEXT,

    Comment TEXT DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_inventory PRIMARY KEY (rowId)
);

-- ----------------------------
-- Table structure for laboratory.sample_source
-- ----------------------------
CREATE TABLE laboratory.sample_source
(
    Source VARCHAR(105) NOT NULL,

    CONSTRAINT PK_sample_source PRIMARY KEY (source)
)
WITH (OIDS=FALSE);

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
CREATE TABLE laboratory.module_properties
(
    RowId SERIAL NOT NULL,

    prop_name VARCHAR(255) DEFAULT NULL,
    stringvalue VARCHAR(255) DEFAULT NULL,
    floatvalue FLOAT DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_module_properties PRIMARY KEY (rowId)
)
WITH (OIDS=FALSE);

-- ----------------------------
-- Table structure for laboratory.site_module_properties
-- ----------------------------
CREATE TABLE laboratory.site_module_properties
(
    prop_name VARCHAR(255) DEFAULT NULL,
    stringvalue VARCHAR(255) DEFAULT NULL,
    floatvalue FLOAT DEFAULT NULL,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_site_module_properties PRIMARY KEY (prop_name)
)
WITH (OIDS=FALSE);

--primers
CREATE TABLE laboratory.dna_oligos
(
    RowId serial NOT NULL,
    name VARCHAR(255) NOT NULL,
    sequence TEXT NOT NULL,
    oligo_type VARCHAR(255),
    modifications VARCHAR(255),
    target VARCHAR(255),
    cognate_primer INTEGER,
    melting_temp FLOAT,
    purification VARCHAR(255),

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_dna_oligos PRIMARY KEY (rowid)
)
WITH (OIDS=FALSE);

--peptides
CREATE TABLE laboratory.peptides
(
    RowId serial NOT NULL,
    sequence VARCHAR(4000),
    concentration FLOAT,
    alias VARCHAR(255),
    vendor VARCHAR(255),
    mw FLOAT,
    ref_aa INTEGER,
    ref_aa_name VARCHAR(255),
    ref_aa_start INTEGER,
    ref_aa_stop INTEGER,
    mhc_restriction VARCHAR(255),
    mhc_nt_id INTEGER,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_peptides PRIMARY KEY (rowid)
)
WITH (OIDS=FALSE);


-- ----------------------------
-- Table structure for laboratory.dna_mol_type
-- ----------------------------
CREATE TABLE laboratory.dna_mol_type
(
    Mol_type VARCHAR(45) NOT NULL,

    CONSTRAINT PK_dna_mol_type PRIMARY KEY (mol_type)
)
WITH (OIDS=FALSE);

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
CREATE TABLE laboratory.sample_type
(
    Type VARCHAR(105) NOT NULL,

    CONSTRAINT PK_sample_type PRIMARY KEY (Type)
)
WITH (OIDS=FALSE);

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

-- ----------------------------
-- Table structure for laboratory.sample_additive
-- ----------------------------
CREATE TABLE laboratory.sample_additive
(
    Additive VARCHAR(255) NOT NULL,

    CONSTRAINT PK_sample_additive PRIMARY KEY (additive)
) WITH (OIDS=FALSE);

INSERT INTO laboratory.sample_additive (Additive) VALUES
    ('EDTA'),
    ('Heparin'),
    ('RNA Later'),
    ('Sodium citrate');

INSERT INTO laboratory.sample_type VALUES ('Cell Line');