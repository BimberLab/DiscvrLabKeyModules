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


alter table sequenceAnalysis.site_module_properties
  drop column container
  ;

DROP TABLE if exists sequenceAnalysis.chemistries;

DROP TABLE IF EXISTS sequenceanalysis.sequence_platforms;
CREATE TABLE sequenceanalysis.sequence_platforms (
  platform varchar(45) NOT NULL,
  aliases varchar(200),

  CONSTRAINT PK_sequence_platforms PRIMARY KEY (platform)
)
WITH (OIDS=FALSE)
;

-- ----------------------------
-- Records of sequenceAnalysis.sequence_platforms
-- ----------------------------
INSERT INTO sequenceanalysis.sequence_platforms
(platform,aliases)
VALUES
('ILLUMINA', 'SLX,SOLEXA'),
('SOLID', null),
('LS454', '454'),
('COMPLETE_GENOMICS', 'COMPLETE'),
('PACBIO', null),
('ION_TORRENT', 'IONTORRENT'),
('SANGER', null)
;

update sequenceAnalysis.sequence_reads set chemistry = 'LS454' where chemistry = 'Pyrosequencing';

delete from sequenceAnalysis.site_module_properties where prop_name = 'contactEmail';
insert into sequenceAnalysis.site_module_properties (prop_name, stringValue) VALUES ('contactEmail', 'bbimber@labkey.com');

DROP TABLE IF EXISTS sequenceAnalysis.sequence_readsets;
CREATE TABLE sequenceAnalysis.sequence_readsets (
RowId serial NOT NULL,
name varchar(220),
subjectid integer,
sampleid integer,
platform varchar(100),
comments text default null,

Container ENTITYID NOT NULL,
CreatedBy USERID,
Created TIMESTAMP,
ModifiedBy USERID,
Modified TIMESTAMP,

CONSTRAINT PK_sequence_readsets PRIMARY KEY (rowId)
);


alter table sequenceAnalysis.sequence_analyses 
  add column readset integer
;

ALTER TABLE sequenceAnalysis.sequence_reads
  add column readset integer
;


--populate readsets based on sequence_reads
insert into sequenceAnalysis.sequence_readsets
(sampleid,container,created,createdby,modified,modifiedby,platform)
(select a.sampleid,container,max(created) as created,max(createdby) as createdby, max(modified) as modified,max(modifiedby) as modifiedby, 'LS454' as platform
FROM sequenceAnalysis.sequence_analyses a
GROUP BY a.sampleid, a.container
);


--then update sequence_reads based on readsets
UPDATE sequenceAnalysis.sequence_reads s
SET readset = (
select rs.rowid
from sequenceAnalysis.sequence_readsets rs
join sequenceAnalysis.sequence_analyses a
on (a.sampleid=rs.sampleid and a.container=rs.container)
WHERE s.analysis_id=a.rowid and s.container=a.container);
