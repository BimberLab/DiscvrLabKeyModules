DROP TABLE sla.purchaseDetails

CREATE TABLE sla.purchaseDetails (
    rowid INT IDENTITY(1,1) NOT NULL,
    purchaseid ENTITYID,
    species varchar(50),
    age varchar(200),
    weight varchar(200),
    weight_units varchar(100),
    gestation VARCHAR(255),
    gender varchar(50),
    strain VARCHAR(255),
    room varchar(255),
    animalsordered INTEGER,
    animalsreceived INTEGER,
    boxesquantity INTEGER,
    costperanimal VARCHAR(255),
    shippingcost VARCHAR(255),
    totalcost VARCHAR(255),
    housingInstructions VARCHAR(255),
    requestedarrivaldate DATETIME,
    expectedarrivaldate DATETIME,
    receiveddate DATETIME,
    receivedby VARCHAR(255),
    cancelledby VARCHAR(255),
    datecancelled DATETIME,
    objectid ENTITYID,

    container ENTITYID,
    createdby USERID,
    created DATETIME,
    modifiedby USERID,
    modified DATETIME,

    CONSTRAINT PK_purchaseDetails PRIMARY KEY (objectid)
 );