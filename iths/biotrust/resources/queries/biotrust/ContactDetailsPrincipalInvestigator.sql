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
CASE WHEN piUsers.UserId IS NOT NULL THEN piUsers.UserId ELSE piContacts.RowId END AS PrincipalInvestigatorUserId,
CASE WHEN piUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN piContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END AS PrincipalInvestigatorContactType,
CASE WHEN piUsers.Email IS NOT NULL THEN piUsers.Email ELSE piContacts.Email END AS PrincipalInvestigatorEmail,
CASE WHEN piUsers.FirstName IS NOT NULL THEN piUsers.FirstName ELSE piContacts.FirstName END AS PrincipalInvestigatorFirstName,
CASE WHEN piUsers.LastName IS NOT NULL THEN piUsers.LastName ELSE piContacts.LastName END AS PrincipalInvestigatorLastName,
CASE WHEN piUsers.DisplayName IS NOT NULL THEN piUsers.DisplayName ELSE piContacts.DisplayName END AS PrincipalInvestigatorDisplayName,
CASE WHEN IFDEFINED(piUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet1) ELSE piContacts.AddressStreet1 END AS PrincipalInvestigatorAddressStreet1,
CASE WHEN IFDEFINED(piUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet2) ELSE piContacts.AddressStreet2 END AS PrincipalInvestigatorAddressStreet2,
CASE WHEN IFDEFINED(piUsers.AddressCity) IS NOT NULL THEN IFDEFINED(piUsers.AddressCity) ELSE piContacts.AddressCity END AS PrincipalInvestigatorAddressCity,
CASE WHEN IFDEFINED(piUsers.AddressState) IS NOT NULL THEN IFDEFINED(piUsers.AddressState) ELSE piContacts.AddressState END AS PrincipalInvestigatorAddressState,
CASE WHEN IFDEFINED(piUsers.AddressZip) IS NOT NULL THEN IFDEFINED(piUsers.AddressZip) ELSE piContacts.AddressZip END AS PrincipalInvestigatorAddressZip,
CASE WHEN IFDEFINED(piUsers.Location) IS NOT NULL THEN IFDEFINED(piUsers.Location) ELSE piContacts.Location END AS PrincipalInvestigatorLocation,
CASE WHEN IFDEFINED(piUsers.Institution) IS NOT NULL THEN IFDEFINED(piUsers.Institution) ELSE piContacts.Institution END AS PrincipalInvestigatorInstitution,
CASE WHEN IFDEFINED(piUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(piUsers.InstitutionOther) ELSE piContacts.InstitutionOther END AS PrincipalInvestigatorInstitutionOther,
CASE WHEN IFDEFINED(piUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(piUsers.PhoneNumber) ELSE piContacts.PhoneNumber END AS PrincipalInvestigatorPhoneNumber,
CASE WHEN IFDEFINED(piUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(piUsers.MobileNumber) ELSE piContacts.MobileNumber END AS PrincipalInvestigatorMobileNumber,
CASE WHEN IFDEFINED(piUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(piUsers.PagerNumber) ELSE piContacts.PagerNumber END AS PrincipalInvestigatorPagerNumber
FROM biotrust.StudyRegistrations
LEFT JOIN core.Users piUsers
  ON StudyRegistrations.PrincipalInvestigator = piUsers.UserId AND StudyRegistrations.PrincipalInvestigatorInSystem=true
LEFT JOIN biotrust.Contacts piContacts
  ON StudyRegistrations.PrincipalInvestigator = piContacts.RowId AND StudyRegistrations.PrincipalInvestigatorInSystem=false