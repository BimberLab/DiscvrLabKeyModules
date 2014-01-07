/*
 * Copyright (c) 2011 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for SLA module here
-- All SQL VIEW definitions should be created in sla-create.sql and dropped in sla-drop.sql
CREATE SCHEMA sla;
GO

CREATE TABLE sla.census
(
    RowID INT IDENTITY(1,1) NOT NULL,
    Project INTEGER,
    CountDate DATETIME,
    InvestigatorId INTEGER,
    Room VARCHAR(255),
    Species VARCHAR(255),
    CageType VARCHAR(255),
    CageSize VARCHAR(255),
    CountType INTEGER,
    AnimalCount INTEGER,
    CageCount INTEGER,
    DLAMInventory INTEGER,
    objectid ENTITYID,

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_census PRIMARY KEY (rowId)
);

CREATE TABLE sla.etl_runs
(
    RowId int identity(1,1),
    date datetime,
    queryname varchar(200),
    rowversion varchar(200),

    Container ENTITYID NOT NULL,

    CONSTRAINT PK_etl_runs PRIMARY KEY (rowId)
);

CREATE TABLE sla.purchase(
    RowID INT IDENTITY(1,1)NOT NULL,
    Project INTEGER ,
    UserID INTEGER,
    PriInvPhone VARCHAR(255),
    PriInvEmail VARCHAR(255),
    RequestorID INTEGER,
    VendorID INTEGER ,
    Username VARCHAR(255),
    OHSUAlias VARCHAR(255),
    HazardousAgentsUsed INTEGER,
    HazardsList VARCHAR(255),
    DOBRequired INTEGER,
    AdditionalVendorInfo VARCHAR(255),
    OtherVendor VARCHAR(255),
    VendorContact VARCHAR(255),
    ConfirmationNum VARCHAR(255),
    HousingConfirmed INTEGER,
    IACUCConfirmed INTEGER,
    RequestDate DATETIME ,
    OrderDate DATETIME,
    AdminComments VARCHAR(500),
    DARComments VARCHAR(500),
    OrderedBy VARCHAR(255),
    ProjFundingSource VARCHAR(255),
    objectid ENTITYID,

    Container ENTITYID ,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_purchase PRIMARY KEY (rowId)

);

CREATE TABLE sla.purchaseDetails(
    RowId INT IDENTITY(1,1)NOT NULL,
    PurchaseID INTEGER ,
    Species INTEGER ,
    Age VARCHAR(255) ,
    Weight VARCHAR(255) ,
    Gestation VARCHAR(255) ,
    Sex INTEGER ,
    Strain VARCHAR(255) ,
    CageID INTEGER ,
    NumAnimalsOrdered INTEGER ,
    NumAnimalsReceived INTEGER ,
    BoxesQuantity INTEGER ,
    CostPerAnimal VARCHAR(255) ,
    ShippingCost VARCHAR(255) ,
    TotalCost VARCHAR(255) ,
    HousingInstructions VARCHAR(255) ,
    RequestedArrivalDate DATETIME ,
    ExpectedArrivalDate DATETIME ,
    ReceivedDate DATETIME ,
    ReceivedBy VARCHAR(255) ,
    CancelledBy VARCHAR(255) ,
    DateCancelled DATETIME ,
    objectid ENTITYID,

    Container ENTITYID ,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_purchaseDetails PRIMARY KEY (rowId)
 );

CREATE TABLE sla.requestors(
	RowId INT IDENTITY(1,1)NOT NULL,
	RequestorId INTEGER,
	LastName VARCHAR(255),
	FirstName VARCHAR(255),
	Initials VARCHAR(10) ,
	PhoneNumber VARCHAR(20),
	EmailAddress VARCHAR(255),
	objectid ENTITYID,

    Container ENTITYID ,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_requestors PRIMARY KEY (rowId)
);

CREATE TABLE sla.vendors(
    RowId INT IDENTITY(1,1)NOT NULL,
    SLAVendorName VARCHAR(255) ,
    Phone1 VARCHAR(15) ,
    Phone2 VARCHAR(15) ,
    FundingSourceRequired INTEGER ,
    Comments VARCHAR(255) ,
    objectid ENTITYID,

    Container ENTITYID ,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_vendors PRIMARY KEY (rowId)

) ;

CREATE TABLE sla.emailList(
    RowId INT IDENTITY(1,1) NOT NULL,
    Name VARCHAR(100) ,
    Email VARCHAR(100) ,
    PrimaryNotifier INTEGER ,
    objectid ENTITYID,

    Container ENTITYID ,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_emailList PRIMARY KEY (rowId)
);