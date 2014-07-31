/*
 * Copyright (c) 2014 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for BLAST module here
-- All SQL VIEW definitions should be created in blast-create.sql and dropped in blast-drop.sql
CREATE SCHEMA blast;

CREATE TABLE blast.databases (
  rowid serial,
  name varchar(1000),
  description varchar(1000),  
  libraryid int,
  objectid entityid,
  datedisabled timestamp,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_databases PRIMARY KEY (objectid)
);

CREATE TABLE blast.blast_jobs (
  rowid serial,
  databaseid entityid,
  title varchar(500),

  params text,
  saveResults boolean,
  hasRun boolean default false,
  objectid entityid,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_blast_jobs PRIMARY KEY (objectid)
);