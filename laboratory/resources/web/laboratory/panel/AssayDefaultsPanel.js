/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This panel is used to set the defaults on assays that have been registered with LaboratoryService.
 */
Ext4.define('Laboratory.panel.AssayDefaultsPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            },
            items: [{
                html: 'This page allows admins to control the default import method for assays enabled in this folder.',
                padding: '0 0 20 0'
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Submit',
                disabled: true,
                itemId: 'submitBtn',
                handler: this.doSave
            },{
                text: 'Cancel',
                handler: function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'start');
                }
            }]
        });

        this.callParent(arguments);

        Laboratory.Utils.getImportMethods({
            success: this.onLoad,
            scope: this
        });
    },

    onLoad: function(results){
        this.results = results;

        Ext4.each(results.providers, function(provider){
            this.renderProvider(provider);
        }, this);

        this.down('#submitBtn').setDisabled(false);
    },

    renderProvider: function(provider){
        if (!provider.protocols.length){
            return;
        }

        var cfg = {
            xtype: 'container',
            border: false,
            defaults: {
                border: false
            },
            style: 'margin-bottom: 20px;',
            items: [{
                html: '<b>' + provider.name + '</b>',
                style: 'padding-bottom: 10px;'
            }]
        }

        Ext4.each(provider.protocols, function(protocol){
            var data = [];
            Ext4.each(protocol.importMethods, function(method){
                data.push([method.name, method.label]);
            });

            var store = Ext4.create('Ext.data.ArrayStore', {
                fields: ['name', 'label'],
                data: data
            });

            cfg.items.push({
                xtype: 'combo',
                width: 400,
                labelWidth: 180,
                style: 'margin-left: 5px;',
                provider: provider,
                protocol: protocol,
                fieldLabel: protocol.name,
                displayField: 'label',
                valueField: 'name',
                store: store,
                helpPopup: 'Container: ' + protocol.containerPath + '<br>Protocol Id: ' + protocol.rowId,
                value: protocol.defaultImportMethod
            });
        }, this);

        this.add(cfg);
    },

    doSave: function(btn){
        var items = {};
        var fields = btn.up('form').getForm().getFields();
        fields.each(function(field){
            items[field.provider.key + '||' + field.protocol.rowId] = field.getValue()
        }, this);

        Ext4.Msg.wait('Saving...');

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('laboratory', 'saveAssayDefaults'),
            params: {
                jsonData: Ext4.encode(items)
            },
            method : 'POST',
            scope: this,
            failure: LDK.Utils.getErrorCallback({}),
            success: function(result){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Save Complete', function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'start', Laboratory.Utils.getQueryContainerPath())
                });
            }
        });
    }
});