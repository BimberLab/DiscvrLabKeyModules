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
