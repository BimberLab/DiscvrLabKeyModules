/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Laboratory.panel.TextFileUploadPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-textfileuploadpanel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            items: [{
                xtype: 'radiogroup',
                name: 'uploadType',
                isFormField: false,
                itemId: 'inputType',
                width: 350,
                defaults: {
                    width: 200
                },
                items: [{
                    boxLabel: 'Copy/Paste Data',
                    xtype: 'radio',
                    name: 'uploadType',
                    isFormField: false,
                    inputValue: 'text',
                    checked: true,
                    scope: this,
                    handler: function(fb, y){
                        if (!y)
                            return;

                        this.down('#fileArea').removeAll();
                        this.down('#fileArea').add({
                            itemId:"fileContent",
                            name: 'fileContent',
                            xtype: 'textarea',
                            height:350,
                            width: 700
                        });
//                        this.down('#assayResults').doLayout();
                    }
                },{
                    boxLabel: 'File Upload',
                    xtype: 'radio',
                    name: 'uploadType',
                    inputValue: 'file',
                    handler: function(fb, y){
                        if (!y)
                            return;

                        this.down('#fileArea').removeAll();
                        this.down('#fileArea').add({
                            xtype: 'filefield',
                            name: 'upload-run-field',
                            itemId: 'upload-run-field',
                            width: 400,
                            buttonText: 'Select a file'
                        });
//                        this.down('#assayResults').doLayout();
                    },
                    scope: this
                }]
            },{
                xtype: 'panel',
                itemId: 'fileArea',
                border: false,
                items: [{
                    itemId: 'fileContent',
                    xtype: 'textarea',
                    name: 'fileContent',
                    height:350,
                    width: 700
                },{
                    itemId: 'errorArea',
                    style: 'margin-bottom: 10px',
                    border: false
                }]
            }]
        });

        this.callParent(arguments);
    }
});