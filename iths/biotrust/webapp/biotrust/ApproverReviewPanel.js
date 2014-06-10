/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("biotrust/RequestDetailsPanel.js");

Ext4.define('LABKEY.ext4.biotrust.ApproverReviewPanel', {
    extend: 'Ext.panel.Panel',

    bubbleEvents : ['closeWindow'],

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            border: false,
            frame: false,
            bodyStyle: 'background-color: transparent;'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        var items = [];

        items.push(Ext4.create('LABKEY.ext4.biotrust.RecordDetailPanel', {
            status : this.status,
            recordId : this.recordId,
            submitted : this.submitted,
            containerId : this.containerId
        }));

        items.push(Ext4.create('LABKEY.ext4.biotrust.InvestigatorDetailPanel', {
            studyId : this.studyId,
            studySurveyDesignId : this.studySurveyDesignId,
            containerId : this.containerId
        }));

        items.push(Ext4.create('LABKEY.ext4.biotrust.StudyRegistrationDetailPanel', {
            studyId : this.studyId,
            studySurveyDesignId : this.studySurveyDesignId,
            containerId : this.containerId
        }));

        items.push(Ext4.create('LABKEY.ext4.biotrust.SampleRequestDetailPanel', {
            tissueId : this.tissueId,
            sampleId : this.sampleId,
            requestType : this.requestType,
            containerId : this.containerId
        }));

        items.push(Ext4.create('LABKEY.ext4.biotrust.GeneralPopulationRequirementsDetailPanel', {
            tissueId : this.tissueId,
            sampleId : this.sampleId,
            requestType : this.requestType,
            containerId : this.containerId
        }));

        // only RC or Faculty Reviewers should see responses
        if (this.isFacultyRole || this.isRcRole)
        {
            items.push(Ext4.create('LABKEY.ext4.biotrust.ApproverResponsePanel', {
                status : this.status,
                isApproval : this.isApproval,
                tissueId : this.tissueId,
                surveyId : this.surveyId,
                containerId : this.containerId,
                isFacultyRole: this.isFacultyRole,
                reviewerSelected: this.reviewerSelected,
                isRcRole : this.isRcRole,
                bubbleEvents : ['closeWindow']
            }));
        }

        this.items = items;

        this.callParent();
    }
});

