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
package org.labkey.biotrust.query.samples;

import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.biotrust.BioTrustSchema;
import org.labkey.biotrust.query.BioTrustProjectRootDomainKind;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: klum
 * Date: 2/15/13
 */
public class SampleRequestDomain extends BioTrustProjectRootDomainKind
{
    public static final String NAME = "SampleRequestDomain";
    public static String NAMESPACE_PREFIX = "NWBioTrust-" + NAME;

    public static final String SAMPLE_PICKUP_CONTACT_PROPERTY_NAME = "SamplePickupPickupContact";
    public static final String SAMPLE_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME = SAMPLE_PICKUP_CONTACT_PROPERTY_NAME + "InSystem";
    public static final String SECONDARY_PICKUP_CONTACT_PROPERTY_NAME = "SamplePickupSecondaryContact";
    public static final String SECONDARY_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME = SECONDARY_PICKUP_CONTACT_PROPERTY_NAME + "InSystem";
    public static final Set<String> ALWAYS_EDITABLE_PROPERTY_NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private static final Set<PropertyStorageSpec> _baseFields = new LinkedHashSet<>();
    private static final Set<String> _reservedNames = new HashSet<>();

    static {
        _baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));       // pk
        _baseFields.add(createFieldSpec("Container", JdbcType.VARCHAR));
        _baseFields.add(createFieldSpec("Created", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("CreatedBy", JdbcType.INTEGER));
        _baseFields.add(createFieldSpec("Modified", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("ModifiedBy", JdbcType.INTEGER));

        // sample information
        _reservedNames.add("StudyId");
        _reservedNames.add("RequireSurgicalTissue");
        _reservedNames.add("RequireNonsurgicalTissue");
        _reservedNames.add("RequireAppointmentsBlood");
        _reservedNames.add("RequireDiscardedBlood");
        _reservedNames.add("RequireFFPETissue");
        _reservedNames.add("RequireArchivedSamples");
        _reservedNames.add("SurgicalPairWithBlood");
        _reservedNames.add("TissueBloodNotCollected");
        _reservedNames.add("SpecimenTypeInformation");
        _reservedNames.add("CollectionStartDate");
        _reservedNames.add("CollectionStartASAP");
        _reservedNames.add("CollectionEndDate");
        _reservedNames.add("CollectionEndOngoing");
        // focus of study
        _reservedNames.add("DiseaseType");
        _reservedNames.add("DiseaseTypeAdditionalInformation");
        // general population requirements
        _reservedNames.add("TotalSpecimenDonors");
        _reservedNames.add("TotalSpecimenDonorsNA");
        _reservedNames.add("GenderRequirements");
        _reservedNames.add("AgeRequirements");
        _reservedNames.add("RaceRequirements");
        _reservedNames.add("HistologicalDiagnosisRequirements");
        _reservedNames.add("BioMarkerRequirements");
        _reservedNames.add("OtherRequirements");
        _reservedNames.add("CancerHistoryRelevant");
        _reservedNames.add("ExclusionPriorCancer");
        _reservedNames.add("ExclusionYearsPostTherapy");
        _reservedNames.add("ExclusionPriorCancerOther");
        _reservedNames.add("ExclusionNeoadjuvantTherapy");
        _reservedNames.add("ExclusionCarcinomaOkay");
        _reservedNames.add("ExclusionOtherCriteria");
        // tissue samples
        _reservedNames.add("ProtocolDocumentTissue");
        _reservedNames.add("TissueAdditionalInformation");
        // blood samples
        _reservedNames.add("ProtocolDocumentBlood");
        _reservedNames.add("BloodAdditionalInformation");
        // sample pickup
        _reservedNames.add("SamplePickupRequestFreshDraw");
        _reservedNames.add("SamplePickupArrangeForPickup");
        _reservedNames.add("SamplePickupHoldOvernight");
        _reservedNames.add("SamplePickupPickupContactChoiceRadioGroup");
        _reservedNames.add("SamplePickupPickupContactChoice");
        _reservedNames.add(SAMPLE_PICKUP_CONTACT_PROPERTY_NAME);
        _reservedNames.add(SAMPLE_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
        _reservedNames.add(SECONDARY_PICKUP_CONTACT_PROPERTY_NAME);
        _reservedNames.add(SECONDARY_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
        _reservedNames.add("SamplePickupNotes");

        ALWAYS_EDITABLE_PROPERTY_NAMES.add("ProtocolDocumentTissue");
        ALWAYS_EDITABLE_PROPERTY_NAMES.add("ProtocolDocumentBlood");
        ALWAYS_EDITABLE_PROPERTY_NAMES.add("SamplePickupPickupContactChoiceRadioGroup");
        ALWAYS_EDITABLE_PROPERTY_NAMES.add("SamplePickupPickupContactChoice");
        ALWAYS_EDITABLE_PROPERTY_NAMES.add(SAMPLE_PICKUP_CONTACT_PROPERTY_NAME);
        ALWAYS_EDITABLE_PROPERTY_NAMES.add(SAMPLE_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
        ALWAYS_EDITABLE_PROPERTY_NAMES.add(SECONDARY_PICKUP_CONTACT_PROPERTY_NAME);
        ALWAYS_EDITABLE_PROPERTY_NAMES.add(SECONDARY_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME);
        ALWAYS_EDITABLE_PROPERTY_NAMES.add("SamplePickupNotes");
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return _baseFields;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return _reservedNames;
    }

    @Override
    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public void ensureDomainProperties(Domain domain, User user)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            boolean dirty = false;

            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "StudyId", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireSurgicalTissue", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireNonsurgicalTissue", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireAppointmentsBlood", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireDiscardedBlood", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireFFPETissue", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequireArchivedSamples", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SurgicalPairWithBlood", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TissueBloodNotCollected", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SpecimenTypeInformation", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "CollectionStartDate", PropertyType.DATE_TIME) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "CollectionStartASAP", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "CollectionEndDate", PropertyType.DATE_TIME) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "CollectionEndOngoing", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "DiseaseType", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "DiseaseTypeAdditionalInformation", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TotalSpecimenDonors", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TotalSpecimenDonorsNA", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "GenderRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AgeRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RaceRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "HistologicalDiagnosisRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BioMarkerRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "OtherRequirements", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "CancerHistoryRelevant", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionPriorCancer", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionYearsPostTherapy", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionPriorCancerOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionNeoadjuvantTherapy", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionCarcinomaOkay", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ExclusionOtherCriteria", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TissueAdditionalInformation", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BloodAdditionalInformation", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplePickupRequestFreshDraw", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplePickupArrangeForPickup", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplePickupHoldOvernight", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplePickupPickupContactChoice", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, SAMPLE_PICKUP_CONTACT_PROPERTY_NAME, PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, SAMPLE_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME, PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, SECONDARY_PICKUP_CONTACT_PROPERTY_NAME, PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, SECONDARY_PICKUP_CONTACT_INSYSTEM_PROPERTY_NAME, PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplePickupNotes", PropertyType.MULTI_LINE) || dirty;

            if (dirty)
                domain.save(user);

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
