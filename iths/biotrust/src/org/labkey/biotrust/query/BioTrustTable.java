/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.biotrust.BioTrustSchema;

/**
 * User: klum
 * Date: 2/15/13
 */
public abstract class BioTrustTable extends FilteredTable<BioTrustQuerySchema>
{
    protected UserSchema _userSchema;
    protected Domain _domain;

    public BioTrustTable(Domain domain, BioTrustQuerySchema schema)
    {
        super(getTable(domain), schema);

        _domain = domain;
        _userSchema = schema;
        init();
    }

    public BioTrustTable(String name, BioTrustQuerySchema schema, String domainKindName)
    {
        super(getTable(name, schema, domainKindName), schema);

        _domain = getDomain(name, schema, domainKindName);
        _userSchema = schema;
        init();
    }

    private void init()
    {
        wrapAllColumns(true);

        ColumnInfo container = getColumn("Container");
        ContainerForeignKey.initColumn(container, _userSchema);

        ColumnInfo createdBy = getColumn(FieldKey.fromParts("CreatedBy"));
        if (createdBy != null)
        {
            UserIdForeignKey.initColumn(createdBy);
        }

        ColumnInfo modifiedBy = getColumn(FieldKey.fromParts("ModifiedBy"));
        if (modifiedBy != null)
        {
            UserIdForeignKey.initColumn(modifiedBy);
        }
    }

    protected static TableInfo getTable(Domain domain)
    {
        if (domain != null)
        {
            return StorageProvisioner.createTableInfo(domain, BioTrustSchema.getInstance().getSchema());
        }
        return null;
    }

    protected static Domain getDomain(String name, UserSchema schema, String domainKindName)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(domainKindName);
        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainKind.generateDomainURI(schema.getName(), name, schema.getContainer(), schema.getUser()));

        return domain;
    }

    protected static TableInfo getTable(String name, UserSchema schema, String domainKindName)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(domainKindName);
        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainKind.generateDomainURI(schema.getName(), name, schema.getContainer(), schema.getUser()));

        return getTable(domain);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new DefaultQueryUpdateService(this, table);

        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(user, perm);
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public DomainKind getDomainKind()
    {
        if (_domain != null)
            return _domain.getDomainKind();
        return null;
    }
}
