/*
 * Copyright (c) 2015 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for HTCondorConnector module here
-- All SQL VIEW definitions should be created in htcondorconnector-create.sql and dropped in htcondorconnector-drop.sql
CREATE SCHEMA htcondorconnector;

CREATE TABLE htcondorconnector.condorJobs (
    rowId SERIAL NOT NULL,

    condorId varchar(255),
    jobId varchar(255),
    isActive boolean,
    hadError boolean,
    clusterId varchar(255),
    processId varchar(255),
    nodeId varchar(255),
    lastStatusCheck timestamp,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created TIMESTAMP,
    modifiedBy USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_condorJobs PRIMARY KEY (rowId)
);
