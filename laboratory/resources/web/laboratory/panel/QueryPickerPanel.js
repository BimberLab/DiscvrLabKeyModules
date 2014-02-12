Ext4.define('Laboratory.panel.QueryPickerPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-querypickerpanel',
    config: {
        containerId: null,
        schemaName: null,
        queryName: null,
        viewName: null
    },

    initComponent: function(){
        Ext4.apply(this, {
            defaults: {
                labelWidth: 150,
                width: 370
            },
            bodyStyle: 'padding: 5px;',
            items: this.getItemsCfg()
        });

        this.addEvents('containerChange', 'schemaChange', 'queryChange', 'viewChange');
        this.callParent();
    },

    getItemsCfg: function(){
        return [
            this.getContainerFieldCfg(),
            this.getSchemaFieldCfg(),
            this.getQueryFieldCfg()
        ];
    },

    getContainerFieldCfg: function(){
        return {
            xtype: 'labkey-combo',
            //NOTE: this has been set non-editable b/c otherwise the change event fires whenever the user types a character, which might include an invalid containerPath
            //in that case, the code tries to find the container and throws an error.  it wouldnt be too hard to make a smarter listener, but not worth it for now
            editable: false,
            itemId: 'containerId',
            fieldLabel: 'Container (optional)',
            valueField: 'EntityId',
            queryMode: 'local',
            displayField: 'Path',
            store: {
                type: 'labkey-store',
                schemaName: 'core',
                queryName: 'containers',
                columns: 'Name,Path,EntityId',
                containerFilter: 'AllFolders',
                filterArray: [LABKEY.Filter.create('ContainerType', 'workbook', LABKEY.Filter.Types.NEQ)],
                autoLoad: true,
                listeners: {
                    load: function(store){
                        //add a null record so user can clear the combo
                        var rec = store.createModel({
                            EntityId: null,
                            Path: null,
                            Name: '[none]'
                        });

                        store.insert(0, rec);

                        store.sort('Path', 'ASC');
                        store.fireEvent('datachanged', store);
                    }
                }
            },
            listeners: {
                change: function(field, val){
                    var panel = field.up('panel');
                    //panel.onContainerChange(records[0].get('EntityId'));
                    panel.onContainerChange(val);
                }
            }
        }
    },

    getSchemaFieldCfg: function(){
        return {
            xtype: 'combo',
            editable: false,
            fieldLabel: 'Schema',
            itemId: 'schemaName',
            disabled: true,
            allowBlank: false,
            queryMode: 'local',
            valueField: 'name',
            displayField: 'name',
            listConfig: {
                deferInitialRefresh: false
            },
            store:{
                proxy: {
                    type: 'memory'
                },
                fields: ['name']
            },
            loadSchemaField: function(containerId){
                this.store.removeAll();
                this.reset();

                var panel = this.up('panel');
                panel.getEl().mask('Loading...');

                var qf = panel.down('#queryName');
                qf.reset();
                qf.setDisabled(true);

                LABKEY.Query.getSchemas({
                    containerPath: containerId,
                    scope: this,
                    success: function(results){
                        var toAdd = [];
                        Ext4.each(results.schemas, function(name){
                            toAdd.push(LDK.StoreUtils.createModelInstance(this.store, {name: name}));
                        }, this);

                        this.store.add(toAdd);
                        this.setDisabled(false);
                        panel.getEl().unmask();
                    },
                    failure: LDK.Utils.getErrorCallback()
                });
            },
            listeners: {
                render: function(field){
                    field.loadSchemaField();
                },
                change: function(field, val){
                    var panel = field.up('panel');
                    //panel.onSchemaChange(records[0].get('name'));
                    panel.onSchemaChange(val);
                }
            }
        };
    },

    getQueryFieldCfg: function(){
        return {
            xtype: 'combo',
            editable: false,
            fieldLabel: 'Query',
            itemId: 'queryName',
            allowBlank: false,
            queryMode: 'local',
            disabled: true,
            valueField: 'name',
            displayField: 'name',
            store: {
                fields: ['name', 'queryDef'],
                proxy: {
                    type: 'memory'
                }
            },
            loadQueryField: function(schemaName){
                this.store.removeAll();
                this.reset();
                this.setDisabled(true);

                var panel = this.up('panel');
                panel.getEl().mask('Loading...');

                if (!schemaName)
                    return;

                LABKEY.Query.getQueries({
                    containerPath: panel.down('#containerId').getValue(),
                    schemaName: schemaName,
                    timeout: 0,
                    scope: this,
                    success: function(results){
                        var toAdd = [];
                        Ext4.each(results.queries, function(qd){
                            toAdd.push(LDK.StoreUtils.createModelInstance(this.store, {name: qd.name, queryDef: qd}));
                        }, this);

                        this.store.add(toAdd);
                        this.store.sort('name');

                        this.setDisabled(false);
                        panel.getEl().unmask();
                    },
                    failure: LDK.Utils.getErrorCallback()
                });
            },
            listeners: {
                change: function(field, val){
                    var panel = field.up('panel');

                    //var qd = records[0].get('queryDef');
                    var recIdx = field.store.findExact(field.valueField, val);
                    var qd = recIdx == -1 ? null : field.store.getAt(recIdx).get('queryDef');
                    panel.onQueryChange(qd);
                }
            }
        }
    },

    onContainerChange: function(containerId){
        this.down('#schemaName').loadSchemaField(containerId);
    },

    onSchemaChange: function(schemaName){
        this.down('#queryName').loadQueryField(schemaName);
    },

    onQueryChange: function(queryDef){

    },

    onViewChange: function(viewName){

    }
});
