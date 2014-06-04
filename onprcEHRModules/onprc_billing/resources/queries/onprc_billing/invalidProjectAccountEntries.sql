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
--first find overlapping intervals
SELECT
  r1.project,
  r1.rowid as rowId1,
  r1.account,
  r1.startDate as startDate1,
  r1.enddate as endDate1,
  r2.rowid as rowId2,
  r2.account as account2,
  r2.startDate as startDate2,
  r2.enddate as endDate2
FROM onprc_billing.projectAccountHistory r1
JOIN onprc_billing.projectAccountHistory r2 ON (r1.rowid != r2.rowid and r1.project = r2.project AND cast(r1.startDate as DATE) <= r2.enddateCoalesced AND r1.enddateCoalesced >= cast(r2.startDate as DATE))
where r1.rowid < r2.rowid --this will result in 1 row per pair of offending records, rather than 2