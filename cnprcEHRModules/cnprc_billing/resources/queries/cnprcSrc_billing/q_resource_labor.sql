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
RL_SEQPK AS seqpk,
RL_DESCRIPTION AS description,
RL_ALT_CODE AS altCode,
RL_COST_CODE AS costCode,
RL_UOM AS uom,
RL_LABOR_RATE AS laborRate,
RL_CREATED AS origCreatedDate,
RL_CREATED_BY AS origCreatedBy,
RL_LAST_UPDATE AS lastUpdate,
RL_LAST_UPDATE_BY AS lastUpdateBy,
(CASE WHEN RL_ACTIVE='A' THEN 1 ELSE 0 END) AS isActive,
RL_BEGIN_DATE AS beginDate,
RL_END_DATE AS endDate,
RL_TIERED_RATE_LABOR_TYPE AS tieredRateLaborType,
RL_RATE_TIER_CODE_FK AS rateTierCodeFk,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZRESOURCE_LABOR;
