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
package org.labkey.sla.etl;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class ETLAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String AUDIT_EVENT_TYPE = "SLASyncAuditEvent";

    public static final String COLUMN_NAME_TYPE = "Type";
    public static final String COLUMN_NAME_SLA_ERRORS = "SlaErrors";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SLA_ERRORS));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ETLAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "SLA ETL Events";
    }

    @Override
    public String getDescription()
    {
        return "SLA ETL Events";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_TYPE);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_SLA_ERRORS);
        return legacyNames;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ETLAuditEvent.class;
    }

    public static void addAuditEntry(Container container, User user, String type, String comment, int slaErrors)
    {
        ETLAuditProvider.ETLAuditEvent event = new ETLAuditProvider.ETLAuditEvent(container.getId(), comment);

        event.setType(type);
        event.setEhrErrors(slaErrors);

        AuditLogService.get().addEvent(user, event);
    }

    public static class ETLAuditEvent extends AuditTypeEvent
    {
        private String _type;
        private int _ehrErrors;
        private int _datasetErrors;
        private int _ehrLookupErrors;

        public ETLAuditEvent()
        {
            super();
        }

        public ETLAuditEvent(String container, String comment)
        {
            super(AUDIT_EVENT_TYPE, container, comment);
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public int getEhrErrors()
        {
            return _ehrErrors;
        }

        public void setEhrErrors(int ehrErrors)
        {
            _ehrErrors = ehrErrors;
        }

        public int getDatasetErrors()
        {
            return _datasetErrors;
        }

        public void setDatasetErrors(int datasetErrors)
        {
            _datasetErrors = datasetErrors;
        }

        public int getEhrLookupErrors()
        {
            return _ehrLookupErrors;
        }

        public void setEhrLookupErrors(int ehrLookupErrors)
        {
            _ehrLookupErrors = ehrLookupErrors;
        }
    }

    public static class ETLAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SLAETLAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public ETLAuditDomainKind()
        {
            super(AUDIT_EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_TYPE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SLA_ERRORS, PropertyType.INTEGER));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
