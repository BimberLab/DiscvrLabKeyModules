/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/survey/AttachmentField.js");

Ext4.define('LABKEY.ext4.biotrust.DocumentAttachment', {

    extend  : 'Ext.panel.Panel',
    alias: 'widget.biotrust-documentattachment',

    constructor : function(config){

        Ext4.applyIf(config, {
            border: false,
            multipleFiles: false,
            showFirstFile: true,
            allowBlank: false,
            initDisabled: false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        // initially the panel includes the radio group for selecting the attachment type and a single file upload field
        this.items = [
            this.getAttachmentTypeRadioGroup(),
            this.getAttachmentFieldCmp()
        ];

        this.callParent();
    },

    getAttachmentTypeRadioGroup : function() {
        return {
            xtype: 'radiogroup',
            fieldLabel: 'Attachment Type',
            labelStyle: 'font-weight: bold;',
            width: this.width || 400,
            disabled: this.initDisabled,
            columns: 2,
            items: [
                { boxLabel: 'File', name: 'attachmenttype', inputValue: 'file', checked: true },
                { boxLabel: 'Linked URL', name: 'attachmenttype', inputValue: 'url', width: 100 }
            ],
            listeners: {
                scope: this,
                change: function(cmp, newValue, oldvalue) {
                    this.clearAttachmentFields(newValue.attachmenttype);
                    if (newValue.attachmenttype == 'file')
                    {
                        this.add(this.getAttachmentFieldCmp());
                    }
                    else
                    {
                        this.add(this.getLinkedDocumentNameCmp());
                        this.add(this.getLinkedDocumentUrlCmp());
                    }
                }
            }
        };
    },

    clearAttachmentFields : function(type) {
        var fields = Ext4.ComponentQuery.query(type == 'file' ? 'textfield' : 'attachmentfield', this);
        Ext4.each(fields, function(field){
            field.destroy();
        });
    },

    getLinkedDocumentNameCmp : function() {
        return {
            xtype: 'textfield',
            fieldLabel: 'Name',
            labelStyle: 'font-weight: bold;',
            width: this.width || 400,
            emptyText: 'Enter Document Name',
            allowBlank: false,
            name : 'documentlinkname'
        }
    },

    getLinkedDocumentUrlCmp : function() {
        return {
            xtype: 'textfield',
            fieldLabel: 'URL',
            labelStyle: 'font-weight: bold;',
            width: this.width || 400,
            emptyText: 'Enter Document URL',
            allowBlank: false,
            vtype: 'url',
            name : 'documentlinkurl'
        }
    },

    getAttachmentFieldCmp : function() {
        return Ext4.create('LABKEY.form.field.Attachment', {
            labelAlign : 'right',
            buttonText : this.buttonText || 'Browse',
            margin : 0,
            width : this.width || 400,
            fieldWidth : this.width || 400,
            multipleFiles : this.multipleFiles,
            showFirstFile : this.showFirstFile,
            allowBlank : false,
            disabled: this.initDisabled,
            name : this.name || 'documentfile'
        });
    }
});
