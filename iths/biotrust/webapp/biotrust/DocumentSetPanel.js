/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/biotrust/DocumentAttachment.js");
LABKEY.requiresCss("dataview/DataViewsPanel.css");
LABKEY.requiresCss("biotrust/NWBioTrust.css");

Ext4.define('LABKEY.ext4.biotrust.DocumentSetPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            bodyPadding: 10,
            bodyStyle:'background-color: transparent;',
            frame  : false,
            border : false,
            displayMode : true
        });

        Ext4.define('Document.View', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'attachmentParentId', type : 'string'},
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
    },

    initComponent : function() {

        this.items = [];

        // the top section displays some information from the submitted request responses
        this.items.push(this.configureHeaderPanel());

        // the main section is a listing of files that have been attached to this study.
        // in displayMode, we'll hide many of the manage options (i.e. add/edit document)
        this.items.push(this.configureStudyAttachmentsGrid());

        // the bottom section displays a read-only view of the sample request documents
        this.items.push(this.configureSRAttachmentsGrid());

        if (!this.displayMode)
            this.items.push(this.addDoneButton());

        this.callParent();
    },

    configureHeaderPanel : function() {
        var headerPanel = Ext4.create('Ext.form.Panel', {
            bodyStyle:'background-color: transparent;',
            border: false,
            maxWidth: 750,
            items: []
        });

        // if we have a requestRowId, then we can query the server for the request details we need to display
        if (this.requestRowId && this.requestContainerPath)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyResponse.api', this.requestContainerPath),
                method  : 'POST',
                jsonData: {rowId : this.requestRowId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.rowCount && o.rowCount === 1)
                    {
                        var row = o.rows[0];
                        this.studyId = this.getRowValue(row, "RowId");
                        headerPanel.add(this.getDisplayField('Request ID', this.studyId));
                        headerPanel.add(this.getDisplayField('Study Name', o.label));
                        headerPanel.add(this.getDisplayField('Principal Investigator', this.getRowValue(row, "PrincipalInvestigator")));

                        this.loadSRDocumentData();
                    }
                },
                failure : function(resp){
                    this.onFailure(resp, null, this);
                },
                scope   : this
            });
        }
        else
            headerPanel.add(this.getDisplayField(null, 'No request row id.'));

        return headerPanel;
    },

    getDisplayField : function(label, value, labelWidth) {
        return {
            xtype: 'displayfield',
            labelStyle: 'font-weight: bold;',
            labelWidth: labelWidth || 160,
            hideFieldLabel: !label || label.length == 0,
            fieldLabel: label,
            margin: 0,
            value: Ext4.util.Format.htmlEncode(value)
        };
    },

    getActiveCheckboxField : function(label, value, labelWidth) {
        return {
            xtype: 'checkboxfield',
            labelStyle: 'font-weight: bold;',
            labelWidth: labelWidth || 160,
            fieldLabel: label,
            name: 'active',
            checked: value
        };
    },

    getRowValue : function(row, varName) {
        // need to try both camel case and lowercase
        var value = row[varName];
        if (value == null)
            value = row[varName.toLowerCase()];

        return (value.displayValue ? value.displayValue : value.value);
    },

    configureStudyAttachmentsGrid : function() {

        this.studyDocGridPanel = Ext4.create('Ext.grid.Panel', {
            title: 'Study Documents',
            width: this.displayMode ? 750 : null,
            style: 'padding: 20px 0px;',
            border: false,
            selType: 'rowmodel',
            multiSelect: false,
            forceFit: true,
            viewConfig: {
                emptyText: '<span style="font-style:italic;">No documents to show</span>',
                deferEmptyText: false,
                stripeRows : true
            },
            columns: this.getAttachmentsGridColumns(true),
            store: Ext4.create('Ext.data.Store', {
                model : 'Document.View',
                sorters: [
                    { property: 'type', direction: 'ASC' },
                    { property: 'name', direction: 'ASC' }
                ],
                scope : this
            }),
            buttonAlign: 'left',
            fbar: (!this.displayMode && LABKEY.user.canUpdate) ? this.addDocumentButton() : undefined,
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // display edit document dialog if the edit link was clicked
                    if (e.target.className == 'edit-views-link')
                        this.editDocumentDialog(record);
                },
                scope : this
            }
        });

        this.loadStudyDocumentSetData();

        return this.studyDocGridPanel;
    },

    loadStudyDocumentSetData : function() {
        if (this.requestRowId && this.requestContainerPath)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'getDocumentSet.api', this.requestContainerPath),
                method  : 'POST',
                jsonData: {rowId : this.requestRowId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    // load the documentset data into the store
                    this.studyDocGridPanel.getStore().loadData(o.documentSet);

                    // display the required document set information for this request
                    if (!this.requiredDocumentTypesField)
                        this.setRequiredDocumentTypes(o.requiredDocumentTypes);
                },
                failure : LABKEY.Utils.displayAjaxErrorResponse,
                scope   : this
            });
        }
    },

    configureSRAttachmentsGrid : function() {

        this.srDocGridPanel = Ext4.create('Ext.grid.Panel', {
            title: 'Sample Request Documents (read-only)',
            width: this.displayMode ? 750 : null,
            style: 'padding: 20px 0px;',
            border: false,
            forceFit: true,
            viewConfig: {
                emptyText: '<span style="font-style:italic;">No documents to show</span>',
                deferEmptyText: false,
                stripeRows : true
            },
            columns: this.getAttachmentsGridColumns(false),
            store: Ext4.create('Ext.data.Store', {
                model : 'Document.View',
                sorters: [
                    { property: 'type', direction: 'ASC' },
                    { property: 'name', direction: 'ASC' }
                ],
                scope : this
            })
        });

        return this.srDocGridPanel;
    },

    loadSRDocumentData : function() {
        if (this.studyId && this.requestContainerPath)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'getSampleRequestDocumentSet.api', this.requestContainerPath),
                method  : 'POST',
                jsonData: {studyId : this.studyId},
                success : function(resp){
                    // load the documentset data into the store
                    var o = Ext4.decode(resp.responseText);
                    this.srDocGridPanel.getStore().loadData(o.documentSet);
                },
                failure : LABKEY.Utils.displayAjaxErrorResponse,
                scope   : this
            });
        }
    },

    setRequiredDocumentTypes : function(typeArr) {
        if (!this.requiredDocumentTypesField)
        {
            var reqTypeNames = "";
            Ext4.each(typeArr, function(type){
                reqTypeNames += (reqTypeNames.length > 0 ? ", " : "") + type.name;
            });

            if (reqTypeNames.length > 0)
                this.requiredDocumentTypesField = this.insert(1, this.getDisplayField('Required Document Types', reqTypeNames));
        }
    },

    getAttachmentsGridColumns : function(isStudy) {

        var columns = [];

        if (!this.displayMode && LABKEY.user.canUpdate && isStudy)
        {
            columns.push({
                text     : '&nbsp;',
                maxWidth : 40,
                flex : 1,
                sortable : false,
                menuDisabled : true,
                renderer : function(view, meta, rec, idx, colIdx, store) {
                    return '<span height="16px" class="edit-views-link"></span>';
                }
            });
        }

        columns.push({
            text: 'Document Name',
            dataIndex: 'name',
            style: 'font-weight: bold;',
            flex: 1,
            renderer : function(value, metaData, record, rowIndex, colIndex, store) {
                var link = "<a href='" + record.get("downloadURL") + "' target='_blank'>" + value + "</a>";
                if (record.get("isLinkedDocument"))
                    link += " <span style='font-style: italic;'>(linked document)</span>";

                return link;
            }
        });

        columns.push({
            text: 'Document Type',
            dataIndex: 'type',
            style: 'font-weight: bold;',
            flex: 1
        });

        if (isStudy)
        {
            columns.push({
                text: 'Active',
                dataIndex: 'active',
                style: 'font-weight: bold;',
                maxWidth: 80,
                flex: 1,
                align: 'center',
                renderer : function(value) {
                    return value ? 'Yes' : 'No';
                }
            });
        }

        columns.push({
            text: 'Created By',
            dataIndex: 'createdBy',
            style: 'font-weight: bold;',
            maxWidth: 115,
            flex: 1
        });

        columns.push({
            text: 'Created',
            dataIndex: 'created',
            style: 'font-weight: bold;',
            maxWidth: 100,
            flex: 1,
            renderer : Ext4.util.Format.dateRenderer('m/d/Y')
        });

        return columns;
    },

    addDoneButton : function() {
        return {
            xtype: 'button',
            text: 'Done',
            scope: this,
            handler: function() {
                if (this.returnURL)
                    window.location = this.returnURL;
                else
                    window.location = LABKEY.ActionURL.buildURL('project', 'begin', null, { pageId: 'nwbt.STUDY_REGISTRATIONS' });
            }
        };
    },

    addDocumentButton : function() {
        return [{
            xtype: 'button',
            text: 'Add Document(s)',
            scope: this,
            handler: this.addDocumentDialog
        }];
    },

    addDocumentDialog : function() {
        // this dialog window lets you add a set of documents for a given document type to the request
        var win = Ext4.create('Ext.window.Window', {
            title: 'Add Document(s)',
            cls: 'data-window',
            modal: true,
            minHeight: 75,
            maxHeight: 400,
            width: 450,
            autoScroll: true,
            items: [Ext4.create('Ext.form.Panel', {
                itemId: 'AddDocumentFormPanel',
                border: false,
                padding: 10,
                items: [{
                    xtype: 'combo',
                    name: 'type',
                    fieldLabel: 'Document Type',
                    labelStyle: 'font-weight: bold;',
                    allowBlank: false,
                    labelWidth: 110,
                    width: 400,
                    padding : '5 0',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.Store', {
                        schemaName: "biotrust",
                        queryName: "DocumentTypes",
                        columns: "TypeId,Name,AllowMultipleUpload",
                        sort: "Name",
                        autoLoad: true
                    }),
                    queryMode: 'local',
                    displayField: 'Name',
                    valueField: 'TypeId',
                    listeners: {
                        scope: this,
                        select: function(cmp, records) {
                            // since it is only a single select combo, grab the AllowMultipleUpload value from the first record
                            if (records.length > 0)
                            {
                                // on document type change, adjust the DocumentAttachment based on if the doc type allows multiple uploads
                                // first, remove any existing attachment fields
                                // then, add new single or multiple attachment field based on the document type settings
                                var fields = Ext4.ComponentQuery.query('biotrust-documentattachment', win);
                                Ext4.each(fields, function(field){
                                    field.destroy();
                                });
                                win.down('panel').add(this.getDocumentAttachmentConfig(records[0].get("AllowMultipleUpload"), false));
                            }
                        }
                    }
                },
                // temp attachment field, for looks, will be replaced on document type selection
                this.getDocumentAttachmentConfig(false, true)
                ],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Submit',
                    formBind: true,
                    scope: this,
                    handler: function() {

                        var form = win.down('#AddDocumentFormPanel').getForm();
                        var values = form.getValues();

                        if (form.isValid())
                        {
                            var params = {
                                ownerId : this.requestRowId,
                                documentTypeId : values["type"]
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
                                    this.loadStudyDocumentSetData();
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
                    text: 'Close',
                    handler: function() { win.close(); }
                }]
            })]
        });
        win.show();
    },

    getDocumentAttachmentConfig : function(allowMultiple, disabled) {
        return {
            xtype : 'biotrust-documentattachment',
            name : 'document',
            width : 403,
            multipleFiles : allowMultiple,
            showFirstFile : true,
            allowBlank : false,
            initDisabled : disabled
        }
    },

    editDocumentDialog : function(record) {
        if (record)
        {
            // this dialog window lets you delete a document (and possibly later replace documents)
            var panel = Ext4.create('Ext.form.Panel', {
                border: false,
                padding: 10,
                minWidth: 280,
                items: [],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Update',
                    scope: this,
                    handler: function() {
                        // call the API to update the document properties
                        win.getEl().mask("Updating document...");
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('biotrust', 'updateDocumentProperties.api'),
                            method  : 'POST',
                            jsonData: {
                                entityId : record.get("attachmentParentId"),
                                fileName : record.get("name"),
                                active : panel.getForm().findField('active').getValue()
                            },
                            success : function(resp){
                                win.getEl().unmask();
                                win.close();

                                // reload the files grid panel store
                                this.loadStudyDocumentSetData();
                            },
                            failure : function(resp) {
                                win.getEl().unmask();
                                this.onFailure(resp)
                            },
                            scope   : this
                        });
                    }
                },{
                    text: 'Delete',
                    scope: this,
                    handler: function() {

                        var deleteFn = function(id) {
                            if (id == 'yes') {

                            // call the API to delete the given file from the document set (by documen type)
                            win.getEl().mask("Deleting document...");
                            Ext4.Ajax.request({
                                url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteSingleDocument.api'),
                                method  : 'POST',
                                jsonData: {
                                    ownerId : this.requestRowId,
                                    documentTypeId : record.get("typeId"),
                                    fileName : record.get("name")
                                },
                                success : function(resp){
                                    win.getEl().unmask();
                                    win.close();

                                    // reload the files grid panel store
                                    this.loadStudyDocumentSetData();
                                },
                                failure : function(resp) {
                                    win.getEl().unmask();
                                    this.onFailure(resp)
                                },
                                scope   : this
                            });
                            }
                        };

                        Ext4.MessageBox.confirm('Delete Document?', 'Are you sure you want to delete the document: ' + record.get('name'),
                            deleteFn, this);
                    }
                },{
                    text: 'Close',
                    handler: function() { win.close(); }
                }]
            });
            panel.add(this.getDisplayField('Document Name', record.get("name"), 120));
            panel.add(this.getDisplayField('Document Type', record.get("type"), 120));
            panel.add(this.getDisplayField('Created By', record.get("createdBy"), 120));
            panel.add(this.getDisplayField('Created', Ext4.util.Format.date(record.get("created"), 'm/d/Y'), 120));
            panel.add(this.getActiveCheckboxField('Active', record.get("active"), 120));

            var win = Ext4.create('Ext.window.Window', {
                title: 'Edit Document',
                cls: 'data-window',
                modal: true,
                autoScroll: true,
                items: panel
            });
            win.show();
        }
    },

    onFailure : function(resp, message, cmp) {
        var error = Ext4.decode(resp.responseText);
        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        if (cmp != undefined)
        {
            this.removeAll();
            this.update("<span class='labkey-error'>Error: " + message + "</span>");
        }
        else
            Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');
    }
});