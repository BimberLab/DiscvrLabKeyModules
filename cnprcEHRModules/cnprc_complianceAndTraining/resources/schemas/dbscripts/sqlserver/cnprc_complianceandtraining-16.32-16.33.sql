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
DROP TABLE cnprc_complianceandtraining.position_skill_area;

CREATE TABLE cnprc_complianceAndtraining.train_area (

  rowid INT IDENTITY(1,1) NOT NULL,
  area nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_COMPLIANCE_AND_TRAINING_TRAIN_AREA PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_COMPLIANCE_AND_TRAINING_TRAIN_AREA_CONTAINER FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_COMPLIANCE_AND_TRAINING_TRAIN_AREA_CONTAINER_INDEX ON cnprc_complianceAndtraining.train_area (Container);
GO


