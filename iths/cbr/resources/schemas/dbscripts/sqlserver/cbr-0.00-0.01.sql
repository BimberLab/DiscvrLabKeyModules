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
-- drop table cbr.consent;
-- drop table cbr.consentForm;
-- drop table cbr.eligibility;
-- drop table cbr.repoCriteria;
-- drop table cbr.studyCriteria;
-- drop table cbr.termFound;
-- drop table cbr.term;
-- drop table cbr.scheduleNotes;
-- drop table cbr.schedule;
-- drop table cbr.study;
-- drop table cbr.participant;
-- drop table cbr.repository;
-- drop schema cbr;


CREATE SCHEMA cbr;
GO

CREATE TABLE cbr.consent (
	consentId int IDENTITY(1,1) NOT NULL,
	eligId int NULL,
	consentformid int NULL,
	ccUser varchar(50) NULL,
	ccReviewDate datetime NULL,
	ccIsPatElig varchar(1) NULL,
	ccDidPatConsent varchar(1) NULL,
	ccPatConsentDate datetime NULL,
	ccPatDeclineReason text NULL,
	ccComments text NULL,
	ccCommentsHx text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
    CONSTRAINT PK_consent PRIMARY KEY (consentId)
);

CREATE TABLE cbr.consentForm(
	consentFormId int IDENTITY(1,1) NOT NULL,
	studyId int NULL,
	formName varchar(200) NULL,
	active int NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_consentForm PRIMARY KEY (consentFormId)
);

CREATE TABLE cbr.eligibility(
	eligId int IDENTITY(1,1) NOT NULL,
	studyId int NULL,
	scheduleId int NULL,
	maruNew int NULL,
	maruActive int NULL,
	rcUser varchar(50) NULL,
	rcReviewDate datetime NULL,
	rcIsPatElig varchar(1) NULL,
	rcComments text NULL,
	container ENTITYID NULL,
	entityId ENTITYID NULL,
	createdBy USERID NULL,
	created datetime NULL,
	modifiedBy USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_eligibility PRIMARY KEY (eligId)
);

CREATE TABLE cbr.participant(
	participantid int IDENTITY(1,1) NOT NULL,
	amalgaID varchar(80) NULL,
	mrn varchar(10) NULL,
	firstName varchar(30) NULL,
	lastName varchar(30) NULL,
	gender varchar(1) NULL,
	dob datetime NULL,
	language varchar(100) NULL,
	approachableForConsent int NULL,
	patNotes text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_participant PRIMARY KEY (participantId)
);

CREATE TABLE cbr.repoCriteria(
	repoCriteriaId int IDENTITY(1,1) NOT NULL,
	repoId int NOT NULL,
	amalgaDataSource varchar(100) NULL,
	field varchar(100) NULL,
	operator varchar(50) NULL,
	value varchar(500) NULL,
	effectiveFromDtTm datetime NULL,
	effectiveToDtTm datetime NULL,
	createdBy USERID NULL,
	created datetime NULL,
	modifiedBy USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_repoCriteria PRIMARY KEY (repoCriteriaId)
);

CREATE TABLE cbr.repository(
	repoId int IDENTITY(1,1) NOT NULL,
	repoName varchar(50) NULL,
	repoPi USERID NULL,
	labManager USERID NULL,
	repoManager USERID NULL,
	institution varchar(50) NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_repository PRIMARY KEY (repoId)
);

CREATE TABLE cbr.schedule(
	scheduleid int IDENTITY(1,1) NOT NULL,
	participantid int NULL,
	provider varchar(200) NULL,
	schedDate datetime NULL,
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
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_schedule PRIMARY KEY (scheduleId)
);

CREATE TABLE cbr.scheduleNotes(
	scheduleNoteId int IDENTITY(1,1) NOT NULL,
	scheduleId int NULL,
	amalgaDataSource varchar(200) NULL,
	field varchar(100) NULL,
	value text NULL,
	container ENTITYID NULL,
	entityid ENTITYID NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_scheduleNotes PRIMARY KEY (scheduleNoteId)
);

CREATE TABLE cbr.study(
	studyId int IDENTITY(1,1) NOT NULL,
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
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_study PRIMARY KEY (studyId),
	CONSTRAINT UNIQUE_study UNIQUE (cbrStudyId)
);

CREATE TABLE cbr.studyCriteria(
	studyCriteriaId int IDENTITY(1,1) NOT NULL,
	studyId int NOT NULL,
	dataSource varchar(100) NULL,
	field varchar(100) NULL,
	operator varchar(50) NULL,
	value varchar(500) NULL,
	effectiveFromDtTm datetime NULL,
	effectiveToDtTm datetime NULL,
	createdBy USERID NULL,
	created datetime NULL,
	modifiedBy USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_studyCriteria PRIMARY KEY (studyCriteriaId)
);

