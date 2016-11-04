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
RICS_SEQPK AS seqPk,
RICS_ITEM_COSTING_FK AS itemCostingFk,
RICS_ITEM_SUPPLY_FK AS itemSupplyFk,
RICS_QUANTITY AS quantity,
RICS_TYPE AS costingSupplyType,
RICS_LABOR_FK AS laborFk,
RICS_SET_NAME AS setName,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZRESOURCE_ITEM_COSTING_SUPPLY;