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
PARAMETERS (Date TIMESTAMP)

SELECT
  ci.rowid,
  ci.name,
  ci.category,
  ci.allowscustomunitcost,
  r.rowid as rateId,
  ca.rowid as creditAccountId
FROM onprc_billing.chargeableItems ci
LEFT JOIN onprc_billing.chargeRates r ON (r.chargeId = ci.rowid AND CAST(Date as DATE) >= cast(r.startDate as date) AND CAST(Date as DATE) <= r.enddateCoalesced)
LEFT JOIN onprc_billing.creditAccount ca ON (ca.chargeId = ci.rowid AND CAST(Date as DATE) >= cast(ca.startDate as date) AND CAST(Date as DATE) <= ca.enddateCoalesced)
where ci.active = true and ((r.rowid is null AND ci.allowscustomunitcost != true) OR ca.rowid is null)