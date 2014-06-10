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
SELECT
StudyRegistrationDetails.rowid AS StudyId,
StudyRegistrationDetails.surveyrowid AS SurveyRowId,
StudyRegistrationDetails.surveylabel AS StudyName,
StudyRegistrationDetails.status AS StudyStatus,
StudyRegistrationDetails.irbapprovalstatus AS IRB_ApprovalStatus,
StudyRegistrationDetails.irbfilenumber AS IRB_FileNumber,
StudyRegistrationDetails.irbexpirationdate AS IRB_ExpirationDate,
StudyRegistrationDetails.reviewingirb AS ReviewingIRB,
StudyRegistrationDetails.created AS Created,
CONVERT('view documents', SQL_VARCHAR) AS Link,
StudyRegistrationDetails.container AS InvestigatorFolder
FROM StudyRegistrationDetails
