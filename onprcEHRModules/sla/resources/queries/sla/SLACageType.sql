/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

  /*   Created  Blasa    2-13-2015  Provide Distinct SLA Cage Type Reference table */

  select    distinct  cagetype
   from  onprc_billing.slaPerDiemFeeDefinition b
   Where active = True
   order by cagetype



