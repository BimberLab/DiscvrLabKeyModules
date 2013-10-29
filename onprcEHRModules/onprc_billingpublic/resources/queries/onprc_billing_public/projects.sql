SELECT
  distinct t.project

FROM onprc_billing_public.publicInvoicedItems t
WHERE t.project is not null