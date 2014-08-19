Ext4.define('SequenceAnalysis.panel.SamplePanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.samplepanel',

    initComponent: function(){
        Ext4.apply(this, {
            width: '100%',
            title: 'Readsets',
            editingPluginId: 'cellediting',
            plugins: [Ext4.create('LDK.grid.plugin.CellEditing', {
                pluginId: 'cellediting',
                clicksToEdit: 1,
                listeners: {
                    beforeedit: function(cell, object){
                        var readset = object.record.get('readset');
                        if (readset && object.field != 'readset' && object.field != 'fileName' && object.field != 'fileName2' && object.field != 'mid5' && object.field != 'mid3'){
                            //alert('This sample is using a readset that was created previously.  You cannot edit ' + object.column.text + ' for this readset.  If you wish to update that readset, click the View/Edit Readsets link above.');
                            return false;
                        }

                        //cant edit the filename if we're merging
                        if (object.field == 'fileName'){
                            var form = object.grid.up('form');
                            if (form.down('#mergeCheckbox').checked){
                                alert('You have selected to merge the input files.  To set the filename, fill out the field labeled "Name For Merged File".');
                                return false;
                            }
                        }
                    }
                }
            })],
            multiSelect: true,
            border: true,
            stripeRows: true,
            forceFit: true,
            name: 'metadataGrid',
            enableHdMenu: false,
            selType: 'rowmodel',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: [
                    {name: 'fileName', allowBlank: false},
                    {name: 'fileName2', allowBlank: false},
                    {name: 'mid5'},
                    {name: 'mid3'},
                    {name: 'readset', allowBlank: false},
                    {name: 'readsetname'},
                    {name: 'platform', allowBlank: false},
                    {name: 'application', allowBlank: false},
                    {name: 'inputmaterial'},
                    {name: 'subjectid'},
                    {name: 'sampledate'},
                    {name: 'sampleid'},
                    {name: 'instrument_run_id'},
                    {name: 'fileId'},
                    {name: 'fileId2'}
                ],
                storeId: 'metadata',
                listeners: {
                    scope: this,
                    update: function(store, rec){
                        this.validateReadsets(store, [rec]);
                    },
                    add: this.validateReadsets
                }
            }),
            tbar: [{
                text: 'Add',
                itemId: 'sampleAddBtn',
                handler : function(c){
                    var grid = c.up('#sampleGrid');
                    // access the Record constructor through the grid's store
                    var r = grid.getStore().createModel({});

                    grid.getStore().insert(0, [r]);
                    if (grid.up('form').down('#mergeCheckbox').checked)
                        r.set('fileName', grid.up('form').down('#mergeName').getValue());
                    else {
                        var plugin = grid.getPlugin('cellediting');
                        LDK.Assert.assertNotEmpty('Unable to find cellediting plugin', plugin);
                        plugin.startEditByPosition({row: 0, column: 0});
                    }
                }
            },{
                text: 'Remove',
                itemId: 'sampleRemoveBtn',
                scope: this,
                handler : function(c){
                    Ext4.Msg.wait("Loading...");
                    this.getPlugin('cellediting').completeEdit( );
                    var s = this.getSelectionModel().getSelection();
                    for (var i = 0, r; r = s[i]; i++){
                        this.getStore().remove(r);
                    }
                    Ext4.Msg.hide();
                }
            },{
                text: 'Add From Spreadsheet',
                tooltip: 'Click this to populate the rows of the grid using a spreadsheet',
                itemId: 'addBatchBtn',
                scope: this,
                handler: function(btn){
                    this.getPlugin('cellediting').completeEdit( );
                    this.excelImportWin = this.getExcelImportWin();
                    this.excelImportWin.show(btn);
                }
            },{
                text: 'Bulk Edit',
                tooltip: 'Click this to change values on all checked rows in bulk',
                scope: this,
                handler : function(btn){
                    var grid = btn.up('grid');
                    var s = grid.getSelectionModel().getSelection();
                    if (!s.length){
                        alert('No rows selected');
                        return;
                    }
                    this.bulkEditWin = this.getBulkEditWin();
                    this.bulkEditWin.show(btn);
                }
            }],
            columns: [{
                name: 'fileName',
                text: 'Source File',
                dataIndex: 'fileName',
                renderer: function(data, attrs, record){
                    if (record.get('isValid') === false){
                        attrs.style = 'background: red;';
                        attrs.tdAttr = attrs.tdAttr || '';
                        if (record.get('qtip'))
                            attrs.tdAttr += 'data-qtip="' + record.get('qtip') + '"';
                    }
                    return data;
                },
                editor: {
                    xtype: 'labkey-combo',
                    triggerAction: 'all',
                    displayField: 'fileName',
                    valueField: 'fileName',
                    allowBlank: false,
                    forceSelection: true,
                    typeAhead: true,
                    lazyRender: false,
                    queryMode: 'local',
                    store: this.fileNameStore
                }
            },{
                name: 'fileName2',
                text: 'Paired File',
                dataIndex: 'fileName2',
                hidden: true,
                editor: {
                    xtype: 'labkey-combo',
                    triggerAction: 'all',
                    displayField: 'fileName',
                    valueField: 'fileName',
                    allowBlank: true,
                    forceSelection: true,
                    typeAhead: true,
                    lazyRender: false,
                    queryMode: 'local',
                    store: this.fileNameStore
                }
            },{
                text: '5\' Barcode',
                id: 'mid5',
                dataIndex: 'mid5',
                mode: 'local',
                hidden: true,
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    editable: false,
                    queryMode: 'local',
                    triggerAction: 'all',
                    forceSelection: true,
                    typeAhead: true,
                    displayField: 'tag_name',
                    valueField: 'tag_name',
                    lazyRender: false,
                    store:  Ext4.create('LABKEY.ext4.data.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'barcodes',
                        sort: 'group_name,tag_name',
                        autoLoad: true,
                        nullRecord: {

                        }
                    })
                }
            },{
                text: '3\' Barcode',
                id: 'mid3',
                dataIndex: 'mid3',
                mode: 'local',
                hidden: true,
                editor: {
                    xtype: 'combo',
                    allowBlank: true,
                    editable: false,
                    triggerAction: 'all',
                    forceSelection: true,
                    typeAhead: true,
                    displayField: 'tag_name',
                    valueField: 'tag_name',
                    lazyRender: false,
                    store:  Ext4.create('LABKEY.ext4.data.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'barcodes',
                        sort: 'group_name,tag_name',
                        autoLoad: true,
                        nullRecord: {

                        }
                    })
                }
            },{
                text: 'Readset Id',
                name: 'readset',
                queryMode: 'local',
                hidden: false,
                dataIndex: 'readset',
                width: 40,
                editable: true,
                editor: {
                    xtype: 'ldk-numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'Readset Name',
                name: 'readsetname',
                mode: 'local',
                dataIndex: 'readsetname',
                editor: {
                    xtype: 'textfield',
                    allowBlank: false
                }
            },{
                text: 'Platform',
                name: 'platform',
                width: 80,
                mode: 'local',
                dataIndex: 'platform',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'platform',
                    valueField: 'platform',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_platforms',
                        autoLoad: true
                    })
                }
            },{
                text: 'Application',
                name: 'application',
                width: 80,
                mode: 'local',
                dataIndex: 'application',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'application',
                    valueField: 'application',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_applications',
                        autoLoad: true
                    })
                }
            },{
                text: 'Input Material',
                name: 'inputmaterial',
                width: 80,
                dataIndex: 'inputmaterial',
                editor: {
                    xtype: 'textfield',
                    allowBlank: true
                }
            },{
                text: 'Subject Id',
                name: 'subjectid',
                dataIndex: 'subjectid',
                editor: {
                    xtype: 'textfield',
                    allowBlank: true
                }
            },{
                text: 'Sample Date',
                name: 'sampledate',
                xtype: 'datecolumn',
                format: 'Y-m-d',
                dataIndex: 'sampledate',
                editor: {
                    xtype: 'datefield',
                    format: 'Y-m-d',
                    allowBlank: true
                }
            },{
                text: 'Freezer Id',
                name: 'sampleid',
                dataIndex: 'sampleid',
                width: 40,
                editor: {
                    xtype: 'ldk-numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'Instrument Run Id',
                name: 'instrument_run_id',
                dataIndex: 'instrument_run_id',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    displayField: 'name',
                    valueField: 'rowid',
                    editable: false,
                    queryMode: 'local',
                    showValueInList: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'instrument_runs',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        autoLoad: true
                    })
                }
            },{
                text: 'File Id',
                name: 'fileId',
                hidden: true,
                dataIndex: 'fileId',
                editor: {
                    xtype: 'numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'File Id 2',
                name: 'fileId2',
                hidden: true,
                dataIndex: 'fileId2',
                editor: {
                    xtype: 'numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            }]
        });

        this.callParent(arguments);

        this.mon(this.parent, 'midchange', this.onMidChange, this);
        this.mon(this.parent, 'pairedendchange', this.onPairedEndChange, this);

    },

    getBulkEditWin: function(){
        return Ext4.create('Ext.window.Window', {
            width: 280,
            closeAction:'destroy',
            modal: true,
            scope: this,
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: this.onBulkEdit,
                scope: this
            }],
            title: 'Bulk Edit',
            items: [{
                xtype: 'form',
                border: false,
                bodyStyle:'padding:5px',
                items: [{
                    emptyText: '',
                    fieldLabel: 'Select Field',
                    itemId: 'fieldName',
                    xtype: 'labkey-combo',
                    displayField: 'name',
                    valueField: 'value',
                    typeAhead: true,
                    triggerAction: 'all',
                    queryMode: 'local',
                    width: '100%',
                    editable: false,
                    required: true,
                    store: Ext4.create('Ext.data.ArrayStore', {
                        fields: ['value', 'name'],
                        data: (function(cols){
                            var values = [];
                            Ext4.each(cols, function(c){
                                if (!c.hidden)
                                    values.push([c.dataIndex, c.text])
                            }, this);
                            return values;
                        })(this.columns)
                    })
                },{
                    xtype: 'textfield',
                    itemId: 'fieldVal',
                    fieldLabel: 'Enter Value'
                }]
            }],
            buttons: [{
                text:'Submit',
                disabled:false,
                formBind: true,
                itemId: 'submit',
                scope: this,
                handler: this.onBulkEdit
            },{
                text: 'Close',
                itemId: 'close',
                scope: this,
                handler: function(c){
                    c.up('window').close();
                }
            }]
        });
    },

    getExcelImportWin: function(){
        return Ext4.create('Ext.window.Window', {
            width: 520,
            modal: true,
            closeAction: 'destroy',
            scope: this,
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: this.processExcel,
                scope: this
            }],
            title: 'Add Readsets',
            items: [{
                border: false,
                bodyStyle:'padding:5px',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'This allows you to add readsets from an excel file.  Just download the template using the link below, fill it out, then cut/paste it into the box below.  Note that all fields are case-sensitive and the import will fail if the case does not match.<br><br>If you are using file/barcode combinations imported previously, if you enter only the file/barcodes the rest of the information will populate automatically.',
                    style: 'padding-bottom: 10px;'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Excel Template For Samples Without Preexisting Readsets',
                    linkPrefix: '[',
                    linkSuffix: ']',
                    scope: this,
                    handler: function(btn){
                        var header = [];
                        Ext4.each(this.columns, function(col){
                            if (!col.hidden && col.dataIndex != 'readset')
                                header.push(col.text);
                        }, this);

                        var data = [header];
                        Ext4.each(this.up('form').fileNames, function(fn){
                            data.push([fn]);
                        }, this);

                        var config = {
                            fileName : 'Sequence_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
                            sheets : [{
                                name: 'data',
                                data: data
                            }]
                        };

                        LABKEY.Utils.convertToExcel(config);
                    }
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Excel Template To Enter Samples Using Readset Id',
                    linkPrefix: '[',
                    linkSuffix: ']',
                    scope: this,
                    handler: function(btn){
                        var header = [];
                        Ext4.each(this.columns, function(col){
                            if (!col.hidden && (col.dataIndex == 'fileName' || col.dataIndex == 'fileName2' || col.dataIndex == 'readset'))
                                header.push(col.text);
                        }, this);

                        var data = [header];
                        Ext4.each(this.up('form').fileNames, function(fn){
                            data.push([fn]);
                        }, this);

                        var config = {
                            fileName : 'Sequence_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
                            sheets : [{
                                name: 'data',
                                data: data
                            }]
                        };

                        LABKEY.Utils.convertToExcel(config);
                    }
                },{
                    xtype: 'textarea',
                    itemId: 'excelContent',
                    height: 300,
                    width: 500
                }]
            }],
            buttons: [{
                text:'Submit',
                disabled:false,
                formBind: true,
                itemId: 'submit',
                scope: this,
                handler: this.processExcel
            },{
                text: 'Close',
                itemId: 'close',
                scope: this,
                handler: function(c){
                    c.up('window').close();
                }
            }]
        });
    },

    processExcel: function(btn){
        var win = btn.up('window');
        var textarea = win.down('textarea');
        var text = textarea.getValue();
        if (!text){
            alert('You must enter something in the textarea');
            return;
        }

        win.close();
        textarea.reset();

        text = Ext4.String.trim(text);
        var data = LDK.Utils.CSVToArray(text, '\t');
        var columns = [];
        var errors = [];

        //translate column text into name
        Ext4.each(data[0], function(field){
            if (!field)
                return;

            Ext4.each(this.columns, function(col, idx){
                if (col.text == field || col.dataIndex.toLowerCase() == field.toLowerCase()){
                    columns.push(col);
                    return false;
                }

                if (!idx == this.columns.length){
                    errors.push('Unable to find column: ' + field);
                }
            }, this);
        }, this);
        data.shift();

        if (errors.length){
            alert(errors.join('\n'));
            return;
        }

        var toAdd = [];
        Ext4.each(data, function(row){
            var obj = {};
            Ext4.each(columns, function(col, idx){
                var value = row[idx];

                if (col.editor){
                    var editor = Ext4.ComponentManager.create(col.editor);

                    if (col.editor.store && !Ext4.isEmpty(value) && col.editor.store.find(col.editor.valueField, value) == -1){
                        console.log(col.text +'/' + col.editor.store.getCount() + '/' + '/' + col.editor.valueField + '/' + value);
                        errors.push('Invalid value for field '+col.text+': '+value);
                    }

                    if (!col.editor.allowBlank && Ext4.isEmpty(value)){
                        errors.push('The field '+col.text+' is required');
                    }

                    if (editor.getErrors){
                        var fieldErrors = editor.getErrors(value);
                        if (fieldErrors.length){
                            errors.push('Error for field ' + col.text + ': ' + fieldErrors[0]);
                        }
                    }
                }
                obj[col.dataIndex] = value;
            }, this);

            if (!LABKEY.Utils.isEmptyObj(obj))
                toAdd.push(this.store.createModel(obj));
        }, this);

        if (errors.length){
            alert(errors.join('\n'));
            return;
        }
        this.store.add(toAdd);
    },

    validateReadsets: function(store, recs){
        var readsetMap = {};
        var readsets = [];
        Ext4.each(recs, function(rec){
            rec.data.isValid = null;
            var id = rec.get('readset');
            if (id){
                readsetMap[id] = rec;
                readsets.push(id);
            }
        }, this);

        var panel = this.up('form');
        var grid = panel.down('#sampleGrid');
        var useMids = panel.down('#useBarcode').getValue();

        if (!readsets.length){
            return;
        }

        var sql = 'select  ' +
                'r.rowid,r.name,r.platform,r.application,r.inputMaterial,r.subjectid,r.sampledate,r.sampleid,r.barcode5,r.barcode3,r.fileid,r.fileid2,r.instrument_run_id,r.fileid2.name as fileName,r.fileid.name as fileName2 \n' +
                'from sequenceanalysis.sequence_readsets r \n';

        sql += 'WHERE rowid IN (' + readsets.join(',') + ')';

        LABKEY.Query.executeSql({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            sql: sql,
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                var msgs = [];
                var rowMap = {};
                for (var i=0;i<results.rows.length;i++){
                    rowMap[results.rows[i].rowid] = results.rows[i];
                }

                //NOTE: dont use set() so we dont trigger an update event for any records
                Ext4.each(readsets, function(readset){
                    var record = readsetMap[readset];
                    if (!rowMap[readset]){
                        msgs.push('Unable to find readset with rowid: ' +  readset);
                        record.data.readset = null;
                        return;
                    }

                    var row = rowMap[readset];

                    if (row.fileid || row.fileid2){
                        msgs.push('Readset ' + readset + 'has already been associated with files and cannot be re-used.  If you would like to reanalyze this readset, load the table of readsets and look for the \'Analyze Data\' button.');
                        record.data.isValid = false;
                        record.data.readset = null;
                        return;
                    }

                    if (useMids && (!row.barcode3 && !row.barcode5))
                    {
                        msgs.push('Readset ' + readset + ' does not have barcodes, but you have selected to use barcodes');
                        record.data.readset = null;
                        return;
                    }
                    else if (!useMids && (row.barcode3 || row.barcode5))
                    {
                        msgs.push('Readset ' + readset + ' has barcodes, but you have not selected to use barcodes');
                        record.data.readset = null;
                        return;
                    }

                    //update row based on saved readset.  avoid firing event
                    Ext4.apply(record.data, {
                        readsetname: row.name,
                        platform: row.platform,
                        application: row.application,
                        sampleid: row.sampleid,
                        subjectid: row.subjectid,
                        sampledate: row.sampledate,
                        inputmaterial: row.inputmaterial,
                        instrument_run_id: row.instrument_run_id,
                        isValid: true
                    });

                }, this);

                if (msgs.length){
                    Ext4.Msg.alert('Error', msgs.join('<br>'));
                }

                //store.fireEvent('datachanged', store);
                grid.getView().refresh();
            }
        });
    },

    onBulkEdit: function(btn){
        var win = btn.up('window');
        win.close();

        var f = win.down('#fieldName').getValue();
        var v = win.down('#fieldVal').getValue();
        var s = this.getSelectionModel().getSelection();
        if (!s.length){
            alert('No rows selected');
        }
        for (var i = 0, r; r = s[i]; i++){
            r.set(f, v);
        }
        win.down('#fieldName').reset();
        win.down('#fieldVal').reset();
    },

    addBarcodeSeries: function(btn){
        var win = btn.up('window');
        var files = win.down('#files').getValue();
        var prefix = win.down('#prefix').getValue();
        var padding = win.down('#padding').getValue();
        var mid1 = win.down('#mid1').getValue();
        var mid2 = win.down('#mid2').getValue();

        if (!files || !prefix || !mid1 || !mid2  || !files.length){
            alert('Must pick one or more files, a prefix, and starting/ending barcode #s');
            return;
        }
        win.close();

        var mid;
        var barcodeStore = this.barcodeStore;
        console.log(barcodeStore);
        Ext4.each(files, function(f){
            for (var i=mid1;i<=mid2;i++){
                mid = prefix + (padding ? LABKEY.Utils.padString (i, 2, 0) : i);

                this.store.add(this.store.createModel({fileName: f, mid5: mid}));
            }
        }, this);

        win.down('#files').reset();
        win.down('#mid1').reset();
        win.down('#mid2').reset();
    },

    onMidChange: function(c, v){
        var changed = false;
        Ext4.each(this.columns, function(col){
            if (col.dataIndex=='mid5' || col.dataIndex=='mid3'){
                col.setVisible(v);
                changed = true;
            }
        }, this);

        if (changed){
            this.reconfigure();
        }
        this.validateReadsets(this.store, this.store.getRange())

    },

    onPairedEndChange: function(c, v){
        var changed = false;
        Ext4.each(this.columns, function(col){
            if (col.dataIndex=='fileName2'){
                col.setVisible(v);
                changed = true;

                if (!v){
                    this.store.each(function(rec){
                        rec.set('fileName2', null);
                    }, this);
                }
            }
        }, this);

        if (changed){
            this.reconfigure();
        }
        this.validateReadsets(this.store, this.store.getRange());
    }
});
