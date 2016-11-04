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
RLCS_SEQPK AS seqpk,
RLCS_ITEM_COSTING_FK AS itemCostingFk,
RLCS_ITEM_SUPPLY_FK AS itemSupplyFk,
RLCS_QUANTITY AS quantity,
RLCS_TYPE AS laborCostingSupplyType,
RLCS_DESCRIPTION AS description,
RLCS_SET_NAME AS setName,
RLCS_TIERED_RATE_LABOR_TYPE AS tieredRateLaborType,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZRESOURCE_LABOR_COSTING_SUPPLY;