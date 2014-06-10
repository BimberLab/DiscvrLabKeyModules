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
StudyRegistrations.FundingSourcePI,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN piUsers.UserId ELSE piContacts.RowId END)
  ELSE (CASE WHEN fsUsers.UserId IS NOT NULL THEN fsUsers.UserId ELSE fsContacts.RowId END)
  END AS FundingSourcePIContactUserId,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN piContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  ELSE (CASE WHEN fsUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN fsContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  END AS FundingSourcePIContactType,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.Email IS NOT NULL THEN piUsers.Email ELSE piContacts.Email END)
  ELSE (CASE WHEN fsUsers.Email IS NOT NULL THEN fsUsers.Email ELSE fsContacts.Email END)
  END AS FundingSourcePIContactEmail,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.FirstName IS NOT NULL THEN piUsers.FirstName ELSE piContacts.FirstName END)
  ELSE (CASE WHEN fsUsers.FirstName IS NOT NULL THEN fsUsers.FirstName ELSE fsContacts.FirstName END)
  END AS FundingSourcePIContactFirstName,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.LastName IS NOT NULL THEN piUsers.LastName ELSE piContacts.LastName END)
  ELSE (CASE WHEN fsUsers.LastName IS NOT NULL THEN fsUsers.LastName ELSE fsContacts.LastName END)
  END AS FundingSourcePIContactLastName,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN piUsers.DisplayName IS NOT NULL THEN piUsers.DisplayName ELSE piContacts.DisplayName END)
  ELSE (CASE WHEN fsUsers.DisplayName IS NOT NULL THEN fsUsers.DisplayName ELSE fsContacts.DisplayName END)
  END AS FundingSourcePIContactDisplayName,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet1) ELSE piContacts.AddressStreet1 END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(fsUsers.AddressStreet1) ELSE fsContacts.AddressStreet1 END)
  END AS FundingSourcePIContactAddressStreet1,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet2) ELSE piContacts.AddressStreet2 END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(fsUsers.AddressStreet2) ELSE fsContacts.AddressStreet2 END)
  END AS FundingSourcePIContactAddressStreet2,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressCity) IS NOT NULL THEN IFDEFINED(piUsers.AddressCity) ELSE piContacts.AddressCity END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.AddressCity) IS NOT NULL THEN IFDEFINED(fsUsers.AddressCity) ELSE fsContacts.AddressCity END)
  END AS FundingSourcePIContactAddressCity,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressState) IS NOT NULL THEN IFDEFINED(piUsers.AddressState) ELSE piContacts.AddressState END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.AddressState) IS NOT NULL THEN IFDEFINED(fsUsers.AddressState) ELSE fsContacts.AddressState END)
  END AS FundingSourcePIContactAddressState,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressZip) IS NOT NULL THEN IFDEFINED(piUsers.AddressZip) ELSE piContacts.AddressZip END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.AddressZip) IS NOT NULL THEN IFDEFINED(fsUsers.AddressZip) ELSE fsContacts.AddressZip END)
  END AS FundingSourcePIContactAddressZip,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Location) IS NOT NULL THEN IFDEFINED(piUsers.Location) ELSE piContacts.Location END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.Location) IS NOT NULL THEN IFDEFINED(fsUsers.Location) ELSE fsContacts.Location END)
  END AS FundingSourcePIContactLocation,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Institution) IS NOT NULL THEN IFDEFINED(piUsers.Institution) ELSE piContacts.Institution END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.Institution) IS NOT NULL THEN IFDEFINED(fsUsers.Institution) ELSE fsContacts.Institution END)
  END AS FundingSourcePIContactInstitution,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(piUsers.InstitutionOther) ELSE piContacts.InstitutionOther END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(fsUsers.InstitutionOther) ELSE fsContacts.InstitutionOther END)
  END AS FundingSourcePIContactInstitutionOther,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(piUsers.PhoneNumber) ELSE piContacts.PhoneNumber END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(fsUsers.PhoneNumber) ELSE fsContacts.PhoneNumber END)
  END AS FundingSourcePIContactPhoneNumber,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(piUsers.MobileNumber) ELSE piContacts.MobileNumber END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(fsUsers.MobileNumber) ELSE fsContacts.MobileNumber END)
  END AS FundingSourcePIContactMobileNumber,
CASE WHEN StudyRegistrations.FundingSourcePI = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(piUsers.PagerNumber) ELSE piContacts.PagerNumber END)
  ELSE (CASE WHEN IFDEFINED(fsUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(fsUsers.PagerNumber) ELSE fsContacts.PagerNumber END)
  END AS FundingSourcePIContactPagerNumber
FROM biotrust.StudyRegistrations
LEFT JOIN core.Users piUsers
  ON StudyRegistrations.PrincipalInvestigator = piUsers.UserId AND StudyRegistrations.PrincipalInvestigatorInSystem=true
LEFT JOIN biotrust.Contacts piContacts
  ON StudyRegistrations.PrincipalInvestigator = piContacts.RowId AND StudyRegistrations.PrincipalInvestigatorInSystem=false
LEFT JOIN core.Users fsUsers
  ON StudyRegistrations.FundingSourcePIContact = fsUsers.UserId AND StudyRegistrations.FundingSourcePIContactInSystem=true
LEFT JOIN biotrust.Contacts fsContacts
  ON StudyRegistrations.FundingSourcePIContact = fsContacts.RowId AND StudyRegistrations.FundingSourcePIContactInSystem=false