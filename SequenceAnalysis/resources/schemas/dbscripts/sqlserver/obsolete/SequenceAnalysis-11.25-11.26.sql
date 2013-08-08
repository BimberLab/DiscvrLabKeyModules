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

--drop column default
declare @name nvarchar(32),
    @sql nvarchar(1000)

-- find constraint name
select @name = O.name
from sys.default_constraints O
where parent_object_id = object_id('sequenceanalysis.sequence_alignments')
AND type = 'D'
and O.name like '%haplo%'

-- delete if found
if not @name is null
begin
    select @sql = 'ALTER TABLE sequenceanalysis.sequence_alignments DROP CONSTRAINT [' + @name + ']'
    execute sp_executesql @sql
end

alter table sequenceanalysis.sequence_alignments
  drop column haplotype
;

alter table sequenceanalysis.samples
  drop column workbook
;

--drop column default
select @name = null;
select @sql = null;

-- find constraint name
select @name = O.name
from sys.default_constraints O
where parent_object_id = object_id('sequenceanalysis.sequence_analyses')
AND type = 'D'
and O.name like '%sampl%'

-- delete if found
if not @name is null
begin
    select @sql = 'ALTER TABLE sequenceanalysis.sequence_analyses DROP CONSTRAINT [' + @name + ']'
    execute sp_executesql @sql
end

alter table sequenceanalysis.sequence_analyses
  drop column sampleid
;

alter table sequenceanalysis.haplotype_types
  drop column container
;
alter table sequenceanalysis.haplotype_types
  drop column created
;
alter table sequenceanalysis.haplotype_types
  drop column createdby
;
alter table sequenceanalysis.haplotype_types
  drop column modified
;
alter table sequenceanalysis.haplotype_types
  drop column modifiedby
;

alter table sequenceanalysis.haplotype_sequences
  drop column container
;

drop table sequenceanalysis.haplotype_mapping;
drop table sequenceanalysis.haplotype_definitions;

CREATE TABLE sequenceanalysis.haplotypes (
  name varchar(200) NOT NULL,
  type varchar(200),
  comment text,

  CreatedBy USERID,
  Created datetime,
  ModifiedBy USERID,
  Modified datetime,

  CONSTRAINT PK_haplotypes PRIMARY KEY (name)

);


EXEC sp_rename 'sequenceanalysis.ref_nt_sequences.category1', 'category', 'COLUMN';
GO
EXEC sp_rename 'sequenceanalysis.ref_nt_sequences.category2', 'subset', 'COLUMN';
GO
EXEC sp_rename 'sequenceanalysis.ref_nt_sequences.category3', 'locus', 'COLUMN';
GO
EXEC sp_rename 'sequenceanalysis.ref_nt_sequences.category4', 'lineage', 'COLUMN';
GO

;