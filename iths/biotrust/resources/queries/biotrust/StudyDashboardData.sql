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

SELECT StudyRegistrationDetails.RowId,
StudyRegistrationDetails.SurveyRowId,
StudyRegistrationDetails.Container.EntityId AS Container,
StudyRegistrationDetails.SurveyDesignId,
StudyRegistrationDetails.SurveyLabel AS Label,
StudyRegistrationDetails.CreatedBy.DisplayName AS CreatedBy,
StudyRegistrationDetails.Created,
StudyRegistrationDetails.ModifiedBy.DisplayName AS ModifiedBy,
StudyRegistrationDetails.Modified,
StudyRegistrationDetails.Status,
RequestStatus.SortOrder AS StatusSortOrder,
StatusChange.LastStatusChange,
SampleRequests.NumRecords,
SampleRequests.NumPending,
SampleRequests.NumSubmitted,
-- extensible table columns
IFDEFINED(StudyRegistrationDetails.Category) AS CategoryId,
CASE WHEN IFDEFINED(StudyRegistrationDetails.Category.Category) IS NOT NULL THEN IFDEFINED(StudyRegistrationDetails.Category.Category) ELSE 'Unassigned' END AS Category,
IFDEFINED(StudyRegistrationDetails.Category.SortOrder) AS CategorySortOrder,
IFDEFINED(StudyRegistrationDetails.IRBNumber)
FROM biotrust.StudyRegistrationDetails
LEFT JOIN biotrust.RequestStatus AS RequestStatus ON StudyRegistrationDetails.Status = RequestStatus.Status
LEFT JOIN (SELECT StudyId, COUNT(RowId) AS NumRecords,
 SUM(CASE WHEN Submitted IS NULL THEN 1 ELSE 0 END) AS NumPending,
 SUM(CASE WHEN Submitted IS NOT NULL THEN 1 ELSE 0 END) AS NumSubmitted,
 FROM biotrust.StudySampleRequests GROUP BY StudyId) AS SampleRequests
	ON StudyRegistrationDetails.RowId = SampleRequests.StudyId
LEFT JOIN (SELECT IntKey1 AS StudyId, MAX(Date) AS LastStatusChange, FROM auditLog.BioTrustAuditEvent WHERE Key1 = 'Study Status Changed' GROUP BY IntKey1) AS StatusChange
	ON StudyRegistrationDetails.RowId = StatusChange.StudyId
