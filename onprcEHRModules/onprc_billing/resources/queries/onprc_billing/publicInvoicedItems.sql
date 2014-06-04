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
  i.rowId,
  i.invoiceId,
  i.transactionNumber,
  i.date,
  i.invoiceDate,
  i.Id,
  i.item,
  i.itemCode,
  i.category,
  i.servicecenter,
  i.project,
  i.debitedaccount,
  i.creditedaccount,
  i.faid,
  i.investigatorId,
  i.firstName,
  i.lastName,
  i.department,
  i.mailcode,
  i.contactPhone,
  i.chargeId,
  i.objectid,
  i.quantity,
  i.unitCost,
  i.totalcost,
  i.chargeCategory

FROM onprc_billing.invoicedItems i
WHERE ((SELECT max(rowid) as expr FROM onprc_billing.dataAccess da WHERE isMemberOf(da.userid) AND (
    da.allData = true OR
    (da.project = i.project) OR
    --TODO: this needs to get cleaned up
    (
      da.investigatorId = i.investigatorId
      OR da.investigatorId = i.debitedaccount.investigatorId
      OR da.investigatorId = i.project.investigatorId
    )
  )) IS NOT NULL OR

  --include if the user is either the project's PI, the account PI, or the financial analyst
  isMemberOf(i.project.investigatorId.userid) OR isMemberOf(i.debitedaccount.investigatorId.userid) OR isMemberOf(i.project.investigatorId.financialAnalyst)

) --AND TIMESTAMPDIFF('SQL_TSI_DAY', curdate(), i.date) < 730

--arbitrary cutoff to avoid problems in legacy data
AND i.date >= '2013-01-01'