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
