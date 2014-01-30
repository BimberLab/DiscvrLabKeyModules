/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- NOTE: this query provides the raw data used in most of billing *Rates.sql queries.  The goal is to have a single implementation of the
-- process to assign rates and accounts to the raw charges.
PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
  p.*
FROM onprc_billing.miscChargesWithRates p

--we want to capture any unclaimed items from this table prior to the end of the billing period
WHERE cast(p.billingDate as date) >= CAST(StartDate as date) AND cast(p.billingDate as date) <= CAST(EndDate as date)