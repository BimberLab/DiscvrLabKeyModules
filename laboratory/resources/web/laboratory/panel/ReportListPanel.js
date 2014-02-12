/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * Renders a grid of reports, based on the getDataItems()
 */
Ext4.define('Laboratory.panel.ReportListPanel', {
    extend: 'Ext.panel.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            items: [{
                border: false,
                html: 'Loading...'
            }]
        });

        Laboratory.Utils.getDataItems({
            types: [Laboratory.ITEM_CATEGORY.reports.name],
            scope: this,
            success: this.onStoreLoad,
            failure: LDK.Utils.getErrorCallback()
        });

        this.callParent(arguments);
    },

    onStoreLoad: function(results){
        var store = this.getStore();
        var cfg = this.getGridConfig();

        Ext4.each(results[Laboratory.ITEM_CATEGORY.reports.name], function(item){
            if (item.visible){
                //compatibility with simple NavItems
                if (item.urlConfig && !item.browseUrl){
                    item.browseUrl = item.urlConfig;
                }
                store.add(store.createModel(item));
            }
        }, this);

        this.removeAll();
        this.add(cfg);
    },

    getGridConfig: function(){
        return {
            xtype: 'ldk-gridpanel',
            layout: 'fit',
            forceFit: true,
            store: this.getStore(),
            //tbar: this.getTbarCfg(),
            //hides a thin border above the grid
            bodyStyle: {
                border: 0
            },
            hideHeaders: true,
            border: false,
            columns  : this.initGridColumns(),
            viewConfig : {
                border: false,
                stripeRows : true,
                emptyText : 'No Reports Found'
            },
            disableSelection: true,
            features : [{
                ftype: 'grouping',
                groupHeaderTpl : '&nbsp;{name}'
            }]
        }
    },

    getStore: function(){
        if (this.store)
            return this.store;

        return this.store = Ext4.create('Ext.data.Store', {
            type: 'store',
            fields: ['name', 'label', 'category', 'browseUrl', 'importUrl', 'key', 'assayRunTemplateUrl', 'searchUrl', 'renderer', 'visible'],
            groupField: 'category'
        })
    },

    initGridColumns: function(){
        return [{
            dataIndex: 'label',
            text: 'Name',
            width: 300,
            renderer: function(value, attrs, record, rowIdx, cellIdx, store, view){
                var url = record.get('browseUrl');
                if(url && !LABKEY.Utils.isEmptyObj(url)){
                    value = '<a href="' + url.url + '">' + value +'</a>';
                }
                return value;
            }
        },{
            dataIndex: 'description',
            text: 'Description'
        }]
    },

    getTbarCfg: function(){
        var filterTask = new Ext4.util.DelayedTask(function() {
            this.filterStore(this.down('#searchField').getValue());
        }, this);

        return {
            ui: 'none',
            border: false,
            items: [{
//                xtype: 'tbfill'
//            },{
                xtype: 'textfield',
                emptyText: 'Search',
                itemId: 'searchField',
                height: 25,
                width: 400,
                border: false,
                //frame : false,
                listeners: {
                    change: function(cmp, e){
                        filterTask.delay(350);
                    }
//                    afterrender: function(cmp){
//                        cmp.getEl().parent().applyStyles('background-image:url("' + LABKEY.ActionURL.getContextPath() + '/_images/search.png);');
//                    }
                }
            }]
        };
    },

    filterStore: function(searchVal) {
        this.getStore().clearFilter();
        this.getStore().sort([{
            property : 'name',
            direction: 'ASC'
        }]);

        this.getStore().filterBy(function(rec, id){
            var answer = true;
            if (rec.data && !Ext4.isEmpty(searchVal)){
                var t = new RegExp(Ext4.escapeRe(searchVal), 'i');
                var s = '';
                if (rec.data.name)
                    s += rec.data.name;
                if (rec.data.category)
                    s += rec.data.category;
                answer = t.test(s);
            }

            return answer;
        }, this);
    }
});