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

SELECT Surveys.RowId AS SurveyRowId,
Surveys.Label AS SurveyLabel,
Surveys.SurveyDesignId,
Surveys.Submitted,
Surveys.SubmittedBy,
Surveys.Status,
IFDEFINED(Surveys.Category) AS Category,
TissueRecords.*
FROM biotrust.TissueRecords
LEFT JOIN survey.Surveys
  ON CONVERT(Surveys.ResponsesPk, SQL_INTEGER) = TissueRecords.RowId
  AND Surveys.Submitted IS NOT NULL
  -- TODO: since PIs don't have access to the project they can't see the survey design schema/query info, this hack requires that all TissueRecord surveys are submitted and all StudyRegistration surveys are not
WHERE TissueRecords.RowId IS NOT NULL
-- NOTE: the following does not work because a PI does not have access to the project folder (where survey desgins
--WHERE SurveyDesignId.SchemaName = 'biotrust' AND SurveyDesignId.QueryName = 'TissueRecords'
