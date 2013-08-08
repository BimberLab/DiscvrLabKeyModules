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
create table sequenceanalysis.nt_snps_by_pos (
  rowid INT IDENTITY(1,1) NOT NULL,

  analysis_id INTEGER,
  ref_nt_id INTEGER,
  ref_nt_name varchar(1000),

  ref_nt VARCHAR(3),
  ref_nt_position INTEGER,
  ref_nt_insert_index INTEGER,

  q_nt VARCHAR(3),

  readcount INTEGER,
  depth INTEGER,
  adj_depth INTEGER,
  pct DOUBLE PRECISION,

  container ENTITYID,
  createdBy INTEGER,
  created DATETIME,
  modifiedBy INTEGER,
  modified DATETIME,

  constraint PK_nt_snps_by_pos PRIMARY KEY (rowid)
);

create table sequenceanalysis.aa_snps_by_codon (
  rowid INT IDENTITY(1,1) NOT NULL,

  analysis_id INTEGER,
  ref_nt_id INTEGER,
  ref_aa_id INTEGER,

  ref_aa VARCHAR(3),
  ref_aa_position INTEGER,
  ref_aa_insert_index INTEGER,

  q_aa VARCHAR(3),
  codon VARCHAR(10),

  readcount INTEGER,
  depth INTEGER,
  adj_depth INTEGER,
  pct DOUBLE PRECISION,

  container ENTITYID,
  createdBy INTEGER,
  created DATETIME,
  modifiedBy INTEGER,
  modified DATETIME,

  constraint PK_aa_snps_by_codon PRIMARY KEY (rowid)
);
