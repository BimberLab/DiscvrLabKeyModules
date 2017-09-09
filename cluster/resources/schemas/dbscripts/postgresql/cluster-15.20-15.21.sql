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

CREATE SCHEMA cluster;

CREATE TABLE cluster.clusterJobs (
    rowId SERIAL NOT NULL,

    clusterId varchar(255),
    jobId varchar(255),
    hasStarted boolean,
    status VARCHAR(100),
    location VARCHAR(1000),
    activeTaskId VARCHAR(1000),
    clusterUser varchar(100),
    lastStatusCheck timestamp,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created TIMESTAMP,
    modifiedBy USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_clusterJobs PRIMARY KEY (rowId)
);


CREATE FUNCTION cluster.handleUpgrade() RETURNS VOID AS $$
DECLARE
    BEGIN
      IF EXISTS ( SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'htcondorconnector' )
      THEN
        INSERT INTO cluster.clusterJobs (clusterId, jobId, hasStarted, status, location, activeTaskId, lastStatusCheck, container, createdBy, created, modifiedBy, modified)
        SELECT condorId as clusterId, jobId, hasStarted, status, location, activeTaskId, lastStatusCheck, container, createdBy, created, modifiedBy, modified
        FROM htcondorconnector.condorJobs;
      END IF;
    END;
$$ LANGUAGE plpgsql;

SELECT cluster.handleUpgrade();

DROP FUNCTION cluster.handleUpgrade();

SELECT core.fn_dropifexists('*', 'htcondorconnector', 'schema', null);


