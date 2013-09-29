/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('Laboratory.panel.DataExplorer', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-dataexplorer',
    config: {
        title: 'DataExplorer',
        queryName: '',
        schemaName: '',
        viewName: '',
        filterArray: [],
        sortArray: []
    },
    initComponent: function(){
        this.panelDefaults = {
            bodyStyle: 'padding: 5px',
            border: false,
            defaults: {
                border: false
            }
        };

        Ext4.apply(this, {
            border: true,
            defaults: this.panelDefaults,
            store: this.getStoreConfig(true),
            items: [{
                itemId: 'msgArea',
                border: true
            },{
                xtype: 'tabpanel',
                itemId: 'tabPanel',
                items: this.getVisibleTabs(),
                listeners: {
                    beforetabchange: this.onBeforeTabChange
                }
            }],
            dockedItems: [
                this.getToolbarConfig()
            ]
        });

        this.callParent(arguments);

        this.mon(this.store, 'load', this.updateMessageArea, this, {delay: 100});
    },

    getToolbarConfig: function(){
        return {
            xtype: 'toolbar',
            dock: 'top',
            itemId: 'topToolbar',
            items: [{
                xtype: 'button',
                text: 'Views'
            },{
                text: 'Visualizations'
            },{
                text: 'Customize',
                menu: {
                    items: [{
                        text: 'Change Filters'
                    },{
                        text: 'Change Sorting'
                    },{
                        text: 'Change Visible Columns'
                    }]
                }
            }]
        }
    },

    getVisibleTabs: function(){
        var items = [{
            //xtype: 'laboratory-querypanel',
            xtype: 'laboratory-delayedgridpanel',
            title: 'Source Data',
            store: this.store,
            gridConfig: {
                autoScroll: true,
                verticalScrollerType: 'paginggridscroller',
                disableSelection: true,
                invalidateScrollerOnRefresh: false,

                //pageSize: 10,  //not used by ext
                showPagingToolbar: false,
                tbar: {
                    items: [{
                        xtype: 'button',
                        text: 'Page Size',
                        menu: this.createPageSizeButtons()
                    }]
                },
                store: this.store,
                bodyStyle: 'padding: 0px;',
                border: true,
                //columnLines: true,
                queryConfig: this.getQueryConfig()
            },
            listeners: {
                scope: this,
                gridcreated: function(grid){
                    console.log('sort changed')
                }
            }
        }];

        //load one tab per active visualization

        //set the active tab

        items.push({
            title: '+',
            itemId: '_newTab'
        });
        return items;
    },

    createPageSizeButtons: function(){
        var buttons = [];
        Ext4.each([20, 50, 100, 'All'], function(num){
            buttons.push({
                text: 'Show ' + num,
                pageSize: (num == 'All' ? null : num),
                scope: this,
                handler: function(btn){
                    this.store.pageSize = btn.pageSize;
                    this.store.load();
                }
            });
        }, this);

        return buttons;
    },

    updateMessageArea: function(){
        var msgArea = this.down('#msgArea');
        msgArea.removeAll();
        var items = this.getMessageAreaItems();
        if(items)
            msgArea.add(items);
        msgArea.doLayout();
    },

    getMessageAreaItems: function(){
        var messages = [];
        if(!this.store)
            return;

        if(this.store.sorters.getCount()){
            var sorts = [];
            this.store.sorters.each(function(sort){
                sorts.push(sort.property);
            });
            messages.push({xtype: 'panel', html: 'Sort: ' + sorts.join(', ')});
        }

        if(this.store.filters.getCount()){
            var filters = [];
            this.store.filters.each(function(filter){
                messages.push({xtype: 'panel', html: 'Filter: ' + filter.property + ' ' + filter.filterType.getDisplayText() + ' "' + filter.value + '"'});
                //filters.push(filter.property);
            });
        }

        return messages;
    },

    getStoreConfig: function(doCreate){
        var storeCfg = Ext4.apply({
            xtype: 'labkey-store',
            pageSize: 10,
            timeout: 99999999,
            buffered: true
        }, this.getQueryConfig());

        if(doCreate){
            storeCfg.autoLoad = true;
            this.store = Ext4.create('LABKEY.ext4.data.Store', storeCfg);
            //this.store.prefetch();
        }
        else
            this.store = storeCfg;

        return this.store;
    },

    getStore: function(){
        if(!this.store)
            this.getStoreConfig();

        return this.store;
    },

    loadVisualization: function(report){

    },

    getQueryConfig: function(){
        //we should rely on the store for information like this
        return {
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            columns: this.columns,
//            filterArray: this.getFilterArray,
//            sortArray: this.getSortArray,
            store: this.store
        }
    },

    onBeforeTabChange: function(panel, newTab, oldTab){
        //update view if sort changed?
        if(newTab.itemId == '_newTab'){
            console.log('adding tab');
            return false;
        }
    }

});

Ext4.define('Laboratory.ext.ColumnExplorerPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-columnexplorerpanel',
    initComponent: function(){

        this.callParent(arguments);
    }
});

Ext4.define('Laboratory.ext.ColumnExplorerWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.laboratory-columnexplorerwindow',
    initComponent: function(){

        this.callParent(arguments);
    }
});


Ext4.define('Laboratory.ext.DelayedGridPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-delayedgridpanel',
    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            autoScroll: true,
            items: [{
                html: 'Loading...'
            }]
        });
        this.callParent(arguments);

        this.addEvents('gridcreated');

        if(this.store.hasLoaded()){
            this.onStoreLoad();
            console.log('has loaded')
        }
        else {
            this.store.on('load', this.onStoreLoad, this, {single: true});
//            if(!this.store.isLoading())
                this.store.load();
//            else
//                console.log('is loading')
        }

    },
    onStoreLoad: function(store){
        this.removeAll();
        var gridConfig = Ext4.apply({
            //xtype: 'laboratory-querypanel',
            xtype: 'labkey-gridpanel',
            autoScroll: true,
            store: this.store,
            bodyStyle: 'padding: 0px;',
            border: true,
            onMenuCreate: function(header, menu, opts){
                menu.items.each(function(item){
                    if(item.text == 'Columns')
                        menu.remove(item);
                });

                menu.add({
                    text: 'Filter',
                    scope: this,
                    handler: function(btn){
                        var win = Ext4.create('Laboratory.ext4.FilterDialogWin', {
                            store: this.store,
                            column: btn.parentMenu.activeHeader.dataIndex
                        });

                        win.show();
                    }
                });

            }
        }, this.gridConfig);

        this.grid = this.add(gridConfig);
        this.doLayout();

        this.fireEvent('gridcreated', this.grid);
    }
});

Ext4.define('Laboratory.ext4.FilterDialog', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-filterdialog',
    initComponent: function(){
        Ext4.apply(this, {
            items: [{
                html: 'Hello'
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    this.store.filter([{
                        property: this.column,
                        filterType: LABKEY.Filter.Types.EQUAL,
                        value: 'Blood'
                    }])
                }
            }]
        });
        this.callParent(arguments);
    }
});

Ext4.define('Laboratory.ext4.FilterDialogWin', {
    extend: 'Ext.window.Window',
    alias: 'widget.laboratory-filterdialogwindow',
    initComponent: function(){
        Ext4.apply(this, {
            items: [{
                xtype: 'laboratory-filterdialog',
                column: this.column,
                store: this.store
            }]
        });
        this.callParent(arguments);
    }
});
