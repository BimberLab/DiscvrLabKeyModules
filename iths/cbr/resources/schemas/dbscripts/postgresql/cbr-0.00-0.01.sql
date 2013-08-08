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
CREATE SCHEMA cbr;

CREATE TABLE cbr.consent (
	consentId SERIAL NOT NULL,
	eligId int NULL,
	consentformid int NULL,
	ccUser varchar(50) NULL,
	ccReviewDate timestamp NULL,
	ccIsPatElig varchar(1) NULL,
	ccDidPatConsent varchar(1) NULL,
	ccPatConsentDate timestamp NULL,
	ccPatDeclineReason text NULL,
	ccComments text NULL,
	ccCommentsHx text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
    CONSTRAINT PK_consent PRIMARY KEY (consentId)
);

CREATE TABLE cbr.consentForm(
	consentFormId SERIAL NOT NULL,
	studyId int NULL,
	formName varchar(200) NULL,
	active int NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_consentForm PRIMARY KEY (consentFormId)
);

CREATE TABLE cbr.eligibility(
	eligId SERIAL NOT NULL,
	studyId int NULL,
	scheduleId int NULL,
	maruNew int NULL,
	maruActive int NULL,
	rcUser varchar(50) NULL,
	rcReviewDate timestamp NULL,
	rcIsPatElig varchar(1) NULL,
	rcComments text NULL,
	container ENTITYID NULL,
	entityId ENTITYID NULL,
	createdBy USERID NULL,
	created timestamp NULL,
	modifiedBy USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_eligibility PRIMARY KEY (eligId)
);

CREATE TABLE cbr.participant(
	participantid SERIAL NOT NULL,
	amalgaID varchar(80) NULL,
	mrn varchar(10) NULL,
	firstName varchar(30) NULL,
	lastName varchar(30) NULL,
	gender varchar(1) NULL,
	dob timestamp NULL,
	language varchar(100) NULL,
	approachableForConsent int NULL,
	patNotes text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_participant PRIMARY KEY (participantId)
);

CREATE TABLE cbr.repoCriteria(
	repoCriteriaId SERIAL NOT NULL,
	repoId int NOT NULL,
	amalgaDataSource varchar(100) NULL,
	field varchar(100) NULL,
	operator varchar(50) NULL,
	value varchar(500) NULL,
	effectiveFromDtTm timestamp NULL,
	effectiveToDtTm timestamp NULL,
	createdBy USERID NULL,
	created timestamp NULL,
	modifiedBy USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_repoCriteria PRIMARY KEY (repoCriteriaId)
);

CREATE TABLE cbr.repository(
	repoId SERIAL NOT NULL,
	repoName varchar(50) NULL,
	repoPi USERID NULL,
	labManager USERID NULL,
	repoManager USERID NULL,
	institution varchar(50) NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_repository PRIMARY KEY (repoId)
);

CREATE TABLE cbr.schedule(
	scheduleid SERIAL NOT NULL,
	participantid int NULL,
	provider varchar(200) NULL,
	schedDate timestamp NULL,
	patType varchar(200) NULL,
	facility varchar(10) NULL,
	location varchar(200) NULL,
	amalgaDataSource varchar(100) NULL,
	amalgaId varchar(80) NULL,
	maruNew int NULL,
	maruActive int NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_schedule PRIMARY KEY (scheduleId)
);

CREATE TABLE cbr.scheduleNotes(
	scheduleNoteId SERIAL NOT NULL,
	scheduleId int NULL,
	amalgaDataSource varchar(200) NULL,
	field varchar(100) NULL,
	value text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_scheduleNotes PRIMARY KEY (scheduleNoteId)
);

CREATE TABLE cbr.study(
	studyId SERIAL NOT NULL,
	cbrStudyId varchar(50) NULL,
	repository int NULL,
	studyTitle varchar(50) NULL,
	studyPi USERID NULL,
	pickupDeliveryContact USERID NULL,
	pickupAfter5 bit NULL,
	holdSampleOvernight bit NULL,
	collectionStart date NULL,
	collectionEnd date NULL,
	consentForm int NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_study PRIMARY KEY (studyId),
	CONSTRAINT UNIQUE_study UNIQUE (cbrStudyId)
);

CREATE TABLE cbr.studyCriteria(
	studyCriteriaId SERIAL NOT NULL,
	studyId int NOT NULL,
	dataSource varchar(100) NULL,
	field varchar(100) NULL,
	operator varchar(50) NULL,
	value varchar(500) NULL,
	effectiveFromDtTm timestamp NULL,
	effectiveToDtTm timestamp NULL,
	createdBy USERID NULL,
	created timestamp NULL,
	modifiedBy USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_studyCriteria PRIMARY KEY (studyCriteriaId)
);

CREATE TABLE cbr.term(
	termId SERIAL NOT NULL,
	studyId int NULL,
	source varchar(100) NULL,
	field varchar(100) NULL,
	term varchar(100) NULL,
	termStrength int NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_term PRIMARY KEY (termId)
);

CREATE TABLE cbr.termFound(
	termFoundId SERIAL NOT NULL,
	termId int NULL,
	scheduleNoteId int NULL,
	termValue varchar(100) NULL,
	termStrength int NULL,
	createdby USERID NULL,
	created timestamp NULL,
	modifiedby USERID NULL,
	modified timestamp NULL,
	CONSTRAINT PK_termFound PRIMARY KEY (termFoundId)
);

ALTER TABLE cbr.consent  ADD  CONSTRAINT FK_ConsentConsentForm FOREIGN KEY(consentformid)
REFERENCES cbr.consentForm (consentFormId);

ALTER TABLE cbr.consent  ADD  CONSTRAINT FK_ConsentEligibility FOREIGN KEY(eligId)
REFERENCES cbr.eligibility (eligId);

ALTER TABLE cbr.consentForm  ADD  CONSTRAINT FK_ConsentFormStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.eligibility  ADD  CONSTRAINT FK_EligibilitySchedule FOREIGN KEY(scheduleId)
REFERENCES cbr.schedule (scheduleid)
ON DELETE CASCADE;

ALTER TABLE cbr.eligibility  ADD  CONSTRAINT FK_EligibilityStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId)
ON DELETE CASCADE;

ALTER TABLE cbr.repoCriteria  ADD  CONSTRAINT FK_RepoCriteriaRepository FOREIGN KEY(repoId)
REFERENCES cbr.repository (repoId)
ON DELETE CASCADE;

ALTER TABLE cbr.schedule ADD  CONSTRAINT FK_ScheduleParticipant FOREIGN KEY(participantid)
REFERENCES cbr.participant (participantid)
ON DELETE CASCADE;

ALTER TABLE cbr.scheduleNotes ADD  CONSTRAINT FK_ScheduleNotesSchedule FOREIGN KEY(scheduleId)
REFERENCES cbr.schedule (scheduleid)
ON DELETE CASCADE;

ALTER TABLE cbr.study  ADD  CONSTRAINT FK_StudyRepo FOREIGN KEY(repository)
REFERENCES cbr.repository (repoId);

ALTER TABLE cbr.studyCriteria  ADD  CONSTRAINT FK_StudyCriteriaStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.term  ADD  CONSTRAINT FK_TermStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.termFound ADD  CONSTRAINT FK_TermFoundScheduleNotes FOREIGN KEY(scheduleNoteId)
REFERENCES cbr.scheduleNotes (scheduleNoteId);

ALTER TABLE cbr.termFound  ADD  CONSTRAINT FK_TermFoundTerm FOREIGN KEY(termId)
REFERENCES cbr.term (termId)
ON DELETE CASCADE;