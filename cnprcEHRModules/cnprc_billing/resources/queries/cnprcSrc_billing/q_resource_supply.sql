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
RS_SEQPK AS seqpk,
RS_DESCRIPTION AS description,
RS_COST_CODE AS costCode,
RS_CATALOG_NUM AS catalogNum,
RS_VENDOR AS vendor,
RS_UOM AS uom,
RS_UNITS_ISSUED AS unitsIssued,
RS_SUBID AS subid,
RS_ID AS id,
RS_PACKAGE_COST AS packageCost,
RS_UNIT_COST AS unitCost,
RS_CREATED AS origCreatedDate,
RS_CREATED_BY AS origCreatedBy,
RS_LAST_UPDATE AS lastUpdate,
RS_LAST_UPDATE_BY AS lastUpdateBy,
RS_CNPRC_SUPPLIER AS cnprcSupplier,
RS_CNPRC_USERS AS cnprcUsers,
(CASE WHEN RS_ACTIVE='A' THEN 1 ELSE 0 END) AS isActive,
RS_GROUP AS supplyGroup,
RS_CODE AS code,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZRESOURCE_SUPPLY;