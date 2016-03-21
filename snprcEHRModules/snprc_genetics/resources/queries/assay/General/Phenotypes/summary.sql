/*
 * Copyright (c) 2016 LabKey Corporation
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
d.subjectId,
d.subjectId.DataSet.demographics.species,
d.SAMPLE_ID,
d.BLEED_DATE,
d.ASSAY,
d.VALUE,
d.Run,
d.folder,
CASE
WHEN d.BLEED_DATE IS NULL THEN NULL
ELSE (ROUND(CONVERT(age_in_months(d.subjectId.DataSet.demographics.birth, COALESCE(d.subjectId.DataSet.demographics.lastDayAtCenter, d.BLEED_DATE)), DOUBLE) / 12, 1))
END as ageAtTime
FROM Data d