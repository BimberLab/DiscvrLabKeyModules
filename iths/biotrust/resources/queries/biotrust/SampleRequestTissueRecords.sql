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
TissueRecords.StudyId,
IFDEFINED(TissueRecords.StudyRecordId) AS StudyRecordId,
(CONVERT(TissueRecords.StudyId, SQL_VARCHAR) || '-' || CONVERT(IFDEFINED(TissueRecords.StudyRecordId), SQL_VARCHAR)) AS RecordId,
TissueRecords.RequestType,
CASE WHEN (TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Residual') THEN CONVERT('Discarded Blood Sample', SQL_VARCHAR)
  WHEN (TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Surgery' AND SampleRequests.SurgicalPairWithBlood = TRUE) THEN CONVERT('Paired Blood Sample', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN CONVERT('Blood Sample', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'TissueSample' THEN CONVERT('Tissue Sample', SQL_VARCHAR)
  ELSE NULL END AS RequestTypeDisplay,
CASE WHEN TissueRecords.TissueType = 'Other' THEN (TissueRecords.TissueType || ' - ' || TissueRecords.TissueTypeOther) ELSE TissueRecords.TissueType END AS TissueType,
CASE WHEN TissueRecords.TubeType = 'Other' THEN (TissueRecords.TubeType || ' - ' || TissueRecords.TubeTypeOther) ELSE TissueRecords.TubeType END AS TubeType,
CASE WHEN TissueRecords.AnatomicalSite = 'Other' THEN (TissueRecords.AnatomicalSite || ' - ' || TissueRecords.AnatomicalSiteOther) ELSE TissueRecords.AnatomicalSite END AS AnatomicalSite,
TissueRecords.BloodSampleType,
CASE WHEN TissueRecords.Preservation = 'Other' THEN (TissueRecords.Preservation || ' - ' || TissueRecords.PreservationOther) ELSE TissueRecords.Preservation END AS Preservation,
TissueRecords.MinimumSize,
TissueRecords.MinimumSizeUnits,
TissueRecords.PreferredSize,
TissueRecords.PreferredSizeUnits,
CASE WHEN TissueRecords.HoldAtLocation = 'Other' THEN (TissueRecords.HoldAtLocation || ' - ' || TissueRecords.HoldAtLocationOther) ELSE TissueRecords.HoldAtLocation END AS HoldAtLocation,
TissueRecords.Submitted,
-- sample request specific fields
SampleRequests.CollectionStartDate,
SampleRequests.CollectionStartASAP,
SampleRequests.CollectionEndDate,
SampleRequests.CollectionEndOngoing,
SampleRequests.SurgicalPairWithBlood
FROM TissueRecordDetails AS TissueRecords
LEFT JOIN SampleRequests
  ON SampleRequests.RowId = TissueRecords.SampleId