Ext4.define('SequenceAnalysis.panel.AdapterPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.adapterpanel',

    initComponent: function() {
        Ext4.apply(this, {
            hideLabel: true,
            hidden: true,
            width: 'auto',
            autoHeight: true,
            bodyStyle: 'padding:5px;',
            items: [{
                xtype: 'grid',
                title: 'Adapters',
                itemId: 'adapterGrid',
                border: true,
                stripeRows: true,
                renderData: {
                    helpPopup: 'These sequences will be trimmed from either one or both ends of all reads.  When trimming from the 3\' end, the adapter is automatically reverse complemented.'
                },
                width: '100%',
                style: 'padding-bottom:10px;',
                forceFit: true,
                selType: 'rowmodel',
                plugins: [Ext4.create('Ext.grid.plugin.CellEditing', {
                    pluginId: 'cellediting',
                    clicksToEdit: 2
                })],
                tbar: [{
                    text: 'Add',
                    scope: this,
                    tooltip: 'Click to add a blank record',
                    name: 'add-record-button',
                    handler: function (btn){
                        var grid = btn.up('form').down('#adapterPanel').down('#adapterGrid');
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
                        var grid = btn.up('form').down('#adapterPanel').down('#adapterGrid');
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
                        var grid = btn.up('form').down('#adapterPanel').down('#adapterGrid').moveSelectedRow(-1);
                    }
                },{
                    text: 'Move Down',
                    scope: this,
                    tooltip: 'Click to reorder selected records',
                    name: 'move-down-button',
                    handler: function(btn){
                        var grid = btn.up('form').down('#adapterPanel').down('#adapterGrid').moveSelectedRow(1);
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
                                        successCallback: function(rows){
                                            Ext4.each(rows.rows, function(r){
                                                var grid = this.up('form').down('#adapterPanel').down('#adapterGrid');
                                                var rec = grid.store.create({
                                                    adapterSequence: r.sequence,
                                                    adapterName: r.name,
                                                    trim5: true,
                                                    trim3: true,
                                                    palindrome: false
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
                                var grid = c.up('form').down('#adapterPanel').down('#adapterGrid');
                                var val = c.getValue();
                                var match = 0;
                                grid.store.each(function(r){
                                    if(r.get('adapterName') == val){
                                        match++;
                                    }
                                }, this);

                                if(match > 1){
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
                },{
                    name: 'mode',
                    header: 'Palindrome Mode',
                    tooltip: 'If paired end data is used, this can specify whether this adapter will be used on the foward file, reverse file or both',
                    width: 80,
                    id: 'palindrome',
                    dataIndex: 'palindrome',
                    align: 'center',
                    editor: {
                        xtype: 'checkbox',
                        checked: true
                    }
                }],
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: [
                        'adapterName',
                        'adapterSequence',
                        {name: 'trim3', type: 'boolean'},
                        {name: 'trim5', type: 'boolean', defaultValue: true, checked: true},
                        {name: 'palindrome', type: 'boolean'}
                    ],
                    data: []
                })
            },{
                xtype: 'numberfield',
                fieldLabel: 'Seed Mismatches',
                name: 'preprocessing.seedMismatches',
                value: 2,
                minValue: 0,
                renderData: {
                    helpPopup: 'The \'seed mismatch\' parameter is used to make alignments more efficient, specifying the maximum base mismatch count in the 16-base \'seed\'. Typical values here are 1 or 2.'
                }
            },{
                xtype: 'numberfield',
                fieldLabel: 'Simple Clip Threshold',
                name: 'preprocessing.simpleClipThreshold',
                value: 12,
                minValue: 0,
                renderData: {
                    helpPopup: 'A full description of this parameter can be found on the trimmomatic homepage.  The following is adapted from their documentation: The thresholds used are a simplified log-likelihood approach. Each matching base adds just over 0.6, while each mismatch reduces the alignment score by Q/10. Therefore, a perfect match of a 20 base sequence will score just over 12, while 25 bases are needed to score 15. As such we recommend values between 12 - 15 for this parameter.'
                }
            },{
                xtype: 'numberfield',
                fieldLabel: 'Palindrome Clip Threshold',
                name: 'preprocessing.palindromeClipThreshold',
                value: 30,
                minValue: 0,
                renderData: {
                    helpPopup: 'A full description of this parameter can be found on the trimmomatic homepage.  The following is adapted from their documentation: For palindromic matches, the entire read sequence plus (partial) adapter sequences can be used - therefore this threshold can be higher, in the range of 30-40.'
                }
            }]
        });

        this.callParent(arguments);
    }
});
