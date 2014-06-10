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
(CONVERT(Studies.RowId, SQL_VARCHAR) || '-' || CONVERT(IFDEFINED(TissueRecords.StudyRecordId), SQL_VARCHAR)) AS RecordId,
TissueRecords.RowId AS TissueRecordId,
SampleRequests.RowId AS SampleRequestId,
Studies.RowId AS StudyId,
TissueRecords.Submitted,
TissueRecords.Status,
CASE WHEN (TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Residual') THEN CONVERT('DiscardedBloodSample', SQL_VARCHAR) ELSE TissueRecords.RequestType END AS RequestType,
-- tissue record specific fields
CASE WHEN (TissueRecords.RequestType = 'BloodSample') THEN CONVERT('Blood Sample', SQL_VARCHAR)
 WHEN TissueRecords.TissueType = 'Other' THEN (TissueRecords.TissueType || ' - ' || TissueRecords.TissueTypeOther)
 ELSE TissueRecords.TissueType END AS TissueType,
AnatomicalSite.SnomedCode AS AnatomicalSiteSnomedCode,
CASE WHEN TissueRecords.RequestType = 'BloodSample' AND TissueRecords.BloodSampleType = 'Surgery' AND SampleRequests.SurgicalPairWithBlood = TRUE THEN CONVERT('Paired Blood', SQL_VARCHAR)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN CONVERT('Blood', SQL_VARCHAR)
  ELSE TissueRecords.AnatomicalSite END AS AnatomicalSite,
TissueRecords.AnatomicalSiteOther,
CASE WHEN TissueRecords.RequestType = 'BloodSample' AND TissueRecords.HoldAtLocation = 'Other' THEN (TissueRecords.HoldAtLocation || ' - ' || TissueRecords.HoldAtLocationOther)
  WHEN TissueRecords.RequestType = 'BloodSample' THEN TissueRecords.HoldAtLocation
  WHEN TissueRecords.Preservation = 'Other' THEN (TissueRecords.Preservation || ' - ' || TissueRecords.PreservationOther)
  ELSE TissueRecords.Preservation END AS Preservation,
TissueRecords.MinimumSize,
TissueRecords.MinimumSizeUnits,
TissueRecords.PreferredSize,
TissueRecords.PreferredSizeUnits,
CASE WHEN TissueRecords.TubeType = 'Other' THEN (TissueRecords.TubeType || ' - ' || TissueRecords.TubeTypeOther) ELSE TissueRecords.TubeType END AS TubeType,
TissueRecords.BloodSampleType,
CASE WHEN TissueRecords.HoldAtLocation = 'Other' THEN (TissueRecords.HoldAtLocation || ' - ' || TissueRecords.HoldAtLocationOther) ELSE TissueRecords.HoldAtLocation END AS HoldAt,
TissueRecords.TissueRequireSerial,
TissueRecords.SamplesPerCase,
TissueRecords.BloodRequireSerial,
TissueRecords.BloodPreferedTiming,
TissueRecords.BloodMaxDrawTime,
TissueRecords.Notes,
-- sample request wizard fields
SampleRequests.SurgicalPairWithBlood,
SampleRequests.TissueBloodNotCollected,
SampleRequests.SpecimenTypeInformation,
SampleRequests.CollectionStartDate,
SampleRequests.CollectionStartASAP,
SampleRequests.CollectionEndDate,
SampleRequests.CollectionEndOngoing,
SampleRequests.TissueAdditionalInformation,
SampleRequests.BloodAdditionalInformation,
SampleRequests.TotalSpecimenDonors,
SampleRequests.TotalSpecimenDonorsNA,
SampleRequests.GenderRequirements,
SampleRequests.AgeRequirements,
SampleRequests.RaceRequirements,
SampleRequests.HistologicalDiagnosisRequirements,
SampleRequests.BioMarkerRequirements,
SampleRequests.OtherRequirements,
SampleRequests.CancerHistoryRelevant,
SampleRequests.ExclusionPriorCancer,
SampleRequests.ExclusionYearsPostTherapy,
SampleRequests.ExclusionPriorCancerOther,
SampleRequests.ExclusionNeoadjuvantTherapy,
SampleRequests.ExclusionCarcinomaOkay,
SampleRequests.ExclusionOtherCriteria,
-- sample pickup specific fields
SampleRequests.SamplePickupRequestFreshDraw,
SampleRequests.SamplePickupArrangeForPickup,
SampleRequests.SamplePickupHoldOvernight,
SampleRequests.SamplePickupNotes,
SampleRequests.SamplePickupPickupContactChoice,
pickupContacts.SamplePickupContactUserId,
pickupContacts.SamplePickupContactType,
pickupContacts.SamplePickupContactEmail,
pickupContacts.SamplePickupContactFirstName,
pickupContacts.SamplePickupContactLastName,
pickupContacts.SamplePickupContactDisplayName,
pickupContacts.SamplePickupContactAddressStreet1,
pickupContacts.SamplePickupContactAddressStreet2,
pickupContacts.SamplePickupContactAddressCity,
pickupContacts.SamplePickupContactAddressState,
pickupContacts.SamplePickupContactAddressZip,
pickupContacts.SamplePickupContactLocation,
pickupContacts.SamplePickupContactInstitution,
pickupContacts.SamplePickupContactInstitutionOther,
pickupContacts.SamplePickupContactPhoneNumber,
pickupContacts.SamplePickupContactMobileNumber,
pickupContacts.SamplePickupContactPagerNumber,
-- secondary sample pickup fields
pickupSecondaryContacts.SamplePickupSecondaryContactUserId,
pickupSecondaryContacts.SamplePickupSecondaryContactType,
pickupSecondaryContacts.SamplePickupSecondaryContactEmail,
pickupSecondaryContacts.SamplePickupSecondaryContactFirstName,
pickupSecondaryContacts.SamplePickupSecondaryContactLastName,
pickupSecondaryContacts.SamplePickupSecondaryContactDisplayName,
pickupSecondaryContacts.SamplePickupSecondaryContactAddressStreet1,
pickupSecondaryContacts.SamplePickupSecondaryContactAddressStreet2,
pickupSecondaryContacts.SamplePickupSecondaryContactAddressCity,
pickupSecondaryContacts.SamplePickupSecondaryContactAddressState,
pickupSecondaryContacts.SamplePickupSecondaryContactAddressZip,
pickupSecondaryContacts.SamplePickupSecondaryContactLocation,
pickupSecondaryContacts.SamplePickupSecondaryContactInstitution,
pickupSecondaryContacts.SamplePickupSecondaryContactInstitutionOther,
pickupSecondaryContacts.SamplePickupSecondaryContactPhoneNumber,
pickupSecondaryContacts.SamplePickupSecondaryContactMobileNumber,
pickupSecondaryContacts.SamplePickupSecondaryContactPagerNumber
FROM TissueRecordDetails AS TissueRecords
LEFT JOIN SampleRequests
  ON SampleRequests.RowId = TissueRecords.SampleId
LEFT JOIN StudyRegistrationDetails AS Studies
  ON SampleRequests.StudyId = Studies.RowId
LEFT JOIN Lists.AnatomicalSite AS AnatomicalSite
  ON (AnatomicalSite.Name = TissueRecords.AnatomicalSite OR (AnatomicalSite.Name = 'Blood' AND TissueRecords.RequestType = 'BloodSample'))
LEFT JOIN biotrust.ContactDetailsSamplePickup pickupContacts
  ON SampleRequests.RowId = pickupContacts.SampleRequestId
LEFT JOIN biotrust.ContactDetailsSamplePickupSecondary pickupSecondaryContacts
  ON SampleRequests.RowId = pickupSecondaryContacts.SampleRequestId
WHERE Studies.RowId IS NOT NULL AND TissueRecords.Submitted IS NOT NULL
