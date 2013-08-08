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
/**
 * This script was created to simplify the migration of data from the older litterbox mssql schema into cbr
 */
INSERT INTO labkey.cbr.consent
     (eligId
     ,consentformid
     ,ccUser
     ,ccReviewDate
     ,ccIsPatElig
     ,ccDidPatConsent
     ,ccPatConsentDate
     ,ccPatDeclineReason
     ,ccComments
     ,ccCommentsHx
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT eligId
     ,consentformid
     ,ccUser
     ,ccReviewDate
     ,ccIsPatElig
     ,ccDidPatConsent
     ,ccPatConsentDate
     ,ccPatDeclineReason
     ,ccComments
     ,ccCommentsHx
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
FROM litterbox.litterbox.consent
GO


INSERT INTO labkey.cbr.consentForm
     (studyId
     ,formName
     ,active
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT studyId
     ,formName
     ,active
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
FROM litterbox.litterbox.consentForm
GO


INSERT INTO labkey.cbr.participant
     (amalgaID
     ,mrn
     ,firstName
     ,lastName
     ,gender
     ,dob
     ,language
     ,approachableForConsent
     ,patNotes
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT amalgaID
     ,mrn
     ,firstName
     ,lastName
     ,gender
     ,dob
     ,language
     ,approachableForConsent
     ,patNotes
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
FROM litterbox.litterbox.participant
GO


INSERT INTO labkey.cbr.repository
     (repoName
     ,repoPi
     ,labManager
     ,repoManager
     ,institution
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT repoName
     ,repoPi
     ,labManager
     ,repoManager
     ,institution
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified     
	from litterbox.litterbox.repository
GO


INSERT INTO labkey.cbr.repoCriteria
     (repoId
     ,amalgaDataSource
     ,field
     ,operator
     ,value
     ,effectiveFromDtTm
     ,effectiveToDtTm
     ,createdBy
     ,created
     ,modifiedBy
     ,modified)
SELECT repoId
     ,amalgaDataSource
     ,field
     ,operator
     ,value
     ,effectiveFromDtTm
     ,effectiveToDtTm
     ,createdBy
     ,created
     ,modifiedBy
     ,modified
FROM litterbox.litterbox.repoCriteria
GO

INSERT INTO labkey.cbr.schedule
     (participantid
     ,provider
     ,schedDate
     ,patType
     ,facility
     ,location
     ,amalgaDataSource
     ,amalgaId
     ,maruNew
     ,maruActive
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT participantid
     ,provider
     ,schedDate
     ,patType
     ,facility
     ,location
     ,amalgaDataSource
     ,amalgaId
     ,maruNew
     ,maruActive
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
	from litterbox.litterbox.schedule     
GO


INSERT INTO labkey.cbr.scheduleNotes
     (scheduleId
     ,amalgaDataSource
     ,field
     ,value
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT scheduleId
     ,amalgaDataSource
     ,field
     ,value
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
FROM litterbox.litterbox.scheduleNotes
GO


INSERT INTO labkey.cbr.study
     (cbrStudyId
     ,repository
     ,studyTitle
     ,studyPi
     ,pickupDeliveryContact
     ,pickupAfter5
     ,holdSampleOvernight
     ,collectionStart
     ,collectionEnd
     ,consentForm
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT cbrStudyId
     ,repository
     ,studyTitle
     ,studyPi
     ,pickupDeliveryContact
     ,pickupAfter5
     ,holdSampleOvernight
     ,collectionStart
     ,collectionEnd
     ,consentForm
     ,container
     ,entityid
     ,createdby
     ,created
     ,modifiedby
     ,modified
	from litterbox.litterbox.study     
GO


INSERT INTO labkey.cbr.studyCriteria
     (studyId
     ,dataSource
     ,field
     ,operator
     ,value
     ,effectiveFromDtTm
     ,effectiveToDtTm
     ,createdBy
     ,created
     ,modifiedBy
     ,modified)
SELECT studyId
     ,dataSource
     ,field
     ,operator
     ,value
     ,effectiveFromDtTm
     ,effectiveToDtTm
     ,createdBy
     ,created
     ,modifiedBy
     ,modified
	from litterbox.litterbox.studyCriteria     
GO


INSERT INTO labkey.cbr.term
     (studyId
     ,source
     ,field
     ,term
     ,termStrength
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT studyId
     ,source
     ,field
     ,term
     ,termStrength
     ,createdby
     ,created
     ,modifiedby
     ,modified
FROM litterbox.litterbox.term
GO


INSERT INTO labkey.cbr.termFound
     (termId
     ,scheduleNoteId
     ,termValue
     ,termStrength
     ,createdby
     ,created
     ,modifiedby
     ,modified)
SELECT termId
     ,scheduleNoteId
     ,termValue
     ,termStrength
     ,createdby
     ,created
     ,modifiedby
     ,modified
	from litterbox.litterbox.termFound
GO


INSERT INTO labkey.cbr.eligibility
     (studyId
     ,scheduleId
     ,maruNew
     ,maruActive
     ,rcUser
     ,rcReviewDate
     ,rcIsPatElig
     ,rcComments
     ,container
     ,entityId
     ,createdBy
     ,created
     ,modifiedBy
     ,modified)
SELECT studyId
     ,scheduleId
     ,maruNew
     ,maruActive
     ,rcUser
     ,rcReviewDate
     ,rcIsPatElig
     ,rcComments
     ,container
     ,entityId
     ,createdBy
     ,created
     ,modifiedBy
     ,modified
FROM litterbox.litterbox.eligibility
GO

UPDATE labkey.cbr.consent set container = 'BFA78089-8B24-102F-B786-9B108238A710';
UPDATE labkey.cbr.consentForm set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.participant set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.repository set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.schedule set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.scheduleNotes set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.study set container = 'BFA78089-8B24-102F-B786-9B108238A710';
update labkey.cbr.eligibility set container = 'BFA78089-8B24-102F-B786-9B108238A710';