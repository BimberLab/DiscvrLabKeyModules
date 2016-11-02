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

 CREATE TABLE cnprc_billing.perdiem_month (

  rowid INT IDENTITY(1,1) NOT NULL,
  periodEndingDate datetime,
  acctId nvarchar(6),
  projectCode nvarchar(5),
  payorId nvarchar(10),
  rateClass nvarchar(1),
  baseRate float,
  deductionRate float,
  netCharge float,
  animalId nvarchar(5),
  startDate datetime,
  endDate datetime,
  animalDays int,
  clientPoNumber nvarchar(20),
  rateTierCodeFk nvarchar(4),
  objectId nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_PERDIEM_MONTH PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_PERDIEM_MONTH_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_PERDIEM_MONTH_CONTAINER_INDEX ON cnprc_billing.perdiem_month (Container);
GO

CREATE TABLE cnprc_billing.perdiem (

  rowid INT IDENTITY(1,1) NOT NULL,
  payorId nvarchar(10),
  accountId nvarchar(6),
  chargeId nvarchar(4),
  projectCode nvarchar(5),
  fundSourceRank nvarchar(2),
  fundSourceCode nvarchar(1),
  animalId nvarchar(5),
  perdiemDate datetime,
  rateClass nvarchar(1),
  baseRate float,
  deductionFlag nvarchar(16),
  deductionRate float,
  netCharge float,
  billingClosed datetime,
  creationDate datetime,
  locationRateClass nvarchar(1),
  rateRierCodeFk nvarchar(4),
  objectId nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_PERDIEM PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_PERDIEM_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_PERDIEM_INDEX ON cnprc_billing.perdiem (Container);
GO