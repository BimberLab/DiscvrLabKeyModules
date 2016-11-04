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

 CREATE TABLE cnprc_billing.resource_item_costing (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqpk int,
  itemCodeFk nvarchar(6),
  componentFk nvarchar(6),
  altItemCode nvarchar(12),
  description nvarchar(100),
  itemCost float,
  updatedCost datetime,
  updatedRrItem datetime,
  updateFlag nvarchar(1),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_ITEM_COSTING PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_ITEM_COSTING_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_ITEM_COSTING_CONTAINER_INDEX ON cnprc_billing.resource_item_costing (Container);
GO

CREATE TABLE cnprc_billing.resource_item_costing_supply (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqPk int,
  itemCostingFk int,
  itemSupplyFk int,
  quantity float,
  costingSupplyType nvarchar(1),
  laborFk nvarchar(10),
  setName nvarchar(20),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_ITEM_COSTING_SUPPLY PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_ITEM_COSTING_SUPPLY_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_ITEM_COSTING_SUPPLY_CONTAINER_INDEX ON cnprc_billing.resource_item_costing_supply (Container);
GO

CREATE TABLE cnprc_billing.resource_labor (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqpk int,
  description nvarchar(250),
  altCode nvarchar(12),
  costCode nvarchar(20),
  uom nvarchar(10),
  laborRate float,
  origCreatedDate datetime,
  origCreatedBy nvarchar(50),
  lastUpdate datetime,
  lastUpdateBy nvarchar(50),
  isActive bit,
  beginDate datetime,
  endDate datetime,
  tieredRateLaborType nvarchar(20),
  rateTierCodeFk nvarchar(4),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_LABOR PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_LABOR_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_LABOR_CONTAINER_INDEX ON cnprc_billing.resource_labor (Container);
GO

CREATE TABLE cnprc_billing.resource_labor_costing (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqpk int,
  itemCodeFk nvarchar(6),
  componentFk nvarchar(6),
  altItemCode nvarchar(12),
  description nvarchar(100),
  itemCost float,
  itemQuantity float,
  updatedCost datetime,
  updatedRrItem datetime,
  updateFlag nvarchar(1),
  tieredRateLaborType nvarchar(20),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_LABOR_COSTING PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_LABOR_COSTING_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_LABOR_COSTING_CONTAINER_INDEX ON cnprc_billing.resource_labor_costing (Container);
GO

CREATE TABLE cnprc_billing.resource_labor_costing_supply (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqpk int,
  itemCostingFk int,
  itemSupplyFk int,
  quantity float,
  laborCostingSupplyType nvarchar(1),
  description nvarchar(250),
  setName nvarchar(20),
  tieredRateLaborType nvarchar(20),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_LABOR_COSTING_SUPPLY PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_LABOR_COSTING_SUPPLY_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_LABOR_COSTING_SUPPLY_CONTAINER_INDEX ON cnprc_billing.resource_labor_costing_supply (Container);
GO

CREATE TABLE cnprc_billing.resource_rate_comp (

  rowid INT IDENTITY(1,1) NOT NULL,
  masterItemCode nvarchar(6),
  componentSeq int,
  componentItemCode nvarchar(6),
  componentQty float,
  componentUom nvarchar(10),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_RATE_COMP PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_RATE_COMP_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_RATE_COMP_CONTAINER_INDEX ON cnprc_billing.resource_rate_comp (Container);
GO

CREATE TABLE cnprc_billing.resource_rate_category (

  rowid INT IDENTITY(1,1) NOT NULL,
  resourceCategory nvarchar(20),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_RATE_CATEGORY PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_RATE_CATEGORY_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_RATE_CATEGORY_CONTAINER_INDEX ON cnprc_billing.resource_rate_category (Container);
GO

CREATE TABLE cnprc_billing.resource_rate_rc (

  rowid INT IDENTITY(1,1) NOT NULL,
  itemCode nvarchar(6),
  rcCode nvarchar(2),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_RATE_RC PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_RATE_RC_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_RATE_RC_CONTAINER_INDEX ON cnprc_billing.resource_rate_rc (Container);
GO

CREATE TABLE cnprc_billing.resource_supply (

  rowid INT IDENTITY(1,1) NOT NULL,
  seqpk int,
  description nvarchar(100),
  costCode nvarchar(20),
  catalogNum nvarchar(25),
  vendor nvarchar(50),
  uom nvarchar(50),
  unitsIssued int,
  subid nvarchar(10),
  id int,
  packageCost float,
  unitCost float,
  origCreatedDate datetime,
  origCreatedBy nvarchar(50),
  lastUpdate datetime,
  lastUpdateBy nvarchar(50),
  cnprcSupplier int,
  cnprcUsers int,
  isActive bit,
  supplyGroup nvarchar(50),
  code nvarchar(50),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_RESOURCE_SUPPLY PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_RESOURCE_SUPPLY_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_RESOURCE_SUPPLY_CONTAINER_INDEX ON cnprc_billing.resource_supply (Container);
GO

CREATE TABLE cnprc_billing.service (

  rowid INT IDENTITY(1,1) NOT NULL,
  svcCode nvarchar(2),
  svcTitle nvarchar(30),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_SERVICE PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_SERVICE_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_SERVICE_CONTAINER_INDEX ON cnprc_billing.service (Container);
GO

CREATE TABLE cnprc_billing.service_rc (

  rowid INT IDENTITY(1,1) NOT NULL,
  serviceCode nvarchar(2),
  rcCode nvarchar(2),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_SERVICE_RC PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_SERVICE_RC_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_SERVICE_RC_CONTAINER_INDEX ON cnprc_billing.service_rc (Container);
GO