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
DELETE FROM laboratory.sample_type WHERE type = 'Serum';
INSERT INTO laboratory.sample_type (type) VALUES ('Serum');
DELETE FROM laboratory.sample_type WHERE type = 'Tissue';
INSERT INTO laboratory.sample_type (type) VALUES ('Tissue');
DELETE FROM laboratory.sample_type WHERE type = 'Supernatant';
INSERT INTO laboratory.sample_type (type) VALUES ('Supernatant');

-- NOTE: SQLServer will not allow 2 FKs pointing to the same table with update cascade
-- therefore rather than let 1 field update and the other not, we get rid of both
ALTER TABLE laboratory.samples
  DROP CONSTRAINT fk_samples_samplesource;
ALTER TABLE laboratory.samples
  DROP CONSTRAINT fk_samples_sampletype;

--in case unexpected rows are in the table
INSERT INTO laboratory.sample_type (type)
  SELECT DISTINCT samplesource FROM laboratory.samples
  WHERE samplesource NOT IN (SELECT type FROM laboratory.sample_type);

DROP TABLE laboratory.sample_source;

CREATE TABLE laboratory.freezers (
    rowid int identity(1,1),
    name varchar(100) not null,
    canes integer,
    boxes integer,
    rows integer,
    columns integer,
    comments varchar(4000),

    container entityid NOT NULL,
    createdBy integer,
    created datetime,
    modifiedBy integer,
    modified datetime,
    CONSTRAINT UNIQUE_freezers UNIQUE (name, container),
    CONSTRAINT PK_freezers PRIMARY KEY (rowid)
);

INSERT INTO laboratory.freezers (name, container)
  SELECT distinct freezer, container FROM laboratory.samples WHERE freezer is not null AND freezer != '';