CREATE TABLE cbr.term(
	termId int IDENTITY(1,1) NOT NULL,
	studyId int NULL,
	source varchar(100) NULL,
	field varchar(100) NULL,
	term varchar(100) NULL,
	termStrength int NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_term PRIMARY KEY (termId)
);

CREATE TABLE cbr.termFound(
	termFoundId int IDENTITY(1,1) NOT NULL,
	termId int NULL,
	scheduleNoteId int NULL,
	termValue varchar(100) NULL,
	termStrength int NULL,
	createdby USERID NULL,
	created datetime NULL,
	modifiedby USERID NULL,
	modified datetime NULL,
	CONSTRAINT PK_termFound PRIMARY KEY (termFoundId)
);

ALTER TABLE cbr.consent  WITH CHECK ADD  CONSTRAINT FK_ConsentConsentForm FOREIGN KEY(consentformid)
REFERENCES cbr.consentForm (consentFormId);

ALTER TABLE cbr.consent CHECK CONSTRAINT FK_ConsentConsentForm;

ALTER TABLE cbr.consent  WITH CHECK ADD  CONSTRAINT FK_ConsentEligibility FOREIGN KEY(eligId)
REFERENCES cbr.eligibility (eligId);

ALTER TABLE cbr.consent CHECK CONSTRAINT FK_ConsentEligibility;

ALTER TABLE cbr.consentForm  WITH CHECK ADD  CONSTRAINT FK_ConsentFormStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.consentForm CHECK CONSTRAINT FK_ConsentFormStudy;

ALTER TABLE cbr.eligibility  WITH CHECK ADD  CONSTRAINT FK_EligibilitySchedule FOREIGN KEY(scheduleId)
REFERENCES cbr.schedule (scheduleid)
ON DELETE CASCADE;

ALTER TABLE cbr.eligibility CHECK CONSTRAINT FK_EligibilitySchedule;

ALTER TABLE cbr.eligibility  WITH CHECK ADD  CONSTRAINT FK_EligibilityStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId)
ON DELETE CASCADE;

ALTER TABLE cbr.eligibility CHECK CONSTRAINT FK_EligibilityStudy;

ALTER TABLE cbr.repoCriteria  WITH CHECK ADD  CONSTRAINT FK_RepoCriteriaRepository FOREIGN KEY(repoId)
REFERENCES cbr.repository (repoId)
ON DELETE CASCADE;

ALTER TABLE cbr.repoCriteria CHECK CONSTRAINT FK_RepoCriteriaRepository;

ALTER TABLE cbr.schedule  WITH CHECK ADD  CONSTRAINT FK_ScheduleParticipant FOREIGN KEY(participantid)
REFERENCES cbr.participant (participantid)
ON DELETE CASCADE;

ALTER TABLE cbr.schedule CHECK CONSTRAINT FK_ScheduleParticipant;

ALTER TABLE cbr.scheduleNotes  WITH CHECK ADD  CONSTRAINT FK_ScheduleNotesSchedule FOREIGN KEY(scheduleId)
REFERENCES cbr.schedule (scheduleid)
ON DELETE CASCADE;

ALTER TABLE cbr.scheduleNotes CHECK CONSTRAINT FK_ScheduleNotesSchedule;

ALTER TABLE cbr.study  WITH CHECK ADD  CONSTRAINT FK_StudyRepo FOREIGN KEY(repository)
REFERENCES cbr.repository (repoId);

ALTER TABLE cbr.study CHECK CONSTRAINT FK_StudyRepo;

ALTER TABLE cbr.studyCriteria  WITH CHECK ADD  CONSTRAINT FK_StudyCriteriaStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.studyCriteria CHECK CONSTRAINT FK_StudyCriteriaStudy;

ALTER TABLE cbr.term  WITH CHECK ADD  CONSTRAINT FK_TermStudy FOREIGN KEY(studyId)
REFERENCES cbr.study (studyId);

ALTER TABLE cbr.term CHECK CONSTRAINT FK_TermStudy;

ALTER TABLE cbr.termFound  WITH CHECK ADD  CONSTRAINT FK_TermFoundScheduleNotes FOREIGN KEY(scheduleNoteId)
REFERENCES cbr.scheduleNotes (scheduleNoteId);

ALTER TABLE cbr.termFound CHECK CONSTRAINT FK_TermFoundScheduleNotes;

ALTER TABLE cbr.termFound  WITH CHECK ADD  CONSTRAINT FK_TermFoundTerm FOREIGN KEY(termId)
REFERENCES cbr.term (termId)
ON DELETE CASCADE;

ALTER TABLE cbr.termFound CHECK CONSTRAINT FK_TermFoundTerm;


