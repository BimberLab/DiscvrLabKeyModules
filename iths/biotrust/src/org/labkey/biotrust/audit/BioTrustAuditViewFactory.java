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
package org.labkey.biotrust.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.data.DataMapColumn;
import org.labkey.api.audit.data.DataMapDiffColumn;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.Sort;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Event field documentation:
 *
 * intKey1 - study id
 * intKey2 - tissue record id
 *
 * key1 - action
 * key2 - status
 * key3 - prev status
 *
 * User: klum
 * Date: 3/8/13
 */
public class BioTrustAuditViewFactory extends SimpleAuditViewFactory
{

    public static final String BIOTRUST_AUDIT_EVENT = "BioTrustAuditEvent";

    public enum Actions {

        StudyRegistered("Study Registered"),
        StudyStatusChange("Study Status Changed"),
        SampleRequestSubmitted("Sample Request Submitted"),
        SampleRequestStatusChange("Sample Request Status Changed"),

        ApprovalReviewStatusChange("Approval Review Status Changed"),
        ReviewApproved("Reviewed, Approved"),
        ReviewApprovedWithChange("Reviewed, Approved with Changes"),
        ReviewNotFeasible("Reviewed, Not Feasible");

        private String _label;

        Actions(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    };

    private static final BioTrustAuditViewFactory INSTANCE = new BioTrustAuditViewFactory();

    private BioTrustAuditViewFactory() {}

    public static BioTrustAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return "NW BioTrust events";
    }

    @Override
    public String getEventType()
    {
        return BIOTRUST_AUDIT_EVENT;
    }

    @Override
    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("IntKey2"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("Key3"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    @Override
    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        ColumnInfo studyId = table.getColumn("IntKey1");
        studyId.setLabel("StudyId");

        ColumnInfo tissueId = table.getColumn("IntKey2");
        tissueId.setLabel("TissueRecordId");

        ColumnInfo action = table.getColumn("Key1");
        action.setLabel("Action");

        ColumnInfo status = table.getColumn("Key2");
        status.setLabel("Status");

        ColumnInfo prevStatus = table.getColumn("Key3");
        prevStatus.setLabel("PreviousStatus");

        table.getColumn("IntKey3").setHidden(true);
    }

    public static void addAuditEvent(Container c, User user, int studyId, Actions action, @Nullable String status,
                                     @Nullable String prevStatus, String comment)
    {
        addAuditEvent(c, user, studyId, 0, action, status, prevStatus, comment);
    }

    public static void addAuditEvent(Container c, User user, int studyId, int tissueId, Actions action,
                                     @Nullable String status, @Nullable String prevStatus, String comment)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(user);
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setContainerId(c.getId());
        event.setEventType(BioTrustAuditViewFactory.BIOTRUST_AUDIT_EVENT);

        if (studyId != 0)
            event.setIntKey1(studyId);
        if (tissueId != 0)
            event.setIntKey2(tissueId);

        event.setKey1(action.getLabel());
        if (status != null)
            event.setKey2(status);
        if (prevStatus != null)
            event.setKey3(prevStatus);
        event.setComment(comment);

        AuditLogService.get().addEvent(event);
    }
}
