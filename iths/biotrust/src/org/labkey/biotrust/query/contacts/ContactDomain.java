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
package org.labkey.biotrust.query.contacts;

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
 * Date: 7/17/13
 */
public class ContactDomain extends BioTrustProjectRootDomainKind
{
    public static final String NAME = "ContactDomain";
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

        _reservedNames.add("FirstName");
        _reservedNames.add("LastName");
        _reservedNames.add("DisplayName");
        _reservedNames.add("Email");
        _reservedNames.add("AddressStreet1");
        _reservedNames.add("AddressStreet2");
        _reservedNames.add("AddressCity");
        _reservedNames.add("AddressState");
        _reservedNames.add("AddressZip");
        _reservedNames.add("Location");
        _reservedNames.add("Institution");
        _reservedNames.add("InstitutionOther");
        _reservedNames.add("PhoneNumber");
        _reservedNames.add("MobileNumber");
        _reservedNames.add("PagerNumber");
        _reservedNames.add("Inactive");
        _reservedNames.add("Role");
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

            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "FirstName", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "LastName", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "DisplayName", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Email", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AddressStreet1", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AddressStreet2", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AddressCity", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AddressState", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "AddressZip", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Location", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Institution", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "InstitutionOther", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "PhoneNumber", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "MobileNumber", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "PagerNumber", PropertyType.STRING) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Inactive", PropertyType.BOOLEAN) || dirty;
            dirty = createPropertyDescriptor(domain, getNamespacePrefix(), user, "Role", PropertyType.STRING) || dirty;

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
