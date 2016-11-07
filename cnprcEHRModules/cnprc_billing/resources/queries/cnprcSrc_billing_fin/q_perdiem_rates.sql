/*
 * Copyright (c) 2016 LabKey Corporation
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
  PR_pk                   AS Rate_pk
  ,PR_Location_rate_class AS Location_rate_class
  ,PR_Component_01        AS Component_01
  ,PR_Component_02        AS Component_02
  ,PR_Component_03        AS Component_03
  ,PR_Component_04        AS Component_04
  ,PR_Component_05        AS Component_05
  ,PR_Component_06        AS Component_06
  ,PR_Component_07        AS Component_07
  ,PR_Component_08        AS Component_08
  ,PR_Component_09        AS Component_09
  ,PR_Component_10        AS Component_10
  ,PR_Component_11        AS Component_11
  ,PR_Component_12        AS Component_12
  ,PR_Component_13        AS Component_13
  ,PR_Component_14        AS Component_14
  ,PR_Component_15        AS Component_15
  ,PR_Component_16        AS Component_16
  ,PR_Daily_rate          AS Daily_rate
  ,PR_Start_date          AS Start_date
  ,PR_End_date            AS End_date
  ,PR_Comment             AS Comments
  ,PR_Rate_tier_code_fk   AS Rate_tier_code_fk
  ,Objectid
  ,Date_Time
from cnprcSrc_billing_fin.zperdiem_rates