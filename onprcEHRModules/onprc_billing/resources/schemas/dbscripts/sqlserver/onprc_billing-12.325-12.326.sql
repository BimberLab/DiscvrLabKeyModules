/*
 * Copyright (c) 2013 LabKey Corporation
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
ALTER TABLE onprc_billing.invoicedItems ADD transactionNumber int;

ALTER TABLE onprc_billing.miscCharges ADD chargeType int;
ALTER TABLE onprc_billing.miscCharges ADD billingDate datetime;
ALTER TABLE onprc_billing.miscCharges ADD invoiceId entityid;
ALTER TABLE onprc_billing.miscCharges ADD description varchar(4000);
ALTER TABLE onprc_billing.miscCharges DROP COLUMN descrption;