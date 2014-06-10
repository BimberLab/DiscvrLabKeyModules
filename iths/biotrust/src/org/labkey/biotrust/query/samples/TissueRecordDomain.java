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
 * Date: 2/16/13
 */
public class TissueRecordDomain extends BioTrustProjectRootDomainKind
{
    public static final String NAME = "TissueRecordDomain";
    public static String NAMESPACE_PREFIX = "NWBioTrust-" + NAME;

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

        _reservedNames.add("SampleId");
        _reservedNames.add("StudyRecordId");
        _reservedNames.add("RequestType");
        _reservedNames.add("TissueType");
        _reservedNames.add("TissueTypeOther");
        _reservedNames.add("AnatomicalSite");
        _reservedNames.add("AnatomicalSiteOther");
        _reservedNames.add("Preservation");
        _reservedNames.add("PreservationOther");
        _reservedNames.add("MinimumSize");
        _reservedNames.add("MinimumSizeUnits");
        _reservedNames.add("PreferredSize");
        _reservedNames.add("PreferredSizeUnits");
        _reservedNames.add("BloodSampleType");
        _reservedNames.add("TubeType");
        _reservedNames.add("TubeTypeOther");
        _reservedNames.add("HoldAtLocation");
        _reservedNames.add("HoldAtLocationOther");
        _reservedNames.add("BloodRequireSerial");
        _reservedNames.add("BloodPreferedTiming");
        _reservedNames.add("BloodMaxDrawTime");
        _reservedNames.add("TissueRequireSerial");
        _reservedNames.add("SamplesPerCase");
        _reservedNames.add("Notes");

        ALWAYS_EDITABLE_PROPERTY_NAMES.add("Notes");
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

            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SampleId", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "StudyRecordId", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "RequestType", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TissueType", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TissueTypeOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AnatomicalSite", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AnatomicalSiteOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Preservation", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "PreservationOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "MinimumSize", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "MinimumSizeUnits", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "PreferredSize", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "PreferredSizeUnits", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BloodSampleType", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TubeType", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TubeTypeOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "HoldAtLocation", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "HoldAtLocationOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BloodRequireSerial", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BloodPreferedTiming", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "BloodMaxDrawTime", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "TissueRequireSerial", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "SamplesPerCase", PropertyType.INTEGER) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Notes", PropertyType.MULTI_LINE) || dirty;

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
