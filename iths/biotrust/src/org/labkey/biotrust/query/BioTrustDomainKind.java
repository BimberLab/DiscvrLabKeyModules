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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;
import org.labkey.biotrust.BioTrustSchema;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 1/7/13
 */
public abstract class BioTrustDomainKind extends DomainKind
{
    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";
    private static String XAR_SUBSTITUTION_GUID = "GUID";

    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    private static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    public static String PROPERTY_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}-${TableName}";
    public static String PROPERTY_LSID_TEMPLATE = "${FolderLSIDBase}:${GUID}";

    @Override
    public Set<String> getNonProvisionedTableNames()
    {
        Set<String> tables = new CaseInsensitiveHashSet();
        tables.add(BioTrustQuerySchema.DOCUMENT_TYPES_TABLE_NAME);
        tables.add(BioTrustQuerySchema.REQUEST_CATEGORY_TABLE_NAME);
        tables.add(BioTrustQuerySchema.REQUEST_STATUS_TABLE_NAME);
        tables.add(BioTrustQuerySchema.REQUIRED_DOCUMENTS_TABLE_NAME);
        tables.add(BioTrustQuerySchema.SPECIMEN_REQUEST_DOCUMENTS_TABLE_NAME);
        tables.add(BioTrustQuerySchema.DOCUMENT_PROPERTIES_TABLE_NAME);

        tables.add(BioTrustQuerySchema.SAMPLE_PICKUP_TABLE_NAME);
        tables.add(BioTrustQuerySchema.SAMPLE_PICKUP_MAP_TABLE_NAME);
        tables.add(BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_MAP_TABLE_NAME);
        tables.add(BioTrustQuerySchema.SAMPLE_REVIEWER_MAP_TABLE_NAME);

        return tables;
    }

    protected abstract String getNamespacePrefix();

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    protected Container getDomainContainer(Container c)
    {
        return c;
    }

    @Override
    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, getNamespacePrefix(), getDomainContainer(c), u);
    }

    public static String getDomainURI(String schemaName, String tableName, String namespacePrefix, Container c, User u)
    {
        try
        {
            XarContext xc = new XarContext("Domains", c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
            xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, tableName);

            String template = String.format(DOMAIN_NAMESPACE_PREFIX_TEMPLATE, namespacePrefix);
            return LsidUtils.resolveLsidFromTemplate(DOMAIN_LSID_TEMPLATE, xc, template);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    private static String generatePropertyURI(String schemaName, String tableName, String namespacePrefix, Container c, User u)
    {
        try
        {
            XarContext xc = new XarContext("Domains", c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
            xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, tableName);
            xc.addSubstitution(XAR_SUBSTITUTION_GUID, GUID.makeGUID());

            String template = String.format(PROPERTY_NAMESPACE_PREFIX_TEMPLATE, namespacePrefix);
            return LsidUtils.resolveLsidFromTemplate(PROPERTY_LSID_TEMPLATE, xc, template);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return QueryService.get().urlFor(containerUser.getUser(), containerUser.getContainer(), QueryAction.executeQuery, BioTrustQuerySchema.NAME, domain.getName());
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(getDomainContainer(containerUser.getContainer()), domain.getTypeURI(), false, false, true);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        String domainURI = generateDomainURI(schemaName, queryName, getDomainContainer(container), user);
        if (domainURI == null)
            return null;

        ActionURL ret = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(container, domainURI, false, false, true);
        ret.addParameter("createOrEdit", true);
        return ret;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return Collections.emptySet();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        return null;
    }

    @Override
    public List<String> updateDomain(GWTDomain original, GWTDomain update, Container container, User user)
    {
        return null;
    }

    @Override
    public DbScope getScope()
    {
        return BioTrustSchema.getInstance().getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return BioTrustSchema.getInstance().getSchema().getName();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        return false;
    }

    @Override
    public void invalidate(Domain domain)
    {
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> names = new HashSet<>();

        for (PropertyStorageSpec spec : getBaseProperties())
            names.add(spec.getName());

        return names;
    }

    protected static PropertyStorageSpec createFieldSpec(String name, JdbcType jdbcType)
    {
        return createFieldSpec(name, jdbcType, false, false);
    }

    protected static PropertyStorageSpec createFieldSpec(String name, JdbcType jdbcType, boolean isPrimaryKey, boolean isAutoIncrement)
    {
        PropertyStorageSpec spec = new PropertyStorageSpec(name, jdbcType);
        spec.setAutoIncrement(isAutoIncrement);
        spec.setPrimaryKey(isPrimaryKey);

        return spec;
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);

        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(getNamespacePrefix()) ? Handler.Priority.MEDIUM : null;
    }

    public abstract void ensureDomainProperties(Domain domain, User user);

    protected boolean createPropertyDescriptor(Domain domain, String namespacePrefix, User user,
                                               String name, PropertyType type)
    {
        return createPropertyDescriptor(domain, namespacePrefix, user, name, null, type, null, false);
    }

    protected boolean createPropertyDescriptor(Domain domain, String namespacePrefix, User user,
                                               String name, String caption, PropertyType type, String description, boolean required)
    {
        if (domain.getPropertyByName(name) == null)
        {
            String propertyURI = generatePropertyURI(BioTrustSchema.getInstance().getSchema().getName(),
                    domain.getName(), namespacePrefix, domain.getContainer(), user);

            DomainProperty prop = domain.addProperty();
            prop.setName(name);
            if (caption != null)
                prop.setLabel(caption);
            prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
            prop.setPropertyURI(propertyURI);
            prop.setRequired(required);
            if (description != null)
                prop.setDescription(description);

            return true;
        }
        return false;
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        return new PropertyStorageSpec(pd);
    }

    @Nullable
    @Override
    public String getMetaDataSchemaName()
    {
        return null;
    }

    @Nullable
    @Override
    public String getMetaDataTableName()
    {
        return null;
    }
}
