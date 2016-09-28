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

-- Create schema, tables, indexes, and constraints used for cnprc_pdl module here
-- All SQL VIEW definitions should be created in cnprc_pdl-create.sql and dropped in cnprc_pdl-drop.sql
CREATE SCHEMA cnprc_pdl;
GO

CREATE TABLE cnprc_pdl.orders (

  order_pk int,
  client_fk int,
  billingContact_fk int,
  reportContact_fk int,
  purchaseOrder nvarchar(20),
  orderDate DATETIME,
  requestNumber nvarchar(20),
  billingDate DATETIME,
  reportDate DATETIME,
  isHideComment bit,
  comments nvarchar(255),
  chargeId nvarchar(4),
  accountId nvarchar(6),
  orderClosedDate DATETIME,
  project nvarchar(5),
  enteredBy nvarchar(30),
  invoiceNumber nvarchar(8),
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PDL_ORDERS PRIMARY KEY (order_pk),
  CONSTRAINT FK_CNPRC_PDL_ORDERS_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE TABLE cnprc_pdl.samples (

  sample_pk	BIGINT,
  order_fk	int,
  animalId	nvarchar(20),
  logNumber	nvarchar(20),
  species	nvarchar(30),
  sampleType	nvarchar(40),
  sampleDate	DATETIME,
  receivedDate	DATETIME,
  isHideComment	bit,
  comments	nvarchar(255),
  isEstimateSampleDate	bit,
  isProcessing	bit,
  isAssign	bit,
  isAllTestsDone	bit,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PDL_SAMPLES PRIMARY KEY (sample_pk),
  CONSTRAINT FK_CNPRC_PDL_SAMPLES_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE TABLE cnprc_pdl.tests (

  test_pk	BIGINT,
  sample_fk	BIGINT,
  type	nvarchar(20),
  isFlag	bit,
  results	nvarchar(50),
  reportDate	DATETIME,
  testDoneDate	DATETIME,
  isNoCharge	bit,
  comments	nvarchar(255),
  isHideComment	bit,
  isPanelYn	bit,
  isHideOnReport	bit,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PDL_TESTS PRIMARY KEY (test_pk),
  CONSTRAINT FK_CNPRC_PDL_TESTS_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO
CREATE TABLE cnprc_pdl.sub_tests (

  subTest_pk	BIGINT,
  test_fk	BIGINT,
  type	nvarchar(20),
  isFlag	bit,
  results	nvarchar(50),
  reportDate	DATETIME,
  testDoneDate	DATETIME,
  isHideOnReport	bit,
  comments	nvarchar(255),
  isHideComment	bit,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PDL_SUBTESTS PRIMARY KEY (subTest_pk),
  CONSTRAINT FK_CNPRC_PDL_SUBTESTS_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO
