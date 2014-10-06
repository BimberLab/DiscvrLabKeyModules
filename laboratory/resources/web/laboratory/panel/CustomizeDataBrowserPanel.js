/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This panel is used to customize the appearance of the tabs in the data browser.
 */
Ext4.define('Laboratory.panel.CustomizeDataBrowserPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            },
            items: [{
                html: 'This page allows admins to control the labels and grouping of the tabs in the data browser.  To reset a value to the default, leave that field blank and save.  If control which tabs are visible, <a href="' + LABKEY.ActionURL.buildURL('laboratory', 'itemVisibility') + '">click here</a>.',
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
            types: [Laboratory.ITEM_CATEGORY.tabbedReports.name],
            includeHidden: true,
            success: this.onLoad,
            scope: this
        });
    },

    onLoad: function(results){
        this.results = results;

        this.renderSection(this.results[Laboratory.ITEM_CATEGORY.tabbedReports.name]);
    },

    renderSection: function(items){
        this.remove(this.down('#loading'));

        var tableItems = [{
            html: '<b>Name</b>',
            style: 'padding-bottom: 5px;'
        },{
            html: '<b>Report Category (top-level tab)</b>',
            style: 'padding-bottom: 5px;margin-left: 5px;'

        },{
            html: '<b>Label (bottom tab)</b>',
            style: 'padding-bottom: 5px;margin-left: 5px;'
        },{
            html: '<b>Visible?</b>',
            style: 'padding-bottom: 5px;margin-left: 5px;'
        }];

        items = LDK.Utils.sortByProperty(items, 'name', false);
        items = LDK.Utils.sortByProperty(items, 'providerName', false);

        Ext4.each(items, function(item){
            tableItems = tableItems.concat([{
                navItem: item,
                width: 300,
                html: item.providerName + ' / ' + item.name
            },{
                xtype: 'textfield',
                style: 'margin-left: 5px;',
                width: 300,
                value: item.reportCategory
            },{
                xtype: 'textfield',
                style: 'margin-left: 5px;',
                width: 300,
                value: item.label
            },{
                xtype: 'displayfield',
                value: item.visible,
                style: 'margin-left: 15px;'
            }]);
        }, this);

        var cfg = {
            itemId: 'theTable',
            layout: {
                type: 'table',
                columns: 4
            },
            border: false,
            defaults: {
                border: false
            },
            style: 'margin-bottom: 20px;',
            items: tableItems
        };

        this.add(cfg);
        this.down('#submitBtn').setDisabled(false);
    },

    doSave: function(btn){
        var toSave = {};
        var items = btn.up('form').down('#theTable').items;
        var cols = 4;
        var rows = (items.getCount() / cols);

        for (var i=1;i<rows;i++){
            var base = i * cols;
            var navItem = items.get(base).navItem;
            toSave[navItem.overridesKey] = {
                label:  items.get(base + 2).getValue(),
                reportCategory: items.get(base + 1).getValue()
            }
        }

        Ext4.Msg.wait('Saving...');

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('laboratory', 'setDataBrowserSettings'),
            params: {
                jsonData: Ext4.encode(toSave)
            },
            method : 'POST',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(response){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Save Complete', function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'start', Laboratory.Utils.getQueryContainerPath())
                });
            }
        });
    }
});
