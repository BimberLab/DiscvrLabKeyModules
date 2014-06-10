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
SampleRequests.RowId AS SampleRequestId,
SampleRequests.SamplePickupPickupContactChoice,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN piUsers.UserId ELSE piContacts.RowId END)
  ELSE (CASE WHEN pickupUsers.UserId IS NOT NULL THEN pickupUsers.UserId ELSE pickupContacts.RowId END)
  END AS SamplePickupContactUserId,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN piContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  ELSE (CASE WHEN pickupUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN pickupContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END)
  END AS SamplePickupContactType,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.Email IS NOT NULL THEN piUsers.Email ELSE piContacts.Email END)
  ELSE (CASE WHEN pickupUsers.Email IS NOT NULL THEN pickupUsers.Email ELSE pickupContacts.Email END)
  END AS SamplePickupContactEmail,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.FirstName IS NOT NULL THEN piUsers.FirstName ELSE piContacts.FirstName END)
  ELSE (CASE WHEN pickupUsers.FirstName IS NOT NULL THEN pickupUsers.FirstName ELSE pickupContacts.FirstName END)
  END AS SamplePickupContactFirstName,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.LastName IS NOT NULL THEN piUsers.LastName ELSE piContacts.LastName END)
  ELSE (CASE WHEN pickupUsers.LastName IS NOT NULL THEN pickupUsers.LastName ELSE pickupContacts.LastName END)
  END AS SamplePickupContactLastName,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN piUsers.DisplayName IS NOT NULL THEN piUsers.DisplayName ELSE piContacts.DisplayName END)
  ELSE (CASE WHEN pickupUsers.DisplayName IS NOT NULL THEN pickupUsers.DisplayName ELSE pickupContacts.DisplayName END)
  END AS SamplePickupContactDisplayName,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet1) ELSE piContacts.AddressStreet1 END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressStreet1) ELSE pickupContacts.AddressStreet1 END)
  END AS SamplePickupContactAddressStreet1,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(piUsers.AddressStreet2) ELSE piContacts.AddressStreet2 END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressStreet2) ELSE pickupContacts.AddressStreet2 END)
  END AS SamplePickupContactAddressStreet2,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressCity) IS NOT NULL THEN IFDEFINED(piUsers.AddressCity) ELSE piContacts.AddressCity END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.AddressCity) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressCity) ELSE pickupContacts.AddressCity END)
  END AS SamplePickupContactAddressCity,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressState) IS NOT NULL THEN IFDEFINED(piUsers.AddressState) ELSE piContacts.AddressState END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.AddressState) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressState) ELSE pickupContacts.AddressState END)
  END AS SamplePickupContactAddressState,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.AddressZip) IS NOT NULL THEN IFDEFINED(piUsers.AddressZip) ELSE piContacts.AddressZip END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.AddressZip) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressZip) ELSE pickupContacts.AddressZip END)
  END AS SamplePickupContactAddressZip,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Location) IS NOT NULL THEN IFDEFINED(piUsers.Location) ELSE piContacts.Location END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.Location) IS NOT NULL THEN IFDEFINED(pickupUsers.Location) ELSE pickupContacts.Location END)
  END AS SamplePickupContactLocation,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.Institution) IS NOT NULL THEN IFDEFINED(piUsers.Institution) ELSE piContacts.Institution END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.Institution) IS NOT NULL THEN IFDEFINED(pickupUsers.Institution) ELSE pickupContacts.Institution END)
  END AS SamplePickupContactInstitution,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(piUsers.InstitutionOther) ELSE piContacts.InstitutionOther END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(pickupUsers.InstitutionOther) ELSE pickupContacts.InstitutionOther END)
  END AS SamplePickupContactInstitutionOther,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(piUsers.PhoneNumber) ELSE piContacts.PhoneNumber END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.PhoneNumber) ELSE pickupContacts.PhoneNumber END)
  END AS SamplePickupContactPhoneNumber,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(piUsers.MobileNumber) ELSE piContacts.MobileNumber END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.MobileNumber) ELSE pickupContacts.MobileNumber END)
  END AS SamplePickupContactMobileNumber,
CASE WHEN SampleRequests.SamplePickupPickupContactChoice = 'Investigator'
  THEN (CASE WHEN IFDEFINED(piUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(piUsers.PagerNumber) ELSE piContacts.PagerNumber END)
  ELSE (CASE WHEN IFDEFINED(pickupUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.PagerNumber) ELSE pickupContacts.PagerNumber END)
  END AS SamplePickupContactPagerNumber
FROM biotrust.SampleRequests
LEFT JOIN biotrust.StudyRegistrationDetails
  ON StudyRegistrationDetails.RowId = SampleRequests.StudyId
LEFT JOIN core.Users piUsers
  ON StudyRegistrationDetails.PrincipalInvestigator = piUsers.UserId AND StudyRegistrationDetails.PrincipalInvestigatorInSystem=true
LEFT JOIN biotrust.Contacts piContacts
  ON StudyRegistrationDetails.PrincipalInvestigator = piContacts.RowId AND StudyRegistrationDetails.PrincipalInvestigatorInSystem=false
LEFT JOIN core.Users pickupUsers
  ON SampleRequests.SamplePickupPickupContact = pickupUsers.UserId AND SampleRequests.SamplePickupPickupContactInSystem=true
LEFT JOIN biotrust.Contacts pickupContacts
  ON SampleRequests.SamplePickupPickupContact = pickupContacts.RowId AND SampleRequests.SamplePickupPickupContactInSystem=false