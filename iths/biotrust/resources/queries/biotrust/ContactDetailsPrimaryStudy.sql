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
StudyRegistrations.PrimaryStudyContactChoice,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN piUsers.UserId ELSE piContacts.RowId END)
  ELSE (CASE WHEN pscUsers.UserId IS NOT NULL THEN pscUsers.UserId ELSE pscContacts.RowId END)
  END AS PrimaryStudyContactUserId,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN piContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  ELSE (CASE WHEN pscUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN pscContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  END AS PrimaryStudyContactType,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.Email IS NOT NULL THEN piUsers.Email ELSE piContacts.Email END)
  ELSE (CASE WHEN pscUsers.Email IS NOT NULL THEN pscUsers.Email ELSE pscContacts.Email END)
  END AS PrimaryStudyContactEmail,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.FirstName IS NOT NULL THEN piUsers.FirstName ELSE piContacts.FirstName END)
  ELSE (CASE WHEN pscUsers.FirstName IS NOT NULL THEN pscUsers.FirstName ELSE pscContacts.FirstName END)
  END AS PrimaryStudyContactFirstName,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.LastName IS NOT NULL THEN piUsers.LastName ELSE piContacts.LastName END)
  ELSE (CASE WHEN pscUsers.LastName IS NOT NULL THEN pscUsers.LastName ELSE pscContacts.LastName END)
  END AS PrimaryStudyContactLastName,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.DisplayName IS NOT NULL THEN piUsers.DisplayName ELSE piContacts.DisplayName END)
  ELSE (CASE WHEN pscUsers.DisplayName IS NOT NULL THEN pscUsers.DisplayName ELSE pscContacts.DisplayName END)
  END AS PrimaryStudyContactDisplayName,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet1) ELSE piContacts.AddressStreet1 END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(pscUsers.AddressStreet1) ELSE pscContacts.AddressStreet1 END)
  END AS PrimaryStudyContactAddressStreet1,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet2) ELSE piContacts.AddressStreet2 END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(pscUsers.AddressStreet2) ELSE pscContacts.AddressStreet2 END)
  END AS PrimaryStudyContactAddressStreet2,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressCity) IS NOT NULL THEN IFDEFINED(piUsers.AddressCity) ELSE piContacts.AddressCity END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.AddressCity) IS NOT NULL THEN IFDEFINED(pscUsers.AddressCity) ELSE pscContacts.AddressCity END)
  END AS PrimaryStudyContactAddressCity,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressState) IS NOT NULL THEN IFDEFINED(piUsers.AddressState) ELSE piContacts.AddressState END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.AddressState) IS NOT NULL THEN IFDEFINED(pscUsers.AddressState) ELSE pscContacts.AddressState END)
  END AS PrimaryStudyContactAddressState,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressZip) IS NOT NULL THEN IFDEFINED(piUsers.AddressZip) ELSE piContacts.AddressZip END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.AddressZip) IS NOT NULL THEN IFDEFINED(pscUsers.AddressZip) ELSE pscContacts.AddressZip END)
  END AS PrimaryStudyContactAddressZip,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Location) IS NOT NULL THEN IFDEFINED(piUsers.Location) ELSE piContacts.Location END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.Location) IS NOT NULL THEN IFDEFINED(pscUsers.Location) ELSE pscContacts.Location END)
  END AS PrimaryStudyContactLocation,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Institution) IS NOT NULL THEN IFDEFINED(piUsers.Institution) ELSE piContacts.Institution END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.Institution) IS NOT NULL THEN IFDEFINED(pscUsers.Institution) ELSE pscContacts.Institution END)
  END AS PrimaryStudyContactInstitution,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(piUsers.InstitutionOther) ELSE piContacts.InstitutionOther END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(pscUsers.InstitutionOther) ELSE pscContacts.InstitutionOther END)
  END AS PrimaryStudyContactInstitutionOther,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(piUsers.PhoneNumber) ELSE piContacts.PhoneNumber END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(pscUsers.PhoneNumber) ELSE pscContacts.PhoneNumber END)
  END AS PrimaryStudyContactPhoneNumber,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(piUsers.MobileNumber) ELSE piContacts.MobileNumber END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(pscUsers.MobileNumber) ELSE pscContacts.MobileNumber END)
  END AS PrimaryStudyContactMobileNumber,
CASE WHEN StudyRegistrations.PrimaryStudyContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(piUsers.PagerNumber) ELSE piContacts.PagerNumber END)
  ELSE (CASE WHEN IFDEFINED(pscUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(pscUsers.PagerNumber) ELSE pscContacts.PagerNumber END)
  END AS PrimaryStudyContactPagerNumber
FROM biotrust.StudyRegistrations
LEFT JOIN core.Users piUsers
  ON StudyRegistrations.PrincipalInvestigator = piUsers.UserId AND StudyRegistrations.PrincipalInvestigatorInSystem=true
LEFT JOIN biotrust.Contacts piContacts
  ON StudyRegistrations.PrincipalInvestigator = piContacts.RowId AND StudyRegistrations.PrincipalInvestigatorInSystem=false
LEFT JOIN core.Users pscUsers
  ON StudyRegistrations.PrimaryStudyContact = pscUsers.UserId AND StudyRegistrations.PrimaryStudyContactInSystem=true
LEFT JOIN biotrust.Contacts pscContacts
  ON StudyRegistrations.PrimaryStudyContact = pscContacts.RowId AND StudyRegistrations.PrimaryStudyContactInSystem=false