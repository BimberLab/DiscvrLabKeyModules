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
drop table laboratory.assay_requests;

CREATE TABLE laboratory.assay_run_templates (
    rowid serial,
    title varchar(200),
    comments varchar(4000),
    json text,
    assayId integer,
    status varchar(100),

    container entityid NOT NULL,
    createdBy integer,
    created timestamp,
    modifiedBy integer,
    modified timestamp,

    CONSTRAINT PK_assay_run_templates PRIMARY KEY (rowid)
);