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

ALTER TABLE cnprc_complianceandtraining.employees DROP COLUMN emp_stud;
ALTER TABLE cnprc_complianceandtraining.employees ADD emp_stud nvarchar(20);

CREATE TABLE cnprc_complianceandtraining.position_skill_area (

  rowid INT IDENTITY(1,1) NOT NULL,
  position nvarchar(500),
  skillRequired nvarchar(100),
  area nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_COMPLIANCE_AND_TRAINING_PSA PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_COMPLIANCE_AND_TRAINING_PSA_CONTAINER FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_CNPRC_COMPLIANCE_AND_TRAINING_PSA_CONTAINER_INDEX ON cnprc_complianceAndtraining.position_skill_area (Container);
GO

CREATE TABLE cnprc_complianceandtraining.trainers (

  rowid INT IDENTITY(1,1) NOT NULL,
  trainerName nvarchar(500),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_COMPLIANCE_AND_TRAINING_TRAINERS PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_COMPLIANCE_AND_TRAINING_TRAINERS_CONTAINER FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_CNPRC_COMPLIANCE_AND_TRAINING_TRAINERS_CONTAINER_INDEX ON cnprc_complianceAndtraining.trainers (Container);
GO

