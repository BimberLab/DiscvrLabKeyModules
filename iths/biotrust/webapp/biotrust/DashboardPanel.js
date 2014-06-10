/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/biotrust/DocumentSetPanel.js");
LABKEY.requiresScript("/biotrust/ApproverReviewPanel.js");
LABKEY.requiresCss("biotrust/NWBioTrust.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('LABKEY.ext4.biotrust.DashboardPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'fit',
            minWidth : 625,
            maxHeight : 1500,
            frame  : false,
            border : false,
            gridEmptyText : 'No study registrations to show'
        });

        Ext4.define('Dashboard.View', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'RowId',        type : 'int'},
                {name : 'SurveyRowId',  type : 'int'},
                {name : 'SurveyDesignId', type : 'int'},
                {name : 'CreatedBy',    type : 'string'},
                {name : 'Created',      type : 'date'},
                {name : 'ModifiedBy',   type : 'string'},
                {name : 'Modified',     type : 'date'},
                {name : 'SubmittedBy',  type : 'string'},
                {name : 'Submitted',    type : 'date'},
                {name : 'Status',       type : 'string'},
                {name : 'StatusSortOrder', type : 'double'},
                {name : 'LastStatusChange', type : 'date'},
                {name : 'DaysSinceModified', type : 'int'},
                {name : 'CategoryId',   type : 'int'},
                {name : 'Category',     type : 'string'},
                {name : 'CategorySortOrder', type : 'double'},

                // study registration specific columns
                {name : 'StudyStatus',   type : 'string'},
                {name : 'StudyCategory', type : 'string'},
                {name : 'StudyCategoryId', type : 'int'},
                {name : 'StudyReceived', type : 'date'},
                {name : 'ContainerPath', type : 'string'},
                {name : 'Label',         type : 'string'},
                {name : 'AvailableRequiredDocumentTypes', type : 'int'},
                {name : 'RequiredDocumentTypes', type : 'int'},
                {name : 'TotalDocumentTypes', type : 'int'},
                {name : 'NumRecords', type: 'int'},

                // sample request specific columns
                {name : 'SampleId',       type : 'int'},
                {name : 'StudyId',        type : 'int'},
                {name : 'StudySurveyId',  type : 'int'},
                {name : 'StudySurveyDesignId',  type : 'int'},
                {name : 'StudyName',      type : 'string'},
                {name : 'StudyRecordId',  type : 'int'},
                {name : 'RecordId',       type : 'string'},
                {name : 'RequestType',    type : 'string'},
                {name : 'RequestTypeDisplay', type : 'string'},
                {name : 'Type',           type : 'string'},
                {name : 'AnatomicalSite', type : 'string'},
                {name : 'Preservation',   type : 'string'},
                {name : 'NumCases',       type : 'int'},
                {name : 'Container',      type : 'string'},
                {name : 'ContainerName',  type : 'string'},
                {name : 'IsApproval',     type : 'boolean'},
                {name : 'IsLocked',       type : 'boolean'},
                {name : 'IsFinal',        type : 'boolean'},
                {name : 'UserReviewResponseExpected', type: 'boolean'},
                {name : 'UserReviewResponseExists', type : 'boolean'},
                {name : 'NumResponsesExpected', type: 'int'},
                {name : 'NumResponsesExist', type: 'int'}
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];
        this.gridPanel = null;

        this.items.push(this.configureGrid());

        this.callParent();
    },

    configureGrid : function() {

        this.gridPanel = Ext4.create('Ext.grid.Panel', {
            minWidth : this.gridMinWidth || 750,
            border : false,
            frame : false,
            layout : 'fit',
            scroll : 'vertical',
            columns : this.configureGridColumns(),
            store : this.configureStore(),
            multiSelect : false,
            viewConfig : {
                stripeRows : false,
                emptyText : '<span style="font-style:italic;">' + this.gridEmptyText + '</span>',
                getRowClass: this.addGridRowClass
            },
            selType : 'rowmodel',
            features : this.configureGroupingFeature(),
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // determine which pop-up dialog (edit, doc set, etc.) based on the class name of the target
                    if (e.target.className == 'edit-views-link')
                        this.rowEditClicked(record);
                    else if (e.target.className == 'document-set-link')
                        this.showDocumentSetDialog(record);
                },
                scope : this
            }
        });

        // add listeners to show/hide loading mask for the grid panel store
        this.gridPanel.getStore().on('beforeload', function(){
            if (this.gridPanel)
                this.gridPanel.setLoading(true);
        }, this);

        return this.gridPanel;
    },

    addGridRowClass : function(record, index) {
        // noop
    },

    configureStore : function() {
        return Ext4.create('Ext.data.ArrayStore', {
            model : 'Dashboard.View'
        });
    },

    configureGroupingFeature : function() {
        return null;
    },

    updateFilterComboStores : function() {
        // do nothing in the general case
    },

    rowEditClicked : function(record) {
        // do nothing in the general case
    },

    getDisplayField : function(label, value, labelWidth) {
        return {
            xtype: 'displayfield',
            labelStyle: 'font-weight: bold;',
            labelWidth: labelWidth || 130,
            hideFieldLabel: !label || label.length == 0,
            fieldLabel: label,
            margin: 0,
            value: Ext4.util.Format.htmlEncode(value)
        };
    },

    showDocumentSetDialog : function(record) {
        if (record)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                height: 420,
                width: 800,
                autoScroll: true,
                title: 'View Document Set',
                items: [Ext4.create('LABKEY.ext4.biotrust.DocumentSetPanel', {
                    displayMode: true,
                    requestRowId: record.get("SurveyRowId"),
                    requestContainerPath: record.get("ContainerPath")
                })],
                buttonAlign: 'center',
                buttons: [{
                    text: LABKEY.user.canUpdate ? 'Manage' : 'View',
                    href: LABKEY.ActionURL.buildURL('biotrust', 'manageDocumentSet', record.get("ContainerPath"), {rowId: record.get("SurveyRowId")}),
                    hrefTarget: "_self"
                },{
                    text: 'Close',
                    scope: this,
                    handler: function() {
                        win.close();
                    }
                }]
            });
            win.show();
        }
    },

    getVisibleColumnNames : function() {
        return [];
    },

    configureGridColumns : function() {

        var visibleColumnNames = this.getVisibleColumnNames();

        return [{
            text     : 'ID',
            dataIndex: 'StudyRecordId',
            width : 35,
            align: 'center',
            hidden   : visibleColumnNames.indexOf("StudyRecordId") == -1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                // make the ID look clickable, cellclick to open the details dialog handled by listener on grid
                return "<a href='javascript:void(0);'>" + Ext4.util.Format.htmlEncode(value) + "</a>";
            }
        },{
            text     : 'ID',
            dataIndex: 'RecordId',
            width : 55,
            align: 'center',
            hidden   : visibleColumnNames.indexOf("RecordId") == -1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                // make the record ID look clickable, cellclick to open the details dialog handled by listener on grid
                return "<a href='javascript:void(0);'>" + Ext4.util.Format.htmlEncode(value) + "</a>";
            }
        },{
            text     : 'Container',
            dataIndex: 'ContainerPath',
            hidden   : visibleColumnNames.indexOf("ContainerPath") == -1
        },{
            text     : 'Study Name',
            dataIndex: 'Label',
            flex     : 1,
            minWidth : 200,
            hidden   : visibleColumnNames.indexOf("Label") == -1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                // make the study name look clickable, cellclick to open the details dialog handled by listener on grid
                return "<a href='javascript:void(0);'>" + Ext4.util.Format.htmlEncode(value) + "</a>";
            }
        },{
            text     : 'Document Set',
            dataIndex: 'TotalDocumentTypes',
            width : 137,
            hidden   : visibleColumnNames.indexOf("TotalDocumentTypes") == -1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                // TODO: display AvailableRequiredDocumentTypes and color the count based on comparison to RequiredDocumentTypes count
                var hoverText = "Includes files for " + value + " document type" + (value != 1 ? "s" : "");
                return "<a onclick='' class='document-set-link' data-qtip='" + hoverText + "'>Document Set (" + value + ")</a>";
            }
        },{
            text     : '# Requests',
            dataIndex: 'NumRecords',
            align: 'center',
            width : 100,
            hidden   : visibleColumnNames.indexOf("NumRecords") == -1
        },{
            text     : 'Request Type',
            dataIndex: 'RequestType',
            width : 200,
            hidden   : visibleColumnNames.indexOf("RequestType") == -1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                return Ext4.util.Format.htmlEncode(record.get("RequestTypeDisplay") || value);
            },
            scope: this
        },{
            text     : 'Tissue / Tube Type',
            dataIndex: 'Type',
            renderer : Ext4.util.Format.htmlEncode,
            flex     : 1,
            minWidth : 200,
            hidden   : visibleColumnNames.indexOf("Type") == -1
        },{
            text     : 'Preservation',
            dataIndex: 'Preservation',
            renderer : Ext4.util.Format.htmlEncode,
            width : 150,
            hidden   : visibleColumnNames.indexOf("Preservation") == -1
        },{
            text     : 'Anatomical Site',
            dataIndex: 'AnatomicalSite',
            renderer : Ext4.util.Format.htmlEncode,
            width : 175,
            hidden   : visibleColumnNames.indexOf("AnatomicalSite") == -1
        },{
            text     : '# Cases',
            dataIndex: 'NumCases',
            renderer : function(value) { return value ? value : "" },
            width : 80,
            align : 'center',
            hidden   : visibleColumnNames.indexOf("NumCases") == -1
        },{
            text     : 'Created By',
            dataIndex: 'CreatedBy',
            width : 125,
            hidden   : visibleColumnNames.indexOf("CreatedBy") == -1
        },{
            text     : 'Created',
            dataIndex: 'Created',
            width : 100,
            hidden   : visibleColumnNames.indexOf("Created") == -1,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        },{
            text     : 'Modified By',
            dataIndex: 'ModifiedBy',
            width : 125,
            hidden   : visibleColumnNames.indexOf("ModifiedBy") == -1
        },{
            text     : 'Modified',
            dataIndex: 'Modified',
            width : 100,
            hidden   : visibleColumnNames.indexOf("Modified") == -1,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        },{
            text     : 'Submitted By',
            dataIndex: 'SubmittedBy',
            width : 125,
            hidden   : visibleColumnNames.indexOf("SubmittedBy") == -1
        },{
            text     : 'Submitted',
            dataIndex: 'Submitted',
            width : 100,
            hidden   : visibleColumnNames.indexOf("Submitted") == -1,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        },{
            text     : 'Last Status Change',
            dataIndex: 'LastStatusChange',
            align    : 'center',
            width : 160,
            hidden   : visibleColumnNames.indexOf("LastStatusChange") == -1,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        },{
            text     : 'Status',
            dataIndex: 'Status',
            minWidth : 150,
            hidden   : visibleColumnNames.indexOf("Status") == -1
        },{
            text     : 'Assigned To',
            dataIndex: 'Category',
            hidden   : visibleColumnNames.indexOf("Category") == -1
        }];
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the updating sample request tissue record status/category
 */
