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
SELECT StudyRegistrations.RowId AS StudyId,
Surveys.RowId AS StudyDocumentsQueryId,
Surveys.Status AS StudyStatus,
Surveys.Label AS StudyName,
StudyRegistrations.StudyDescription,
StudyRegistrations.IrbApprovalStatus,
StudyRegistrations.IrbFileNumber,
StudyRegistrations.ProtocolNumber,
StudyRegistrations.IrbExpirationDate,
CASE WHEN StudyRegistrations.ReviewingIrb = 'Other' THEN (StudyRegistrations.ReviewingIrb || ' - ' || StudyRegistrations.ReviewingIrbOther) ELSE StudyRegistrations.ReviewingIrb END AS ReviewingIrb,
StudyRegistrations.AnticipateSubmissionPublicData,
StudyRegistrations.HasConsentForm,
--PrincipalInvestigator contact information
piContacts.PrincipalInvestigatorUserId,
piContacts.PrincipalInvestigatorContactType,
piContacts.PrincipalInvestigatorEmail,
piContacts.PrincipalInvestigatorFirstName,
piContacts.PrincipalInvestigatorLastName,
piContacts.PrincipalInvestigatorDisplayName,
piContacts.PrincipalInvestigatorAddressStreet1,
piContacts.PrincipalInvestigatorAddressStreet2,
piContacts.PrincipalInvestigatorAddressCity,
piContacts.PrincipalInvestigatorAddressState,
piContacts.PrincipalInvestigatorAddressZip,
piContacts.PrincipalInvestigatorLocation,
piContacts.PrincipalInvestigatorInstitution,
piContacts.PrincipalInvestigatorInstitutionOther,
piContacts.PrincipalInvestigatorPhoneNumber,
piContacts.PrincipalInvestigatorMobileNumber,
piContacts.PrincipalInvestigatorPagerNumber,
--  --PrimaryStudyContact contact information
StudyRegistrations.PrimaryStudyContactChoice,
pscContacts.PrimaryStudyContactUserId,
pscContacts.PrimaryStudyContactType,
pscContacts.PrimaryStudyContactEmail,
pscContacts.PrimaryStudyContactFirstName,
pscContacts.PrimaryStudyContactLastName,
pscContacts.PrimaryStudyContactDisplayName,
pscContacts.PrimaryStudyContactAddressStreet1,
pscContacts.PrimaryStudyContactAddressStreet2,
pscContacts.PrimaryStudyContactAddressCity,
pscContacts.PrimaryStudyContactAddressState,
pscContacts.PrimaryStudyContactAddressZip,
pscContacts.PrimaryStudyContactLocation,
pscContacts.PrimaryStudyContactInstitution,
pscContacts.PrimaryStudyContactInstitutionOther,
pscContacts.PrimaryStudyContactPhoneNumber,
pscContacts.PrimaryStudyContactMobileNumber,
pscContacts.PrimaryStudyContactPagerNumber,
-- Billing information
StudyRegistrations.HaveFunding,
-- FundingSourcePI contact information
StudyRegistrations.FundingSourcePI,
fsContacts.FundingSourcePIContactUserId,
fsContacts.FundingSourcePIContactType,
fsContacts.FundingSourcePIContactEmail,
fsContacts.FundingSourcePIContactFirstName,
fsContacts.FundingSourcePIContactLastName,
fsContacts.FundingSourcePIContactDisplayName,
fsContacts.FundingSourcePIContactAddressStreet1,
fsContacts.FundingSourcePIContactAddressStreet2,
fsContacts.FundingSourcePIContactAddressCity,
fsContacts.FundingSourcePIContactAddressState,
fsContacts.FundingSourcePIContactAddressZip,
fsContacts.FundingSourcePIContactLocation,
fsContacts.FundingSourcePIContactInstitution,
fsContacts.FundingSourcePIContactInstitutionOther,
fsContacts.FundingSourcePIContactPhoneNumber,
fsContacts.FundingSourcePIContactMobileNumber,
fsContacts.FundingSourcePIContactPagerNumber,
--  -- Budget contact information
bContacts.BudgetContactUserId,
bContacts.BudgetContactType,
bContacts.BudgetContactEmail,
bContacts.BudgetContactFirstName,
bContacts.BudgetContactLastName,
bContacts.BudgetContactDisplayName,
bContacts.BudgetContactAddressStreet1,
bContacts.BudgetContactAddressStreet2,
bContacts.BudgetContactAddressCity,
bContacts.BudgetContactAddressState,
bContacts.BudgetContactAddressZip,
bContacts.BudgetContactLocation,
bContacts.BudgetContactInstitution,
bContacts.BudgetContactInstitutionOther,
bContacts.BudgetContactPhoneNumber,
bContacts.BudgetContactMobileNumber,
bContacts.BudgetContactPagerNumber,
--  -- other study registration fields
StudyRegistrations.AmountBudgeted,
StudyRegistrations.BudgetSource1Num AS BudgetSource1Number,
StudyRegistrations.BudgetSource1Perc,
StudyRegistrations.BudgetSource1Institution,
StudyRegistrations.BudgetSource1InstitutionOther,
StudyRegistrations.BudgetSource2Num AS BudgetSource2Number,
StudyRegistrations.BudgetSource2Perc,
StudyRegistrations.BudgetSource2Institution,
StudyRegistrations.BudgetSource2InstitutionOther,
StudyRegistrations.BudgetSource3Num AS BudgetSource3Number,
StudyRegistrations.BudgetSource3Perc,
StudyRegistrations.BudgetSource3Institution,
StudyRegistrations.BudgetSource3InstitutionOther,
StudyRegistrations.BillingComments
FROM survey.Surveys
LEFT JOIN biotrust.StudyRegistrations
  ON CONVERT(Surveys.ResponsesPk, SQL_INTEGER) = StudyRegistrations.RowId
LEFT JOIN biotrust.ContactDetailsPrincipalInvestigator piContacts
  ON StudyRegistrations.RowId = piContacts.StudyId
LEFT JOIN biotrust.ContactDetailsPrimaryStudy pscContacts
  ON StudyRegistrations.RowId = pscContacts.StudyId
LEFT JOIN biotrust.ContactDetailsFundingSourcePI fsContacts
  ON StudyRegistrations.RowId = fsContacts.StudyId
LEFT JOIN biotrust.ContactDetailsBudget bContacts
  ON StudyRegistrations.RowId = bContacts.StudyId
WHERE StudyRegistrations.RowId IS NOT NULL AND SurveyDesignId.SchemaName = 'biotrust' AND SurveyDesignId.QueryName = 'StudyRegistrations'