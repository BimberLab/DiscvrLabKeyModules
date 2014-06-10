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
package org.labkey.biotrust.query;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;

import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: klum
 * Date: 2/15/13
 */
public class RequestResponsesDomain extends BioTrustProjectRootDomainKind
{
    public static final String NAME = "RequestReponsesTable";
    public static String NAMESPACE_PREFIX = "BioTrust";

    private static final Set<PropertyStorageSpec> _baseFields = new LinkedHashSet<>();

    static {
        _baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));       // pk
        _baseFields.add(createFieldSpec("Container", JdbcType.VARCHAR));
        _baseFields.add(createFieldSpec("Created", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("CreatedBy", JdbcType.INTEGER));
        _baseFields.add(createFieldSpec("Modified", JdbcType.TIMESTAMP));
        _baseFields.add(createFieldSpec("ModifiedBy", JdbcType.INTEGER));
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
    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public void ensureDomainProperties(Domain domain, User user)
    {
    }
}
