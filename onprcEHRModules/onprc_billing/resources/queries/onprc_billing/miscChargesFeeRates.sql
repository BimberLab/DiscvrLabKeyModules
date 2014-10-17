/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
  mc.*
FROM onprc_billing.miscChargesFeeRateData mc

--note: these are captured by the appropriate rate queries:
WHERE mc.category IS NULL OR mc.category NOT IN (
  'Lease Fees', 'Lease Setup Fees', 'Animal Per Diem',
  'Surgical Procedure', 'Surgery', 'Clinical Procedure', 'Clinical Lab Test', 'Small Animal Per Diem', 'Pathology'
)
