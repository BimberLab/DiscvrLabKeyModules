/*
 * Copyright (c) 2012 LabKey Corporation
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
ALTER TABLE onprc_billing.chargeRates drop column unit;
ALTER TABLE onprc_billing.chargeRateExemptions drop column unit;

alter table onprc_billing.leaseFeeDefinition add project int;
alter table onprc_billing.chargableItems add shortName varchar(100);

CREATE TABLE onprc_billing.procedureFeeDefinition (
    rowid int identity(1,1),
    procedureId int,
    chargeType int,
    chargeId int,

    active bit default 1,
    objectid ENTITYID,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_procedureFeeDefinition PRIMARY KEY (rowId)
);

CREATE TABLE onprc_billing.financialContacts (
    rowid int identity(1,1),
    firstName varchar(100),
    lastName varchar(100),
    position varchar(100),
    address varchar(500),
    city varchar(100),
    state varchar(100),
    country varchar(100),
    zip varchar(100),
    phoneNumber varchar(100),

    active bit default 1,
    objectid ENTITYID,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_financialContacts PRIMARY KEY (rowId)
);

CREATE TABLE onprc_billing.grants (
    "grant" varchar(100),
    investigatorId int,
    title varchar(500),
    startDate datetime,
    endDate datetime,
    fiscalAuthority int,

    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_grants PRIMARY KEY ("grant")
);

CREATE TABLE onprc_billing.accounts (
    account varchar(100),
    "grant" varchar(100),
    investigator integer,
    startdate datetime,
    enddate datetime,
    externalid varchar(200),
    comment varchar(4000),
    fiscalAuthority int,
    tier integer,
    active bit default 1,

    objectid entityid,
    createdBy userid,
    created datetime,
    modifiedBy userid,
    modified datetime,

    CONSTRAINT PK_accounts PRIMARY KEY (account)
);
