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
ALTER Table onprc_billing.invoicedItems DROP COLUMN flag;

ALTER Table onprc_billing.invoicedItems ADD credit bit;
ALTER Table onprc_billing.invoicedItems ADD lastName varchar(100);
ALTER Table onprc_billing.invoicedItems ADD firstName varchar(100);
ALTER Table onprc_billing.invoicedItems ADD project int;
ALTER Table onprc_billing.invoicedItems ADD invoiceDate datetime;
ALTER Table onprc_billing.invoicedItems ADD invoiceNumber int;
ALTER Table onprc_billing.invoicedItems ADD transactionType varchar(10);
ALTER Table onprc_billing.invoicedItems ADD department varchar(100);
ALTER Table onprc_billing.invoicedItems ADD mailcode varchar(20);
ALTER Table onprc_billing.invoicedItems ADD contactPhone varchar(30);
ALTER Table onprc_billing.invoicedItems ADD faid int;
ALTER Table onprc_billing.invoicedItems ADD cageId int;
ALTER Table onprc_billing.invoicedItems ADD objectId entityid;

ALTER Table onprc_billing.invoiceRuns ADD runDate datetime;
 