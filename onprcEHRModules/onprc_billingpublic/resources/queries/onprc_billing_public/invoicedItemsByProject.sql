SELECT
  i.invoiceId,
  i.project,
  sum(i.quantity) as numItems,
  sum(i.totalCost) as total,
  ' [Summary By Item]' as summaryByItem,
  ' [All Items]' as allItems

FROM onprc_billing_public.publicInvoicedItems i

GROUP BY i.invoiceId, i.project