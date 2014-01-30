/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

SELECT
  mc.Id,
  mc.date,
  mc.billingDate,
  mc.project,
  mc.chargeId,
  mc.item,
  mc.quantity,
  mc.unitCost,
  mc.totalCost,
  mc.category,
  mc.chargeType,
  mc.invoicedItemId,
  mc.objectid as sourceRecord,
  mc.comment,
  mc.debitedaccount,
  mc.creditedaccount,
  mc.sourceInvoicedItem,
  mc.invoiceId,
  mc.taskid,

FROM onprc_billing.miscCharges mc