Ext4.define('LABKEY.ext4.biotrust.ApproverResponsePanel', {
    extend: 'Ext.form.Panel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            frame: false,
            border: false,
            width: 630,
            padding: 5,
            bodyStyle: 'background-color: transparent;',
            fieldSetTitle: 'Approval Review Response'
        });

        Ext4.define('Reviewer.Document.View', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name',         type : 'string'},
                {name : 'type',         type : 'string'},
                {name : 'typeId',       type : 'int'},
                {name : 'createdBy',    type : 'string'},
                {name : 'created',      type : 'date'},
                {name : 'downloadURL',  type : 'string'}
            ]
        });

        this.callParent([config]);

        this.addEvents('closeWindow');
    },

    initComponent : function() {
        var printView = LABKEY.ActionURL.getParameter("_print") != null;

        // set the fieldSetTitle based on the study registration status
        if (this.status && this.isApproval)
            this.fieldSetTitle += " - " + this.status;

        var items = [];

        // show the review form to users in the FacultyRole
        if (this.isFacultyRole && this.reviewerSelected)
        {
            items.push({
                xtype: 'radiogroup',
                fieldLabel: 'Recommendation',
                labelAlign: 'top',
                columns: 1,
                vertical: true,
                allowBlank: printView,
                defaults: {
                    width: 600,
                    readOnly: printView
                },
                items: [
                    { boxLabel: 'Approve, no changes needed', name: 'ActionType', inputValue: 'Reviewed, Approved'},
                    { boxLabel: 'Approve, if comments below are addressed', name: 'ActionType', inputValue: 'Reviewed, Approved with Changes'},
                    { boxLabel: 'Not Feasible as submitted, see comments below', name: 'ActionType', inputValue: 'Reviewed, Not Feasible'}
                ]
            });

            items.push({
                xtype: 'textarea',
                name: 'Comment',
                fieldLabel: 'Changes Requested / Notes / Comments',
                labelAlign: 'top',
                anchor: '99%',
                height: 90,
                readOnly: printView,
                allowBlank: printView
            });

            items.push({
                xtype: 'button',
                text: 'Submit',
                hidden: printView,
                formBind: true,
                scope: this,
                handler: function(btn) {
                    var values = btn.up('form').getValues();

                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('biotrust', 'saveReviewerResponse.api'),
                        method  : 'POST',
                        params  : {
                            rowId   : this.tissueId,
                            surveyId : this.surveyId,
                            status : this.status,
                            actionType : values["ActionType"],
                            comment : values["Comment"],
                            containerId : this.containerId // container to look for tissue record exists
                        },
                        success: function(response) {
                            this.fireEvent('closeWindow');
                        },
                        scope : this
                    });
                }
            });
        }

        this.items = [{
            xtype: 'fieldset',
            title: this.fieldSetTitle,
            itemId: 'ApprovalReviewResponse',
            collapsible: false,
            anchor: '99%',
            defaults: {
                labelStyle: 'font-style: italic;',
                labelWidth: 150
            },
            items: items
        }];

        this.callParent();

        this.getPreviousResponses();

        this.getResponseDocuments();
    },

    getRCComments : function() {
        var filters = [
            LABKEY.Filter.create('IntKey2', this.surveyId),
            LABKEY.Filter.create('Key1', 'Sample Request Status Changed'), // Key1 = Action
            LABKEY.Filter.create('Key2', this.status), // Key2 = Status
            LABKEY.Filter.create('Key3', this.status, LABKEY.Filter.Types.NOT_EQUAL) // Key2 = PreviousStatus
        ];

        // query to see if this user has already responded to this review request, and display the results if so
        LABKEY.Query.selectRows({
            schemaName: 'auditLog',
            queryName: 'BioTrustAuditEvent',
            filterArray: filters,
            columns: 'Date,CreatedBy/DisplayName,Comment',
            sort: '-Date',
            success: function(data) {
                for (var i = 0; i < data.rows.length; i++)
                {
                    // insert the RC comments before the approval review form and previous responses
                    var row = data.rows[i];
                    var fieldSetForm = this.down('fieldset[itemId=ApprovalReviewResponse]');
                    if (fieldSetForm)
                    {
                        fieldSetForm.insert(0, {
                            xtype: 'form',
                            border: true,
                            padding: 5,
                            bodyStyle: 'padding: 3px;',
                            defaults : {
                                xtype: 'displayfield',
                                labelStyle: 'font-weight: bold;',
                                labelWidth: 125
                            },
                            items: [{
                                xtype: 'fieldcontainer',
                                fieldLabel: '',
                                layout: 'hbox',
                                items: [{
                                    xtype: 'displayfield',
                                    width: 300,
                                    labelStyle: 'font-weight: bold;',
                                    labelWidth: 125,
                                    fieldLabel: 'Review Date',
                                    value: Ext4.util.Format.date(row['Date'], 'm/d/Y')
                                },{
                                    xtype: 'displayfield',
                                    labelStyle: 'font-weight: bold;',
                                    labelWidth: 125,
                                    fieldLabel: 'Research Coord.',
                                    value: Ext4.util.Format.htmlEncode(row['CreatedBy/DisplayName'])
                                }]
                            },{
                                fieldLabel: 'Comments',
                                value: Ext4.util.Format.htmlEncode(row['Comment'])
                            }]
                        });
                    }
                }
            },
            scope: this
        });
    },

    getPreviousResponses : function() {
        var filters = [
            LABKEY.Filter.create('IntKey2', this.surveyId),
            LABKEY.Filter.create('Key1', 'Reviewed,', LABKEY.Filter.Types.STARTS_WITH), // Key1 = Action
            LABKEY.Filter.create('Key2', this.status) // Key2 = Status
        ];

        // filter for specific user if this is a faculty reviewer
        if (this.isFacultyRole)
            filters.push(LABKEY.Filter.create('CreatedBy', LABKEY.user.id));

        // query to see if this user has already responded to this review request, and display the results if so
        LABKEY.Query.selectRows({
            schemaName: 'auditLog',
            queryName: 'BioTrustAuditEvent',
            filterArray: filters,
            columns: 'Date,CreatedBy/DisplayName,Key1,Key2,Comment',
            sort: '-Date',
            success: function(data) {
                for (var i = 0; i < data.rows.length; i++)
                {
                    var row = data.rows[i];
                    var recommendation = Ext4.util.Format.htmlEncode(row['Key1']);
                    if (row['Key2'])
                    {
                        recommendation = recommendation.replace('Reviewed', row['Key2']);
                    }

                    // insert the previous records before the approval review form
                    var fieldSetForm = this.down('fieldset[itemId=ApprovalReviewResponse]');
                    if (fieldSetForm)
                    {
                        fieldSetForm.insert(0, {
                            xtype: 'form',
                            border: true,
                            padding: 5,
                            bodyStyle: 'padding: 3px;',
                            defaults : {
                                xtype: 'displayfield',
                                labelStyle: 'font-weight: bold;',
                                labelWidth: 125
                            },
                            items: [{
                                xtype: 'fieldcontainer',
                                fieldLabel: '',
                                layout: 'hbox',
                                items: [{
                                    xtype: 'displayfield',
                                    width: 300,
                                    labelStyle: 'font-weight: bold;',
                                    labelWidth: 125,
                                    fieldLabel: 'Response Date',
                                    value: Ext4.util.Format.date(row['Date'], 'm/d/Y')
                                },{
                                    xtype: 'displayfield',
                                    labelStyle: 'font-weight: bold;',
                                    labelWidth: 80,
                                    fieldLabel: 'Approver',
                                    value: Ext4.util.Format.htmlEncode(row['CreatedBy/DisplayName'])
                                }]
                            },{
                                fieldLabel: 'Recommendation',
                                value: recommendation
                            },{
                                fieldLabel: 'Comments',
                                value: Ext4.util.Format.htmlEncode(row['Comment'])
                            }]
                        });
                    }
                }

                if (this.isApproval)
                    this.getRCComments();
            },
            scope: this
        });
    },

    getResponseDocuments : function() {
        if (this.isRcRole)
        {
            var printView = LABKEY.ActionURL.getParameter("_print") != null;

            var fieldSetForm = this.down('fieldset[itemId=ApprovalReviewResponse]');
            if (fieldSetForm)
            {
                this.gridPanel = Ext4.create('Ext.grid.Panel', {
                    border: false,
                    padding: '0 0 5px 0',
                    multiSelect: false,
                    forceFit: true,
                    viewConfig: {
                        emptyText: '<span style="font-style:italic;">No documents to show</span>',
                        deferEmptyText: true
                    },
                    columns: [{
                        text: 'Document Name',
                        dataIndex: 'name',
                        menuDisabled : true,
                        flex: 1,
                        renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                            return "<a href='" + record.get("downloadURL") + "' target='_blank'>" + value + "</a>";
                        }
                    },{
                        xtype    : 'actioncolumn',
                        maxWidth : 40,
                        fles     : 1,
                        align    : 'center',
                        sortable : false,
                        menuDisabled : true,
                        hidden: printView || this.readOnly,
                        items: [{
                            icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_42 + '/resources/ext-theme-access/images/qtip/close.gif',
                            tooltip : 'Delete',
                            handler: function(grid, rowIndex, colIndex) {
                                var record = grid.getStore().getAt(rowIndex);
                                // call the API to delete the given file from the document set (by documen type)
                                grid.getEl().mask("Deleting document...");
                                Ext4.Ajax.request({
                                    url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteSingleDocument.api'),
                                    method  : 'POST',
                                    jsonData: {
                                        recordContainerId : this.containerId,
                                        ownerId : this.tissueId,
                                        ownerType : 'tissue',
                                        documentTypeId : record.get("typeId"),
                                        fileName : record.get("name")
                                    },
                                    success : function(resp){
                                        grid.getEl().unmask();

                                        // reload the files grid panel store
                                        this.loadDocumentSetData();
                                    },
                                    failure : function(resp) {
                                        grid.getEl().unmask();
                                        this.onFailure(resp)
                                    },
                                    scope   : this
                                });                    },
                            scope: this
                        }]
                    }],
                    store: Ext4.create('Ext.data.Store', {
                        model : 'Reviewer.Document.View',
                        sorters: [ { property: 'name', direction: 'ASC' } ],
                        scope : this
                    }),
                    listeners: {
                        scope: this,
                        viewReady: this.loadDocumentSetData,
                        beforeselect : function() { return false; } // prevent row selection
                    }
                });

                // give RCs an option to upload a review response attachment (document set associated with tissue record)
                var items = [this.gridPanel];
                items.push({
                    xtype: 'container',
                    width: 110,
                    hidden: printView || this.readOnly,
                    html : '<a href="javascript:void(0);"><img src="' + LABKEY.contextPath + '/_images/paperclip.gif">&nbsp;&nbsp;Attach a file</a>',
                    listeners: {
                        scope: this,
                        render : function(cmp) {
                            cmp.getEl().on('click', this.onAttachFile, this);
                        }
                    }
                });

                this.filesPanel = Ext4.create('Ext.panel.Panel', {
                    border: false,
                    layout: 'anchor',
                    defaults: { anchor: '100%' },
                    items: items
                });

                var formItems = [{
                    border: false,
                    width: 150,
                    items: [{
                        xtype: 'label',
                        style: 'font-weight: bold;',
                        html: 'Reviewer Response Documents:'
                    }]
                },{
                    border: false,
                    columnWidth: 1.0,
                    items: [this.filesPanel]
                }];

                fieldSetForm.add({
                    xtype: 'panel',
                    border: true,
                    padding: 5,
                    bodyStyle: 'padding: 3px;',
                    layout: 'column',
                    items: formItems
                });
            }
        }
    },

    loadDocumentSetData : function() {
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'getDocumentSet.api'),
            method  : 'POST',
            jsonData: {
                rowId : this.tissueId,
                ownerType: 'tissue',
                documentTypeName: 'Approval Reviewer Response'
            },
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                if (o.documentSet)
                {
                    // load the documentset data into the store
                    if (this.gridPanel)
                        this.gridPanel.getStore().loadData(o.documentSet);
                }
                else
                    this.onFailure(o);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onAttachFile: function() {
        // open a dialog so that we can add the file directly to the document set and test for duplicates, etc.
        var win = Ext4.create('Ext.window.Window', {
            title: 'Add Reviewer Response Document',
            cls: 'data-window',
            modal: true,
            items: [Ext4.create('Ext.form.Panel', {
                border: false,
                padding: 10,
                items: [{
                    xtype: 'hidden', name: 'tissueId', value: this.tissueId
                },{
                    xtype: 'filefield',
                    name : 'reviewerdocument',
                    buttonText : 'Browse',
                    width : 300,
                    allowBlank : false
                }],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Submit',
                    formBind: true,
                    scope: this,
                    handler: function(cmp) {
                        var form = cmp.up('form').getForm();
                        var values = form.getValues();

                        if (form.hasUpload() && form.isValid())
                        {
                            win.getEl().mask("Submitting document...");
                            var options = {
                                method  :'POST',
                                params  : {
                                    ownerId : values.tissueId,
                                    recordContainerId : this.containerId,
                                    ownerType: 'tissue',
                                    documentTypeName: 'Approval Reviewer Response'
                                },
                                url     : LABKEY.ActionURL.buildURL('biotrust', 'saveDocuments.api'),
                                success : function(cmp, resp) {
                                    win.getEl().unmask();
                                    win.close();

                                    this.loadDocumentSetData();
                                },
                                failure : function(cmp, resp) {
                                    win.getEl().unmask();
                                    this.onFailure(resp.response);
                                },
                                scope : this
                            };
                            form.submit(options);
                        }
                    }
                },{
                    text: 'Cancel',
                    handler: function() { win.close(); }
                }]
            })]
        });
        win.show();
    },

    onFailure : function(resp) {
        var message = null;
        if (resp.responseText)
        {
            var error = Ext4.decode(resp.responseText);
            if (error.exception)
                message = error.exception;
            else if (error.errorInfo)
                message = error.errorInfo;
        }

        Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');
    }
});