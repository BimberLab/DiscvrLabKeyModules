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
--this table contains one row each time a billing run is performed, which gleans items to be charged from a variety of sources
--and snapshots them into invoicedItems
CREATE TABLE onprc_billing.invoiceRuns (
    rowId SERIAL NOT NULL,
    date timestamp,
    dataSources varchar(1000),
    runBy userid,
    comment varchar(4000),

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_invoiceRuns PRIMARY KEY (rowId)
);

--this table contains a snapshot of items actually invoiced, which will draw from many places in the animal record
CREATE TABLE onprc_billing.invoicedItems (
    rowId SERIAL NOT NULL,
    id varchar(100),
    date timestamp,
    debitedaccount varchar(100),
    creditedaccount varchar(100),
    category varchar(100),
    item varchar(500),
    quantity double precision,
    unitcost double precision,
    totalcost double precision,
    chargeId int,
    rateId int,
    exemptionId int,
    comment varchar(4000),
    flag integer,
    sourceRecord varchar(200),
    billingId int,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_billedItems PRIMARY KEY (rowId)
);


--this table contains a list of all potential items that can be charged.  it maps between the integer ID
--and a descriptive name.  it does not contain any fee information
CREATE TABLE onprc_billing.chargableItems (
    rowId SERIAL NOT NULL,
    name varchar(200),
    category varchar(200),
    comment varchar(4000),
    active boolean default true,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_chargableItems PRIMARY KEY (rowId)
);

--this table contains a list of the current changes for each item in onprc_billing.charges
--it will retain historic information, so we can accurately determine 'cost at the time'
CREATE TABLE onprc_billing.chargeRates (
    rowId SERIAL NOT NULL,
    chargeId int,
    unitcost double precision,
    unit varchar(100),
    startDate timestamp,
    endDate timestamp,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_chargeRates PRIMARY KEY (rowId)
);

--contains records of project-specific exemptions to chargeRates
CREATE TABLE onprc_billing.chargeRateExemptions (
    rowId SERIAL NOT NULL,
    project int,
    chargeId int,
    unitcost double precision,
    unit varchar(100),
    startDate timestamp,
    endDate timestamp,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_chargeRateExemptions PRIMARY KEY (rowId)
);

--maps the account to be credited for each charged item
CREATE TABLE onprc_billing.creditAccount (
    rowId SERIAL NOT NULL,
    chargeId int,
    account int,
    startDate timestamp,
    endDate timestamp,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_creditAccount PRIMARY KEY (rowId)
);

--this table contains records of misc charges that have happened that cannot otherwise be
--automatically inferred from the record
CREATE TABLE onprc_billing.miscCharges (
    rowId SERIAL NOT NULL,
    id varchar(100),
    date timestamp,
    project integer,
    account varchar(100),
    category varchar(100),
    chargeId int,
    descrption varchar(1000), --usually null, allow other random values to be supported
    quantity double precision,
    unitcost double precision,
    totalcost double precision,
    comment varchar(4000),

    taskid entityid,
    requestid entityid,

    container ENTITYID NOT NULL,
    createdBy USERID,
    created timestamp,
    modifiedBy USERID,
    modified timestamp,

    CONSTRAINT PK_miscCharges PRIMARY KEY (rowId)
);


--this table details how to calculate lease fees, and produces a list of charges over a billing period
--no fee info is contained
CREATE TABLE onprc_billing.leaseFeeDefinition (
    rowId SERIAL NOT NULL,
    minAge int,
    maxAge int,

    assignCondition int,
    releaseCondition int,
    chargeId int,

    active boolean default true,
    objectid ENTITYID,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_leaseFeeDefinition PRIMARY KEY (rowId)
);

--this table details how to calculate lease fees, and produces a list of charges over a billing period
--no fee info is contained
CREATE TABLE onprc_billing.perDiemFeeDefinition (
    rowId SERIAL NOT NULL,
    chargeId int,
    housingType int,
    housingDefinition int,

    startdate timestamp,
    releaseCondition int,

    active boolean default true,
    objectid ENTITYID,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_perDiemFeeDefinition PRIMARY KEY (rowId)
);

--creates list of all procedures that are billable
CREATE TABLE onprc_billing.clinicalFeeDefinition (
    rowId SERIAL NOT NULL,
    procedureId int,
    snomed varchar(100),

    active boolean default true,
    objectid ENTITYID,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_clinicalFeeDefinition PRIMARY KEY (rowId)
);