Ext4.define('Laboratory.panel.ManageDemographicsSourcesPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-managedemographicssourcespanel',
    initComponent: function(){
        Ext4.apply(this, {
            //width: 600,
            bodyStyle: 'padding: 5px;',
            border: false,
            buttonAlign: 'left',
            buttons: [{
                text: 'Add New',
                hidden: !Laboratory.Utils.isLaboratoryAdmin(),
                scope: this,
                handler: this.doAdd
            },{
                text: 'Add Default Sources',
                itemId: 'defaultSources',
                hidden: !Laboratory.Utils.isLaboratoryAdmin() || LDK.Utils.isSharedProject(),
                scope: this,
                menu: []
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
        Laboratory.Utils.getDemographicsSources({
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
                columns: 5
            },
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            bodyStyle: 'margin-bottom: 10px',
            items: [{
                html: '<b>Label</b>'
            },{
                html: '<b>Table Name</b>'
            },{
                html: '<b>Target Column</b>'
            },{
                html: '<b>Total Subjects</b>'
            },{
                html: ''
            }]
        };

        if (results.sources && results.sources.length){
            results.sources = LDK.Utils.sortByProperty(results.sources, 'label', false);
            Ext4.each(results.sources, function(source, idx){
                if (source){
                    toAdd.items = toAdd.items.concat([{
                        source: source,
                        html: source.label
                    },{
                        html: (source.canRead ? '<a href="' + source.browseUrl + '">' : '') + source.schemaName + '.' + source.queryName + (!source.containerPath ? "" : ' (' + source.containerPath + ')') + (source.canRead ? '</a>' : '')
                    },{
                        html: source.targetColumn
                    },{
                        html: source.canRead ? '<a href="' + source.browseUrl + '">' + source.total + '</a>' : 'User does not have permission to read this table'
                    },{
                        xtype: 'button',
                        text: 'Remove',
                        hidden: !Laboratory.Utils.isLaboratoryAdmin(),
                        style: 'padding: 0px;',
                        border: true,
                        sourceIdx: idx,
                        handler: function(btn){
                            var panel = btn.up('laboratory-managedemographicssourcespanel');
                            panel.removeSource(btn.sourceIdx);
                            var sources = panel.getSources();
                            panel.saveSources(sources);
                        }
                    }]);
                }
            }, this);
        }

        if (results.sources.length)
            this.add(toAdd);
        else
            this.add({html: 'No sources have been registered'});

        var btn = this.down('#defaultSources');
        btn.menu.removeAll();

        if (results.sharedSources){
            var menuItems = [];
            results.sharedSources = LDK.Utils.sortByProperty(results.sharedSources, 'label', false);
            Ext4.each(results.sharedSources, function(source){
                menuItems.push({
                    text: source.label + ' (' + (source.containerPath ? '"' + source.containerPath + '".' : '') + source.schemaName + '.' + source.queryName + ')',
                    xtype: 'menuitem',
                    source: source,
                    handler: function(btn){
                        var panel = btn.up('laboratory-managedemographicssourcespanel');
                        var sources = panel.getSources();

                        var present = false;
                        Ext4.each(sources, function(source){
                            if (source.schemaName == btn.source.schemaName && source.queryName == btn.source.queryName && (!btn.source.containerId ? source.containerId == LABKEY.Security.currentContainer.id : source.containerId == btn.source.containerId)){
                                present = true;
                                return false;
                            }
                        }, this);

                        if (!present){
                            var newSource = Ext4.apply({}, btn.source);
                            if (!newSource.containerId)
                                newSource.containerId = LABKEY.Security.currentContainer.id;

                            sources.push(newSource);
                            panel.saveSources(sources);
                        }
                        else
                        {
                            Ext4.Msg.alert('Error', 'This table has already been enabled in this folder');
                        }
                    }
                })
            }, this);

            if (menuItems.length){
                btn.menu.add(menuItems);
            }
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

    doAdd: function(btn){
        var panel = Ext4.define('Laboratory.panel.AddNewDemographicsSourcePanel', {
            extend: 'Laboratory.panel.QueryPickerPanel',
            getItemsCfg: function(){
                var items = [];

                items.push({
                    xtype: 'textfield',
                    fieldLabel: 'Label',
                    allowBlank: false,
                    itemId: 'label'
                });

                items = items.concat(this.callParent());

                items.push({
                    xtype: 'combo',
                    editable: false,
                    fieldLabel: 'Target Column',
                    itemId: 'targetColumn',
                    allowBlank: false,
                    queryMode: 'local',
                    disabled: true,
                    valueField: 'name',
                    displayField: 'caption',
                    store: {
                        fields: ['name', 'caption'],
                        proxy: {
                            type: 'memory'
                        }
                    },
                    loadColumns: function(qd){
                        this.store.removeAll();
                        this.reset();
                        this.setDisabled(true);

                        if (!qd)
                            return;

                        var panel = this.up('panel');

                        LABKEY.Query.getQueryDetails({
                            containerPath: panel.down('#containerId').getValue(),
                            schemaName: panel.down('#schemaName').getValue(),
                            queryName: qd.name,

                            scope: this,
                            success: function(results){
                                Ext4.each(results.columns, function(col){
                                    if (col.jsonType == 'string'){
                                        this.store.add(LDK.StoreUtils.createModelInstance(this.store, {
                                            name: col.name,
                                            caption: col.caption
                                        }));
                                    }
                                }, this);

                                if (!this.store.getCount())
                                    Ext4.Msg.alert('Invalid Query', 'The selected query did not have any usage columns');
                                else
                                    this.setDisabled(false);
                            },
                            failure: LDK.Utils.getErrorCallback()
                        });
                    }
                });

                return items;
            },

            onContainerChange: function(containerId){
                var cf = this.down('#targetColumn');
                cf.reset();
                cf.setDisabled(true);

                this.callParent(arguments);
            },

            onSchemaChange: function(schemaName){
                var targetColField = this.down('#targetColumn');
                targetColField.setDisabled(true);
                targetColField.reset();

                this.callParent(arguments);
            },

            onQueryChange: function(queryDef){
                var target = this.down('#targetColumn');
                target.loadColumns(queryDef);

                this.callParent(arguments);
            }
        });

        Ext4.create('Ext.window.Window', {
            width: 400,
            title: 'Add Demographics Source',
            modal: true,
            closeAction: 'destroy',
            items: [Ext4.create('Laboratory.panel.AddNewDemographicsSourcePanel')],
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
        var containerId = win.down('#containerId').getValue();
        var schemaName = win.down('#schemaName').getValue();
        var queryName = win.down('#queryName').getValue();
        var column = win.down('#targetColumn').getValue();
        var label = win.down('#label').getValue();

        if (!schemaName || !queryName || !label || !column){
            Ext4.Msg.alert('Error', 'Must provide a schema, query, label and target column');
            return;
        }

        var sources = this.getSources();
        for (var i=0;i<sources.length;i++){
            var source = sources[i];
            if (source.label == label){
                Ext4.Msg.alert('Error', 'This label is already in use');
                return;
            }
        }

        sources.push({
            containerId: containerId,
            schemaName: schemaName,
            queryName: queryName,
            targetColumn: column,
            label: label
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
        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL('laboratory', 'setDemographicsSources'),
            params: {
                tables: Ext4.encode(sources)
            },
            method : 'POST',
            scope: this,
            failure: LDK.Utils.getErrorCallback({
                logToServer: false
            }),
            success: function(result){
                Ext4.Msg.hide();

                if (win)
                    win.close();

                Ext4.Msg.alert('Success', 'Demographics Sources Updated', function(){
                    this.doLoad();
                }, this);
            }
        });
    }
});