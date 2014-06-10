/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss('biotrust/NWBioTrust.css');

/* TODO: THIS IS NO LONGER IS USER FOR THE STUDY REGISTRATION WIZARD AND CAN LIKELY BE REMOVED */
Ext4.define('LABKEY.ext4.biotrust.StudySampleRequestPanel', {

    extend : 'Ext.panel.Panel',

    alias: 'widget.biotrust-studysamplerequestpanel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame  : false,
            border : false,
            isSubmitted : false,
            canEdit : true
        });

        this.callParent([config]);

        this.addEvents('saveSurvey');
    },

    initComponent : function() {
        this.studyId = LABKEY.ActionURL.getParameter("rowId") || -1;

        this.items = [
            this.getTopButtonsCfg(),
            this.getTissueRecordCfgs(),
            this.getBottomButtonsCfg()
        ];

        this.callParent();
    },

    getTissueRecordCfgs : function() {

        this.sampleRequestGrid = Ext4.create('Ext.grid.Panel', {
            cls: 'sample-request-panel',
            border: true,
            forceFit: true,
            emptyText: 'No sample requests have been added to this study.',
            minHeight: 30,
            collapsible: false,
            features: Ext4.create('Ext.grid.feature.Grouping', {
                groupHeaderTpl : [
                    '<span class="category-label">{groupValue}</span>'
                ]
            }),
            store: Ext4.create('LABKEY.ext4.Store', {
                groupField : 'RequestTypeDisplay',
                schemaName: "biotrust",
                queryName: "StudySampleRequests",
                columns: "RowId,RecordId,SampleId,RequestType,RequestTypeDisplay,Type,AnatomicalSite",
                filterArray: [LABKEY.Filter.create('StudyId', this.studyId)],
                sort: "RequestTypeDisplay,RecordId",
                autoLoad: true,
                listeners: {
                    scope: this,
                    beforeload: function(){ this.getEl().mask("Loading..."); },
                    load: function(store, records) { this.getEl().unmask(); }
                }
            }),
            columns: [
                { text: 'Request ID',  dataIndex: 'RecordId', width: 20, menuDisabled : true, style: 'font-weight: bold' },
                { text: 'Tissue/Tube Type',  dataIndex: 'Type', menuDisabled : true, style: 'font-weight: bold' },
                { text: 'Anatomical Site',  dataIndex: 'AnatomicalSite', menuDisabled : true, style: 'font-weight: bold' }
            ],
            selModel: Ext4.create('Ext.selection.RowModel', {
                allowDeselect: true,
                listeners: {
                    scope: this,
                    selectionchange: function() {
                        this.down('#edit-selected-btn').setDisabled(!this.sampleRequestGrid.getSelectionModel().hasSelection());
                        this.down('#delete-selected-btn').setDisabled(!this.sampleRequestGrid.getSelectionModel().hasSelection());
                    }
                }
            })
        });

        return this.sampleRequestGrid;
    },

    getTopButtonsCfg : function() {
        return Ext4.create('Ext.panel.Panel', {
            border: false,
            frame: false,
            bodyStyle: 'padding-bottom: 10px;',
            defaults: {
                width: 250,
                height: 25,
                style : 'margin: 7px;'
            },
            items: [
                {
                    xtype: 'button',
                    hidden: !this.canEdit,
                    text: 'New Sample Request',
                    scope: this,
                    handler: function(){ this.newSampleRequestHandler(); }
                }
            ]
        });
    },

    getBottomButtonsCfg : function() {
        return Ext4.create('Ext.panel.Panel', {
            border: false,
            frame: false,
            bodyStyle: 'padding-top: 10px;',
            defaults: {
                width: 150,
                height: 25,
                style : 'margin: 7px;'
            },
            items: [
                {
                    xtype: 'button',
                    itemId: 'edit-selected-btn',
                    text: this.canEdit ? 'Edit Selected' : 'View Selected',
                    disabled: true, // disabled until grid selection
                    handler: function() {
                        var selected = this.sampleRequestGrid.getSelectionModel().getLastSelected();
                        if (selected)
                        {
                            window.location = LABKEY.ActionURL.buildURL("biotrust", "updateSampleRequest", null, {
                                rowId: selected.get("SampleId"),
                                studyId: this.studyId,
                                studySectionTitle: 'Sample Requests',
                                sectionTitle: (selected.get("RequestTypeDisplay") || selected.get("RequestType")) + "s"
                            });
                        }
                    },
                    scope: this
                },
                {
                    xtype: 'button',
                    hidden: !this.canEdit,
                    itemId: 'delete-selected-btn',
                    text: 'Delete Selected',
                    disabled: true, // disabled until grid selection
                    handler: function() {
                        var selected = this.sampleRequestGrid.getSelectionModel().getLastSelected();
                        if (selected)
                        {
                            // confirm that they really want to delete the tissue record
                            Ext4.Msg.confirm('Confirm Sample Request Deletion', 'Are you sure you want to delete this tissue/blood record and all of its data?', function(btn){
                                if (btn == 'yes')
                                {
                                    this.getEl().mask("Deleting tissue/blood record...");
                                    Ext4.Ajax.request({
                                        url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteTissueRecord.api'),
                                        method  : 'POST',
                                        jsonData: { tissueId : selected.get("RowId") },
                                        success : function(resp){
                                            this.sampleRequestGrid.getStore().load();
                                        },
                                        failure : function(resp) {
                                            this.onFailure(resp);
                                            this.getEl().unmask();
                                        },
                                        scope: this
                                    });
                                }
                            }, this);
                        }
                    },
                    scope: this
                }
            ]
        });
    },

    newSampleRequestHandler : function(requestType) {
        var params = {
            requestType: requestType,
            studySectionTitle: 'Sample Requests'
        };

        // if we have a study ID, then this is an existing study and we can go, else fire the saveSurvey event
        if (this.studyId != -1)
        {
            params['studyId'] = this.studyId;
            window.location = LABKEY.ActionURL.buildURL("biotrust", "updateSampleRequest", null, params);
        }
        else
        {
            Ext4.Msg.confirm('Confirm Save Study Registration', 'The study registration has not yet been saved. Would you like your changes to be saved before continuing?', function(btn){
                if (btn == 'yes')
                    this.fireEvent('saveSurvey', LABKEY.ActionURL.buildURL("biotrust", "updateSampleRequest", null, params), "studyId");
            }, this);
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