Ext4.define('Laboratory.panel.AdditionalLinksPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-additionallinkspanel',

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            border: false,
            buttonAlign: 'left',
            buttons: [{
                text: 'Add New',
                hidden: !LABKEY.Security.currentUser.isAdmin,
                scope: this,
                handler: this.doAdd
            }],
            defaults: {
                border: false
            },
            items: [{
                html: 'Loading...'
            }]
        });

        this.callParent();

        this.doLoad();
    },

    doLoad: function(){
        Laboratory.Utils.getAdditionalDataSources({
            scope: this,
            includeTotals: true,
            success: this.onLoad
        });

        this.removeAll();
        this.add({
            html: 'Loading...'
        });
    },

    onLoad: function(results){
        this.removeAll();

        var toAdd = {
            itemId: 'itemsPanel',
            layout: {
                type: 'table',
                columns: 4
            },
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            bodyStyle: 'margin-bottom: 10px',
            items: [{
                html: '<b>Item Type</b>'
            },{
                html: '<b>Label</b>'
            },{
                html: '<b>URL</b>'
            },{
                html: ''
            }]
        };

        if (results.urlSources && results.urlSources.length){
            results.urlSources = LDK.Utils.sortByProperty(results.urlSources, 'label', false);
            Ext4.each(results.urlSources, function(source, idx){
                if (source){
                    toAdd.items = toAdd.items.concat([{
                        source: source,
                        html: Laboratory.ITEM_CATEGORY[source.itemType].label
                    },{
                        html: source.label
                    },{
                        html: source.urlExpression
                    },{
                        xtype: 'button',
                        text: 'Remove',
                        hidden: !LABKEY.Security.currentUser.isAdmin,
                        style: 'padding: 0px;',
                        border: true,
                        sourceIdx: idx,
                        handler: function(btn){
                            var panel = btn.up('laboratory-additionallinkspanel');
                            panel.removeSource(btn.sourceIdx);
                            var sources = panel.getSources();
                            panel.saveSources(sources);
                        }
                    }]);
                }
            }, this);
        }

        if (results.urlSources.length)
            this.add(toAdd);
        else
            this.add({
                html: 'No links have been registered'
            });
    },

    doAdd: function(btn){
        var dataTypes = [];
        for (var i in Laboratory.ITEM_CATEGORY){
            if ([Laboratory.ITEM_CATEGORY.tabbedReports.name, Laboratory.ITEM_CATEGORY.reports.name].indexOf(i) == -1)
                dataTypes.push([Laboratory.ITEM_CATEGORY[i].name, Laboratory.ITEM_CATEGORY[i].label]);
        }

        Ext4.create('Ext.window.Window', {
            width: 500,
            title: 'Add URL Source',
            closeAction: 'destroy',
            modal: true,
            items: [{
                xtype: 'panel',
                defaults: {
                    width: 480
                },
                items: [{
                    xtype: 'combo',
                    editable: false,
                    fieldLabel: 'Item Type',
                    itemId: 'itemType',
                    allowBlank: false,
                    queryMode: 'local',
                    valueField: 'name',
                    displayField: 'caption',
                    store: {
                        type: 'array',
                        fields: ['name', 'caption'],
                        data: dataTypes
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Label',
                    allowBlank: false,
                    itemId: 'label'
                },{
                    xtype: 'textfield',
                    fieldLabel: 'URL',
                    allowBlank: false,
                    itemId: 'urlExpression'
                }]
            }],
            buttons: [{
                text: 'Save',
                scope: this,
                handler: this.addSource
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        }).show(btn);
    },

    addSource: function(btn){
        var win = btn.up('window');

        var url = win.down('#urlExpression').getValue();
        var itemType = win.down('#itemType').getValue();
        var label = win.down('#label').getValue();

        if (!label || !url || !itemType){
            Ext4.Msg.alert('Error', 'Must provide a label, itemType and URL');
            return;
        }

        var sources = this.getSources();
        sources.push({
            itemType: itemType,
            label: label,
            urlExpression: url
        });

        this.saveSources(sources, win);
    },

    getSourceIdx: function(label){
        var sources = this.getSources();
        for (var i=0;i<sources.length;i++){
            if (sources[i].label == label)
                return i;
        }
    },

    removeSource: function(idx){
        var panel = this.down('#itemsPanel');
        var size = panel.layout.columns;
        var start = (idx + 1) * size;

        for (var i=1;i<=size;i++){
            panel.remove(panel.items.get(size + start - i));
        }
    },

    getSources: function(){
        var sources = [];
        var panel = this.down('#itemsPanel');
        if (panel){
            panel.items.each(function(item){
                if (item.source)
                    sources.push(item.source);
            }, this);
        }
        return sources;
    },

    saveSources: function(sources, win){
        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('laboratory', 'setUrlDataSources'),
            params: {
                sources: Ext4.encode(sources)
            },
            method : 'POST',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(result){
                Ext4.Msg.hide();

                if (win)
                    win.close();

                Ext4.Msg.alert('Success', 'Additional Links Updated', function(){
                    this.doLoad();
                }, this);
            }
        });
    }
});