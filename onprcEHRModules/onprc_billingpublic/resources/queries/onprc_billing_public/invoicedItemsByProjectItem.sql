SELECT
  i.invoiceId,
  i.project,
  i.item,
  sum(i.quantity) as numItems,
  sum(i.totalCost) as total

FROM onprc_billing_public.publicInvoicedItems i

GROUP BY i.invoiceId, i.project, i.item