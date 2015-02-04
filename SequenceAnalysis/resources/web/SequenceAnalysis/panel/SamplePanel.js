Ext4.define('SequenceAnalysis.panel.SamplePanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.samplepanel',

    initComponent: function(){
        Ext4.apply(this, {
            width: '100%',
            minHeight: 200,
            cls: 'ldk-grid',
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
                    }
                }
            })],
            multiSelect: true,
            border: true,
            stripeRows: true,
            //forceFit: false,
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
                    {name: 'librarytype'},
                    {name: 'inputmaterial'},
                    {name: 'sampletype'},
                    {name: 'subjectid'},
                    {name: 'sampledate'},
                    {name: 'comments'},
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
//                text: 'Populate/Reorder Files',
//                tooltip: 'Click to reorder the files based on either name or best-guess based on Illumina filename conventions.',
//                scope: this,
//                handler : function(btn){
//                    var grid = btn.up('grid');
//
//                    Ext4.create('SequenceAnalysis.window.SequenceFileOrderWindow', {
//                        targetGrid: this,
//                        sequencePanel: this.up('sequenceanalysis-sequenceimportpanel')
//                    }).show(btn);
//                }
//            },{
                text: 'Add',
                itemId: 'sampleAddBtn',
                handler : function(c){
                    var grid = c.up('#sampleGrid');
                    // access the Record constructor through the grid's store
                    var r = grid.getStore().createModel({});

                    grid.getStore().insert(0, [r]);

                    var plugin = grid.getPlugin('cellediting');
                    LDK.Assert.assertNotEmpty('Unable to find cellediting plugin', plugin);
                    plugin.startEditByPosition({row: 0, column: 0});
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
                text: 'Apply Metadata From Spreadsheet',
                tooltip: 'Click this to populate the rows of the grid using a spreadsheet',
                itemId: 'addBatchBtn',
                scope: this,
                handler: function(btn){
                    this.getPlugin('cellediting').completeEdit();
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
                        Ext4.Msg.alert('Error', 'No rows selected');
                        return;
                    }

                    Ext4.create('LDK.window.GridBulkEditWindow', {
                        targetGrid: this
                    }).show(btn);
                }
            }],
            columns: [{
                name: 'fileName',
                text: 'Source File',
                tdCls: 'ldk-wrap-text',
                width: 275,
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
                tdCls: 'ldk-wrap-text',
                width: 275,
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
                tdCls: 'ldk-wrap-text',
                id: 'mid5',
                dataIndex: 'mid5',
                width: 80,
                mode: 'local',
                hidden: true,
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    editable: true,
                    queryMode: 'local',
                    triggerAction: 'all',
                    forceSelection: true,
                    typeAhead: true,
                    displayField: 'tag_name',
                    valueField: 'tag_name',
                    lazyRender: false,
                    store:  Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
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
                tdCls: 'ldk-wrap-text',
                id: 'mid3',
                dataIndex: 'mid3',
                mode: 'local',
                width: 80,
                hidden: true,
                editor: {
                    xtype: 'combo',
                    allowBlank: true,
                    editable: true,
                    triggerAction: 'all',
                    forceSelection: true,
                    typeAhead: true,
                    displayField: 'tag_name',
                    valueField: 'tag_name',
                    lazyRender: false,
                    store:  Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
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
                tdCls: 'ldk-wrap-text',
                name: 'readset',
                queryMode: 'local',
                hidden: false,
                dataIndex: 'readset',
                width: 80,
                editable: true,
                editor: {
                    xtype: 'ldk-numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'Readset Name',
                tdCls: 'ldk-wrap-text',
                name: 'readsetname',
                width: 180,
                mode: 'local',
                dataIndex: 'readsetname',
                editor: {
                    xtype: 'textfield',
                    allowBlank: false
                }
            },{
                text: 'Platform',
                tdCls: 'ldk-wrap-text',
                name: 'platform',
                width: 100,
                mode: 'local',
                dataIndex: 'platform',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'platform',
                    valueField: 'platform',
                    editable: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_platforms',
                        autoLoad: true
                    })
                }
            },{
                text: 'Application',
                tdCls: 'ldk-wrap-text',
                name: 'application',
                width: 180,
                mode: 'local',
                dataIndex: 'application',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'application',
                    valueField: 'application',
                    plugins: ['ldk-usereditablecombo'],
                    editable: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_applications',
                        autoLoad: true
                    })
                }
            },{
                text: 'Library Type',
                tdCls: 'ldk-wrap-text',
                name: 'librarytype',
                width: 180,
                mode: 'local',
                dataIndex: 'librarytype',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    forceSelection: true,
                    displayField: 'type',
                    valueField: 'type',
                    plugins: ['ldk-usereditablecombo'],
                    editable: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'library_types',
                        autoLoad: true
                    })
                }
            },{
                text: 'Sample Type',
                tdCls: 'ldk-wrap-text',
                name: 'sampletype',
                width: 120,
                dataIndex: 'sampletype',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    forceSelection: true,
                    displayField: 'type',
                    valueField: 'type',
                    plugins: ['ldk-usereditablecombo'],
                    editable: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'laboratory',
                        queryName: 'sample_type',
                        autoLoad: true
                    })
                }
            },{
                text: 'Input Material',
                tdCls: 'ldk-wrap-text',
                name: 'inputmaterial',
                width: 120,
                dataIndex: 'inputmaterial',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    forceSelection: true,
                    displayField: 'material',
                    valueField: 'material',
                    plugins: ['ldk-usereditablecombo'],
                    editable: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'input_material',
                        autoLoad: true
                    })
                }
            },{
                text: 'Subject Id',
                tdCls: 'ldk-wrap-text',
                name: 'subjectid',
                width: 100,
                dataIndex: 'subjectid',
                editor: {
                    xtype: 'textfield',
                    allowBlank: true
                }
            },{
                text: 'Sample Date',
                tdCls: 'ldk-wrap-text',
                name: 'sampledate',
                xtype: 'datecolumn',
                width: 120,
                format: 'Y-m-d',
                dataIndex: 'sampledate',
                editor: {
                    xtype: 'datefield',
                    format: 'Y-m-d',
                    allowBlank: true
                }
            },{
                text: 'Comments',
                tdCls: 'ldk-wrap-text',
                name: 'comments',
                width: 180,
                dataIndex: 'comments',
                editor: {
                    xtype: 'textarea',
                    height: 100,
                    allowBlank: true
                }
            },{
                text: 'Freezer Id',
                tdCls: 'ldk-wrap-text',
                name: 'sampleid',
                dataIndex: 'sampleid',
                width: 80,
                editor: {
                    xtype: 'ldk-integerfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'Instrument Run Id',
                tdCls: 'ldk-wrap-text',
                name: 'instrument_run_id',
                width: 140,
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
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'instrument_runs',
                        autoLoad: true
                    })
                }
            },{
                text: 'File Id',
                tdCls: 'ldk-wrap-text',
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
                tdCls: 'ldk-wrap-text',
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

        this.on('afterrender', this.updateColWidth, this, {single: true});
        this.on('columnresize', this.updateColWidth, this);
    },

    updateColWidth: function(){
        var width = 20;
        Ext4.Array.forEach(this.columns, function(c){
            if (c.isVisible()){
                width += c.getWidth();
            }
        }, this);

        this.up('sequenceanalysis-basesequencepanel').doResize(width);
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
                        var dataIndexes = [];
                        Ext4.each(this.columns, function(col){
                            if (!col.hidden && col.dataIndex != 'readset') {
                                header.push(col.text);
                                dataIndexes.push(col.dataIndex);
                            }
                        }, this);

                        var data = [header];
                        this.store.each(function(rec){
                            var row = [];
                            Ext4.Array.forEach(dataIndexes, function(di){
                                row.push(Ext4.isEmpty(rec.get(di)) ? null : rec.get(di));
                            }, this);

                            data.push(row);
                        }, this);

                        LABKEY.Utils.convertToExcel({
                            fileName : 'Sequence_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
                            sheets : [{
                                name: 'data',
                                data: data
                            }]
                        });
                    }
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Excel Template To Enter Samples Using Readset Id',
                    linkPrefix: '[',
                    linkSuffix: ']',
                    scope: this,
                    handler: function(btn){
                        var header = [];
                        var dataIndexes = [];
                        Ext4.each(this.columns, function(col){
                            if (!col.hidden && (col.dataIndex == 'fileName' || col.dataIndex == 'fileName2' || col.dataIndex == 'readset')) {
                                header.push(col.text);
                                dataIndexes.push(col.dataIndex);
                            }
                        }, this);

                        var data = [header];
                        this.store.each(function(rec){
                            var row = [];
                            Ext4.Array.forEach(dataIndexes, function(di){
                                row.push(Ext4.isEmpty(rec.get(di)) ? null : rec.get(di));
                            }, this);

                            data.push(row);
                        }, this);

                        LABKEY.Utils.convertToExcel({
                            fileName : 'Sequence_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
                            sheets : [{
                                name: 'data',
                                data: data
                            }]
                        });
                    }
                },{
                    xtype: 'textarea',
                    itemId: 'excelContent',
                    style: 'margin-top: 10px;',
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
                handler: function(btn){
                    Ext4.Msg.confirm('Import Rows', 'This will remove all existing rows and replace them with the rows you pasted into the textarea.  Continue?', function(val){
                        if (val == 'yes'){
                            this.processExcel(btn);
                        }
                    }, this);
                }
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
            Ext4.Msg.alert('Error', errors.join('\n'));
            return;
        }

        this.store.removeAll();
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
                'r.rowid,r.name,r.platform,r.application,r.librarytype,r.inputMaterial,r.sampletype,r.subjectid,r.sampledate,r.sampleid,r.comments,r.barcode5,r.barcode3,r.fileid,r.fileid2,r.instrument_run_id,r.fileid2.name as fileName,r.fileid.name as fileName2 \n' +
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
                        librarytype: row.librarytype,
                        sampleid: row.sampleid,
                        subjectid: row.subjectid,
                        sampledate: row.sampledate,
                        comments: row.comments,
                        inputmaterial: row.inputmaterial,
                        sampletype: row.sampletype,
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
            this.updateWidth();
        }
        this.validateReadsets(this.store, this.store.getRange());
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
            this.updateWidth();
        }

        this.up('sequenceanalysis-sequenceimportpanel').populateSamples();
        this.validateReadsets(this.store, this.store.getRange());
    },

    updateWidth: function(){
        this.reconfigure();

        var width = 20;
        Ext4.Array.forEach(this.columns, function(col){
            if (col.width){
                if (!col.hidden)
                    width += col.width;
            }
            else {
                console.log('no width');
            }
        }, this);

        this.minWidth = width;
        this.up('form').minWidth = width;
        this.up('form').doLayout();
    }
});
