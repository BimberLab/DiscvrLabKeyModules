/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This panel is used to toggle the default visibility of laboratory NavItems in a container.
 */
Ext4.define('Laboratory.panel.ItemVisibilityPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            },
            items: [{
                html: 'This page allows admins to control which items are visible on the main laboratory pages.  The checkboxes next to each item can be used to toggle the default visibility.',
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
            includeHidden: true,
            success: this.onLoad,
            scope: this
        });
    },

    onLoad: function(results){
        this.results = results;

        for (var i in Laboratory.ITEM_CATEGORY){
            if (Laboratory.ITEM_CATEGORY[i].name == Laboratory.ITEM_CATEGORY.settings.name)
                continue;

            this.renderSection(Laboratory.ITEM_CATEGORY[i].name, Laboratory.ITEM_CATEGORY[i].label);
        }
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
            cfg.items.push({
                xtype: 'checkbox',
                width: 600,
                style: 'margin-left: 5px;',
                navItem: item,
                boxLabel: item.label,
                checked: item.visible,
                itemCategory: name,
                listeners: {
                    change: function(field, val){
                        //if items are marked as children of this item, toggle their visibility.
                        //this is primarily used for reports
                        field.up('form').getForm().getFields().each(function(item){
                            if (item.navItem && item.navItem.ownerKey == field.navItem.key){
                                item.setValue(val);
                            }
                        }, this);
                    }
                }
            });
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

        Laboratory.Utils.saveItemVisibility({
            items: items,
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(response){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Save Complete', function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'start', Laboratory.Utils.getQueryContainerPath())
                });
            }
        })
    }
});
