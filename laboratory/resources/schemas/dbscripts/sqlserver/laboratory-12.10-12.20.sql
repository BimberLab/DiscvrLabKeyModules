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
/* laboratory-12.10-12.11.sql */

alter table laboratory.peptides
  add name varchar(200)
;

alter table laboratory.peptides
  add comment varchar(4000)
;

/* laboratory-12.11-12.12.sql */

ALTER TABLE laboratory.samples
  alter column samplesource varchar(200);

ALTER TABLE laboratory.sample_source
	DROP CONSTRAINT PK_sample_source;

ALTER TABLE laboratory.sample_source
  alter column source varchar(200) NOT NULL;

GO

ALTER TABLE laboratory.sample_source
	ADD CONSTRAINT PK_sample_source PRIMARY KEY (source);

GO

ALTER TABLE laboratory.samples
  alter column sampletype varchar(200);

ALTER TABLE laboratory.sample_type
	DROP CONSTRAINT PK_sample_type;

ALTER TABLE laboratory.sample_type
  alter column TYPE varchar(200) NOT NULL;

GO

ALTER TABLE laboratory.sample_type
	ADD CONSTRAINT PK_sample_type PRIMARY KEY (type);

GO

ALTER TABLE laboratory.samples
  alter column molecule_type varchar(45);

ALTER TABLE laboratory.subjects
  alter column gender varchar(50);

ALTER TABLE laboratory.subjects
  alter column geographic_origin varchar(255);

GO

--in case unexpected rows are in the table
INSERT INTO laboratory.sample_source (source)
  SELECT DISTINCT samplesource FROM laboratory.samples
  WHERE samplesource NOT IN (SELECT source FROM laboratory.sample_source);

ALTER TABLE laboratory.samples
  ADD CONSTRAINT fk_samples_samplesource FOREIGN KEY (samplesource)
      REFERENCES laboratory.sample_source (source)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.sample_type (type)
  SELECT DISTINCT sampletype FROM laboratory.samples
  WHERE sampletype NOT IN (SELECT type FROM laboratory.sample_type);

ALTER TABLE laboratory.samples
  ADD CONSTRAINT fk_samples_sampletype FOREIGN KEY (sampletype)
      REFERENCES laboratory.sample_type (type)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.species (common_name)
  SELECT DISTINCT samplespecies FROM laboratory.samples
  WHERE samplespecies NOT IN (SELECT common_name FROM laboratory.species);

ALTER TABLE laboratory.samples
  ADD CONSTRAINT fk_samples_samplespecies FOREIGN KEY (samplespecies)
      REFERENCES laboratory.species (common_name)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.sample_additive (additive)
  SELECT DISTINCT additive FROM laboratory.samples
  WHERE additive NOT IN (SELECT additive FROM laboratory.sample_additive);

ALTER TABLE laboratory.samples
  ADD CONSTRAINT fk_samples_additive FOREIGN KEY (additive)
      REFERENCES laboratory.sample_additive (additive)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.dna_mol_type (mol_type)
  SELECT DISTINCT molecule_type FROM laboratory.samples
  WHERE molecule_type NOT IN (SELECT mol_type FROM laboratory.dna_mol_type);

ALTER TABLE laboratory.samples
  ADD CONSTRAINT fk_samples_molecule_type FOREIGN KEY (molecule_type)
      REFERENCES laboratory.dna_mol_type (mol_type)
      ON UPDATE CASCADE;

-- ALTER TABLE laboratory.samples
--   ADD CONSTRAINT fk_samples_parentsample FOREIGN KEY (parentsample)
--       REFERENCES laboratory.samples (rowid)
--       ON DELETE SET NULL;

--in case unexpected rows are in the table
INSERT INTO laboratory.genders (code)
SELECT DISTINCT gender FROM laboratory.subjects
  WHERE gender NOT IN (SELECT code FROM laboratory.genders) and gender is not null;

ALTER TABLE laboratory.subjects
  ADD CONSTRAINT fk_subjects_gender FOREIGN KEY (gender)
      REFERENCES laboratory.genders (code)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.species (common_name)
SELECT DISTINCT species FROM laboratory.subjects
  WHERE species NOT IN (SELECT common_name FROM laboratory.species) and species is not null;

ALTER TABLE laboratory.subjects
  ADD CONSTRAINT fk_subjects_samplespecies FOREIGN KEY (species)
      REFERENCES laboratory.species (common_name)
      ON UPDATE CASCADE;

--in case unexpected rows are in the table
INSERT INTO laboratory.geographic_origins (origin)
SELECT DISTINCT geographic_origin FROM laboratory.subjects
  WHERE geographic_origin NOT IN (SELECT origin FROM laboratory.geographic_origins) and geographic_origin is not null;

ALTER TABLE laboratory.subjects
  ADD CONSTRAINT fk_subjects_geographic_origin FOREIGN KEY (geographic_origin)
      REFERENCES laboratory.geographic_origins (origin)
      ON UPDATE CASCADE;

-- ALTER TABLE laboratory.dna_oligos
--   ADD CONSTRAINT fk_dna_oligos_cognate_primer FOREIGN KEY (cognate_primer)
--       REFERENCES laboratory.dna_oligos (rowid)
--       ON DELETE SET NULL;

/* laboratory-12.12-12.13.sql */

ALTER TABLE laboratory.samples add remove_comment varchar(4000);
