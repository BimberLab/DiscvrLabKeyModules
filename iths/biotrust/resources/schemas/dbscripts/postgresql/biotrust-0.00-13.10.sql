/*
 * Copyright (c) 2013 LabKey Corporation
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

/* biotrust-0.01-12.30.sql */

CREATE SCHEMA biotrust;

/* biotrust-12.30-12.31.sql */

CREATE TABLE biotrust.RequestOwner
(
    RowId SERIAL,
    Label VARCHAR(100) NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_RequestOwner PRIMARY KEY (RowId),
    CONSTRAINT UQ_ContainerLabel UNIQUE (Container, Label)
);

CREATE TABLE biotrust.RequestStatus
(
    Status VARCHAR(40) NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_RequestStatus PRIMARY KEY (Status)
);

/* biotrust-12.31-12.32.sql */

-- drop the RequestOwner table and replace it with RequestCategory
DROP TABLE biotrust.RequestOwner;

CREATE TABLE biotrust.RequestCategory
(
    RowId SERIAL,
    Category VARCHAR(100) NOT NULL,
    SortOrder REAL NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_RequestCategory PRIMARY KEY (RowId),
    CONSTRAINT UQ_ContainerCategory UNIQUE (Container, Category),
    CONSTRAINT UQ_ContainerSortOrder UNIQUE (Container, SortOrder)
);

/* biotrust-12.32-12.33.sql */

CREATE TABLE biotrust.DocumentTypes
(
    TypeId SERIAL NOT NULL,

    Name VARCHAR(255),
    Description VARCHAR(255),
    AllowMultipleUpload BOOLEAN NOT NULL DEFAULT FALSE,
    Expiration BOOLEAN NOT NULL DEFAULT FALSE,

    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_documenttypes PRIMARY KEY (TypeId)
);

CREATE TABLE biotrust.RequiredDocuments
(
    SurveyDesignId INT NOT NULL,
    DocumentTypeId INT NOT NULL,

    CONSTRAINT PK_surveydesignid_documentid PRIMARY KEY (SurveyDesignId, DocumentTypeId),
    CONSTRAINT fk_requireddocuments_surveydesignid FOREIGN KEY (SurveyDesignId) REFERENCES survey.SurveyDesigns (RowId),
    CONSTRAINT fk_requireddocuments_documentid FOREIGN KEY (DocumentTypeId) REFERENCES biotrust.DocumentTypes (TypeId)
);

CREATE TABLE biotrust.SpecimenRequestDocuments
(
    SurveyId INT NOT NULL,
    DocumentTypeId INT NOT NULL,
    AttachmentParentId ENTITYID NOT NULL,

    CONSTRAINT PK_surveyid_documentid_attachmentid PRIMARY KEY (SurveyId, DocumentTypeId, AttachmentParentId),
    CONSTRAINT fk_requestdocuments_surveyid FOREIGN KEY (SurveyId) REFERENCES survey.Surveys (RowId),
    CONSTRAINT fk_requestdocuments_documentid FOREIGN KEY (DocumentTypeId) REFERENCES biotrust.DocumentTypes (TypeId)
);

/* biotrust-12.33-12.34.sql */

-- remove Container column from RequestCategory and RequestStatus tables
ALTER TABLE biotrust.RequestCategory DROP CONSTRAINT UQ_ContainerCategory;
ALTER TABLE biotrust.RequestCategory DROP CONSTRAINT UQ_ContainerSortOrder;
ALTER TABLE biotrust.RequestCategory DROP COLUMN Container;
ALTER TABLE biotrust.RequestStatus DROP COLUMN Container;

-- add SortOrder column to RequestStatus
ALTER TABLE biotrust.RequestStatus ADD COLUMN SortOrder REAL;

/* biotrust-12.34-12.35.sql */

ALTER TABLE biotrust.RequiredDocuments DROP CONSTRAINT fk_requireddocuments_surveydesignid;
ALTER TABLE biotrust.SpecimenRequestDocuments DROP CONSTRAINT fk_requestdocuments_surveyid;

/* biotrust-12.35-12.36.sql */

CREATE TABLE biotrust.SamplePickup
(
    RowId SERIAL NOT NULL,

    Name VARCHAR(255),
    Description VARCHAR(255),
    ArrangeForPickup BOOLEAN NOT NULL DEFAULT FALSE,
    HoldOvernight BOOLEAN NOT NULL DEFAULT FALSE,
    PickupContact USERID,

    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_samplepickup PRIMARY KEY (RowId)
);

CREATE TABLE biotrust.ParticipantEligibilityMap
(
    TissueId INT NOT NULL,
    EligibilityId INT NOT NULL,

    CONSTRAINT PK_tissueid_eligibilityid PRIMARY KEY (TissueId, EligibilityId)
);

CREATE TABLE biotrust.SamplePickupMap
(
    TissueId INT NOT NULL,
    PickupId INT NOT NULL,

    CONSTRAINT PK_tissueid_pickupid PRIMARY KEY (TissueId, PickupId),
    CONSTRAINT fk_pickupmap_pickupid FOREIGN KEY (PickupId) REFERENCES biotrust.SamplePickup (RowId)
);

-- modify document set behavior to handle either studies or tissue records

ALTER TABLE biotrust.SpecimenRequestDocuments DROP CONSTRAINT PK_surveyid_documentid_attachmentid;
ALTER TABLE biotrust.SpecimenRequestDocuments RENAME COLUMN SurveyId TO OwnerId;
ALTER TABLE biotrust.SpecimenRequestDocuments ADD COLUMN OwnerType VARCHAR(60) NOT NULL DEFAULT 'study';
ALTER TABLE biotrust.SpecimenRequestDocuments ADD CONSTRAINT
  PK_ownerid_ownertype_documentid_attachmentid PRIMARY KEY (OwnerId, OwnerType, DocumentTypeId, AttachmentParentId);

/* biotrust-12.36-12.37.sql */

ALTER TABLE biotrust.SamplePickup ADD Container ENTITYID;

/* biotrust-12.37-12.38.sql */

-- add 3 new text fields for the sample pickup lab information
ALTER TABLE biotrust.SamplePickup ADD COLUMN SampleTestLocation VARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD COLUMN LabBuilding VARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD COLUMN LabRoom VARCHAR(4000);

-- drop not null contraint on ArrangeForPickup and HoldOvernight columns
ALTER TABLE biotrust.SamplePickup ALTER COLUMN ArrangeForPickup DROP NOT NULL;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN ArrangeForPickup DROP DEFAULT;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN HoldOvernight DROP NOT NULL;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN HoldOvernight DROP DEFAULT;

/* biotrust-12.38-12.39.sql */

DELETE FROM biotrust.ParticipantEligibilityMap;
DELETE FROM biotrust.SamplePickupMap;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD Container ENTITYID NOT NULL;
ALTER TABLE biotrust.SamplePickupMap ADD Container ENTITYID NOT NULL;

ALTER TABLE biotrust.ParticipantEligibilityMap DROP CONSTRAINT PK_tissueid_eligibilityid;
ALTER TABLE biotrust.SamplePickupMap DROP CONSTRAINT PK_tissueid_pickupid;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD CONSTRAINT PK_tissueid_eligibilityid PRIMARY KEY (TissueId, EligibilityId, Container);
ALTER TABLE biotrust.SamplePickupMap ADD CONSTRAINT PK_tissueid_pickupid PRIMARY KEY (TissueId, PickupId, Container);