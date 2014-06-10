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

SELECT
StudyRegistrations.RowId AS StudyId,
CASE WHEN bUsers.UserId IS NOT NULL THEN bUsers.UserId ELSE bContacts.RowId END AS BudgetContactUserId,
CASE WHEN bUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN bContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END AS BudgetContactType,
CASE WHEN bUsers.Email IS NOT NULL THEN bUsers.Email ELSE bContacts.Email END AS BudgetContactEmail,
CASE WHEN bUsers.FirstName IS NOT NULL THEN bUsers.FirstName ELSE bContacts.FirstName END AS BudgetContactFirstName,
CASE WHEN bUsers.LastName IS NOT NULL THEN bUsers.LastName ELSE bContacts.LastName END AS BudgetContactLastName,
CASE WHEN bUsers.DisplayName IS NOT NULL THEN bUsers.DisplayName ELSE bContacts.DisplayName END AS BudgetContactDisplayName,
CASE WHEN IFDEFINED(bUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(bUsers.AddressStreet1) ELSE bContacts.AddressStreet1 END AS BudgetContactAddressStreet1,
CASE WHEN IFDEFINED(bUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(bUsers.AddressStreet2) ELSE bContacts.AddressStreet2 END AS BudgetContactAddressStreet2,
CASE WHEN IFDEFINED(bUsers.AddressCity) IS NOT NULL THEN IFDEFINED(bUsers.AddressCity) ELSE bContacts.AddressCity END AS BudgetContactAddressCity,
CASE WHEN IFDEFINED(bUsers.AddressState) IS NOT NULL THEN IFDEFINED(bUsers.AddressState) ELSE bContacts.AddressState END AS BudgetContactAddressState,
CASE WHEN IFDEFINED(bUsers.AddressZip) IS NOT NULL THEN IFDEFINED(bUsers.AddressZip) ELSE bContacts.AddressZip END AS BudgetContactAddressZip,
CASE WHEN IFDEFINED(bUsers.Location) IS NOT NULL THEN IFDEFINED(bUsers.Location) ELSE bContacts.Location END AS BudgetContactLocation,
CASE WHEN IFDEFINED(bUsers.Institution) IS NOT NULL THEN IFDEFINED(bUsers.Institution) ELSE bContacts.Institution END AS BudgetContactInstitution,
CASE WHEN IFDEFINED(bUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(bUsers.InstitutionOther) ELSE bContacts.InstitutionOther END AS BudgetContactInstitutionOther,
CASE WHEN IFDEFINED(bUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(bUsers.PhoneNumber) ELSE bContacts.PhoneNumber END AS BudgetContactPhoneNumber,
CASE WHEN IFDEFINED(bUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(bUsers.MobileNumber) ELSE bContacts.MobileNumber END AS BudgetContactMobileNumber,
CASE WHEN IFDEFINED(bUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(bUsers.PagerNumber) ELSE bContacts.PagerNumber END AS BudgetContactPagerNumber
FROM biotrust.StudyRegistrations
LEFT JOIN core.Users bUsers
  ON StudyRegistrations.BudgetContact = bUsers.UserId AND StudyRegistrations.BudgetContactInSystem=true
LEFT JOIN biotrust.Contacts bContacts
  ON StudyRegistrations.BudgetContact = bContacts.RowId AND StudyRegistrations.BudgetContactInSystem=false