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
GO

/* biotrust-12.30-13.10.sql */

/* biotrust-12.30-12.31.sql */

CREATE TABLE biotrust.RequestOwner
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Label NVARCHAR(100) NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_RequestOwner PRIMARY KEY (RowId),
    CONSTRAINT UQ_ContainerLabel UNIQUE (Container, Label)
);

CREATE TABLE biotrust.RequestStatus
(
    Status NVARCHAR(40) NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_RequestStatus PRIMARY KEY (Status)
);

/* biotrust-12.31-12.32.sql */

-- drop the RequestOwner table and replace it with RequestCategory
DROP TABLE biotrust.RequestOwner;

CREATE TABLE biotrust.RequestCategory
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Category NVARCHAR(100) NOT NULL,
    SortOrder REAL NOT NULL,

    Container ENTITYID,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_RequestCategory PRIMARY KEY (RowId),
    CONSTRAINT UQ_ContainerCategory UNIQUE (Container, Category),
    CONSTRAINT UQ_ContainerSortOrder UNIQUE (Container, SortOrder)
);

/* biotrust-12.32-12.33.sql */

CREATE TABLE biotrust.DocumentTypes
(
    TypeId INT IDENTITY(1, 1) NOT NULL,

    Name NVARCHAR(255),
    Description NVARCHAR(255),
    AllowMultipleUpload BIT NOT NULL DEFAULT 0,
    Expiration BIT NOT NULL DEFAULT 0,

    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

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
ALTER TABLE biotrust.RequestStatus ADD SortOrder REAL;

/* biotrust-12.34-12.35.sql */

ALTER TABLE biotrust.RequiredDocuments DROP CONSTRAINT fk_requireddocuments_surveydesignid;
ALTER TABLE biotrust.SpecimenRequestDocuments DROP CONSTRAINT fk_requestdocuments_surveyid;

/* biotrust-12.35-12.36.sql */

CREATE TABLE biotrust.SamplePickup
(
    RowId INT IDENTITY(1, 1) NOT NULL,

    Name NVARCHAR(255),
    Description NVARCHAR(255),
    ArrangeForPickup BIT NOT NULL DEFAULT 0,
    HoldOvernight BIT NOT NULL DEFAULT 0,
    PickupContact USERID,

    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

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
EXECUTE sp_rename N'biotrust.SpecimenRequestDocuments.SurveyId', N'OwnerId', 'COLUMN';
ALTER TABLE biotrust.SpecimenRequestDocuments ADD OwnerType NVARCHAR(60) NOT NULL DEFAULT 'study';
ALTER TABLE biotrust.SpecimenRequestDocuments ADD CONSTRAINT
  PK_ownerid_ownertype_documentid_attachmentid PRIMARY KEY (OwnerId, OwnerType, DocumentTypeId, AttachmentParentId);

/* biotrust-12.36-12.37.sql */

ALTER TABLE biotrust.SamplePickup ADD Container ENTITYID;

/* biotrust-12.37-12.38.sql */

-- add 3 new text fields for the sample pickup lab information
ALTER TABLE biotrust.SamplePickup ADD SampleTestLocation NVARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD LabBuilding NVARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD LabRoom NVARCHAR(4000);

-- drop not null contraint on ArrangeForPickup and HoldOvernight columns
ALTER TABLE biotrust.SamplePickup ALTER COLUMN ArrangeForPickup BIT NULL;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN HoldOvernight BIT NULL;

/* biotrust-12.38-12.39.sql */

DELETE FROM biotrust.ParticipantEligibilityMap;
DELETE FROM biotrust.SamplePickupMap;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD Container ENTITYID NOT NULL;
ALTER TABLE biotrust.SamplePickupMap ADD Container ENTITYID NOT NULL;

ALTER TABLE biotrust.ParticipantEligibilityMap DROP CONSTRAINT PK_tissueid_eligibilityid;
ALTER TABLE biotrust.SamplePickupMap DROP CONSTRAINT PK_tissueid_pickupid;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD CONSTRAINT PK_tissueid_eligibilityid PRIMARY KEY (Container, TissueId, EligibilityId);
ALTER TABLE biotrust.SamplePickupMap ADD CONSTRAINT PK_tissueid_pickupid PRIMARY KEY (Container, TissueId, PickupId);