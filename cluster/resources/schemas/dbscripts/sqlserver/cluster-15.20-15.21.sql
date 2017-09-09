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
CREATE SCHEMA cluster;
GO
CREATE TABLE cluster.clusterJobs (
    rowId int identity(1,1),

    clusterId NVARCHAR(255),
    jobId NVARCHAR(255),
    hasStarted bit,
    status NVARCHAR(100),
    location NVARCHAR(1000),
    activeTaskId NVARCHAR(1000),
    clusterUser NVARCHAR(100),
    lastStatusCheck datetime,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created datetime,
    modifiedBy USERID,
    modified datetime,

    CONSTRAINT PK_clusterJobs PRIMARY KEY (rowId)
);
GO

CREATE PROCEDURE cluster.handleUpgrade AS
    BEGIN
    IF EXISTS( SELECT * FROM sys.schemas WHERE name = 'htcondorconnector' )
        BEGIN
          INSERT INTO cluster.clusterJobs (clusterId, jobId, hasStarted, status, location, activeTaskId, lastStatusCheck, container, createdBy, created, modifiedBy, modified)
          SELECT condorId as clusterId, jobId, hasStarted, status, location, activeTaskId, lastStatusCheck, container, createdBy, created, modifiedBy, modified
          FROM htcondorconnector.condorJobs;
        END
    END;
GO

EXEC cluster.handleUpgrade
GO

DROP PROCEDURE cluster.handleUpgrade
GO

EXEC core.fn_dropifexists '*', 'htcondorconnector', 'SCHEMA';