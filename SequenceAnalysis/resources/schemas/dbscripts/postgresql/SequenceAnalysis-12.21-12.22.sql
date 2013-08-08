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
CREATE TABLE sequenceanalysis.alignment_summary (
  rowid SERIAL,
  analysis_id INTEGER,
  file_id INTEGER,
  total INTEGER,
  total_forward INTEGER,
  total_reverse INTEGER,

  container ENTITYID NOT NULL,
  createdby INTEGER,
  created TIMESTAMP,
  modifiedby INTEGER,
  modified TIMESTAMP,

  CONSTRAINT pk_alignment_summary PRIMARY KEY (rowid)
);

ALTER TABLE sequenceanalysis.alignment_summary
  ADD CONSTRAINT fk_alignment_summary_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.alignment_summary
  ADD CONSTRAINT fk_alignment_summary_analysis_id FOREIGN KEY (analysis_id)
      REFERENCES sequenceanalysis.sequence_analyses (rowid);

CREATE TABLE sequenceanalysis.alignment_summary_junction (
  rowid SERIAL,
  analysis_id INTEGER,
  alignment_id INTEGER,
  ref_nt_id INTEGER,
  status BOOL,

  CONSTRAINT pk_alignment_summary_junction PRIMARY KEY (rowid)

);

ALTER TABLE sequenceanalysis.alignment_summary_junction
  ADD CONSTRAINT fk_alignment_summary_junction_analysis_id FOREIGN KEY (analysis_id)
      REFERENCES sequenceanalysis.sequence_analyses (rowid);

alter table sequenceanalysis.ref_nt_sequences alter column createdby type integer;
alter table sequenceanalysis.ref_nt_sequences alter column modifiedby type integer;

--these are the official illumina names
update sequenceanalysis.barcodes set tag_name = replace(tag_name, 'N50', 'S50')
where tag_name like 'N50%';