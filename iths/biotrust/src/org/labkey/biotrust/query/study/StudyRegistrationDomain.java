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
package org.labkey.biotrust.query.study;

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

/**
 * User: cnathe
 * Date: 2/18/13
 */
public class StudyRegistrationDomain extends BioTrustProjectRootDomainKind
{
    public static final String NAME = "StudyRegistrationDomain";
    public static final String PRINCIPAL_INVESTIGATOR_PROPERTY_NAME = "PrincipalInvestigator";
    public static final String PRINCIPAL_INVESTIGATOR_INSYSTEM_PROPERTY_NAME = PRINCIPAL_INVESTIGATOR_PROPERTY_NAME + "InSystem";
    public static final String PRIMARY_STUDY_CONTACT_PROPERTY_NAME = "PrimaryStudyContact";
    public static final String PRIMARY_STUDY_CONTACT_CHOICE_PROPERTY_NAME = PRIMARY_STUDY_CONTACT_PROPERTY_NAME + "Choice";
    public static final String PRIMARY_STUDY_CONTACT_INSYSTEM_PROPERTY_NAME = PRIMARY_STUDY_CONTACT_PROPERTY_NAME + "InSystem";
    public static final String BUDGET_CONTACT_PROPERTY_NAME = "BudgetContact";
    public static final String BUDGET_CONTACT_INSYSTEM_PROPERTY_NAME = BUDGET_CONTACT_PROPERTY_NAME + "InSystem";

    public static String NAMESPACE_PREFIX = "NWBioTrust-" + NAME;

    private static final Set<PropertyStorageSpec> _baseFields = new LinkedHashSet<>();
    private static final Set<String> _reservedNames = new HashSet<>();

    static {
        _baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));       // pk
        _baseFields.add(createFieldSpec("Container", JdbcType.VARCHAR));
        _baseFields.add(createFieldSpec("Created", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("CreatedBy", JdbcType.INTEGER));
        _baseFields.add(createFieldSpec("Modified", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("ModifiedBy", JdbcType.INTEGER));

        _reservedNames.add("StudyId");
        _reservedNames.add("StudyDescription");
        _reservedNames.add("IrbApprovalStatus");
        _reservedNames.add("IrbFileNumber");
        _reservedNames.add("ProtocolNumber");
        _reservedNames.add("IrbExpirationDate");
        _reservedNames.add("ReviewingIrb");
        _reservedNames.add("ReviewingIrbOther");
        _reservedNames.add("AnticipateSubmissionPublicData");
        _reservedNames.add("HasConsentForm");
        _reservedNames.add(PRINCIPAL_INVESTIGATOR_PROPERTY_NAME);
        _reservedNames.add(PRINCIPAL_INVESTIGATOR_INSYSTEM_PROPERTY_NAME);
        _reservedNames.add(PRIMARY_STUDY_CONTACT_CHOICE_PROPERTY_NAME);
        _reservedNames.add(PRIMARY_STUDY_CONTACT_PROPERTY_NAME);
        _reservedNames.add(PRIMARY_STUDY_CONTACT_INSYSTEM_PROPERTY_NAME);
        _reservedNames.add("HaveFunding");
        _reservedNames.add("FundingSourcePI");
        _reservedNames.add("FundingSourcePIContact");
        _reservedNames.add("FundingSourcePIContactInSystem");
        _reservedNames.add(BUDGET_CONTACT_PROPERTY_NAME);
        _reservedNames.add(BUDGET_CONTACT_INSYSTEM_PROPERTY_NAME);
        _reservedNames.add("AmountBudgeted");
        _reservedNames.add("BudgetSource1Num");
        _reservedNames.add("BudgetSource1Perc");
        _reservedNames.add("BudgetSource1Institution");
        _reservedNames.add("BudgetSource1InstitutionOther");
        _reservedNames.add("BudgetSource2Num");
        _reservedNames.add("BudgetSource2Perc");
        _reservedNames.add("BudgetSource2Institution");
        _reservedNames.add("BudgetSource2InstitutionOther");
        _reservedNames.add("BudgetSource3Num");
        _reservedNames.add("BudgetSource3Perc");
        _reservedNames.add("BudgetSource3Institution");
        _reservedNames.add("BudgetSource3InstitutionOther");
        _reservedNames.add("BillingComments");
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

            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "StudyDescription", PropertyType.MULTI_LINE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "IrbApprovalStatus", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "IrbFileNumber", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ProtocolNumber", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "IrbExpirationDate", PropertyType.DATE_TIME) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ReviewingIrb", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "ReviewingIrbOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AnticipateSubmissionPublicData", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "HasConsentForm", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, PRINCIPAL_INVESTIGATOR_INSYSTEM_PROPERTY_NAME, PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, PRINCIPAL_INVESTIGATOR_PROPERTY_NAME, PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, PRIMARY_STUDY_CONTACT_INSYSTEM_PROPERTY_NAME, PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, PRIMARY_STUDY_CONTACT_PROPERTY_NAME, PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, PRIMARY_STUDY_CONTACT_CHOICE_PROPERTY_NAME, PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "HaveFunding", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "FundingSourcePI", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "FundingSourcePIContact", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "FundingSourcePIContactInSystem", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, BUDGET_CONTACT_PROPERTY_NAME, PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, BUDGET_CONTACT_INSYSTEM_PROPERTY_NAME, PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AmountBudgeted", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource1Num", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource1Perc", PropertyType.DOUBLE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource1Institution", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource1InstitutionOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource2Num", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource2Perc", PropertyType.DOUBLE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource2Institution", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource2InstitutionOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource3Num", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource3Perc", PropertyType.DOUBLE) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource3Institution", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BudgetSource3InstitutionOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BillingComments", PropertyType.MULTI_LINE) || dirty;

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
