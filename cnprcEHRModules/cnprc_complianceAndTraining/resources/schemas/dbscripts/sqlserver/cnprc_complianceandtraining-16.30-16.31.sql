/*
 * Copyright (c) 2016 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for cnprc_complianceAndTraining module here
-- All SQL VIEW definitions should be created in cnprc_complianceandtraining-create.sql and dropped in cnprc_complianceandtraining-drop.sql
CREATE SCHEMA cnprc_complianceandtraining;
GO

CREATE TABLE cnprc_complianceandtraining.employees (

  rowid INT IDENTITY(1,1) NOT NULL,
  employeeId nvarchar(255),
  person_cnprc_pk int,
  status nvarchar(10),
  emp_stud nvarchar(1),
  pager nvarchar(25),
  departfk int,
  institution int,
  sponsor int,
  comments nvarchar(1000),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_COMPLIANCE_AND_TRAINING PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_COMPLIANCE_AND_TRAINING_CONTAINER FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_CNPRC_COMPLIANCE_AND_TRAINING_CONTAINER_INDEX ON cnprc_complianceAndTraining.employees (Container);
GO