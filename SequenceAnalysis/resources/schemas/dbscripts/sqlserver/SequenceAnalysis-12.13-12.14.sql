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
alter table sequenceanalysis.sequence_readsets drop column qc_file;
alter table sequenceanalysis.sequence_readsets drop column qc_file2;

alter table sequenceanalysis.sequence_analyses drop column qc_file;

drop table sequenceanalysis.virus_strains;
drop table sequenceanalysis.samples;

create table sequenceanalysis.illumina_templates (
  name varchar(100) not null,
  json varchar(4000),
  editable bit default 1,

  CreatedBy USERID,
  Created datetime,
  ModifiedBy USERID,
  Modified datetime,

  constraint PK_illumina_templates PRIMARY KEY (name)
);

insert into sequenceanalysis.illumina_templates (name, json, editable)
VALUES
('Default', '{' +
  'Header: [["Template",""],["IEMFileVersion","3"],["Assay",""],["Chemistry","Default"]],' +
  'Reads: [["151",""], ["151",""]]' +
  '}', 0
);

insert into sequenceanalysis.illumina_templates (name, json, editable)
VALUES
('Resequencing', '{' +
  'Header: [["Template","Resequencing"],["IEMFileVersion","3"],["Assay","TruSeq DNA/RNA"],["Chemistry","Default"]],' +
  'Reads: [["151",""], ["151",""]],' +
  'Settings: [["OnlyGenerateFASTQ","1"]]' +
  '}', 1
);