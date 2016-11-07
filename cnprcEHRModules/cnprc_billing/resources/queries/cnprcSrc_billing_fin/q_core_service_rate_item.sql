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
   Csri_pk
  ,Item_code
  ,Description
  ,Service_code
  ,Rc_code
  ,Category
  ,Uom
  ,Active_yn
  ,Item_cost_amt
  ,Objectid
  ,Date_Time
from cnprcSrc_billing_fin.core_service_rate_item