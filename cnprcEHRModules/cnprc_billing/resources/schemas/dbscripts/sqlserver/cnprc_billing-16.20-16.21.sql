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

-- Create schema, tables, indexes, and constraints used for cnprc_billing module here
-- All SQL VIEW definitions should be created in cnprc_billing-create.sql and dropped in cnprc_billing-drop.sql
CREATE SCHEMA cnprc_billing;
GO

CREATE TABLE cnprc_billing.wo_recipient_codes (

  rowid INT IDENTITY(1,1) NOT NULL,
  code nvarchar(16) NOT NULL,
  description nvarchar(16),
  name nvarchar(16),
  mailstop nvarchar(16),
  copiesTo int,
  copiesToFile int,
  copiesToRS int,
  copiesToTherap int,
  copiesCC int,
  copiesCCFile int,
  userName nvarchar(30),
  printQueue nvarchar(30),
  email nvarchar(50),
  fax nvarchar(18),
  handlingTo nvarchar(5),
  copiesRqFile int,
  includeWOS012 bit,
  toDestinationType nvarchar(10),
  ccDestinationType nvarchar(10),
  requestDestinationType nvarchar(10),
  isGroupRecipient bit,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WO_RECIPIENT_CODES PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WO_RECIPIENT_CODES_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WO_RECIPIENT_CODES_CONTAINER_INDEX ON cnprc_billing.wo_recipient_codes (Container);
GO


CREATE TABLE cnprc_billing.wo_line_item (

  rowid INT IDENTITY(1,1) NOT NULL,
  workOrderNum nvarchar(6) NOT NULL,
  lineNum float,
  itemCode nvarchar(20),
  description nvarchar(40),
  quantity float,
  uom nvarchar(10),
  chargeAmount float,
  exemptAmount float,
  rcCode nvarchar(2),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WO_LINE_ITEM PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WO_LINE_ITEM_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WO_LINE_ITEM_CONTAINER_INDEX ON cnprc_billing.wo_line_item (Container);
GO

CREATE TABLE cnprc_billing.wo_costing (

  rowid INT IDENTITY(1,1) NOT NULL,
  workOrderNumber nvarchar(6),
  lineNumber int,
  costSequence int,
  itemCode nvarchar(20),
  description nvarchar(40),
  costCode nvarchar(20),
  costCodeType nvarchar(2),
  exemptFactor float,
  compositeQuantity float,
  componentQuantity float,
  componentQuantityUOM nvarchar(10),
  basicRate float,
  basicRateUOM nvarchar(10),
  basicAdjustedQuantity float,
  costRate float,
  costUnits float,
  chargeAmount float,
  exemptAmount float,
  RCCode nvarchar(2),
  rateTierCodeFk nvarchar(4),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WO_COSTING PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WO_COSTING_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WO_COSTING_CONTAINER_INDEX ON cnprc_billing.wo_costing (Container);
GO

CREATE TABLE cnprc_billing.work_order (

  rowid INT IDENTITY(1,1) NOT NULL,
  workOrderNumber nvarchar(6),
  completionStatus nvarchar(1),
  periodEndingDate DATETIME,
  serviceType nvarchar(2),
  projectCode nvarchar(5),
  chargeId nvarchar(4),
  accountId nvarchar(6),
  accountFundType nvarchar(1),
  copiedFormatId nvarchar(6),
  beginDate DATETIME,
  endDate DATETIME,
  finishDate DATETIME,
  submittedOn DATETIME,
  writtenOn DATETIME,
  printedOn DATETIME,
  modifiedOn DATETIME,
  closedOn DATETIME,
  investigator nvarchar(16),
  requestor nvarchar(16),
  description nvarchar(40),
  printFlag nvarchar(1),
  printVersionTally int,
  printPagesTally int,
  exPrFlag nvarchar(1),
  exChargeFlag nvarchar(1),
  exPrChargeFlag nvarchar(1),
  exFundServiceFlag nvarchar(1),
  totalChargeAmount float,
  totalExemptAmount float,
  workOrderText text,
  serviceCode nvarchar(2),
  clientPONumber nvarchar(20),
  hasWorkOrderCredit bit,
  creditComment nvarchar(120),
  creditWorkOrder bit,
  creditPriorWorkOrderNumber nvarchar(6),
  creditPriorInvoiceNumber nvarchar(8),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WORK_ORDER PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WORK_ORDER_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WORK_ORDER_CONTAINER_INDEX ON cnprc_billing.work_order (Container);
GO

CREATE TABLE cnprc_billing.wo_recipients (

  rowid INT IDENTITY(1,1) NOT NULL,
  workOrderNumber nvarchar(6),
  toRecipient nvarchar(16),
  ccRecipient nvarchar(16),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WORK_ORDER_RECIPIENTS PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WORK_ORDER_RECIPIENTS_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WORK_ORDER_RECIPIENTS_CONTAINER_INDEX ON cnprc_billing.wo_recipients (Container);
GO

CREATE TABLE cnprc_billing.resource_center (

  rowid INT IDENTITY(1,1) NOT NULL,
  code nvarchar(2) NOT NULL,
  altCode	nvarchar(2),
  title nvarchar(50),
  isAnimalCare bit,
  isActive bit,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_CENTER PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_CENTER_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_CENTER_CONTAINER_INDEX ON cnprc_billing.resource_center (Container);
GO

CREATE TABLE cnprc_billing.resource_rate_item (

  rowid INT IDENTITY(1,1) NOT NULL,
  itemCode nvarchar(6),
  altItemCode nvarchar(12),
  description nvarchar(40),
  category nvarchar(20),
  basicCompositeType nvarchar(1),
  basicRate float,
  basicUOM nvarchar(10),
  basicCostCode nvarchar(20),
  resourceRateItemPk int,
  tieredRateLaborType nvarchar(20),
  rateTierCodeFk nvarchar(4),
  rateReportGroup nvarchar(30),
  isBasicItemLabor bit,
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_RATE_ITEM PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_RATE_ITEM_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_RATE_ITEM_CONTAINER_INDEX ON cnprc_billing.resource_rate_item (Container);
GO

CREATE TABLE cnprc_billing.wo_template (

  rowid INT IDENTITY(1,1) NOT NULL,
  templateId nvarchar(20),
  serviceType nvarchar(20),
  prCode nvarchar(20),
  chargeId nvarchar(20),
  beginDate datetime,
  endDate datetime,
  submittedOn datetime,
  writtenOn datetime,
  modifiedOn datetime,
  copiedOn datetime,
  copiedWorkOrderNumber nvarchar(6),
  printFlag nvarchar(1),
  investigator nvarchar(16),
  requestor nvarchar(16),
  description nvarchar(40),
  templateText text,
  rcCode nvarchar(2),
  serviceCode nvarchar(2),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_WO_TEMPLATE PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_WO_TEMPLATE_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_WO_TEMPLATE_CONTAINER_INDEX ON cnprc_billing.wo_template (Container);
GO