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
SELECT SampleRequests.*,
pickupContacts.SamplePickupContactEmail,
pickupContacts.SamplePickupContactDisplayName,
pickupContacts.SamplePickupContactPhoneNumber,
pickupSecondaryContacts.SamplePickupSecondaryContactEmail,
pickupSecondaryContacts.SamplePickupSecondaryContactDisplayName,
pickupSecondaryContacts.SamplePickupSecondaryContactPhoneNumber,
TissueSubmitInfo.Submitted,
TissueSubmitInfo.SubmittedBy
FROM biotrust.SampleRequests
LEFT JOIN (SELECT SampleId, MAX(Submitted) AS Submitted, MAX(SubmittedBy.DisplayName) AS SubmittedBy
  FROM biotrust.TissueRecordDetails
  GROUP BY SampleId) AS TissueSubmitInfo
    ON SampleRequests.RowId = TissueSubmitInfo.SampleId
LEFT JOIN biotrust.ContactDetailsSamplePickup pickupContacts
  ON SampleRequests.RowId = pickupContacts.SampleRequestId
LEFT JOIN biotrust.ContactDetailsSamplePickupSecondary pickupSecondaryContacts
  ON SampleRequests.RowId = pickupSecondaryContacts.SampleRequestId