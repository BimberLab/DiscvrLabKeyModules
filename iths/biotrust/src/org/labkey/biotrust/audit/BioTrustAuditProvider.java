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
package org.labkey.biotrust.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

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
public class BioTrustAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String BIOTRUST_AUDIT_EVENT = "BioTrustAuditEvent";

    public static final String COLUMN_NAME_STUDY_ID = "StudyId";
    public static final String COLUMN_NAME_TISSUE_RECORD_ID = "TissueRecordId";
    public static final String COLUMN_NAME_ACTION = "Action";
    public static final String COLUMN_NAME_STATUS = "Status";
    public static final String COLUMN_NAME_PREVIOUS_STATUS = "PreviousStatus";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STUDY_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TISSUE_RECORD_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_ACTION));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STATUS));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PREVIOUS_STATUS));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }


    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new BioTrustAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return BIOTRUST_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "NW BioTrust events";
    }

    @Override
    public String getDescription()
    {
        return "NW BioTrust events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        BioTrustAuditEvent bean = new BioTrustAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setStudyId(event.getIntKey1());
        if (event.getIntKey2() != null)
            bean.setTissueRecordId(event.getIntKey2());

        bean.setAction(event.getKey1());
        bean.setStatus(event.getKey2());
        bean.setPreviousStatus(event.getKey3());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_STUDY_ID);
        legacyMap.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_TISSUE_RECORD_ID);
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_ACTION);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_STATUS);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_PREVIOUS_STATUS);
        return legacyMap;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_STUDY_ID.equalsIgnoreCase(col.getName()))
                {
                    // UNDONE: Create QueryForeignKey to study
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };

        return table;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)BioTrustAuditEvent.class;
    }

    public static class BioTrustAuditEvent extends AuditTypeEvent
    {
        private int _studyId;
        private int _tissueRecordId;
        private String _action;
        private String _status;
        private String _previousStatus;

        public BioTrustAuditEvent()
        {
            super();
        }

        public BioTrustAuditEvent(String container, String comment)
        {
            super(BIOTRUST_AUDIT_EVENT, container, comment);
        }

        public int getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(int studyId)
        {
            _studyId = studyId;
        }

        public int getTissueRecordId()
        {
            return _tissueRecordId;
        }

        public void setTissueRecordId(int tissueRecordId)
        {
            _tissueRecordId = tissueRecordId;
        }

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getStatus()
        {
            return _status;
        }

        public void setStatus(String status)
        {
            _status = status;
        }

        public String getPreviousStatus()
        {
            return _previousStatus;
        }

        public void setPreviousStatus(String previousStatus)
        {
            _previousStatus = previousStatus;
        }
    }

    public static class BioTrustAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "BioTrustAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public BioTrustAuditDomainKind()
        {
            super(BIOTRUST_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_STUDY_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TISSUE_RECORD_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_ACTION, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_STATUS, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PREVIOUS_STATUS, PropertyType.STRING));
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
