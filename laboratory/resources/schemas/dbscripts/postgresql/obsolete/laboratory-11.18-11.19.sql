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


DROP TABLE IF EXISTS laboratory.datatypes;
CREATE TABLE laboratory.datatypes
(
  rowid serial NOT NULL,
  name varchar(255),
  isImportable boolean,
  importAsWorkbook boolean,
  isBrowsable boolean,
  isSearchable boolean,
  title varchar(255),
  containerpath varchar(255),
  schemaname varchar(255),
  queryname varchar(255),
  viewname varchar(255),
  reportname varchar(255),
  jsonconfig varchar(4000),
  description varchar(4000),
  sort_order integer,

  container entityid NOT NULL,
  createdby userid NOT NULL,
  created timestamp,
  modifiedby userid,
  modified timestamp,
  CONSTRAINT pk_datatypes PRIMARY KEY (rowid )
);


--rename inventory
alter table laboratory.inventory rename to samples;

alter table laboratory.samples
  add column labwareIdentifier varchar(200)
;

DROP TABLE IF EXISTS laboratory.reports;
CREATE TABLE laboratory.reports
(
  rowid serial NOT NULL,

  category varchar(255),
  reporttype varchar(255),
  reporttitle varchar(255),
  description varchar(4000),
  sort_order integer,

  containerpath varchar(255),
  schemaname varchar(255),
  queryname varchar(255),
  viewname varchar(255),
  reportname varchar(255),
  jsfunctionname varchar(255),
  jsonconfig varchar(4000),
  subjectfieldname varchar(100),
  
  container entityid NOT NULL,
  createdby userid NOT NULL,
  created timestamp NOT NULL,
  modifiedby userid NOT NULL,
  modified timestamp NOT NULL,

  CONSTRAINT pk_reports PRIMARY KEY (rowid)
);


alter table laboratory.site_module_properties
  drop column container
  ;