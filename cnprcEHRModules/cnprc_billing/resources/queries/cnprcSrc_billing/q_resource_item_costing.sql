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
RIC_SEQPK AS seqpk,
RIC_ITEM_CODE_FK AS itemCodeFk,
RIC_COMPONENT_FK AS componentFk,
RIC_ALT_ITEM_CODE AS altItemCode,
RIC_DESCR AS description,
RIC_ITEM_COST AS itemCost,
RIC_UPDATED_COST AS updatedCost,
RIC_UPDATED_RR_ITEM AS updatedRrItem,
RIC_UPDATE_FLAG AS updateFlag,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZRESOURCE_ITEM_COSTING;