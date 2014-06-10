/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/biotrust/DocumentAttachment.js");

/**
 * The file input field for documents attached as either a file or a linked URL (stored as document set)
 */
Ext4.define('LABKEY.ext4.biotrust.DocumentUploadQuestion', {
    extend  : 'Ext.form.Panel',
    alias: 'widget.biotrust-documentupload',
    childEls: [
        /**
         * @property {Ext.Element} attachFile
         * A reference to the element wrapping the attach a file link. Only set after the field has been rendered.
         */
        'attachFile'
    ],

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            border: false,
            frame: false,
            anchor: '100%',
            minHeight: 75,
            layout: 'column',
            showFirstFile: true,
            windowTitle: 'Add Document'
        });

        Ext4.define('BioTrust.Document.View', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name',         type : 'string'},
                {name : 'type',         type : 'string'},
                {name : 'typeId',       type : 'int'},
                {name : 'active',       type : 'boolean'},
                {name : 'createdBy',    type : 'string'},
                {name : 'created',      type : 'date'},
                {name : 'downloadURL',  type : 'string'},
                {name : 'isLinkedDocument', type : 'boolean'}
            ]
        });

        this.callParent([config]);

        this.addEvents('saveSurvey');
    },

    initComponent : function() {

        // this component can be used for either study, sample request, or tissue record document set upload
        this.tissueId = null;
        this.sampleRequestId = null;
        this.studyId = null;

        this.filesPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            layout: 'anchor',
            defaults: { anchor: '100%' },
            items: [
                this.configureAttachmentsGrid(),
                {
                    xtype: 'label',
                    id : this.id + '-attachFile',
                    hidden : this.readOnly,
                    html : '<span style="cursor: pointer;"><img src="' + LABKEY.contextPath + '/_images/paperclip.gif">&nbsp;&nbsp;Attach a file</span>'
                }
            ]
        });

        this.items = [{
            border: false,
            width: this.labelWidth || 200,
            items: [{
                xtype: 'label',
                html: this.fieldLabel || 'Document upload:'
            }]
        },{
            border: false,
            columnWidth: 1.0,
            items: [this.filesPanel]
        }];

        this.callParent();
    },

    getOwnerId : function() {
        return this.tissueId ? this.tissueId : (this.sampleRequestId ? this.sampleRequestId : this.studyId);
    },

    getOwnerType : function() {
        return this.tissueId ? 'tissue' : (this.sampleRequestId ? 'samplerequest' : 'study');
    },

    configureAttachmentsGrid : function() {

        this.gridPanel = Ext4.create('Ext.grid.Panel', {
            border: false,
            padding: '0 0 5px 0',
            multiSelect: false,
            forceFit: true,
            viewConfig: {
                deferEmptyText: true
            },
            columns: [{
                text: 'Document Name',
                dataIndex: 'name',
                menuDisabled : true,
                flex: 1,
                renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                    var link = "<a href='" + record.get("downloadURL") + "' target='_blank'>" + value + "</a>";
                    if (!record.get("active"))
                        link = "<span style='text-decoration: line-through;'>" + link + "</span>";
                    if (record.get("isLinkedDocument"))
                        link += " <span style='font-style: italic;'>(linked document)</span>";

                    return link;
                }
            },{
                xtype    : 'actioncolumn',
                maxWidth : 40,
                fles     : 1,
                align    : 'center',
                sortable : false,
                menuDisabled : true,
                hidden : this.readOnly,
                items: [{
                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_42 + '/resources/ext-theme-access/images/qtip/close.gif',
                    tooltip : 'Delete',
                    handler: function(grid, rowIndex, colIndex) {

                        var record = grid.getStore().getAt(rowIndex);
                        var deleteFn = function(id) {

                            if (id == 'yes') {

                                // call the API to delete the given file from the document set (by documen type)
                                grid.getEl().mask("Deleting document...");
                                Ext4.Ajax.request({
                                    url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteSingleDocument.api'),
                                    method  : 'POST',
                                    jsonData: {
                                        ownerId : this.getOwnerId(),
                                        ownerType : this.getOwnerType(),
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
                                });
                            }
                        };

                        Ext4.MessageBox.confirm('Delete Document?', 'Are you sure you want to delete the document: ' + record.get('name'),
                            deleteFn, this);
                    },
                    scope: this
                }]
            }],
            store: Ext4.create('Ext.data.Store', {
                model : 'BioTrust.Document.View',
                sorters: [ { property: 'name', direction: 'ASC' } ],
                scope : this
            }),
            listeners: {
                beforeselect : function() { return false; } // prevent row selection
            }
        });

        return this.gridPanel;
    },

    loadDocumentSetData : function() {
        if ((this.tissueId || this.sampleRequestId || this.studyId) && this.documentTypeName)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'getDocumentSet.api'),
                method  : 'POST',
                jsonData: {
                    rowId : this.getOwnerId(),
                    ownerType: this.getOwnerType(),
                    documentTypeName: this.documentTypeName
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
        }
    },

    onRender: function() {
        var me = this;

        me.callParent(arguments);

        // look for the Id to save a document to, either tissue record, sample request, or study registration
        this.searchForOwnerIds();

        this.loadDocumentSetData();

        if (me.attachFile) {

            // register a listener for the remove button
            me.mon(me.attachFile, {
                click: me.onAttachFile,
                scope: me
            });
        }
    },

    searchForOwnerIds : function() {
        this.tissueRecordPanel = this.up('#TissueRecordFormPanel');
        if (this.tissueRecordPanel && this.tissueRecordPanel.tissueId)
            this.tissueId = this.tissueRecordPanel.tissueId;

        this.sampleRecordPanel = this.up('#SampleRequestFormPanel');
        if (this.sampleRecordPanel && this.sampleRecordPanel.rowId)
            this.sampleRequestId = this.sampleRecordPanel.rowId;

        this.studyRecordPanel = this.up('#StudyRegistrationFormPanel');
        if (this.studyRecordPanel && this.studyRecordPanel.surveyRowId)
            this.studyId = this.studyRecordPanel.surveyRowId;
    },

    onAttachFile: function() {
        this.searchForOwnerIds();

        if (this.tissueId != null || this.sampleRequestId != null || this.studyId != null)
        {
            this.saveSurveyFired = false;

            // open a dialog so that we can add the file directly to the document set and test for duplicates, etc.
            var win = Ext4.create('Ext.window.Window', {
                title: this.windowTitle,
                cls: 'data-window',
                modal: true,
                items: [Ext4.create('Ext.form.Panel', {
                    border: false,
                    padding: 10,
                    items: [{
                        xtype: 'hidden', name: 'ownerId', value: this.getOwnerId()
                    },{
                        xtype: 'hidden', name: 'ownerType', value: this.getOwnerType()
                    },{
                        xtype: 'biotrust-documentattachment',
                        name : this.name || 'documentupload',
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

                            if (form.isValid())
                            {
                                var params = {
                                    ownerId : values.ownerId,
                                    ownerType: values.ownerType,
                                    documentTypeName: this.documentTypeName
                                };

                                if (values["attachmenttype"] == 'url')
                                {
                                    params["fileName"] = values["documentlinkname"];
                                    params["linkedDocumentUrl"] = values["documentlinkurl"];
                                }

                                win.getEl().mask("Saving documents...");
                                var options = {
                                    method  :'POST',
                                    params  : params,
                                    url     : LABKEY.ActionURL.buildURL('biotrust', 'saveDocuments.api'),
                                    success : function(cmp, resp) {
                                        win.getEl().unmask();
                                        win.close();

                                        // reload the files grid panel store
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
        }
        else if (this.saveSurveyFired)
        {
            this.saveSurveyFired = false;

            // the actual studyId has not yet been returned, so give an error message
            if (!Ext4.MessageBox.isVisible())
            {
                Ext4.MessageBox.show({
                    title: 'Error',
                    msg: 'The ' + (this.studyRecordPanel ? 'study registration' : 'sample request') + ' must first be saved before documents can be attached.',
                    buttons: Ext4.Msg.OK,
                    icon: Ext4.Msg.ERROR
                });
            }
        }
        else
        {
            this.saveSurveyFired = true;
            this.fireEvent('saveSurvey', null, this.studyRecordPanel ? "studyId" : "sampleRequestId");

            // re-call the onAttachFile method with a delay to wait for the save to complete (hack, is there a better way to get the returned studyId?)
            new Ext4.util.DelayedTask(this.onAttachFile, this).delay(2000);
        }
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
})