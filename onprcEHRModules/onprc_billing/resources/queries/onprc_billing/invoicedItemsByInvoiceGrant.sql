SELECT
  i.invoiceId,
  i.debitedaccount.grantNumber,

  count(i.invoiceId) as numItems,
  sum(i.totalCost) as total

FROM onprc_billing.invoicedItems i

GROUP BY i.invoiceId, i.debitedaccount.grantNumber