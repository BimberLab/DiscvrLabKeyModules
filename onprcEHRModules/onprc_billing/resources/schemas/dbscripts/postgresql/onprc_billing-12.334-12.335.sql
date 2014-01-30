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
ALTER TABLE onprc_billing.grantProjects ADD protocolNumber Varchar(100);
ALTER TABLE onprc_billing.grantProjects ADD projectStatus Varchar(100);
ALTER TABLE onprc_billing.grantProjects ADD aliasEnabled Varchar(100);
ALTER TABLE onprc_billing.grantProjects ADD ogaProjectId int;

ALTER TABLE onprc_billing.grantProjects DROP COLUMN spid;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN currentDCBudget;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN currentFABudget;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN totalDCBudget;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN totalFABudget;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN awardStartDate;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN awardEndDate;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN currentYear;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN totalYears;
ALTER TABLE onprc_billing.grantProjects DROP COLUMN awardSuffix;

ALTER TABLE onprc_billing.grants ADD awardStatus Varchar(100);
ALTER TABLE onprc_billing.grants ADD applicationType Varchar(100);
ALTER TABLE onprc_billing.grants ADD activityType Varchar(100);

ALTER TABLE onprc_billing.grants ADD ogaAwardId int;

ALTER TABLE onprc_billing.fiscalAuthorities ADD employeeId varchar(100);