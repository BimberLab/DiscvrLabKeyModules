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

SELECT TissueRecords.RowId,
TissueRecords.SurveyRowId,
TissueRecords.SampleId,
SampleRequests.StudyId.RowId AS StudyId,
Studies.SurveyRowId AS StudySurveyId,
Studies.SurveyDesignId AS StudySurveyDesignId,
TissueRecords.RequestType,
CASE WHEN (TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Residual') THEN CONVERT('Discarded Blood Sample', SQL_VARCHAR)
  WHEN (TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Surgery' AND SampleRequests.SurgicalPairWithBlood = TRUE) THEN CONVERT('Paired Blood Sample', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN CONVERT('Blood Sample', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'TissueSample' THEN CONVERT('Tissue Sample', SQL_VARCHAR)
  ELSE NULL END AS RequestTypeDisplay,
CASE WHEN TissueRecords.TissueType IS NOT NULL
  THEN (CASE WHEN TissueRecords.TissueType = 'Other' THEN (TissueRecords.TissueType || ' - ' || TissueRecords.TissueTypeOther) ELSE TissueRecords.TissueType END)
  ELSE (CASE WHEN TissueRecords.TubeType = 'Other' THEN (TissueRecords.TubeType || ' - ' || TissueRecords.TubeTypeOther) ELSE TissueRecords.TubeType END) END AS Type,
CASE WHEN TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Surgery' AND SampleRequests.SurgicalPairWithBlood = TRUE THEN CONVERT('Paired Blood', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN CONVERT('Blood', SQL_VARCHAR)
  WHEN TissueRecords.AnatomicalSite = 'Other' THEN (TissueRecords.AnatomicalSite || ' - ' || TissueRecords.AnatomicalSiteOther)
  ELSE TissueRecords.AnatomicalSite END AS AnatomicalSite,
CASE WHEN TissueRecords.RequestType = 'BloodSample' AND TissueRecords.HoldAtLocation = 'Other' THEN (TissueRecords.HoldAtLocation || ' - ' || TissueRecords.HoldAtLocationOther)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN TissueRecords.HoldAtLocation
  WHEN TissueRecords.Preservation = 'Other' THEN (TissueRecords.Preservation || ' - ' || TissueRecords.PreservationOther)
  ELSE TissueRecords.Preservation END AS Preservation,
CASE WHEN (SampleRequests.TotalSpecimenDonorsNA=true) THEN 'N/A' ELSE CONVERT(SampleRequests.TotalSpecimenDonors, SQL_VARCHAR) END AS NumCases,
IFDEFINED(TissueRecords.StudyRecordId) AS StudyRecordId,
(CONVERT(SampleRequests.StudyId, SQL_VARCHAR) || '-' || CONVERT(IFDEFINED(TissueRecords.StudyRecordId), SQL_VARCHAR)) AS RecordId,
Studies.SurveyLabel AS StudyName,
IFDEFINED(Studies.Registered) AS StudyReceived,
Studies.Status AS StudyStatus,
IFDEFINED(Studies.Category) AS StudyCategoryId,
CASE WHEN IFDEFINED(Studies.Category.Category) IS NOT NULL THEN IFDEFINED(Studies.Category.Category) ELSE 'Unassigned' END AS StudyCategory,
TissueRecords.Created,
TissueRecords.CreatedBy.DisplayName AS CreatedBy,
TissueRecords.Modified,
TissueRecords.ModifiedBy.DisplayName AS ModifiedBy,
TissueRecords.Submitted,
TissueRecords.SubmittedBy.DisplayName AS SubmittedBy,
CASE WHEN TissueRecords.Status IS NULL THEN CONVERT('Pending', SQL_VARCHAR) ELSE TissueRecords.Status END AS Status,
CASE WHEN RequestStatus.LockedState IS NOT NULL THEN RequestStatus.LockedState
 WHEN TissueRecords.Status IS NOT NULL THEN FALSE
 ELSE NULL END AS IsLocked,
CASE WHEN RequestStatus.ApprovalState IS NOT NULL THEN RequestStatus.ApprovalState
 WHEN TissueRecords.Status IS NOT NULL THEN FALSE
 ELSE NULL END AS IsApproval,
CASE WHEN RequestStatus.FinalState IS NOT NULL THEN RequestStatus.FinalState
 WHEN TissueRecords.Status IS NOT NULL THEN FALSE
 ELSE NULL END AS IsFinal,
StatusChange.LastStatusChange,
TIMESTAMPDIFF('SQL_TSI_DAY', (CASE WHEN SampleRequests.Modified > TissueRecords.Modified THEN SampleRequests.Modified ELSE TissueRecords.Modified END), now()) AS DaysSinceModified,
IFDEFINED(TissueRecords.Category) AS CategoryId,
CASE WHEN IFDEFINED(TissueRecords.Category.Category) IS NOT NULL THEN IFDEFINED(TissueRecords.Category.Category) ELSE 'Unassigned' END AS Category,
TissueRecords.Container.EntityId AS Container,
TissueRecords.Container.Name AS ContainerName,
CASE WHEN SampleReviewerMap.Reviewer IS NOT NULL THEN TRUE ELSE FALSE END AS UserReviewResponseExpected,
CASE WHEN ReviewResponsesByUser.HasResponse IS NOT NULL THEN ReviewResponsesByUser.HasResponse ELSE FALSE END AS UserReviewResponseExists,
ReviewResponseCounts.NumResponsesExpected,
ReviewResponseCounts.NumResponsesExist
FROM TissueRecordDetails AS TissueRecords
LEFT JOIN SampleRequests
  ON SampleRequests.RowId = TissueRecords.SampleId
LEFT JOIN StudyRegistrationDetails AS Studies
  ON SampleRequests.StudyId = Studies.RowId
LEFT JOIN (SELECT IntKey2 AS TissueId, MAX(Date) AS LastStatusChange FROM auditLog.BioTrustAuditEvent WHERE Key1 = 'Sample Request Status Changed' GROUP BY IntKey2) AS StatusChange
	ON TissueRecords.SurveyRowId = StatusChange.TissueId
LEFT JOIN biotrust.RequestStatus AS RequestStatus
    ON TissueRecords.Status = RequestStatus.Status
-- join in approver respose counts
LEFT JOIN ReviewResponseCounts
  ON TissueRecords.SurveyRowId = ReviewResponseCounts.TissueId AND TissueRecords.Status = ReviewResponseCounts.Status
-- join in approver review map to see if a response is expected fromt eh given userid
LEFT JOIN SampleReviewerMap
  ON TissueRecords.SurveyRowId = SampleReviewerMap.TissueId AND TissueRecords.Status = SampleReviewerMap.Status AND SampleReviewerMap.Reviewer = USERID()
-- join in approver review responses for the given userId
LEFT JOIN ReviewResponsesByUser
  ON TissueRecords.SurveyRowId = ReviewResponsesByUser.TissueRecordId AND TissueRecords.Status = ReviewResponsesByUser.Status AND ReviewResponsesByUser.UserId = USERID()
WHERE Studies.RowId IS NOT NULL
