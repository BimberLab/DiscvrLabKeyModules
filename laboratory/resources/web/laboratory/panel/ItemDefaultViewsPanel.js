/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This panel is used to toggle the default visibility of laboratory NavItems in a container.
 */
Ext4.define('Laboratory.panel.ItemDefaultViewsPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            },
            items: [{
                html: 'This page allows admins to control the default view that is the target of the browse all link.',
                padding: '0 0 20 0'
            },{
                html: 'Loading...',
                style: 'padding-bottom: 20px;',
                itemId: 'loading'
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
                    window.location = LABKEY.ActionURL.buildURL('project', 'start')
                }
            }]
        });

        this.callParent(arguments);

        Laboratory.Utils.getDataItems({
            types: [Laboratory.ITEM_CATEGORY.data.name, Laboratory.ITEM_CATEGORY.samples.name],
            includeHidden: true,
            success: this.onLoad,
            scope: this
        });
    },

    onLoad: function(results){
        this.results = results;

        Ext4.each([Laboratory.ITEM_CATEGORY.data, Laboratory.ITEM_CATEGORY.samples], function(i){
            this.renderSection(i.name, i.label);
        }, this);
    },

    renderSection: function(name, label){
        var items = this.results[name];

        this.remove(this.down('#loading'));

        var cfg = {
            xtype: 'container',
            border: false,
            defaults: {
                border: false
            },
            style: 'margin-bottom: 20px;',
            items: [{
                html: '<b>' + label + '</b>',
                style: 'padding-bottom: 5px;'
            }]
        }

        Ext4.each(items, function(item){
            if (Ext4.isDefined(item.browseDefaultView)){
                cfg.items.push({
                    xtype: 'textfield',
                    width: 600,
                    style: 'margin-left: 5px;',
                    navItem: item,
                    fieldLabel: item.label,
                    labelWidth: 180,
                    itemCategory: name,
                    value: item.browseDefaultView
                });
            }
        }, this);

        this.add(cfg);
        this.down('#submitBtn').setDisabled(false);
    },

    doSave: function(btn){
        var items = {};
        var fields = btn.up('form').getForm().getFields();
        fields.each(function(field){
            items[field.navItem.key] = field.getValue()
        }, this);

        Ext4.Msg.wait('Saving...');

        return LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('laboratory', 'setItemDefaultView', null),
            params: {
                jsonData: Ext4.encode(items)
            },
            method : 'POST',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Save Complete', function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'start', Laboratory.Utils.getQueryContainerPath())
                });
            }
        });
    }
});
