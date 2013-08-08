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

/* laboratory-11.20-11.21.sql */

alter table laboratory.samples
  drop column subjectid,
  add column subjectid varchar(200)
;


insert into laboratory.species
(common_name,scientific_name)
values
('Vector', null),
('SIV', 'Simian Immunodeficiency Virus'),
('HIV', 'Human Immunodeficiency Virus'),
('Influenza A', null),
('DENV', 'Dengue Virus')
;

insert into laboratory.dna_mol_type
(mol_type) values ('Vector')
;

DROP TABLE IF EXISTS laboratory.site_module_properties;
CREATE TABLE laboratory.site_module_properties (
    rowid serial,
    prop_name varchar(255),
    stringvalue varchar(255),
    stringvalue2 varchar(255),
    floatvalue float,
    floatvalue2 float,
    jsonvalue varchar(4000),
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_site_module_properties PRIMARY KEY (rowid)
);

DROP TABLE IF EXISTS laboratory.module_properties;
CREATE TABLE laboratory.module_properties (
    rowid serial,
    prop_name varchar(255),
    stringvalue varchar(255),
    stringvalue2 varchar(255),
    floatvalue float,
    floatvalue2 float,
    jsonvalue varchar(4000),

    Container ENTITYID,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_module_properties PRIMARY KEY (rowid)
);