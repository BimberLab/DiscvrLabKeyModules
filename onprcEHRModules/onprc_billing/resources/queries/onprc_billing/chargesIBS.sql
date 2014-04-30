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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
SELECT
  i.servicecenter,
  'N' as transactionType,
  i.transactionNumber,
  i.date as transactionDate,
  CASE
    WHEN i.servicecenter = 'SLAU' THEN i.item
    ELSE i.Id
  END as transactionDescription,  --show animal Id, rather than description, except for SLAU
  i.lastName,
  i.firstName,
  null as blank,
  --i.faid,
  i.faid.faid as fiscalAuthorityNumber,
  --i.faid.lastName as fiscalAuthorityName,
  i.department,
  'L584' as mailcode,
  COALESCE(i.contactPhone, moduleProperty('onprc_billing','DefaultBillingPhoneNumber')) as contactPhone,
  i.itemCode,
  i.quantity,
  i.unitCost as price,
  i.debitedaccount as OSUAlias,
  --i.creditedaccount,
  i.totalcost,
  i.invoiceId,
  i.category,
  i.project

FROM onprc_billing.invoicedItems i
WHERE i.totalcost != 0 AND (i.transactionType != 'ERROR' OR i.transactionType IS NULL)

UNION ALL

SELECT
  i.servicecenter,
  'N' as transactionType,
  i.transactionNumber,
  i.date as transactionDate,
  CASE
    WHEN i.servicecenter = 'SLAU' THEN i.item
    ELSE i.Id
  END as transactionDescription,  --show animal Id, rather than description, except for SLAU
  i.lastName,
  i.firstName,
  null as blank,
  --i.faid,
  i.faid.faid as fiscalAuthorityNumber,
  --i.faid.lastName as fiscalAuthorityName,
  i.department,
  'L584' as mailcode,
  COALESCE(i.contactPhone, moduleProperty('onprc_billing','DefaultBillingPhoneNumber')) as contactPhone,
  (i.itemCode || 'C') as itemCode,
  i.quantity,
  (i.unitCost * -1) as price,
  --i.debitedaccount as OSUAlias,
  i.creditedaccount as OSUAlias,
  (i.totalcost * -1) as totalcost,
  i.invoiceId,
  i.category,
  i.project

FROM onprc_billing.invoicedItems i
WHERE i.totalcost != 0 AND i.creditedaccount IS NOT NULL and i.creditedaccount != '-1' AND (i.transactionType != 'ERROR' OR i.transactionType IS NULL)
