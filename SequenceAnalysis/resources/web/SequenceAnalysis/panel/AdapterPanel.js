Ext4.define('SequenceAnalysis.panel.AdapterPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.sequenceanalysis-adapterpanel',

    initComponent: function() {
        Ext4.apply(this, {
            width: '100%',
            border: false,
            items: [{
                xtype: 'grid',
                title: 'Adapters',
                itemId: 'adapterGrid',
                border: true,
                stripeRows: true,
                helpPopup: 'These sequences will be trimmed from either one or both ends of all reads.  When trimming from the 3\' end, the adapter is automatically reverse complemented.',
                width: '100%',
                style: 'padding-bottom:10px;',
                forceFit: true,
                selType: 'rowmodel',
                plugins: [Ext4.create('Ext.grid.plugin.CellEditing', {
                    pluginId: 'cellediting',
                    clicksToEdit: 1
                })],
                tbar: [{
                    text: 'Add',
                    scope: this,
                    tooltip: 'Click to add a blank record',
                    name: 'add-record-button',
                    handler: function (btn){
                        var grid = btn.up('sequenceanalysis-adapterpanel').down('#adapterGrid');
                        var store = grid.store;
                        var rec = store.createModel({});
                        store.add(rec);
                        var idx = store.indexOf(rec);
                        grid.getPlugin('cellediting').startEditByPosition({row: idx, column: 0});
                    }
                },{
                    text: 'Remove',
                    scope: this,
                    tooltip: 'Click to delete selected row(s)',
                    name: 'delete-records-button',
                    handler: function(btn){
                        var grid = btn.up('sequenceanalysis-adapterpanel').down('#adapterGrid');
                        grid.getPlugin('cellediting').completeEdit( );
                        var s = grid.getSelectionModel().getSelection();
                        for (var i = 0, r; r = s[i]; i++){
                            r.store.remove(r);
                        }
                        return false;
                    }
                },{
                    text: 'Move Up',
                    scope: this,
                    tooltip: 'Click to reorder selected records',
                    name: 'move-up-button',
                    handler: function(btn){
                        btn.up('sequenceanalysis-adapterpanel').down('#adapterGrid').moveSelectedRow(-1);
                    }
                },{
                    text: 'Move Down',
                    scope: this,
                    tooltip: 'Click to reorder selected records',
                    name: 'move-down-button',
                    handler: function(btn){
                        btn.up('sequenceanalysis-adapterpanel').down('#adapterGrid').moveSelectedRow(1);
                    }
                },{
                    text: 'Common Adapters',
                    scope: this,
                    tooltip: 'Click to using common adapters',
                    name: 'add-batch-button',
                    handler: function(btn){
                        this.adapterSelectorWin = Ext4.create('Ext.window.Window', {
                            title: 'Choose Adapters',
                            modal: true,
                            bodyBorder: true,
                            border: true,
                            closeAction: 'destroy',
                            bodyStyle: 'padding:5px;',
                            width: 450,
                            items: [{
                                xtype: 'labkey-combo',
                                fieldLabel: 'Choose Adapter Group',
                                itemId: 'adapter_group',
                                triggerAction: 'all',
                                width: 400,
                                displayField: 'group_name',
                                valueField: 'group_name',
                                store: Ext4.create('LABKEY.ext4.data.Store', {
                                    schemaName: 'sequenceanalysis',
                                    queryName: 'adapter_sets',
                                    autoLoad: true
                                })
                            }],
                            buttons: [{
                                text:'Submit',
                                disabled:false,
                                itemId: 'submit',
                                scope: this,
                                handler: function(s, v){
                                    var gn = s.up('window').down('#adapter_group').getValue();

                                    LABKEY.Query.selectRows({
                                        schemaName: 'sequenceanalysis',
                                        queryName: 'dna_adapters',
                                        filterArray: [LABKEY.Filter.create('group_name', gn, LABKEY.Filter.Types.EQUAL)],
                                        scope: this,
                                        success: function(rows){
                                            Ext4.each(rows.rows, function(r){
                                                var grid = this.down('#adapterGrid');
                                                var rec = grid.store.create({
                                                    adapterSequence: r.sequence,
                                                    adapterName: r.name,
                                                    trim5: true,
                                                    trim3: true
                                                });
                                                grid.store.add(rec);
                                            }, this);
                                        },
                                        failure: LDK.Utils.getErrorCallback()
                                    });

                                    s.up('window').close();
                                }
                            },{
                                text: 'Close',
                                scope: this,
                                handler: function(s){
                                    s.up('window').close();
                                }
                            }]
                        }).show(btn);
                    }
                }],
                moveSelectedRow: function(direction) {
                    var record = this.getSelectionModel().getSelection();

                    if (!record || !record.length) {
                        return;
                    }

                    record = record[0];

                    var index = this.getStore().indexOf(record);
                    if (direction < 0) {
                        index--;
                        if (index < 0) {
                            return;
                        }
                    } else {
                        index++;
                        if (index >= this.getStore().getCount()) {
                            return;
                        }
                    }
                    this.getStore().remove(record);
                    this.getStore().insert(index, record);
                    this.getSelectionModel().select(index, true);
                },
                columns: [{
                    name: 'adapterName',
                    header: 'Adapter Name',
                    width: 160,
                    itemId: 'adapterName',
                    dataIndex: 'adapterName',
                    allowBlank: false,
                    editor: {
                        xtype: 'textfield',
                        allowBlank: false,
                        listeners: {
                            scope: this,
                            change: function(c){
                                var grid = c.up('sequenceanalysis-adapterpanel').down('#adapterGrid');
                                var val = c.getValue();
                                var match = 0;
                                grid.store.each(function(r){
                                    if (r.get('adapterName') == val){
                                        match++;
                                    }
                                }, this);

                                if (match > 1){
                                    c.markInvalid();
                                    alert('ERROR: Adapter names must be unique');
                                }
                            }
                        }
                    }
                },{
                    name: 'adapterSequence',
                    header: 'Sequence',
                    width: 200,
                    itemId: 'adapterSequence',
                    allowBlank: false,
                    dataIndex: 'adapterSequence',
                    editor: {
                        xtype: 'textfield',
                        allowBlank: false,
                        maskRe: new RegExp('[ATGCNRYSWKMBDHVN]', 'i'),
                        listeners: {
                            scope: this,
                            change: function(c){
                                var val = c.getValue();
                                val = val.replace(/\s/g,'');
                                c.setValue(val);
                            }
                        }
                    }
                },{
                    name: 'trim5',
                    header: 'Trim 5\' End',
                    width: 80,
                    id: 'trim5',
                    dataIndex: 'trim5',
                    defaultValue: true,
                    checked: true,
                    align: 'center',
                    editor: {
                        xtype: 'checkbox',
                        checked: true,
                        value: true
                    }
                },{
                    name: 'trim3',
                    text: 'Trim 3\' End',
                    tooltip: 'Will automatically reverse completment the adapter sequence',
                    width: 80,
                    id: 'trim3',
                    dataIndex: 'trim3',
                    align: 'center',
                    editor: {
                        xtype: 'checkbox',
                        align: 'center'
                    }
                }],
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: [
                        'adapterName',
                        'adapterSequence',
                        {name: 'trim3', type: 'boolean'},
                        {name: 'trim5', type: 'boolean', defaultValue: true, checked: true}
                    ],
                    data: []
                })
            }]
        });

        this.callParent(arguments);
    },

    getValue: function(){
        var ret = [];
        this.down('gridpanel').store.each(function(r, i) {
            ret.push([r.data.adapterName, r.data.adapterSequence, r.data.trim5==true, r.data.trim3==true]);
        }, this);

        return Ext4.isEmpty(ret) ? null : ret;
    },

    getErrors: function(){
        var msgs = [];
        this.down('gridpanel').store.each(function(r, i){
            if (!r.data.adapterName){
                msgs.push('Missing name for one or more adapters');
            }
            if (!r.data.adapterSequence){
                msgs.push('Missing sequence for one or more adapters');
            }
            if (!r.data.trim5 && !r.data.trim3){
                msgs.push('Adapter: '+r.name+' must be trimmed from either 5\' or 3\' end');
            }
        }, this);

        return msgs;
    },

    setValue: function(val){
        //special handling of adapters:
        var grid = this.down('gridpanel');
        if (val){
            if (!Ext4.isArray(val)){
                val = Ext4.JSON.decode(val);
            }

            Ext4.Array.forEach(val, function(row){
                var rec = grid.store.createModel({
                    adapterName: row[0],
                    adapterSequence: row[1],
                    trim5: row[2],
                    trim3: row[3]
                });
                grid.store.add(rec);
            }, this);
        }
        else {
            grid.store.removeAll();
        }
    }
});
