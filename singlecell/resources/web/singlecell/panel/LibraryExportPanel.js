Ext4.define('SingleCell.panel.LibraryExportPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.singlecell-libraryexportpanel',

    statics: {
        BARCODES5: ['N701', 'N702', 'N703', 'N704', 'N705', 'N706', 'N707', 'N708', 'N709', 'N710', 'N711', 'N712'],

        BARCODES3: ['S517', 'S502', 'S503', 'S504', 'S505', 'S506', 'S507', 'S508'],

        TENX_BARCODES: ['SI-GA-A1','SI-GA-A2','SI-GA-A3','SI-GA-A4','SI-GA-A5','SI-GA-A6','SI-GA-A7','SI-GA-A8','SI-GA-A9','SI-GA-A10','SI-GA-A11','SI-GA-A12','SI-GA-B1','SI-GA-B2','SI-GA-B3','SI-GA-B4','SI-GA-B5','SI-GA-B6','SI-GA-B7','SI-GA-B8','SI-GA-B9','SI-GA-B10','SI-GA-B11','SI-GA-B12','SI-GA-C1','SI-GA-C2','SI-GA-C3','SI-GA-C4','SI-GA-C5','SI-GA-C6','SI-GA-C7','SI-GA-C8','SI-GA-C9','SI-GA-C10','SI-GA-C11','SI-GA-C12','SI-GA-D1','SI-GA-D2','SI-GA-D3','SI-GA-D4','SI-GA-D5','SI-GA-D6','SI-GA-D7','SI-GA-D8','SI-GA-D9','SI-GA-D10','SI-GA-D11','SI-GA-D12','SI-GA-E1','SI-GA-E2','SI-GA-E3','SI-GA-E4','SI-GA-E5','SI-GA-E6','SI-GA-E7','SI-GA-E8','SI-GA-E9','SI-GA-E10','SI-GA-E11','SI-GA-E12','SI-GA-F1','SI-GA-F2','SI-GA-F3','SI-GA-F4','SI-GA-F5','SI-GA-F6','SI-GA-F7','SI-GA-F8','SI-GA-F9','SI-GA-F10','SI-GA-F11','SI-GA-F12','SI-GA-G1','SI-GA-G2','SI-GA-G3','SI-GA-G4','SI-GA-G5','SI-GA-G6','SI-GA-G7','SI-GA-G8','SI-GA-G9','SI-GA-G10','SI-GA-G11','SI-GA-G12','SI-GA-H1','SI-GA-H2','SI-GA-H3','SI-GA-H4','SI-GA-H5','SI-GA-H6','SI-GA-H7','SI-GA-H8','SI-GA-H9','SI-GA-H10','SI-GA-H11','SI-GA-H12']
    },

    initComponent: function () {
        Ext4.apply(this, {
            title: null,
            border: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'radiogroup',
                name: 'importType',
                columns: 1,
                items: [{
                    boxLabel: 'Novogene/Plate List',
                    inputValue: 'plateList',
                    name: 'importType',
                    checked: true
                },{
                    boxLabel: 'Other',
                    inputValue: 'other',
                    name: 'importType'
                }],
                listeners: {
                    scope: this,
                    afterrender: function(field) {
                        field.fireEvent('change', field, field.getValue());
                    },
                    change: function(field, val) {
                        val = val.importType;
                        var target = field.up('panel').down('#importArea');
                        target.removeAll();
                        if (val === 'other') {
                            target.add([{
                                xtype: 'ldk-simplecombo',
                                itemId: 'instrument',
                                fieldLabel: 'Instrument/Core',
                                forceSelection: true,
                                editable: false,
                                labelWidth: 160,
                                storeValues: ['NextSeq (MPSSR)', 'MiSeq (ONPRC)', 'Basic List (MedGenome)', '10x Sample Sheet', 'Novogene', 'Novogene-New']
                            },{
                                xtype: 'ldk-simplecombo',
                                itemId: 'application',
                                fieldLabel: 'Application/Type',
                                forceSelection: true,
                                editable: true,
                                labelWidth: 160,
                                allowBlank: true,
                                storeValues: ['Whole Transcriptome RNA-Seq', 'TCR Enriched', '10x GEX', '10x VDJ']
                            },{
                                xtype: 'labkey-combo',
                                forceSelection: true,
                                multiSelect: true,
                                displayField: 'plateId',
                                valueField: 'plateId',
                                itemId: 'sourcePlates',
                                fieldLabel: 'Source Plate Id',
                                store: {
                                    type: 'labkey-store',
                                    schemaName: 'singlecell',
                                    sql: 'SELECT distinct plateId as plateId from singlecell.cdna_libraries c WHERE c.allReadsetsHaveData = false',
                                    autoLoad: true
                                },
                                labelWidth: 160
                            },{
                                xtype: 'textfield',
                                itemId: 'adapter',
                                fieldLabel: 'Adapter',
                                labelWidth: 160,
                                value: 'CTGTCTCTTATACACATCT'
                            }]);
                        }
                        else {
                            target.add({
                                border: false,
                                defaults: {
                                    border: false
                                },
                                items: [{
                                    html: 'Add an ordered list of plates, using tab-delimited columns.  The first column(s) are plate ID and library type (GEX, VDJ, CITE, or HTO).  These can either be one column (i.e. G234-1, C234-1, H234-1, or T234-1), or as two columns (234-1 GEX or 234-1    HTO). An optional next column is the lane assignment (i.e. Novaseq1, HiSeq1, HiSeq2). Finally, an optional final column can be used to provide the alias for this pool. This is mostly used for CITE-Seq/HTOs, where multiple libraries are pre-pooled.  Note, a wildcard can be used to specify all plates beginning with that prefix. See these examples:<br>' +
                                            '<pre>' +
                                                '234-2\tGEX<br>' +
                                                '234-2\tVDJ<br>' +
                                                'G233-2<br>' +
                                                'T235-2<br>' +
                                                '234-2\tVDJ\tNovaSeq1<br>' +
                                                'G233-2\tNovaSeq1<br>' +
                                                '235-2\tHTO\tHiSeq1\tBNB-HTO-1<br>' +
                                                'H235-2\tHiSeq1\tBNB-HTO-1<br>' +
                                                '235-2\tHTO\tHiSeq2\tBNB-HTO-1<br>' +
                                                'H235-2\tHiSeq1\tBNB-HTO-1<br>' +
                                                'C235-2\tHiSeq1\tBNB-HTO-1<br>' +
                                                'C235-*\tHiSeq2\tBNB-HTO-2' +
                                            '</pre>',
                                    border: false
                                },{
                                    xtype: 'ldk-simplecombo',
                                    itemId: 'instrument',
                                    value: 'Novogene-New',
                                    fieldLabel: 'Format',
                                    forceSelection: true,
                                    editable: true,
                                    allowBlank: true,
                                    storeValues: ['Novogene', 'Novogene-New']
                                },{
                                    xtype: 'textarea',
                                    itemId: 'plateList',
                                    fieldLabel: 'Plate List',
                                    labelAlign: 'top',
                                    width: 270,
                                    height: 200,
                                    enableKeyEvents: true,
                                    listeners: {
                                        specialkey: function (field, e) {
                                            if (e.getKey() === e.TAB) {
                                                field.setValue(field.getValue() + '\t');
                                                e.preventDefault();
                                            }
                                        }
                                    },
                                },{
                                    xtype: 'ldk-numberfield',
                                    itemId: 'defaultVolume',
                                    fieldLabel: 'Default Volume (uL)',
                                    value: 10
                                }],
                                buttonAlign: 'left',
                                buttons: [{
                                    text: 'Add',
                                    scope: this,
                                    handler: function (btn) {
                                        var text = btn.up('panel').down('#plateList').getValue();
                                        if (!text) {
                                            Ext4.Msg.alert('Error', 'Must enter a list of plates');
                                            return;
                                        }

                                        text = LDK.Utils.CSVToArray(Ext4.String.trim(text), '\t');
                                        Ext4.Array.forEach(text, function(r, idx){
                                            var val = r[0];
                                            if (val.startsWith('G')){
                                                val = val.substr(1);
                                                val = val.replace('_', '-');
                                                r[0] = 'GEX';
                                                r.unshift(val);

                                            }
                                            else if (val.startsWith('T')){
                                                val = val.substr(1);
                                                val = val.replace('_', '-');
                                                r[0] = 'VDJ';
                                                r.unshift(val);
                                            }
                                            else if (val.startsWith('H')){
                                                val = val.substr(1);
                                                val = val.replace('_', '-');
                                                r[0] = 'HTO';
                                                r.unshift(val);
                                            }
                                            else if (val.startsWith('C')){
                                                val = val.substr(1);
                                                val = val.replace('_', '-');
                                                r[0] = 'CITE';
                                                r.unshift(val);
                                            }
                                        }, this);

                                        var hadError = false;
                                        var wildcards = {};
                                        Ext4.Array.forEach(text, function(r){
                                            if (r.length < 2){
                                                hadError = true;
                                            }

                                            //ensure all rows are of length 4
                                            if (r.length !== 4) {
                                                for (i=0;i<(4-r.length);i++) {
                                                    r.push('');
                                                }
                                            }

                                            Ext4.Array.forEach(r, function(val, idx){
                                                r[idx] = Ext4.String.trim(val);
                                            }, this);

                                            if (r[0].match('\\*$')) {
                                                var m = r[0].match('\\*$');
                                                var val = r[0].substr(0, m.index);
                                                wildcards[val] = r;
                                            }
                                        }, this);

                                        if (hadError) {
                                            Ext4.Msg.alert('Error', 'All rows must have at least 2 values');
                                            return;
                                        }

                                        if (!Ext4.Object.isEmpty(wildcards)) {
                                            LABKEY.Query.selectRows({
                                                method: 'POST',
                                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                schemaName: 'singlecell',
                                                queryName: 'cdna_libraries',
                                                columns: 'rowid,plateId,hashingReadsetId,citeseqReadsetId',
                                                filterArray: [LABKEY.Filter.create('plateId', Ext4.Object.getKeys(wildcards).join(';'), LABKEY.Filter.Types.CONTAINS_ONE_OF)],
                                                scope: this,
                                                failure: LDK.Utils.getErrorCallback(),
                                                success: function (results) {
                                                    if (results.rows.length) {
                                                        var prefixToPlate = {};
                                                        Ext4.Array.forEach(results.rows, function (row) {
                                                            Ext4.Array.forEach(Ext4.Object.getKeys(wildcards), function (prefix) {
                                                                if (row.plateId && row.plateId.includes(prefix)) {
                                                                    prefix = prefix + '*';
                                                                    prefixToPlate[prefix] = prefixToPlate[prefix] || {};
                                                                    prefixToPlate[prefix][row.plateId] = prefixToPlate[prefix][row.plateId] || {}
                                                                    if (row.hashingReadsetId) {
                                                                        prefixToPlate[prefix][row.plateId].HTO = true;
                                                                    }

                                                                    if (row.citeseqReadsetId) {
                                                                        prefixToPlate[prefix][row.plateId].CITE = true;
                                                                    }
                                                                }
                                                            }, this);
                                                        }, this);

                                                        var updatedText = [];
                                                        var prefixes = Ext4.Object.getKeys(prefixToPlate);
                                                        Ext4.Array.forEach(text, function (r, idx) {
                                                            var plateId = r[0];
                                                            if (prefixes.indexOf(plateId) === -1) {
                                                                updatedText.push(r);
                                                            }
                                                            else {
                                                                Ext4.Array.forEach(Ext4.Object.getKeys(prefixToPlate[plateId]), function(newPlateId){
                                                                    if (Ext4.Object.getKeys(prefixToPlate[plateId][newPlateId]).indexOf(r[1]) > -1) {
                                                                        var r2 = [].concat(r);
                                                                        r2[0] = newPlateId;
                                                                        updatedText.push(r2);
                                                                    }
                                                                }, this);
                                                            }
                                                        }, this);

                                                        text = updatedText;
                                                    }

                                                    this.onSubmit(btn, text);
                                                }
                                            });
                                        } else {
                                            this.onSubmit(btn, text);
                                        }
                                    }
                                }]
                            });
                        }
                    }
                }
            }, {
                bodyStyle: 'padding: 5px;',
                itemId: 'importArea',
                border: false,
                defaults: {
                    border: false
                }
            },{
                xtype: 'checkbox',
                boxLabel: 'Allow Duplicate Barcodes',
                checked: false,
                itemId: 'allowDuplicates'
            },{
                xtype: 'checkbox',
                boxLabel: 'Use Simple Sample Names',
                checked: true,
                itemId: 'simpleSampleNames'
            },{
                xtype: 'checkbox',
                boxLabel: 'Include Blanks',
                checked: true,
                itemId: 'includeBlanks'
            },{
                xtype: 'checkbox',
                boxLabel: 'Include Libraries With Data',
                checked: false,
                itemId: 'includeWithData',
                listeners: {
                    change: function (field, val) {
                        var target = field.up('singlecell-libraryexportpanel').down('#sourcePlates');
                        if (target) {
                            var sql = 'SELECT distinct plateId as plateId from singlecell.cdna_libraries ' + (val ? '' : 'c WHERE c.allReadsetsHaveData = false');
                            target.store.sql = sql;
                            target.store.removeAll();
                            target.store.load(function () {
                                if (target.getPicker()) {
                                    target.getPicker().refresh();
                                }
                            }, this);
                        }
                    }
                }
            },{
                xtype: 'textarea',
                itemId: 'outputArea',
                fieldLabel: 'Output',
                labelAlign: 'top',
                width: 1000,
                height: 400
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    this.onSubmit(btn);
                }
            }, {
                text: 'Download Data',
                itemId: 'downloadData',
                disabled: true,
                handler: function (btn) {
                    var instrument = btn.up('singlecell-libraryexportpanel').down('#instrument').getValue();
                    var plateId = btn.up('singlecell-libraryexportpanel').down('#sourcePlates').getValue();
                    var delim = 'TAB';
                    var extension = 'txt';
                    var split = '\t';
                    if (instrument !== 'NextSeq (MPSSR)') {
                        delim = 'COMMA';
                        extension = 'csv';
                        split = ',';
                    }

                    var val = btn.up('singlecell-libraryexportpanel').down('#outputArea').getValue();
                    var rows = LDK.Utils.CSVToArray(Ext4.String.trim(val), split);

                    LABKEY.Utils.convertToTable({
                        fileName: plateId + '.' + extension,
                        rows: rows,
                        delim: delim
                    });
                }
            },{
                text: 'Assign Readsets To Batch',
                itemId: 'readsetBatch',
                disabled: true,
                handler: function (btn) {
                    var panel = btn.up('singlecell-libraryexportpanel');
                    var readsetIds = btn.readsetIds;
                    if (!readsetIds) {
                        Ext4.Msg.alert('Error', 'No Readset IDs Found');
                        return;
                    }

                    Ext4.Msg.wait('Loading...');
                    LABKEY.Query.selectRows({
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_readsets',
                        columns: 'rowid,container',
                        filterArray: [LABKEY.Filter.create('rowid', readsetIds.join(';'), LABKEY.Filter.Types.IN)],
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: function (results) {
                            Ext4.Msg.hide();

                            if (!results || !results.rows || !results.rows.length) {
                                Ext4.Msg.hide();
                                Ext4.Msg.alert('Error', 'Readsets not found: ' + readsetIds.join(';'));
                                return;
                            }

                            var readsetRows = results.rows;

                            Ext4.create('Ext.window.Window', {
                                title: 'Assign Readsets To Batch',
                                width: 800,
                                bodyStyle: 'padding: 10px;',
                                readsetIds: readsetIds,
                                items: [{
                                    html: 'The following readsets will be assigned to an instrument run/batch: ' + readsetIds.join(', '),
                                    style: 'padding-bottom: 10px;',
                                    border: false
                                }, {
                                    xtype: 'textfield',
                                    fieldLabel: 'Run/Batch Name',
                                    labelWidth: 150,
                                    itemId: 'batchName'
                                }, {
                                    xtype: 'ldk-integerfield',
                                    fieldLabel: 'Target Workbook',
                                    labelWidth: 150,
                                    itemId: 'targetWorkbook'
                                }],
                                buttons: [{
                                    text: 'Submit',
                                    scope: this,
                                    handler: function (btn) {
                                        var win = btn.up('window');
                                        var batchId = win.down('#batchName').getValue();
                                        if (!batchId) {
                                            Ext4.Msg.alert('Error', 'Must enter a batch name');
                                            return;
                                        }

                                        var workbook = win.down('#targetWorkbook').getValue();
                                        if (workbook) {
                                            Ext4.Msg.wait('Loading...');
                                            LABKEY.Query.selectRows({
                                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                schemaName: 'core',
                                                queryName: 'workbooks',
                                                columns: 'EntityId',
                                                filterArray: [LABKEY.Filter.create('workbookId/workbookId', workbook, LABKEY.Filter.Types.EQUAL)],
                                                scope: this,
                                                failure: LDK.Utils.getErrorCallback(),
                                                success: function (results) {
                                                    if (!results || !results.rows || !results.rows.length) {
                                                        Ext4.Msg.hide();
                                                        Ext4.Msg.alert('Error', 'Workbook not found: ' + workbook);
                                                        return;
                                                    }

                                                    LDK.Assert.assertEquality('Expected single workbook to be returned', results.rows.length, 1);

                                                    win.close();
                                                    panel.createInstrumentRun(readsetRows, batchId, results.rows[0].EntityId);
                                                }
                                            });
                                        }
                                        else {
                                            panel.createInstrumentRun(readsetRows, batchId);
                                        }
                                    }
                                }, {
                                    text: 'Cancel',
                                    handler: function (btn) {
                                        btn.up('window').close();
                                    }
                                }]
                            }).show();
                        }
                    });
                }
            }]
        });

        this.callParent(arguments);

        Ext4.Msg.wait('Loading...');
        LABKEY.Query.selectRows({
            schemaName: 'sequenceanalysis',
            queryName: 'barcodes',
            sort: 'group_name,tag_name',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.barcodeMap = {};

                Ext4.Array.forEach(results.rows, function(r){
                    this.barcodeMap[r.group_name] = this.barcodeMap[r.group_name] || {};
                    this.barcodeMap[r.group_name][r.tag_name] = r.sequence;
                }, this);

                Ext4.Msg.hide();
            }
        });
    },

    createInstrumentRun: function (readsetRows, batchId, containerId) {
        containerId = containerId || Laboratory.Utils.getQueryContainerPath();
        LABKEY.Query.insertRows({
            containerPath: containerId,
            schemaName: 'sequenceanalysis',
            queryName: 'instrument_runs',
            scope: this,
            rows: [{
                name: batchId
            }],
            failure: LDK.Utils.getErrorCallback(),
            success: function (results) {
                var runId = results.rows[0].rowId;
                LDK.Assert.assertNotEmpty('Error creating instrument run', runId);

                Ext4.Array.forEach(readsetRows, function (rs) {
                    rs.instrument_run_id = runId
                }, this);

                LABKEY.Query.updateRows({
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'sequence_readsets',
                    rows: readsetRows,
                    failure: LDK.Utils.getErrorCallback(),
                    success: function (results) {
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Success', 'Readsets updated');
                    }
                });
            }
        });
    },

    onSubmit: function(btn, expectedPairs){
        var plateIds = [];

        if (expectedPairs) {
            var hadError = false;
            Ext4.Array.forEach(expectedPairs, function(p){
                plateIds.push(Ext4.String.trim(p[0]));
            }, this);
        }
        else {
            plateIds = btn.up('singlecell-libraryexportpanel').down('#sourcePlates').getValue();
        }

        if (!plateIds || !plateIds.length){
            Ext4.Msg.alert('Error', 'Must provide the plate Id(s)');
            return;
        }

        plateIds = Ext4.unique(plateIds);

        var instrument = btn.up('singlecell-libraryexportpanel').down('#instrument').getValue();
        var application = btn.up('singlecell-libraryexportpanel').down('#application') ? btn.up('singlecell-libraryexportpanel').down('#application').getValue() :  null;
        var defaultVolume = btn.up('singlecell-libraryexportpanel').down('#defaultVolume') ? btn.up('singlecell-libraryexportpanel').down('#defaultVolume').getValue() :  '';
        var adapter = btn.up('singlecell-libraryexportpanel').down('#adapter') ? btn.up('singlecell-libraryexportpanel').down('#adapter').getValue() : null;
        var includeWithData = btn.up('singlecell-libraryexportpanel').down('#includeWithData').getValue();
        var allowDuplicates = btn.up('singlecell-libraryexportpanel').down('#allowDuplicates').getValue();
        var simpleSampleNames = btn.up('singlecell-libraryexportpanel').down('#simpleSampleNames').getValue();
        var includeBlanks = btn.up('singlecell-libraryexportpanel').down('#includeBlanks').getValue();
        var doReverseComplement = btn.up('singlecell-libraryexportpanel').doReverseComplement;

        var isMatchingApplication = function(application, libraryType, readsetApplication, rowLevelApplication){
            if (!application && !rowLevelApplication){
                return true;
            }

            if (application === 'Whole Transcriptome RNA-Seq'){
                return readsetApplication === 'RNA-seq' || readsetApplication === 'RNA-seq, Single Cell';
            }
            else if (application === 'TCR Enriched'){
                return readsetApplication === 'RNA-seq + Enrichment';
            }
            else if (readsetApplication === 'RNA-seq, Single Cell'){
                application = rowLevelApplication || application;
                return (libraryType.match(/^10x [35]\' GEX/) && application === '10x GEX') || (libraryType.match(/^10x 5' VDJ/) && application === '10x VDJ');
            }
            else if (readsetApplication === 'Cell Hashing'){
                application = rowLevelApplication || application;
                return (application === '10x HTO');
            }
            else if (readsetApplication === 'CITE-Seq'){
                application = rowLevelApplication || application;
                return (application === '10x CITE-Seq');
            }
        };

        var getSampleName = function(simpleSampleNames, readsetId, readsetName, suffix){
            return (simpleSampleNames ? 's_' + readsetId : readsetId + '_' + readsetName) + (suffix ? '_' + suffix : '');
        };

        Ext4.Msg.wait('Loading cDNA data');
        LABKEY.Query.selectRows({
            method: 'POST',
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'singlecell',
            queryName: 'cdna_libraries',
            sort: 'plateId,well/addressByColumn',
            columns: 'rowid,plateid' +
                ',readsetId,readsetId/name,readsetId/application,readsetId/librarytype,readsetId/barcode5,readsetId/barcode5/sequence,readsetId/barcode3,readsetId/barcode3/sequence,readsetId/totalFiles,readsetId/concentration' +
                ',tcrReadsetId,tcrReadsetId/name,tcrReadsetId/application,tcrReadsetId/librarytype,tcrReadsetId/barcode5,tcrReadsetId/barcode5/sequence,tcrReadsetId/barcode3,tcrReadsetId/barcode3/sequence,tcrReadsetId/totalFiles,tcrReadsetId/concentration' +
                ',hashingReadsetId,hashingReadsetId/name,hashingReadsetId/application,hashingReadsetId/librarytype,hashingReadsetId/barcode5,hashingReadsetId/barcode5/sequence,hashingReadsetId/barcode3,hashingReadsetId/barcode3/sequence,hashingReadsetId/totalFiles,hashingReadsetId/concentration' +
                ',citeseqReadsetId,citeseqReadsetId/name,citeseqReadsetId/application,citeseqReadsetId/librarytype,citeseqReadsetId/barcode5,citeseqReadsetId/barcode5/sequence,citeseqReadsetId/barcode3,citeseqReadsetId/barcode3/sequence,citeseqReadsetId/totalFiles,citeseqReadsetId/concentration',
            scope: this,
            filterArray: [LABKEY.Filter.create('plateId', plateIds.join(';'), LABKEY.Filter.Types.IN)],
            failure: LDK.Utils.getErrorCallback(),
            success: function (results) {
                Ext4.Msg.hide();

                if (!results || !results.rows || !results.rows.length) {
                    Ext4.Msg.alert('Error', 'No libraries found for the selected plates');
                    return;
                }

                var sortedRows = results.rows;
                if (expectedPairs) {
                    sortedRows = [];
                    var missingRows = [];
                    Ext4.Array.forEach(expectedPairs, function(p){
                        var found = false;
                        Ext4.Array.forEach(results.rows, function(row){
                            if (row.plateId === p[0]) {
                                if (p[1] === 'GEX') {
                                    if (includeWithData || row['readsetId/totalFiles'] === 0) {
                                        if (row['readsetId'] && row['readsetId/librarytype'] && row['readsetId/librarytype'].match('GEX')) {
                                            sortedRows.push(Ext4.apply({targetApplication: '10x GEX', laneAssignment: (p.length > 2 ? p[2] : null), plateAlias: (p.length > 3 ? p[3] : null)}, row));
                                            found = true;
                                            return false;
                                        }
                                    }
                                }
                                else if (p[1] === 'HTO') {
                                    if (includeWithData || row['hashingReadsetId/totalFiles'] === 0) {
                                        if (row['hashingReadsetId'] && row['hashingReadsetId/application'] && row['hashingReadsetId/application'].match('Cell Hashing')) {
                                            sortedRows.push(Ext4.apply({targetApplication: '10x HTO', laneAssignment: (p.length > 2 ? p[2] : null), plateAlias: (p.length > 3 ? p[3] : null)}, row));
                                            found = true;
                                            return false;
                                        }
                                    }
                                }
                                else if (p[1] === 'CITE') {
                                    if (includeWithData || row['citeseqReadsetId/totalFiles'] === 0) {
                                        if (row['citeseqReadsetId'] && row['citeseqReadsetId/application'] && row['citeseqReadsetId/application'].match('CITE-Seq')) {
                                            sortedRows.push(Ext4.apply({targetApplication: '10x CITE-Seq', laneAssignment: (p.length > 2 ? p[2] : null), plateAlias: (p.length > 3 ? p[3] : null)}, row));
                                            found = true;
                                            return false;
                                        }
                                    }
                                }
                                else if (p[1] === 'VDJ') {
                                    if (includeWithData || row['tcrReadsetId/totalFiles'] === 0) {
                                        if (row['tcrReadsetId'] && row['tcrReadsetId/librarytype'].match('VDJ')) {
                                            sortedRows.push(Ext4.apply({targetApplication: '10x VDJ', laneAssignment: (p.length > 2 ? p[2] : null), plateAlias: (p.length > 3 ? p[3] : null)}, row));
                                            found = true;
                                            return false;
                                        }
                                    }
                                }
                            }
                        }, this);

                        if (!found) {
                            missingRows.push(p[0] + '/' + p[1]);
                        }
                    }, this);

                    if (missingRows.length){
                        Ext4.Msg.alert('Error', 'The following plates were not found:<br>' + missingRows.join('<br>'));
                        return;
                    }
                }

                var barcodes = 'Illumina';
                var readsetIds = {};
                var barcodeCombosUsed = [];
                if (instrument === 'NextSeq (MPSSR)' || instrument === 'Basic List (MedGenome)') {
                    var rc5 = (instrument === 'NextSeq (MPSSR)');
                    var rc3 = (instrument === 'NextSeq (MPSSR)');

                    var rows = [['Name', 'Adapter', 'I7_Index_ID', 'I7_Seq', 'I5_Index_ID', 'I5_Seq'].join('\t')];
                    Ext4.Array.forEach(sortedRows, function (r) {
                        //only include readsets without existing data
                        var processSample = function(rows, r, fieldName) {
                            if (!readsetIds[r[fieldName]] && r[fieldName] && (includeWithData || r[fieldName + '/totalFiles'] === 0) && isMatchingApplication(application, r[fieldName + '/librarytype'], r[fieldName + '/application'], r.targetApplication)) {
                                //allow for cell hashing / shared readsets
                                readsetIds[r[fieldName]] = true;

                                //reverse complement both barcodes:
                                var barcode5 = rc5 ? doReverseComplement(r[fieldName + '/barcode5/sequence']) : r[fieldName + '/barcode5/sequence'];
                                var barcode3 = rc3 ? doReverseComplement(r[fieldName + '/barcode3/sequence']) : r[fieldName + '/barcode3/sequence'];
                                barcodeCombosUsed.push(r[fieldName + '/barcode5'] + '/' + r[fieldName + '/barcode3']);
                                rows.push([getSampleName(simpleSampleNames, r[fieldName], r[fieldName + '/name']), adapter, r[fieldName + '/barcode5'], barcode5, r[fieldName + '/barcode3'], barcode3].join('\t'));
                            }
                        };

                        processSample(rows, r, 'readsetId');
                        processSample(rows, r, 'tcrReadsetId');
                        processSample(rows, r, 'hashingReadsetId');
                        processSample(rows, r, 'citeseqReadsetId');
                    }, this);

                    //add missing barcodes:
                    if (includeBlanks) {
                        var blankIdx = 0;
                        Ext4.Array.forEach(SingleCell.panel.LibraryExportPanel.BARCODES5, function (barcode5) {
                            Ext4.Array.forEach(SingleCell.panel.LibraryExportPanel.BARCODES3, function (barcode3) {
                                var combo = barcode5 + '/' + barcode3;
                                if (barcodeCombosUsed.indexOf(combo) === -1) {
                                    blankIdx++;
                                    var barcode5Seq = rc5 ? doReverseComplement(this.barcodeMap[barcodes][barcode5]) : this.barcodeMap[barcodes][barcode5];
                                    var barcode3Seq = rc3 ? doReverseComplement(this.barcodeMap[barcodes][barcode3]) : this.barcodeMap[barcodes][barcode3];

                                    var name = simpleSampleNames ? 's_Blank' + blankIdx : plateIds.join(';').replace(/\//g, '-') + '_Blank' + blankIdx;
                                    rows.push([name, adapter, barcode5, barcode5Seq, barcode3, barcode3Seq].join('\t'));
                                }
                            }, this);
                        }, this);
                    }
                }
                else if (instrument === 'MiSeq (ONPRC)') {
                    var rows = [];
                    rows.push('[Header]');
                    rows.push('IEMFileVersion,4');
                    rows.push('Investigator Name,Bimber');
                    rows.push('Experiment Name,' + plateIds.join(';'));
                    rows.push('Date,11/16/2017');
                    rows.push('Workflow,GenerateFASTQ');
                    rows.push('Application,FASTQ Only');
                    rows.push('Assay,Nextera XT');
                    rows.push('Description,');
                    rows.push('Chemistry,Amplicon');
                    rows.push('');
                    rows.push('[Reads]');
                    rows.push('251');
                    rows.push('251');
                    rows.push('');
                    rows.push('[Settings]');
                    rows.push('ReverseComplement,0');
                    rows.push('Adapter,' + adapter);
                    rows.push('');
                    rows.push('[Data]');
                    rows.push('Sample_ID,Sample_Name,Sample_Plate,Sample_Well,I7_Index_ID,index,I5_Index_ID,index2,Sample_Project,Description');

                    Ext4.Array.forEach(sortedRows, function (r) {
                        //only include readsets without existing data
                        if (!readsetIds[r.readsetId] && r.readsetId && (includeWithData || r['readsetId/totalFiles'] === 0) && isMatchingApplication(application, r['readsetId/librarytype'], r['readsetId/application'], r.targetApplication)) {
                            //allow for cell hashing / shared readsets
                            readsetIds[r.readsetId] = true;

                            //reverse complement both barcodes:
                            var barcode5 = doReverseComplement(r['readsetId/barcode5/sequence']);
                            var barcode3 = r['readsetId/barcode3/sequence'];
                            var cleanedName = r.readsetId + '_' + r['readsetId/name'].replace(/ /g, '_');
                            cleanedName = cleanedName.replace(/\//g, '-');

                            barcodeCombosUsed.push(r['readsetId/barcode5'] + '/' + r['readsetId/barcode3']);
                            rows.push([r.readsetId, cleanedName, '', '', r['readsetId/barcode5'], barcode5, r['readsetId/barcode3'], barcode3].join(','));
                        }

                        if (!readsetIds[r.tcrReadsetId] && r.tcrReadsetId && (includeWithData || r['tcrReadsetId/totalFiles'] == 0) && isMatchingApplication(application, r['tcrReadsetId/librarytype'], r['tcrReadsetId/application'], r.targetApplication)) {
                            //allow for cell hashing / shared readsets
                            readsetIds[r.tcrReadsetId] = true;

                            var barcode5 = doReverseComplement(r['tcrReadsetId/barcode5/sequence']);
                            var barcode3 = r['tcrReadsetId/barcode3/sequence'];
                            var cleanedName = r.tcrReadsetId + '_' + r['tcrReadsetId/name'].replace(/ /g, '_');
                            cleanedName = cleanedName.replace(/\//g, '-');

                            barcodeCombosUsed.push(r['tcrReadsetId/barcode5'] + '/' + r['tcrReadsetId/barcode3']);
                            rows.push([r.tcrReadsetId, cleanedName, '', '', r['tcrReadsetId/barcode5'], barcode5, r['tcrReadsetId/barcode3'], barcode3].join(','))
                        }
                    }, this);

                    //add missing barcodes:
                    if (includeBlanks) {
                        var blankIdx = 0;
                        Ext4.Array.forEach(SingleCell.panel.LibraryExportPanel.BARCODES5, function (barcode5) {
                            Ext4.Array.forEach(SingleCell.panel.LibraryExportPanel.BARCODES3, function (barcode3) {
                                var combo = barcode5 + '/' + barcode3;
                                if (barcodeCombosUsed.indexOf(combo) === -1) {
                                    blankIdx++;
                                    var barcode5Seq = doReverseComplement(this.barcodeMap[barcodes][barcode5]);
                                    var barcode3Seq = this.barcodeMap[barcodes][barcode3];
                                    rows.push([plateIds.join(';').replace(/\//g, '-') + '_Blank' + blankIdx, null, null, null, barcode5, barcode5Seq, barcode3, barcode3Seq].join(','));
                                }
                            }, this);
                        }, this);
                    }
                }
                else if (instrument === '10x Sample Sheet' || instrument === 'Novogene' || instrument === 'Novogene-New') {
                    //we make the default assumption that we're using 10x primers, which are listed in the sample-sheet orientation
                    var doRC = false;
                    var rows = [];
                    var barcodes = '10x Chromium Single Cell v2';

                    if (instrument === '10x Sample Sheet') {
                        rows.push('Sample_ID,Sample_Name,index,Sample_Project');
                    }

                    //only include readsets without existing data
                    var processType = function(readsetIds, rows, r, fieldName, suffix, size, phiX, samplePrefix, comment, doRC) {
                        if (!readsetIds[r[fieldName]] && r[fieldName] && (includeWithData || r[fieldName + '/totalFiles'] === 0) && isMatchingApplication(application, r[fieldName + '/librarytype'], r[fieldName + '/application'], r.targetApplication)) {
                            //allow for shared readsets across cDNAs (hashing, etc.)
                            readsetIds[r[fieldName]] = true;

                            var cleanedName = r[fieldName] + '_' + r[fieldName + '/name'].replace(/ /g, '_');
                            cleanedName = cleanedName.replace(/\//g, '-');
                            var sampleName = getSampleName(simpleSampleNames, r[fieldName], r[fieldName + '/name']) + (suffix && instrument.startsWith('Novogene') ? '' : '-' + suffix);

                            var barcode5s = r[fieldName + '/barcode5/sequence'] ? r[fieldName + '/barcode5/sequence'].split(',') : [];
                            if (!barcode5s) {
                                LDK.Utils.logError('Sample missing barcode: ' + sampleName);
                            }

                            barcodeCombosUsed.push([r[fieldName + '/barcode5'], '', r.laneAssignment || ''].join('/'));

                            //The new format requires one/line
                            if (instrument === 'Novogene-New') {
                                if (doRC && barcode5s.length > 1) {
                                    var msg =  'Did not expect Novogene-New, reverse complement and multiple barcodes';
                                    LDK.Utils.logError(msg);
                                    Ext4.Msg.alert('Error', msg);
                                    return;
                                }
                                barcode5s = [barcode5s.join(',')];
                            }

                            Ext4.Array.forEach(barcode5s, function (bc, idx) {
                                bc = doRC ? doReverseComplement(bc) : bc;

                                var data = [sampleName, (instrument.startsWith('Novogene') ? '' : cleanedName), bc, ''];
                                if (instrument === 'Novogene') {
                                    data = [sampleName];
                                    if (r.plateAlias) {
                                        data.unshift(r.plateAlias);
                                    }
                                    else {
                                        data.unshift(samplePrefix + r.plateId.replace(/-/g, '_'));
                                    }

                                    data.push('Macaca mulatta');
                                    data.push(bc);
                                    data.push('');
                                    data.push(r[fieldName + '/concentration'] || '');
                                    data.push(defaultVolume);
                                    data.push('');
                                    data.push(size);
                                    data.push(phiX);  //PhiX
                                    data.push(r.laneAssignment || '');
                                    data.push(comment || 'Please QC individually and pool in equal amounts per lane');
                                }
                                else if (instrument === 'Novogene-New') {
                                    data = ['Premade-10X transcriptome library'];
                                    data.push(r.plateAlias ? r.plateAlias : samplePrefix + r.plateId.replace(/-/g, '_'));
                                    data.push(sampleName);
                                    data.push('Partial lane sequencing-With Demultiplexing');  //TODO: HiSeq?
                                    data.push(bc);
                                    data.push(''); //P5
                                    data.push(size);
                                    data.push('Others'); //Library Status
                                    data.push('ddH2O');
                                    data.push('Partial Lane sequencing-lib QC');
                                    data.push(200); //Total data
                                    data.push('M raw reads');
                                    data.push(r[fieldName + '/concentration'] || '');
                                    data.push(defaultVolume);
                                    data.push(comment || 'Please QC individually and pool in equal amounts per lane');

                                    //data.push(phiX);  //PhiX
                                    data.push(r.laneAssignment || '');

                                }
                                rows.push(data.join(delim));
                            }, this);
                        }
                    };

                    var delim = instrument.startsWith('Novogene') ? '\t' : ',';
                    Ext4.Array.forEach(sortedRows, function (r) {
                        processType(readsetIds, rows, r, 'readsetId', 'GEX', 500, 0.01, 'G', null, false);
                        processType(readsetIds, rows, r, 'tcrReadsetId', 'TCR', 700, 0.01, 'T', null, false);
                        processType(readsetIds, rows, r, 'hashingReadsetId', 'HTO', 182, 0.05, 'H', 'Cell hashing, 190bp amplicon.  Please QC individually and pool in equal amounts per lane', true);
                        processType(readsetIds, rows, r, 'citeseqReadsetId', 'CITE', 182, 0.05, 'C', 'CITE-Seq, 190bp amplicon.  Please QC individually and pool in equal amounts per lane', false);
                    }, this);

                    //add missing barcodes:
                    if (includeBlanks && !instrument.startsWith('Novogene')) {
                        var blankIdx = 0;
                        Ext4.Array.forEach(SingleCell.panel.LibraryExportPanel.TENX_BARCODES, function (barcode5) {
                            if (barcodeCombosUsed.indexOf(barcode5) === -1) {
                                blankIdx++;
                                var barcode5Seq = this.barcodeMap[barcodes][barcode5].split(',');
                                Ext4.Array.forEach(barcode5Seq, function (seq, idx) {
                                    seq = doRC ? doReverseComplement(seq) : seq;
                                    rows.push([barcode5 + '_' + (idx + 1), plateIds.join(';').replace(/\//g, '-') + '_Blank' + blankIdx, seq, ''].join(delim));
                                }, this);
                            }
                        }, this);
                    }
                }

                //check for unique barcodes
                var sorted = barcodeCombosUsed.slice().sort();
                var duplicates = [];
                for (var i = 0; i < sorted.length - 1; i++) {
                    if (sorted[i + 1] === sorted[i]) {
                        duplicates.push(sorted[i]);
                    }
                }

                duplicates = Ext4.unique(duplicates);
                if (!allowDuplicates && duplicates.length){
                    Ext4.Msg.alert('Error', 'Duplicate barcodes: ' + duplicates.join(', '));
                    btn.up('singlecell-libraryexportpanel').down('#outputArea').setValue(null);
                    btn.up('singlecell-libraryexportpanel').down('#downloadData').setDisabled(true);
                }
                else {
                    btn.up('singlecell-libraryexportpanel').down('#outputArea').setValue(rows.join('\n'));
                    btn.up('singlecell-libraryexportpanel').down('#downloadData').setDisabled(false);

                    var rsBtn = btn.up('singlecell-libraryexportpanel').down('#readsetBatch');
                    rsBtn.readsetIds = Ext4.Object.getKeys(readsetIds);
                    rsBtn.setDisabled(false);
                }
            }
        });
    },

    doReverseComplement: function(seq){
        if (!seq){
            return seq;
        }
        var match={'a': 'T', 'A': 'T', 't': 'A', 'T': 'A', 'g': 'C', 'G': 'C', 'c': 'G', 'C': 'G'};
        var o = '';
        for (var i = seq.length - 1; i >= 0; i--) {
            if (match[seq[i]] === undefined) break;
            o += match[seq[i]];
        }

        return o;
    }
});
