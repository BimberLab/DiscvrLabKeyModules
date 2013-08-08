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
insert into sequenceanalysis.illumina_templates (name, json, editable)
VALUES
('Nextera XT', '{' +
  '"Header": [["Workflow","PCR Amplicon"],["Application","PCR Amplicon"],["Assay","Nextera XT"],["Chemistry","Amplicon"]],' +
  '"Reads": [["251",""], ["251",""]],' +
  '"Settings": [["Adapter","CTGTCTCTTATACACATCT"]]' +
  '}', 1
);

ALTER TABLE sequenceanalysis.sequence_readsets add inputmaterial integer;

create table sequenceanalysis.input_material (
  rowid INT IDENTITY(1,1),
  name varchar(500) not null,
  category varchar(100),
  description varchar(4000),

  container ENTITYID,
  createdBy USERID,
  created DATETIME,
  modifiedBy USERID,
  modified DATETIME,

  constraint PK_input_materials PRIMARY KEY (rowid)
);

delete from sequenceAnalysis.site_module_properties where prop_name = 'contactEmail';