Ext4.define('LABKEY.ext4.biotrust.EditSampleRequestDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.DashboardPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.on('cellclick', function(view, cell, cellIndex, record, row, rowIndex, e) {
            if (e.target.tagName == 'A')
            {
                if (view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'Status')
                    window.location = LABKEY.ActionURL.buildURL('biotrust', 'approverReview', null, {status: record.get("Status")});
                else if (view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'LastStatusChange')
                    this.viewHistory(record.get('SurveyRowId'), record.get('RecordId'), false);
                else if (view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'RecordId')
                    this.requestDetailWindow(record);
            }
        }, this);

        this.gridPanel.on('groupclick', function(view, node, group, e) {
            if (e.target.className == 'x4-grid-group-title')
            {
                if (!view.features[0].collapsedState)
                    view.features[0].collapsedState = {};

                if (view.features[0].collapsedState[group])
                {
                    view.features[0].expand(group);
                    view.features[0].collapsedState[group] = false;
                }
                else
                {
                    view.features[0].collapse(group);
                    view.features[0].collapsedState[group] = true;
                }
            }
            else
            {
                var studyRecord = view.getStore().findRecord("StudyId", group, 0, false, true, true);
                if (studyRecord && studyRecord.get("StudyStatus") != 'Pending')
                {
                    this.statusCategoryEditDialog("Edit Study : " + group, studyRecord.get("StudyCategoryId"),
                            studyRecord.get("StudyStatus"), studyRecord.get("StudySurveyId"), true, studyRecord);
                }
            }
        }, this);
    },

    addGridRowClass : function(record, index) {
        // display different sample requests with alternate shading
        if (record.get("SampleId"))
        {
            if (this.shadeRowForSampleRequest == undefined)
                this.shadeRowForSampleRequest = false;

            var previous = this.getStore().getAt(index-1);
            if (previous != null && record.get("StudyId") == previous.get("StudyId") && record.get("SampleId") != previous.get("SampleId"))
                this.shadeRowForSampleRequest = !this.shadeRowForSampleRequest;

            return this.shadeRowForSampleRequest ? 'request-alt-row' : '';
        }
    },

    configureGroupingFeature : function() {
        return Ext4.create('Ext.grid.feature.Grouping', {
            groupHeaderTpl : Ext4.create('Ext.XTemplate',
                '<table width="100%"><tr>',
                '<td valign="top">{[this.formatValue(values)]}</td>',
                '<td width="100px" align="left">{[this.showStudyDetail(values, "StudyCategory")]}</td>',
                '<td width="125px" align="center">{[this.showStudyDetail(values, "StudyReceived")]}</td>',
                '<td width="170px" align="left">{[this.showStudyStatus(values)]}</td>',
                '<td width="145px" align="right">{[this.showDocumentSetLink(values)]}</td>',
                '</tr></table>',
                {
                    formatValue: function(values) {
                        var value = values.groupValue;

                        if (values.rows.length > 0)
                        {
                            var studyName = values.rows[0].data["StudyName"];
                            if (studyName.length > 85)
                                studyName = studyName.substring(0, 85) + "...";

                            var studyId = values.rows[0].data["StudyId"];
                            var url = LABKEY.ActionURL.buildURL('project', 'begin', values.rows[0].data["Container"]);
                            var containerLink = "<a href='" + url + "'>" + values.rows[0].data["ContainerName"] + "</a>";
                            value = studyId + " " + studyName + " (" + containerLink + ")";
                        }

                        return value;
                    },

                    showStudyStatus : function(values) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data["StudyStatus"];
                            if (val == 'In Review')
                                return '<span style="color: red;">In Review</span>';
                            else if (val == 'Active')
                                return '<span style="color: green;">Active</span>';
                            else
                                return val || '';
                        }
                        else
                            return '&nbsp;';
                    },

                    showStudyDetail : function(values, colName) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data[colName];
                            if (val != null && colName == 'StudyReceived')
                                return Ext4.util.Format.date(val, 'm/d/Y');
                            else
                                return val || "";
                        }
                        else
                            return '&nbsp;';
                    },

                    showDocumentSetLink : function(values) {
                        if (values.rows.length > 0)
                        {
                            var url = LABKEY.ActionURL.buildURL('biotrust', 'manageDocumentSet', values.rows[0].data["Container"], {
                                rowId: values.rows[0].data["StudySurveyId"],
                                srcURL: window.location
                            });
                            return LABKEY.Utils.textLink({text: 'view document set', href: url});
                        }
                        else
                            return '&nbsp;';
                    }
                }
            ),
            startCollapsed : this.collapseGroups,
            onGroupClick: function(view, rowEl, groupName) {} // do nothing, prevent group collapse
        });
    },

    rowEditClicked : function(record) {
        if (record)
        {
            this.statusCategoryEditDialog('Edit Sample Request : ' + record.get('RecordId'), record.get("CategoryId"),
                    record.get("Status"), record.get("SurveyRowId"), false, record);
        }
    },

    getSelectedSampleReviewers : function(surveyRowId, status, container, maskedEl) {
        // get the list of selected approvers for reshow, only expected to return results if the selected status is an approval state
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'getSampleReviewers.api', container),
            method  : 'POST',
            jsonData: {
                rowId : surveyRowId,
                status : status
            },
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                this.reviewerIds = o.userIds ? o.userIds : [];

                // select the corresponding reviewer checkboxes for this rowId/status
                var cbGroup = Ext4.ComponentQuery.query('#reviewersGroup');
                if (cbGroup.length == 1)
                {
                    Ext4.each(cbGroup[0].items.items, function(cb) {
                        cb.setValue(this.reviewerIds.indexOf(cb.inputValue) > -1);
                    }, this);

                    if (maskedEl)
                        maskedEl.unmask();
                }
            },
            failure : LABKEY.Utils.displayAjaxErrorResponse,
            scope   : this
        });
    },

    statusCategoryEditDialog : function(title, categoryId, status, surveyRowId, isStudy, record) {
        var me = this;

        var statusStore = null;
        if (isStudy)
        {
            // issue 187: restrict study status options based on current state
            var data = [["Closed"]];
            if (this.isAdmin || status != "Closed")
                data.splice(0, 0, ["In Review"], ["Active"]);
            if (this.isAdmin || status == "Received")
                data.splice(0, 0, ["Received"]);

            statusStore = Ext4.create('Ext.data.ArrayStore', {
                fields: ["Status"],
                data : data
            });
        }
        else
            statusStore = this.getStatusStore(record);

        // display a window for the user to edit the request status and category
        var win = Ext4.create('Ext.window.Window', {
            cls: 'data-window',
            modal: true,
            minHeight: 100,
            minWidth: 200,
            title: title,
            items: [{
                xtype: 'form',
                itemId: 'requestWindowFormPanel',
                border: false,
                bodyStyle: 'padding: 10px;',
                defaults: {
                    labelWidth: 130,
                    labelStyle: 'font-weight: bold'
                },
                items: [{
                    xtype: 'combo',
                    name: 'RequestCategory',
                    fieldLabel: 'NWBT Resource',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.Store', {
                        schemaName: "biotrust",
                        queryName: "RequestCategory",
                        columns: "RowId,Category",
                        autoLoad: true,
                        sort: "SortOrder,Category",
                        listeners: {
                            scope: this,
                            beforeload: function(){ win.getEl().mask("Loading..."); },
                            load: function() { this.storeLoadSuccess(win); }
                        }
                    }),
                    queryMode: 'local',
                    displayField: 'Category',
                    valueField: 'RowId',
                    value: categoryId || null
                },{
                    xtype: 'combo',
                    name: 'RequestStatus',
                    fieldLabel: 'Status',
                    editable: false,
                    store: statusStore,
                    queryMode: 'local',
                    displayField: 'Status',
                    valueField: 'Status',
                    value: status,
                    allowBlank: false,
                    listeners: {
                        scope: this,
                        select: function(combo, records) {
                            var cbGroup = win.down('#reviewersGroup');
                            var piCCGroup = win.down('#ccPIGroup');
                            if (records.length == 1 && cbGroup)
                            {
                                cbGroup.reset();
                                cbGroup.setVisible(records[0].get('ApprovalState'));
                                piCCGroup.setVisible(records[0].get('ApprovalState'));

                                if (records[0].get('ApprovalState'))
                                {
                                    win.getEl().mask("Loading...");
                                    this.getSelectedSampleReviewers(record.get("SurveyRowId"), records[0].get("Status"), record.get("Container"), win.getEl());
                                }
                            }
                        }
                    }
                },{
                    xtype: 'textarea',
                    name: 'Comment',
                    fieldLabel: 'Notes / Comments',
                    labelAlign: 'top',
                    width: 300,
                    height: 90,
                    allowBlank: false
                },{
                    xtype: 'container',
                    html: LABKEY.Utils.textLink({text: 'view history'}),
                    listeners: {
                        scope: this,
                        render : function(cmp) {
                            cmp.getEl().on('click', function(){
                                if (record)
                                {
                                    me.viewHistory(
                                        (isStudy ? record.get('StudySurveyId') : record.get('SurveyRowId')),
                                        (isStudy ? record.get('StudyName') : record.get('RecordId')),
                                        isStudy
                                    );
                                }
                            });
                        }
                    }
                }],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Update',
                    formBind: true,
                    handler: function() {
                        var values = win.down('#requestWindowFormPanel').getForm().getValues();
                        var selectedStatus = win.down('#requestWindowFormPanel').getForm().findField("RequestStatus").getStore().findRecord("Status", values["RequestStatus"]);

                        var params = {
                            rowId   : surveyRowId,
                            status  : values["RequestStatus"],
                            comment : values["Comment"],
                            study   : isStudy
                        };

                        // category is not required to update a status / comment
                        if (values["RequestCategory"])
                            params["category"] = values["RequestCategory"];

                        // reviewers are only provided for a state that involves review
                        if (selectedStatus && selectedStatus.get("ApprovalState"))
                            params["reviewers"] = typeof values["Reviewers"] == 'number' ? [values["Reviewers"]] : values["Reviewers"];

                        if (isStudy)
                            params["registered"] = record.get("StudyReceived")

                        if (values['notifyinvestigator'])
                            params['notifyinvestigator'] = values['notifyinvestigator'];

                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('biotrust', 'updateSurvey.api'),
                            method  : 'POST',
                            params  : params,
                            success: function(response) {
                                // on save success, reload the store
                                this.updateSampleRequestSuccess();
                                this.updateFilterComboStores();
                            },
                            scope : this
                        });

                        win.close();
                    },
                    scope: this
                },{
                    text: 'Cancel',
                    handler: function() { win.close(); }
                }]
            }]
        });

        statusStore.on('beforeload', function(){ win.getEl().mask("Loading...") });
        statusStore.on('load', function(store) {
            this.storeLoadSuccess(win);
            this.getReviewerCheckboxGroup(store, status, surveyRowId, record.get("Container"));
        }, this);

        win.show();
    },

    getReviewerCheckboxGroup : function(statusStore, status, surveyRowId, container) {

        // query for the set of approval reviewers and add a checkboxgroup
        LABKEY.Security.getUsers({
            scope: this,
            group: 'Approval Reviewers',
            success: function(data) {
                var selectedRecord = statusStore.findRecord("Status", status);

                var elementsArr = [];
                Ext4.each(data.users, function(user){
                    elementsArr.push({
                        boxLabel: user.displayName,
                        name: 'Reviewers',
                        inputValue: user.userId
                    });
                }, this);

                var formPanel = Ext4.ComponentQuery.query('#requestWindowFormPanel')[0];
                if (formPanel){

                    formPanel.insert(2, {
                        xtype: 'checkboxgroup',
                        itemId: 'reviewersGroup',
                        fieldLabel: 'Reviewers',
                        labelAlign: 'top',
                        defaults: {width: 300},
                        columns: 1,
                        vertical: true,
                        hidden: !selectedRecord || !selectedRecord.get("ApprovalState"),
                        items: elementsArr
                    });

                    // add the cc pi checkbox
                    formPanel.insert(3, {
                        xtype   : 'checkboxgroup',
                        itemId  : 'ccPIGroup',
                        fieldLabel: 'Email Notifications (<i>notification sent after update</i>)',
                        labelAlign: 'top',
                        defaults: {width: 300},
                        columns: 1,
                        vertical: true,
                        hidden: !selectedRecord || !selectedRecord.get("ApprovalState"),
                        items: [{
                            boxLabel: 'Copy PI on email' +
                                    '&nbsp;<img data-qtip="If this box is checked the PI will be cc\'ed on this email but will not be copied on any of the reviewers reponses." height="12px" width="12px" src="' + LABKEY.ActionURL.getContextPath() + '/_images/info.png">',
                            name: 'notifyinvestigator',
                            inputValue: true
                        }]
                    });
                    this.getSelectedSampleReviewers(surveyRowId, status, container, null);
                }
            }
        });
    },

    getStatusStore : function(record) {
        // issue 187: sample request status transitions, for non-admin filter the status options if the request is locked, final, etc.
        var filters = [];
        if (!this.isAdmin)
        {
            if (record.get("Status") != 'Submitted')
                filters.push(LABKEY.Filter.create('Status', 'Submitted', LABKEY.Filter.Types.NOT_EQUAL));

            if (record.get("IsFinal"))
                filters.push(LABKEY.Filter.create('Status', record.get("Status")));
            else if (record.get("IsLocked"))
            {
                filters.push(LABKEY.Filter.create('LockedState', true));
                filters.push(LABKEY.Filter.create('Status', 'Closed', LABKEY.Filter.Types.NOT_EQUAL));
            }
        }

        return Ext4.create('LABKEY.ext4.Store', {
            schemaName: "biotrust",
            queryName: "RequestStatus",
            filterArray: filters,
            columns: "Status,ApprovalState",
            autoLoad: true,
            sort: "SortOrder,Status"
        });
    },

    updateSampleRequestSuccess : function() {
        this.gridPanel.getStore().load();
    },

    storeLoadSuccess : function(win) {
        // if both of the stores have loaded, then unmask the window
        var combos = win.down('#requestWindowFormPanel').query('combo');
        var loading = false;
        Ext4.each(combos, function(combo){
            if (combo.getStore().getTotalCount() == 0)
                loading = true;
        });

        if (!loading)
        {
            win.getEl().unmask();
            combos[0].addClass('edit-sample-request-ready'); // for selenium testing
        }
    },

    viewHistory : function(surveyRowId, title, isStudy) {
        this.historyGrid = Ext4.create('Ext.Component', {
            border : false,
            listeners : {
                scope: this,
                render : function() {
                    var qwp = new LABKEY.QueryWebPart({
                        renderTo: this.historyGrid.getId(),
                        schemaName: 'biotrust',
                        queryName: 'StatusChangeAuditEvent',
                        filters: [
                            LABKEY.Filter.create(isStudy ? 'StudyId' : 'TissueRecordId', surveyRowId),
                            LABKEY.Filter.create('Action', isStudy ? 'Study Status Changed' : 'Sample Request Status Changed')
                        ],
                        sort: '-Date',
                        frame : 'none',
                        buttonBarPosition: 'none',
                        scope : this
                    });
                }
            }
        });

        var win = Ext4.create('Ext.window.Window', {
            cls: 'data-window',
            modal: true,
            height: 300,
            width: 750,
            autoScroll: true,
            padding: 10,
            title: 'Status Change History : ' + title,
            items: [this.historyGrid],
            buttons: [{
                text: 'Close',
                scope: this,
                handler: function() { win.close(); }
            }]
        });
        win.show();
    },

    requestDetailWindow : function(record) {
        if (record)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                minHeight: 300,
                height: Ext4.getBody().getViewSize().height * .9,
                width: 650,
                autoScroll: true,
                title: "Sample Request Details",
                items: [{
                    xtype: 'panel',
                    frame: false,
                    border: false,
                    items: [
                        Ext4.create('LABKEY.ext4.biotrust.RecordDetailPanel', {
                            status : record.get("Status"),
                            recordId : record.get("RecordId"),
                            submitted : record.get("Submitted"),
                            containerId : record.get("Container")
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.InvestigatorDetailPanel', {
                            studyId : record.get("StudyId"),
                            studySurveyDesignId : record.get("StudySurveyDesignId"),
                            containerId : record.get("Container")
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.StudyRegistrationDetailPanel', {
                            studyId : record.get("StudyId"),
                            studySurveyDesignId : record.get("StudySurveyDesignId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.SampleRequestDetailPanel', {
                            tissueId : record.get("RowId"),
                            sampleId : record.get("SampleId"),
                            requestType : record.get("RequestType"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.GeneralPopulationRequirementsDetailPanel', {
                            sampleId : record.get("SampleId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.SamplePickupDetailPanel', {
                            sampleId : record.get("SampleId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.ApproverResponsePanel', {
                            status : record.get("Status"),
                            isApproval : record.get("IsApproval"),
                            tissueId : record.get("RowId"),
                            surveyId : record.get("SurveyRowId"),
                            containerId : record.get("Container"),
                            isRcRole : this.isRcRole,
                            readOnly : true
                        })
                    ]
                }],
                buttonAlign: 'right',
                buttons: [{
                    text: 'Close',
                    handler : function() { win.close(); }
                }]
            });
            win.show();
        }
    },

    configureGridColumns : function() {
        var columns = this.callParent();

        // add edit icon if user is an RC or Admin
        if ((this.isRcRole && this.canUpdate) || this.isAdmin)
        {
            columns.splice(0, 0, {
                text     : '&nbsp;',
                width    : 40,
                sortable : false,
                menuDisabled : true,
                renderer : function(value, meta, rec, idx, colIdx, store) {
                    if (rec.get("RowId") > 0 && rec.get("Status") != "Pending")
                        return '<span height="16px" class="edit-views-link" data-qtip="Edit Sample Request Status/Category"></span>';
                }
            });
        }
        else
        {
            // pad the RecordId column in the other non-Approved grids so the columns align
            Ext4.each(columns, function(col){
                if (col.dataIndex == "RecordId")
                    col.width = col.width + 40;
            });
        }

        columns.push({
            text     : 'Date Received',
            dataIndex: 'Submitted',
            type     : 'date',
            align    : 'center',
            width : 125,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        });

        columns.push({
            text     : 'Status',
            dataIndex: 'Status',
            minWidth : 170,
            renderer : function(value, meta, rec, idx, colIdx, store) {
                // make the status look clickable for review states, cellclick to open the approver review page
                var retVal = Ext4.util.Format.htmlEncode(value);
                if (rec.get("IsApproval"))
                {

                    retVal += " (" + rec.get("NumResponsesExist") + "/" + rec.get("NumResponsesExpected") + ")";
                    return "<a href='javascript:void(0);'>" + retVal + "</a>";
                }
                else if (value == 'Pending')
                {
                    return retVal += " (" + rec.get('DaysSinceModified') + ")";
                }
                else
                    return retVal;
            }
        });

        columns.push({
            text     : 'Last Status Change',
            dataIndex: 'LastStatusChange',
            align    : 'center',
            width : 160,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                // make the date look clickable, cellclick to open the 'view history' dialog handled by listener on grid
                return "<a href='javascript:void(0);'>" + Ext4.util.Format.date(value, 'm/d/Y') + "</a>";
            }
        });

        return columns;
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the Research Coordinator open studies and requests tab
 */
Ext4.define('LABKEY.ext4.biotrust.OpenRequestsDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.EditSampleRequestDashboardPanel',

    constructor : function(config) {
        Ext4.applyIf(config, {
            gridMinWidth: 1425,
            isRcRole: false,
            collapseGroups: false // whether or not to start the Study grouping section collapsed
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.addDocked(this.configureFilterCombo());
        this.gridPanel.on('render', function(cmp) {
            this.loadGridStore(cmp);
        }, this);
    },

    loadGridStore : function(grid) {

        var params = {};
        var filterObj = LABKEY.Filter.create('StudyStatus', 'Closed', LABKEY.Filter.Types.NOT_EQUAL);
        params[filterObj.getURLParameterName()] = filterObj.getURLParameterValue();

        // custom api action to get the grid rows in an attempt to improve loading perf
        this.gridPanel.setLoading(true);
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'getRCDashboardRequests.api', null, params),
            success : function(resp){
                grid.setLoading(false);
                o = Ext4.decode(resp.responseText);
                if (o && o.rows)
                {
                    grid.getStore().loadData(o.rows);

                    // add states and categories to the filter combo
                    if (this.studyStatusFilterCombo && this.studyCategoryFilterCombo)
                    {
                        if (o.studyStatus) {
                            Ext4.each(o.studyStatus, function(status){
                                if (!this.studyStatusFilterCombo.getStore().findRecord('StudyStatus', status))
                                    this.studyStatusFilterCombo.getStore().add({StudyStatus: status});
                            }, this);
                        }

                        if (o.studyCategory) {
                            Ext4.each(o.studyCategory, function(category){
                                if (!this.studyCategoryFilterCombo.getStore().findRecord('StudyCategory', category))
                                    this.studyCategoryFilterCombo.getStore().add({StudyCategory: category});
                            }, this);
                        }
                    }
                    this.updateDashboardGridFilter();
                }
            },
            failure : function(resp) {
                grid.setLoading(false);
            },
            scope   : this
        });
    },

    updateSampleRequestSuccess : function() {
        this.loadGridStore(this.gridPanel);
    },

    configureFilterCombo : function() {

        this.srCategoryFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Assigned To',
            width: 230,
            labelWidth : 95,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT Category FROM StudySampleRequests WHERE StudyStatus <> 'Closed'",
                containerFilter: "CurrentAndSubfolders",
                sort: "CategoryId",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'Category',
            valueField : 'Category',
            value : LABKEY.ActionURL.getParameter("category"), // initial value based on URL parameter
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

        this.srStatusFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Status',
            width: 190,
            labelWidth : 55,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT Status FROM StudySampleRequests WHERE StudyStatus <> 'Closed'",
                containerFilter: "CurrentAndSubfolders",
                sort: "Status",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'Status',
            valueField : 'Status',
            value : LABKEY.ActionURL.getParameter("status"), // initial value based on URL parameter
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

        this.srDaysSinceModCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Days Since Modified',
            width: 225,
            labelWidth : 140,
            editable : false,
            store : Ext4.create('Ext.data.ArrayStore', {
                fields: ['Name','MinValue','MaxValue'],
                data: [['0-7','0','7'], ['8-14','8','14'], ['15-30','15','30'], ['31-60','31','60'], ['61+','61',null]]
            }),
            queryMode : 'local',
            displayField : 'Name',
            valueField : 'MinValue',
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

        this.studyCategoryFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Assigned To',
            width: 230,
            labelWidth : 95,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT StudyCategory FROM StudySampleRequests WHERE StudyStatus <> 'Closed'",
                containerFilter: "CurrentAndSubfolders",
                sort: "StudyCategoryId",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'StudyCategory',
            valueField : 'StudyCategory',
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

        this.studyStatusFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Status',
            width: 190,
            labelWidth : 55,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT StudyStatus FROM StudySampleRequests WHERE StudyStatus <> 'Closed'",
                containerFilter: "CurrentAndSubfolders",
                sort: "StudyStatus",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'StudyStatus',
            valueField : 'StudyStatus',
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

//        this.dateReceivedFilter = Ext4.create('Ext.form.field.Date', {
//            fieldLabel : 'Date Received',
//            maxValue: new Date(),
//            listeners : {
//                scope: this,
//                change: this.updateDashboardGridFilter,
//                select: this.updateDashboardGridFilter
//            }
//        });

        return Ext4.create('Ext.form.Panel', {
            dock : 'top',
            layout: 'hbox',
            items : [
                { xtype: 'label',  width: 5 },
                {
                    xtype: 'fieldset',
                    title: 'Study Registration Filters',
                    layout: 'hbox',
                    padding: 3,
                    items: [
                        this.studyCategoryFilterCombo,
                        { xtype: 'label',  width: 10 },
                        this.studyStatusFilterCombo,
                        { xtype: 'label',  width: 10 },
                        {
                            xtype: 'button',
                            text: 'Reset',
                            scope: this,
                            handler: function() {
                                this.studyCategoryFilterCombo.setValue(null);
                                this.studyStatusFilterCombo.setValue(null);
                                this.updateDashboardGridFilter();
                            }
                        }
                    ]
                },
                { xtype: 'label',  width: 15 },
                {
                    xtype: 'fieldset',
                    title: 'Sample Request Filters',
                    layout: 'hbox',
                    padding: 3,
                    items: [
                        this.srCategoryFilterCombo,
                        { xtype: 'label',  width: 10 },
                        this.srStatusFilterCombo,
                        { xtype: 'label',  width: 10 },
                        this.srDaysSinceModCombo,
                        { xtype: 'label',  width: 10 },
                        {
                            xtype: 'button',
                            text: 'Reset',
                            scope: this,
                            handler: function() {
                                this.srCategoryFilterCombo.setValue(null);
                                this.srStatusFilterCombo.setValue(null);
                                this.srDaysSinceModCombo.setValue(null);
                                this.updateDashboardGridFilter();
                            }
                        }
                    ]
                }
//                this.dateReceivedFilter,
//                { xtype: 'label',  width: 25 },
            ]
        });
    },

    updateFilterComboStores : function() {
        this.srCategoryFilterCombo.getStore().load();
        this.srStatusFilterCombo.getStore().load();
        this.studyCategoryFilterCombo.getStore().load();
        this.studyStatusFilterCombo.getStore().load();
    },

    updateDashboardGridFilter : function() {
        var filters = [];

        var srCategoryFilterVal = this.srCategoryFilterCombo.getValue();
        if (srCategoryFilterVal)
            filters.push({property: "Category", value: srCategoryFilterVal});

        var srStatusFilterVal = this.srStatusFilterCombo.getValue();
        if (srStatusFilterVal)
            filters.push({property: "Status", value: srStatusFilterVal});

        var srDaysSinceModRecord = this.srDaysSinceModCombo.findRecordByValue(this.srDaysSinceModCombo.getValue());
        if (srDaysSinceModRecord)
        {
            filters.push({filterFn: function(rec){
                var val = rec.get("RowId") != 0 ? parseInt(rec.get("DaysSinceModified")) : null;
                var min = parseInt(srDaysSinceModRecord.get("MinValue"));
                var max = null;
                if (srDaysSinceModRecord.get("MaxValue"))
                    max = parseInt(srDaysSinceModRecord.get("MaxValue"));

                return val != null && val >= min && (max == null || val <= max);
            }});
        }

        var studyCategoryFilterVal = this.studyCategoryFilterCombo.getValue();
        if (studyCategoryFilterVal)
            filters.push({property: "StudyCategory", value: studyCategoryFilterVal});

        var studyFilterVal = this.studyStatusFilterCombo.getValue();
        if (studyFilterVal)
            filters.push({property: "StudyStatus", value: studyFilterVal});

        // TODO: this isn't working
//        var dateFilterVal = this.dateReceivedFilter.getValue();
//        if (dateFilterVal)
//            filters.push({property: "Submitted", value: dateFilterVal});

        var store = this.gridPanel.getStore();
        store.clearFilter();
        if (filters.length > 0)
            store.filter(filters);
    },

    configureStore : function() {

        // loading is controlled by loadGridStore
        return Ext4.create('Ext.data.Store', {
            model : 'Dashboard.View',
            autoLoad : false,
            pageSize : 10000, // more than 10 thousand rows will likely cause other issues anyway
            groupField : 'StudyId'
        });
    },

    getVisibleColumnNames : function() {
        return ["RecordId", "Type", "Preservation", "AnatomicalSite", "NumCases", "SubmittedBy", "Category"];
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the Requestor study registrations
 */
Ext4.define('LABKEY.ext4.biotrust.RequestorStudyRegDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.DashboardPanel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            gridMinWidth: 1050,
            submittedOnly: false,
            pendingOnly: false,
            isRcRole: false
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.on('cellclick', function(view, cell, cellIndex, record, row, rowIndex, e) {
            if (e.target.tagName == 'A' && view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'Label')
            {
                this.requestDetailWindow(record);
            }
        }, this);
    },

    configureStore : function() {

        return Ext4.create('Ext.data.Store', {
            model : 'Dashboard.View',
            autoLoad : true,
            pageSize : 10000, // more than 10 thousand rows will likely cause other issues anyway
            proxy : {
                type : 'ajax',
                url : LABKEY.ActionURL.buildURL('biotrust', 'getStudyDashboardData.api'),
                extraParams : {
                    submittedOnly: this.submittedOnly,
                    pendingOnly: this.pendingOnly
                },
                reader : { type : 'json', root : 'dashboard' }
            },
            sorters: [{property: 'RowId', direction: 'ASC'}],
            scope : this,
            listeners : {
                scope: this,
                'load' : function() {
                    if (this.gridPanel)
                        this.gridPanel.setLoading(false);
                }
            }
        });
    },

    rowEditClicked : function(record) {
        if (record)
        {
            // this dialog window lets you edit or delete a study registration
            var panel = Ext4.create('Ext.form.Panel', {
                border: false,
                minWidth: 300,
                maxWidth: 700,
                padding: 10,
                items: [],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Edit',
                    scope: this,
                    handler: function() {
                        // navigate to the update request page
                        window.location = LABKEY.ActionURL.buildURL('biotrust', 'updateStudyRegistration', record.get("ContainerPath"), {
                            rowId : record.get("RowId"),
                            srcURL : window.location.href
                        });
                    }
                },{
                    text: 'Delete',
                    disabled : record.get("Status") != "Pending" && !this.isRcRole,
                    tooltip : (record.get("Status") != "Pending" && !this.isRcRole) ? "You do not have permissions to delete registered studies. Please contact NWBT@uw.edu if you would like to delete a registered study." : null,
                    scope: this,
                    handler: function() {
                        // confirm that they really want to delete the request
                        Ext4.Msg.confirm('Confirm Study Registration Deletion', 'Are you sure you want to delete this study registration and all of its requests?', function(btn){
                            if (btn == 'yes')
                            {
                                var store = record.store;

                                win.getEl().mask("Deleting study registration...");
                                LABKEY.Query.deleteRows({
                                    containerPath: record.get("ContainerPath"),
                                    schemaName: 'survey',
                                    queryName: 'Surveys',
                                    rows: [{rowId: record.get("SurveyRowId")}],
                                    success: function(){
                                        win.getEl().unmask();
                                        win.close();

                                        // reload the requests grid
                                        store.load();
                                    },
                                    failure: function(resp){
                                        Ext4.Msg.alert('Error', resp.exception || 'An unknown error has ocurred.');
                                        win.getEl().unmask();
                                    }
                                });
                            }
                        }, this);
                    }
                },{
                    text: 'Close',
                    handler: function() { win.close(); }
                }]
            });

            panel.add(this.getDisplayField('Study Name', record.get("Label"), 100));
            panel.add(this.getDisplayField('Created By', record.get("CreatedBy"), 100));
            panel.add(this.getDisplayField('Created', Ext4.util.Format.date(record.get("Created"), 'm/d/Y'), 100));
            panel.add({xtype: 'label', html:'&nbsp;'}); // for spacing

            var win = Ext4.create('Ext.window.Window', {
                title: 'Update Study Registration',
                cls: 'data-window',
                modal: true,
                autoScroll: true,
                items: panel
            });
            win.show();
        }
    },

    getVisibleColumnNames : function() {
        return ["Label", "TotalDocumentTypes", "NumRecords", "CreatedBy", "Created", "ModifiedBy", "Modified"];
    },

    configureGridColumns : function() {
        var columns = this.callParent();

        // add status column with custom rendering
        columns.push({
            text     : 'Status',
            dataIndex: 'Status',
            minWidth : 150,
            renderer : function(view, meta, rec) {
                var val = rec.get('Status');
                if (val == 'Pending')
                    return '<span style="color: red;">' + val + '</span>';
                else
                    return val;
            }
        });

        // add edit icon if user has editor perm.
        if (LABKEY.user.canUpdate)
        {
            columns.splice(0, 0, {
                text     : '&nbsp;',
                width    : 40,
                sortable : false,
                menuDisabled : true,
                scope    : this,
                renderer : function(view, meta, rec, idx, colIdx, store) {
                    return '<span height="16px" class="edit-views-link" data-qtip="Edit/Delete Study Registration"></span>';
                }
            });
        }

        return columns;
    },

    requestDetailWindow : function(record) {
        if (record)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                height: 435,
                width: 650,
                autoScroll: true,
                title: "Study Registration Details",
                items: [{
                    xtype: 'panel',
                    frame: false,
                    border: false,
                    items: [
                        Ext4.create('LABKEY.ext4.biotrust.InvestigatorDetailPanel', {
                            studyId : record.get("RowId"),
                            studySurveyDesignId : record.get("SurveyDesignId"),
                            containerId : record.get("Container")
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.StudyRegistrationDetailPanel', {
                            studyId : record.get("RowId"),
                            studySurveyDesignId : record.get("SurveyDesignId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        })
                    ]
                }],
                buttonAlign: 'right',
                buttons: [{
                    text: 'Close',
                    handler : function() { win.close(); }
                }]
            });
            win.show();
        }
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the Requestor sample requests
 */
Ext4.define('LABKEY.ext4.biotrust.RequestorSRDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.DashboardPanel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            gridMinWidth: 1275,
            submittedOnly: false,
            pendingOnly: false,
            filterIsLocked: null,
            filterStatus: null, // the specific regisration status to filter on
            filterStatusType: LABKEY.Filter.Types.IN,
            collapseGroups: false // whether or not to start the Study grouping section collapsed
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.on('cellclick', function(view, cell, cellIndex, record, row, rowIndex, e) {
            if (e.target.tagName == 'A' && view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'StudyRecordId')
            {
                this.requestDetailWindow(record);
            }
        }, this);
    },

    configureStore : function() {

        var params = {};
        if (this.submittedOnly)
            params["query.Submitted~isnonblank"] = null;
        if (this.pendingOnly)
            params["query.Submitted~isblank"] = null;

        if (this.filterIsLocked != null)
        {
            var filterObj = LABKEY.Filter.create('IsLocked', this.filterIsLocked);
            params[filterObj.getURLParameterName()] = filterObj.getURLParameterValue();
        }
        else if (this.filterStatus)
        {
            var filterObj = LABKEY.Filter.create('Status', this.filterStatus, this.filterStatusType);
            params[filterObj.getURLParameterName()] = filterObj.getURLParameterValue();
        }

        return Ext4.create('Ext.data.Store', {
            model : 'Dashboard.View',
            autoLoad : true,
            pageSize : 10000, // more than 10 thousand rows will likely cause other issues anyway
            proxy : {
                type : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'selectRows.api', null, params),
                extraParams : {
                    schemaName: 'biotrust',
                    queryName: 'StudySampleRequests'
                },
                reader : { type : 'json', root : 'rows' }
            },
            groupField : 'StudyName',
            scope : this,
            listeners : {
                scope: this,
                'load' : function() {
                    if (this.gridPanel)
                        this.gridPanel.setLoading(false);
                }
            }
        });
    },

    configureGroupingFeature : function() {
        return Ext4.create('Ext.grid.feature.Grouping', {
            groupHeaderTpl : Ext4.create('Ext.XTemplate',
                '<table width="100%"><tr>',
                '<td valign="top">{groupValue}</td>',
                '<td width="125px" align="center">{[this.showStudyDetail(values, "StudyReceived")]}</td>',
                '<td width="140px" align="left">{[this.showStudyStatus(values)]}</td>',
                '</tr></table>',
                {
                    showStudyStatus : function(values) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data["StudyStatus"];
                            if (val == 'Pending')
                                return '<span style="color: red;">Pending</span>';
                            else
                                return val || '';
                        }
                        else
                            return '&nbsp;';
                    },

                    showStudyDetail : function(values, colName) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data[colName];
                            if (val != null && colName == 'StudyReceived')
                                return Ext4.util.Format.date(val, 'm/d/Y');
                            else
                                return val || "";
                        }
                        else
                            return '&nbsp;';
                    }
                }
            ),
            startCollapsed : this.collapseGroups
        });
    },

    rowEditClicked : function(record) {
        if (record)
        {
            var mappedRequestType = record.get("RequestTypeDisplay") || record.get("RequestType");
            var sectionTitle = record.get("RequestType") == "BloodSample" ? "Blood Samples" : "Tissue Samples";

            // this dialog window lets you edit or delete a pending request
            var panel = Ext4.create('Ext.form.Panel', {
                border: false,
                minWidth: 300,
                maxWidth: 700,
                padding: 10,
                items: [],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Edit',
                    scope: this,
                    handler: function() {
                        // navigate to the update request page
                        window.location = LABKEY.ActionURL.buildURL('biotrust', 'updateSampleRequest', null, {
                            rowId: record.get("SampleId"), sectionTitle: sectionTitle
                        });
                    }
                },{
                    text: 'Delete',
                    scope: this,
                    handler: function() {
                        // confirm that they really want to delete the request
                        Ext4.Msg.confirm('Confirm Pending Request Deletion', 'Are you sure you want to delete this pending sample request tissue/blood record and all of its data?', function(btn){
                            if (btn == 'yes')
                            {
                                var store = record.store;

                                win.getEl().mask("Deleting tissue/blood record...");
                                Ext4.Ajax.request({
                                    url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteTissueRecord.api'),
                                    method  : 'POST',
                                    jsonData: { tissueId : record.get("RowId") },
                                    success : function(resp){
                                        win.getEl().unmask();
                                        win.close();

                                        // reload the requests grid
                                        store.load();
                                    },
                                    failure : function(resp) {
                                        this.onFailure(resp);
                                        win.getEl().unmask();
                                    },
                                    scope   : this
                                });
                            }
                        }, this);
                    }
                },{
                    text: 'Close',
                    handler: function() { win.close(); }
                }]
            });
            panel.add(this.getDisplayField('ID', record.get("StudyRecordId"), 110));
            panel.add(this.getDisplayField('Request Type', mappedRequestType, 110));
            panel.add(this.getDisplayField('Study Name', record.get("StudyName"), 110));
            panel.add(this.getDisplayField('Created By', record.get("CreatedBy"), 110));
            panel.add(this.getDisplayField('Created', Ext4.util.Format.date(record.get("Created"), 'm/d/Y'), 110));
            panel.add({xtype: 'label', html:'&nbsp;'}); // for spacing

            var win = Ext4.create('Ext.window.Window', {
                title: 'Update Pending Sample Request',
                cls: 'data-window',
                modal: true,
                autoScroll: true,
                items: panel
            });
            win.show();
        }
    },

    getVisibleColumnNames : function() {
        return ["StudyRecordId", "Type", "Preservation", "AnatomicalSite", "NumCases",
                "CreatedBy", "Created", "SubmittedBy", "Submitted"];
    },

    configureGridColumns : function() {
        var columns = this.callParent();

        // add status column with custom rendering
        columns.push({
            text     : 'Status',
            dataIndex: 'Status',
            minWidth : 150,
            renderer : function(view, meta, rec) {
                var val = rec.get('Status');
                if (val == 'Pending')
                    return '<span style="color: red;">' + val + '</span>';
                else
                    return val;
            }
        });

        // add edit for the pending sample requests icon if user has editor perm.
        if (LABKEY.user.canUpdate)
        {
            columns.splice(0, 0, {
                text     : '&nbsp;',
                width    : 40,
                sortable : false,
                menuDisabled : true,
                renderer : function(view, meta, rec, idx, colIdx, store) {
                    // investigators can only edit pending requests
                    if (rec.get('Status') == 'Pending')
                        return '<span height="16px" class="edit-views-link" data-qtip="Edit Sample Request"></span>';
                    else
                        return '&nbsp;<img data-qtip="Please contact NWBT@uw.edu if you would like to modify a submitted request." height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_locked.png" alt="Locked">';
                }
            });
        }

        return columns;
    },

    requestDetailWindow : function(record) {
        if (record)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                minHeight: 300,
                height: Ext4.getBody().getViewSize().height * .9,
                width: 650,
                autoScroll: true,
                title: "Sample Request Details",
                items: [{
                    xtype: 'panel',
                    frame: false,
                    border: false,
                    items: [
                        Ext4.create('LABKEY.ext4.biotrust.RecordDetailPanel', {
                            idFieldLabel : "ID",
                            status : record.get("Status"),
                            recordId : record.get("StudyRecordId"),
                            submitted : record.get("Submitted"),
                            containerId : record.get("Container")
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.InvestigatorDetailPanel', {
                            studyId : record.get("StudyId"),
                            studySurveyDesignId : record.get("StudySurveyDesignId"),
                            containerId : record.get("Container")
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.StudyRegistrationDetailPanel', {
                            studyId : record.get("StudyId"),
                            studySurveyDesignId : record.get("StudySurveyDesignId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.SampleRequestDetailPanel', {
                            tissueId : record.get("RowId"),
                            sampleId : record.get("SampleId"),
                            requestType : record.get("RequestType"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.GeneralPopulationRequirementsDetailPanel', {
                            sampleId : record.get("SampleId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        }),
                        Ext4.create('LABKEY.ext4.biotrust.SamplePickupDetailPanel', {
                            sampleId : record.get("SampleId"),
                            containerId : record.get("Container"),
                            linkTarget : '_self'
                        })
                    ]
                }],
                buttonAlign: 'right',
                buttons: [{
                    text: 'Close',
                    handler : function() { win.close(); }
                }]
            });
            win.show();
        }
    },

    onFailure : function(resp, message) {
        var error = {};
        if (resp && resp.responseText)
            error = Ext4.decode(resp.responseText);
        else if (resp)
            error = resp;

        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the Approver Review of sample requests
 */
Ext4.define('LABKEY.ext4.biotrust.ApproverReviewDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.DashboardPanel',

    constructor : function(config) {

        Ext.applyIf(config, {
            gridMinWidth : 1425,
            isFacultyRole : false,
            isRcRole : false
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.addDocked(this.configureFilterCombo());

        this.gridPanel.on('cellclick', function(view, cell, cellIndex, record, row, rowIndex, e) {
            if (e.target.tagName == 'A' && view.panel.headerCt.getHeaderAtIndex(cellIndex).dataIndex == 'RecordId')
            {
                this.requestDetailWindow(record);
            }
        }, this);
    },

    configureFilterCombo : function() {

        this.requestTypeFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Filter request type by',
            width: 350,
            labelWidth : 140,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT RequestType, RequestTypeDisplay FROM StudySampleRequests",
                containerFilter: "CurrentAndSubfolders",
                sort: "RequestType",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'RequestTypeDisplay',
            valueField : 'RequestType',
            value : LABKEY.ActionURL.getParameter("requestType"), // initial value based on URL parameter
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });

        this.srStatusFilterCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel : 'Filter status by',
            width: 300,
            labelWidth : 100,
            editable : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql: "SELECT DISTINCT Status FROM StudySampleRequests WHERE IsApproval = true",
                containerFilter: "CurrentAndSubfolders",
                sort: "Status",
                autoLoad: true
            }),
            queryMode : 'local',
            displayField : 'Status',
            valueField : 'Status',
            value : LABKEY.ActionURL.getParameter("status"), // initial value based on URL parameter
            listeners : {
                scope : this,
                change : this.updateDashboardGridFilter
            }
        });


        // add listener to grid store to apply the initial filter values
        this.gridPanel.getStore().on('load', this.updateDashboardGridFilter, this, {single: true});

        return Ext4.create('Ext.form.FieldContainer', {
            dock : 'top',
            fieldLabel : '',
            layout: 'hbox',
            style: 'margin: 7px;',
            items : [
                this.srStatusFilterCombo,
                {
                    xtype: 'label', // spacer
                    width: 25
                },
                this.requestTypeFilterCombo,
                {
                    xtype: 'label', // spacer
                    width: 25
                },
                {
                    xtype: 'button',
                    text: 'Clear Filters',
                    scope: this,
                    handler: function() {
                        this.requestTypeFilterCombo.setValue(null);
                        this.srStatusFilterCombo.setValue(null);
                        this.gridPanel.getStore().clearFilter();
                    }
                }
            ]
        });
    },

    updateDashboardGridFilter : function(cmp, newValue, oldValue) {
        var filters = [];

        var requestTypeFilterVal = this.requestTypeFilterCombo.getValue();
        if (requestTypeFilterVal)
            filters.push({property: "RequestType", value: requestTypeFilterVal});

        var statusFilterVal = this.srStatusFilterCombo.getValue();
        if (statusFilterVal)
            filters.push({property: "Status", value: statusFilterVal});

        var store = this.gridPanel.getStore();
        store.clearFilter();
        if (filters.length > 0)
            store.filter(filters);
    },

    configureStore : function() {

        var params = {"query.IsApproval~eq": true, "query.StudyStatus~neq": 'Closed'};
        if (this.isFacultyRole && !this.isRcRole)
            params["query.UserReviewResponseExpected~eq"] = true;

        return Ext4.create('Ext.data.Store', {
            model : 'Dashboard.View',
            autoLoad : true,
            pageSize : 10000, // more than 10 thousand rows will likely cause other issues anyway
            proxy : {
                type : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'selectRows.api', null, params),
                extraParams : {
                    schemaName: 'biotrust',
                    queryName: 'StudySampleRequests',
                    containerFilter: 'CurrentAndSubfolders'
                },
                reader : { type : 'json', root : 'rows' }
            },
            groupField : 'StudyId',
            scope : this,
            listeners : {
                scope: this,
                'load' : function() {
                    if (this.gridPanel)
                        this.gridPanel.setLoading(false);
                }
            }
        });
    },

    configureGroupingFeature : function() {
        return Ext4.create('Ext.grid.feature.Grouping', {
            groupHeaderTpl : Ext4.create('Ext.XTemplate',
                '<table width="100%"><tr>',
                '<td valign="top">{[this.formatValue(values)]}</td>',
                '<td width="80px" align="left">{[this.showStudyDetail(values, "StudyCategory")]}</td>',
                '<td width="135px" align="center">{[this.showStudyDetail(values, "StudyReceived")]}</td>',
                '<td width="140px" align="left">{[this.showStudyStatus(values)]}</td>',
                '</tr></table>',
                {
                    formatValue: function(values) {
                        var value = values.groupValue;

                        if (values.rows.length > 0)
                        {
                            var studyName = values.rows[0].data["StudyName"];
                            if (studyName.length > 85)
                                studyName = studyName.substring(0, 85) + "...";

                            var studyId = values.rows[0].data["StudyId"];
                            var url = LABKEY.ActionURL.buildURL('project', 'begin', values.rows[0].data["Container"]);
                            var containerLink = "<a href='" + url + "'>" + values.rows[0].data["ContainerName"] + "</a>";
                            value = studyId + " " + studyName + " (" + containerLink + ")";
                        }

                        return value;
                    },

                    showStudyStatus : function(values) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data["StudyStatus"];
                            if (val == 'In Review')
                                return '<span style="color: red;">In Review</span>';
                            else if (val == 'Active')
                                return '<span style="color: green;">Active</span>';
                            else
                                return val || '';
                        }
                        else
                            return '&nbsp;';
                    },

                    showStudyDetail : function(values, colName) {
                        if (values.rows.length > 0)
                        {
                            var val = values.rows[0].data[colName];
                            if (val != null && colName == 'StudyReceived')
                                return Ext4.util.Format.date(val, 'm/d/Y');
                            else
                                return val || "";
                        }
                        else
                            return '&nbsp;';
                    }
                }
            )
        });
    },

    getVisibleColumnNames : function() {
        return ["RecordId", "Type", "Preservation", "AnatomicalSite", "NumCases", "SubmittedBy", "Category"];
    },

    configureGridColumns : function() {
        var columns = this.callParent();

        // column to indicate if this user has reviewed a particular sample request tissue record
        if (this.isFacultyRole)
        {
            columns.splice(0, 0, {
                text     : '',
                sortable : false,
                dataIndex: 'UserReviewResponseExists',
                width : 25,
                align: 'center',
                renderer : function(value) {
                    return value ? "<img src='" + LABKEY.contextPath + "/biotrust/images/check.png' height=15 width=15 />" : "";
                }
            });
        }

        columns.push({
            text     : 'Date Received',
            dataIndex: 'Submitted',
            type     : 'date',
            align    : 'center',
            width : 125,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        });

        columns.push({
            text     : 'Status',
            dataIndex: 'Status',
            minWidth : 150,
            renderer : function(value, meta, rec, idx, colIdx, store) {
                return Ext4.util.Format.htmlEncode(value);
            }
        });

        return columns;
    },

    requestDetailWindow : function(record) {
        if (record)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                minHeight: 300,
                height: Ext4.getBody().getViewSize().height * .9,
                width: 650,
                autoScroll: true,
                title: record.get('Status') + " Assessment Details",
                items: [
                    Ext4.create('LABKEY.ext4.biotrust.ApproverReviewPanel', {
                        status : record.get('Status'),
                        isApproval : record.get('IsApproval'),
                        submitted: record.get('Submitted'),
                        recordId: record.get('RecordId'),
                        tissueId: record.get("RowId"),
                        surveyId: record.get("SurveyRowId"),
                        sampleId: record.get("SampleId"),
                        studyId: record.get("StudyId"),
                        studySurveyDesignId: record.get("StudySurveyDesignId"),
                        requestType: record.get("RequestTypeDisplay") || record.get("RequestType"),
                        containerId: record.get("Container"),
                        reviewerSelected : record.get("UserReviewResponseExpected"),
                        isFacultyRole: this.isFacultyRole,
                        isRcRole: this.isRcRole,
                        listeners: {
                            closeWindow : function() {
                                win.close();

                                // reload the grid store to update the UserReviewResponseExists icon
                                record.store.load();
                            }
                        }
                    })
                ],
                buttonAlign: 'right',
                buttons: [{
                    text: 'Print',
                    hrefTarget: '_blank',
                    href: LABKEY.ActionURL.buildURL('biotrust','approverReviewDetails', null, {
                        rowId: record.get("RowId"),
                        "_print": 1
                    })
                },{
                    text: 'Close',
                    handler : function() { win.close(); }
                }]
            });
            win.show();
        }
    }
});

