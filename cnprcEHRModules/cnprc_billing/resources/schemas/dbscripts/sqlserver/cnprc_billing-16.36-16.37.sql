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

CREATE TABLE cnprc_billing.central_supply_line_item (

  rowid INT IDENTITY(1,1) NOT NULL,
  csplyliPk int,
  csplytmFk int,
  requisitionNum int,
  serviceCode nvarchar(2),
  rcCode nvarchar(2),
  lineNum int,
  itemSku nvarchar(40),
  description nvarchar(80),
  costCode nvarchar(20),
  itemQty float,
  unitOfMeasure nvarchar(30),
  itemCost float,
  chargeAmt float,
  overrideValidationError bit,
  overrideValidationErrorUser nvarchar(30),
  requisitionNumSuffix nvarchar(1),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_CENTRAL_SUPPLY_LINE_ITEM PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_CENTRAL_SUPPLY_LINE_ITEM_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_CENTRAL_SUPPLY_LINE_ITEM_CONTAINER_INDEX ON cnprc_billing.central_supply_line_item (Container);
GO

CREATE TABLE cnprc_billing.central_supply_trans_master (

  rowid INT IDENTITY(1,1) NOT NULL,
  csplytmPk int,
  centralSupplyMasterOrigin nvarchar(10),
  centralSupplyCreateDate datetime,
  periodEndingDate datetime,
  dafisFinCoaCd nvarchar(2),
  dafisAccountNbr nvarchar(7),
  dafisSubAcctNbr nvarchar(5),
  dafisProjectCd nvarchar(10),
  dafisObjOverride nvarchar(4),
  serviceCode nvarchar(2),
  rcCode nvarchar(2),
  requisitionNum int,
  saleDate datetime,
  closeDate datetime,
  purchasedBy nvarchar(40),
  chargeId nvarchar(4),
  accountId nvarchar(6),
  billingMethodCode nvarchar(2),
  clientPoNum nvarchar(20),
  totalChargeAmt float,
  clientPoNumber nvarchar(20),
  requisitionNumSuffix nvarchar(1),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_CENTRAL_SUPPLY_TRANS_MASTER PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_CENTRAL_SUPPLY_TRANS_MASTER_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_CENTRAL_SUPPLY_TRANS_MASTER_CONTAINER_INDEX ON cnprc_billing.central_supply_trans_master (Container);
GO

CREATE TABLE cnprc_billing.pdl_billing_summary (

  rowid INT IDENTITY(1,1) NOT NULL,
  pdlPk int,
  soFk int,
  ssFk bigint,
  chargeItem nvarchar(6),
  chargeQty int,
  creationDate datetime,
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_PDL_BILLING_SUMMARY PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_PDL_BILLING_SUMMARY_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_PDL_BILLING_SUMMARY_CONTAINER_INDEX ON cnprc_billing.pdl_billing_summary (Container);
GO

CREATE TABLE cnprc_billing.pdl_charge_id (

  rowid INT IDENTITY(1,1) NOT NULL,
  pdlPk int,
  pdlScFk int,
  chargeId nvarchar(100),
  isActive bit,
  sortOrder int,
  comments nvarchar(255),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_PDL_CHARGE_ID PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_PDL_CHARGE_ID_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_PDL_CHARGE_ID_CONTAINER_INDEX ON cnprc_billing.pdl_charge_id (Container);
GO