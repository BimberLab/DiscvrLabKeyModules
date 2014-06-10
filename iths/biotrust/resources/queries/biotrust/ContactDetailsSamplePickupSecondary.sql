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
CASE WHEN pickupUsers.UserId IS NOT NULL THEN pickupUsers.UserId ELSE pickupContacts.RowId END AS SamplePickupSecondaryContactUserId,
CASE WHEN pickupUsers.UserId IS NOT NULL THEN CONVERT('InSystem', SQL_VARCHAR) WHEN pickupContacts.RowId IS NOT NULL THEN CONVERT('OutOfSystem', SQL_VARCHAR) END AS SamplePickupSecondaryContactType,
CASE WHEN pickupUsers.Email IS NOT NULL THEN pickupUsers.Email ELSE pickupContacts.Email END AS SamplePickupSecondaryContactEmail,
CASE WHEN pickupUsers.FirstName IS NOT NULL THEN pickupUsers.FirstName ELSE pickupContacts.FirstName END AS SamplePickupSecondaryContactFirstName,
CASE WHEN pickupUsers.LastName IS NOT NULL THEN pickupUsers.LastName ELSE pickupContacts.LastName END AS SamplePickupSecondaryContactLastName,
CASE WHEN pickupUsers.DisplayName IS NOT NULL THEN pickupUsers.DisplayName ELSE pickupContacts.DisplayName END AS SamplePickupSecondaryContactDisplayName,
CASE WHEN IFDEFINED(pickupUsers.AddressStreet1) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressStreet1) ELSE pickupContacts.AddressStreet1 END AS SamplePickupSecondaryContactAddressStreet1,
CASE WHEN IFDEFINED(pickupUsers.AddressStreet2) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressStreet2) ELSE pickupContacts.AddressStreet2 END AS SamplePickupSecondaryContactAddressStreet2,
CASE WHEN IFDEFINED(pickupUsers.AddressCity) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressCity) ELSE pickupContacts.AddressCity END AS SamplePickupSecondaryContactAddressCity,
CASE WHEN IFDEFINED(pickupUsers.AddressState) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressState) ELSE pickupContacts.AddressState END AS SamplePickupSecondaryContactAddressState,
CASE WHEN IFDEFINED(pickupUsers.AddressZip) IS NOT NULL THEN IFDEFINED(pickupUsers.AddressZip) ELSE pickupContacts.AddressZip END AS SamplePickupSecondaryContactAddressZip,
CASE WHEN IFDEFINED(pickupUsers.Location) IS NOT NULL THEN IFDEFINED(pickupUsers.Location) ELSE pickupContacts.Location END AS SamplePickupSecondaryContactLocation,
CASE WHEN IFDEFINED(pickupUsers.Institution) IS NOT NULL THEN IFDEFINED(pickupUsers.Institution) ELSE pickupContacts.Institution END AS SamplePickupSecondaryContactInstitution,
CASE WHEN IFDEFINED(pickupUsers.InstitutionOther) IS NOT NULL THEN IFDEFINED(pickupUsers.InstitutionOther) ELSE pickupContacts.InstitutionOther END AS SamplePickupSecondaryContactInstitutionOther,
CASE WHEN IFDEFINED(pickupUsers.PhoneNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.PhoneNumber) ELSE pickupContacts.PhoneNumber END AS SamplePickupSecondaryContactPhoneNumber,
CASE WHEN IFDEFINED(pickupUsers.MobileNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.MobileNumber) ELSE pickupContacts.MobileNumber END AS SamplePickupSecondaryContactMobileNumber,
CASE WHEN IFDEFINED(pickupUsers.PagerNumber) IS NOT NULL THEN IFDEFINED(pickupUsers.PagerNumber) ELSE pickupContacts.PagerNumber END AS SamplePickupSecondaryContactPagerNumber
FROM biotrust.SampleRequests
LEFT JOIN core.Users pickupUsers
  ON SampleRequests.SamplePickupSecondaryContact = pickupUsers.UserId AND SampleRequests.SamplePickupSecondaryContactInSystem=true
LEFT JOIN biotrust.Contacts pickupContacts
  ON SampleRequests.SamplePickupSecondaryContact = pickupContacts.RowId AND SampleRequests.SamplePickupSecondaryContactInSystem=false