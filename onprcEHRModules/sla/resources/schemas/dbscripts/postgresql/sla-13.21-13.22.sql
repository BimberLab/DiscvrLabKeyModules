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

--it is easier to drop/recreate, rather than try incremental changes:
CREATE TABLE sla.census (
    rowid SERIAL NOT NULL,
    project INTEGER,
    date TIMESTAMP,
    investigatorid ENTITYID,  --onprc_ehr.investigators
    room VARCHAR(255),
    species VARCHAR(255),
    cagetype VARCHAR(255),
    cagesize VARCHAR(255),
    counttype INTEGER,
    animalcount INTEGER,
    cagecount INTEGER,
    dlaminventory INTEGER,
    objectid ENTITYID,

    container ENTITYID NOT NULL,
    createdby USERID,
    created TIMESTAMP,
    modifiedby USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_census PRIMARY KEY (rowid)
);

CREATE TABLE sla.etl_runs (
    rowid SERIAL,
    date TIMESTAMP,
    queryname varchar(200),
    rowversion varchar(200),

    container ENTITYID NOT NULL,

    CONSTRAINT PK_etl_runs PRIMARY KEY (rowid)
);

CREATE TABLE sla.purchase (
    rowid SERIAL NOT NULL, --not the PK
    project INTEGER,
    account VARCHAR(255),
    requestorid ENTITYID,
    vendorid ENTITYID,

    hazardslist VARCHAR(255),
    dobrequired INTEGER,
    comments VARCHAR(4000),
    confirmationnum VARCHAR(255),
    housingconfirmed INTEGER,
    iacucconfirmed INTEGER,
    requestdate TIMESTAMP,
    orderdate TIMESTAMP,
    orderedby VARCHAR(100),
    objectid ENTITYID,

    container ENTITYID,
    createdby USERID,
    created TIMESTAMP,
    modifiedby USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_purchase PRIMARY KEY (objectid)
);

CREATE TABLE sla.purchaseDetails (
    rowid SERIAL NOT NULL,
    purchaseid ENTITYID,
    species INTEGER,
    age double precision,
    weight double precision,
    weight_units varchar(100),
    gestation VARCHAR(255),
    gender varchar(100),
    strain VARCHAR(255),
    cageid INTEGER,
    animalsordered INTEGER,
    animalsreceived INTEGER,
    boxesquantity INTEGER,
    costperanimal VARCHAR(255),
    shippingcost VARCHAR(255),
    totalcost VARCHAR(255),
    housinginstructions VARCHAR(255),
    requestedarrivaldate TIMESTAMP,
    expectedarrivaldate TIMESTAMP,
    receiveddate TIMESTAMP,
    receivedby VARCHAR(255),
    cancelledby VARCHAR(255),
    datecancelled TIMESTAMP,
    objectid ENTITYID,

    container ENTITYID,
    createdby USERID,
    created TIMESTAMP,
    modifiedby USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_purchaseDetails PRIMARY KEY (objectid)
 );

CREATE TABLE sla.requestors (
	rowid SERIAL NOT NULL,  --not the PK
	lastname VARCHAR(255),
	firstname VARCHAR(255),
	initials VARCHAR(10),
	phone VARCHAR(20),
	email VARCHAR(255),
	userid USERID,
	objectid ENTITYID NOT NULL,

    container ENTITYID,
    createdby USERID,
    created TIMESTAMP,
    modifiedby USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_requestors PRIMARY KEY (objectid)
);

CREATE TABLE sla.vendors (
    rowid SERIAL NOT NULL, --not the PK
    name VARCHAR(255),
    phone1 VARCHAR(15),
    phone2 VARCHAR(15),
    fundingSourceRequired INTEGER,
    comments VARCHAR(255),
    objectid ENTITYID NOT NULL,

    container ENTITYID,
    createdby USERID,
    created TIMESTAMP,
    modifiedby USERID,
    modified TIMESTAMP,

    CONSTRAINT PK_vendors PRIMARY KEY (objectid)
);