/*
 * Override of the generic BioTrust DashboardPanel with specifics for the RC closed studies tab
 */
Ext4.define('LABKEY.ext4.biotrust.ClosedStudiesDashboardPanel', {

    extend : 'LABKEY.ext4.biotrust.EditSampleRequestDashboardPanel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            gridMinWidth: 1425,
            isRcRole: false,
            collapseGroups: false // whether or not to start the Study grouping section collapsed
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.gridPanel.on('render', function(cmp) {
            this.loadGridStore(cmp);
        }, this);
    },

    configureStore : function() {

        // loading is controlled by loadGridStore
        return Ext4.create('Ext.data.Store', {
            model : 'Dashboard.View',
            autoLoad : false,
            pageSize : 10000, // more than 10 thousand rows will likely cause other issues anyway
            groupField : 'StudyId'
        });
    },

    loadGridStore : function(grid) {

        var params = {};
        var filterObj = LABKEY.Filter.create('StudyStatus', 'Closed');
        params[filterObj.getURLParameterName()] = filterObj.getURLParameterValue();

        // custom api action to get the grid rows in an attempt to improve loading perf
        this.gridPanel.setLoading(true);
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'getRCDashboardRequests.api', null, params),
            success : function(resp){
                grid.setLoading(false);
                o = Ext4.decode(resp.responseText);
                if (o && o.rows)
                {
                    grid.getStore().loadData(o.rows);
                }
            },
            failure : function(resp) {
                grid.setLoading(false);
            },
            scope   : this
        });
    },

    updateSampleRequestSuccess : function() {
        this.loadGridStore(this.gridPanel);
    },

    getVisibleColumnNames : function() {
        return ["RecordId", "Type", "Preservation", "AnatomicalSite", "NumCases", "SubmittedBy", "Category"];
    },

    onFailure : function(resp, message) {
        var error = {};
        if (resp && resp.responseText)
            error = Ext4.decode(resp.responseText);
        else if (resp)
            error = resp;

        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');
    }
});