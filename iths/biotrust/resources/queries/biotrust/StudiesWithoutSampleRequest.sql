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
CONVERT('No sample requests', SQL_VARCHAR) AS Type,
srd.RowId AS StudyId,
srd.SurveyRowId AS StudySurveyId,
srd.SurveyDesignId AS StudySurveyDesignId,
srd.SurveyLabel AS StudyName,
IFDEFINED(srd.Registered) AS StudyReceived,
srd.Status AS StudyStatus,
IFDEFINED(srd.Category) AS StudyCategoryId,
CASE WHEN IFDEFINED(srd.Category.Category) IS NOT NULL THEN IFDEFINED(srd.Category.Category) ELSE 'Unassigned' END AS StudyCategory,
srd.Container.EntityId AS Container,
srd.Container.Name AS ContainerName
FROM StudyRegistrationDetails AS srd
LEFT JOIN (SELECT ssr.StudyId, COUNT(ssr.RowId) AS NumSampleRequests
    FROM StudySampleRequests AS ssr
    GROUP BY ssr.StudyId) AS srCounts
    ON srd.RowId = srCounts.StudyId
WHERE srCounts.NumSampleRequests IS NULL
