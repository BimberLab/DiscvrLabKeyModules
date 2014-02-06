/*
 * Copyright (c) 2011 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for ONPRC_SSU module here
-- All SQL VIEW definitions should be created in onprc_ssu-create.sql and dropped in onprc_ssu-drop.sql
CREATE SCHEMA onprc_ssu;
GO

CREATE TABLE onprc_ssu.schedule (
  rowid int identity(1,1),
  Id varchar(100),
  date datetime,
  location varchar(200),
  procedureid int,

  objectid entityid not null,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_schedule PRIMARY KEY (objectid)
);