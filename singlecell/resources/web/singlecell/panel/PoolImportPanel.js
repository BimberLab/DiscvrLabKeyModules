Ext4.define('SingleCell.panel.PoolImportPanel', {
    extend: 'Ext.panel.Panel',

    COLUMNS: [{
        name: 'workbook',
        labels: ['Experiment/Workbook', 'Expt', 'Expt #', 'Experiment', 'Exp#', 'Exp #', 'Workbook', 'Workbook #'],
        allowRowSpan: true,
        alwaysShow: true,
        transform: 'expt',
        allowBlank: false
    },{
        name: 'plateId',
        labels: ['Pool/Tube', 'Pool', 'Pool Num', 'Pool #', 'Tube #', 'Tube#'],
        allowRowSpan: true,
        alwaysShow: true,
        transform: 'pool',
        allowBlank: false
    },{
        name: 'sampleId',
        labels: ['Sample', 'Sample Id', 'Stim Id'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true
    },{
        name: 'subjectId',
        labels: ['Subject Id', 'Animal', 'AnimalId', 'Animal Id', 'SubjectId'],
        allowRowSpan: true,
        allowBlank: false,
        transform: 'subject'
    },{
        name: 'celltype',
        labels: ['Cell Type', 'CellType', 'Input Cell Type'],
        allowRowSpan: true,
        allowBlank: false,
        transform: 'celltype'
    },{
        name: 'sampleDate',
        labels: ['Sample Date', 'Date'],
        alwaysShow: true,
        allowRowSpan: true,
        transform: 'sampleDate',
        allowBlank: false
    },{
        name: 'tissue',
        labels: ['Tissue', 'Tissue Sample'],
        alwaysShow: false,
        allowRowSpan: true,
        allowBlank: true
    },{
        name: 'stim',
        labels: ['Stim', 'Peptide Only Conditions'],
        allowRowSpan: false,
        allowBlank: false,
        transform: 'stim'
    },{
        name: 'assaytype',
        labels: ['Assay Type', 'Assay Type', 'Assay', 'treatment'],
        allowRowSpan: false,
        alwaysShow: true,
        allowBlank: false,
        transform: 'assaytype'
    },{
        name: 'disposition',
        labels: ['Sample Disposition'],
        allowRowSpan: false,
        allowBlank: true
    },{
        name: 'tube_num',
        labels: ['Tube #', 'Stim #'],
        allowRowSpan: false,
        alwaysShow: true
    },{
        name: 'population',
        labels: ['Population', 'Target Population', 'Target Pop'],
        allowRowSpan: true,
        allowBlank: false,
        transform: 'population'
    },{
        name: 'tetramer',
        labels: ['Tetramer'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'tetramer'
    },{
        name: 'sortId',
        labels: ['Sort Id'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true
    },{
        name: 'hto',
        labels: ['HTO', 'HTO Oligo', 'HTO-Oligo', 'HTO barcode', 'Barcode'],
        allowRowSpan: false,
        transform: 'hto'
    },{
        name: 'cells',
        labels: ['Cells', 'Cell #', 'Sort', 'Sort Cell Count'],
        allowRowSpan: false,
        allowBlank: false,
        transform: 'cells'
    },{
        name: 'hto_library_index',
        labels: ['HTO Library Index', 'HTO Index', 'MultiSeq Index', 'MultiSeq Library Index'],
        allowRowSpan: true,
        transform: 'htoIndex'
    },{
        name: 'hto_library_conc',
        labels: ['HTO Library Conc', 'HTO Library Conc (ng/uL)', 'HTO (qubit) ng/uL', 'HTO (quibit) ng/uL', 'MultiSeq Library Conc', 'MultiSeq Library (qubit) ng/uL', 'MultiSeq Library Conc (qubit) ng/uL'],
        allowRowSpan: true
    },{
        name: 'citeseqpanel',
        labels: ['Cite-Seq Panel', 'Cite-Seq Panel Name', 'CiteSeq Panel', 'citeseqpanel'],
        allowRowSpan: true,
        transform: 'citeSeqPanel'
    },{
        name: 'citeseq_library_index',
        labels: ['Cite-Seq Library Index', 'Cite-Seq Index', 'CiteSeq Library Index', 'CiteSeq Index', 'Cite-Seq Library Index', 'Cite-Seq Index', 'CiteSeq Library (qubit) ng/uL'],
        allowRowSpan: true,
        transform: 'citeSeqTenXBarcode'
    },{
        name: 'citeseq_library_conc',
        labels: ['Cite-Seq Library Conc', 'Cite-Seq Library Conc (ng/uL)', 'Cite-Seq (qubit) ng/uL', 'Cite-Seq (quibit) ng/uL'],
        allowRowSpan: true
    },{
        name: 'gex_library_index',
        labels: ['5\' GEX Library Index', '5\' GEX Index', 'GEX Index', 'GEX Library Index', '5-GEX Index', '5\'GEX Library Index'],
        allowRowSpan: true,
        transform: 'tenXBarcode'
    },{
        name: 'gex_library_conc',
        labels: ['5\' GEX Library Conc', 'GEX Library Conc', 'GEX Library Conc (ng/uL)', '5\' GEX Conc', 'GEX Conc', 'GEX Conc (ng/uL)', '5\' GEX (qubit) ng/uL', '5\' GEX Library (qubit) ng/uL'],
        allowRowSpan: true
    },{
        name: 'gex_library_fragment',
        labels: ['5\' GEX Library Fragment Size', 'GEX Library Fragment Size', '5\' GEX Fragment Size', 'GEX Fragment Size', 'GEX Library Fragment Size (bp)'],
        allowRowSpan: true
    },{
        name: 'tcr_library_index',
        labels: ['TCR Library Index', 'TCR Index', 'TCR Libray Index'],
        allowRowSpan: true,
        transform: 'tenXBarcode'
    },{
        name: 'tcr_library_conc',
        labels: ['TCR Library Conc', 'TCR Library Conc (ng/uL)', 'TCR (qubit) ng/uL', 'TCR library (qubit) ng/uL'],
        allowRowSpan: true
    },{
        name: 'tcr_library_fragment',
        labels: ['TCR Library Fragment Size', 'TCR Library Fragment Size (bp)'],
        allowRowSpan: true
    },{
        name: 'kitType',
        labels: ['Kit Type', 'V1.1/V2/HT', 'V1.1/HT', 'HT/V1.1/V2'],
        transform: 'kitType'
    }],

    IGNORED_COLUMNS: [],

    transforms: {
        stim: function(val, panel) {
            if (val && (val === '--' || val === '-' || val.toLowerCase() === 'no stim')) {
                val = 'NoStim';
            }

            return val;
        },

        assaytype: function(val, panel) {
            var requireAssayType = panel.down('#requireAssayType').getValue();
            if (val && (val === '--' || val === '-')) {
                val = null;
            }

            if (!requireAssayType && !val) {
                return 'N/A';
            }

            return val;
        },

        subject: function(val, panel) {
            if (val) {
                val = val.replace(/ PBMC/, '');
            }

            return val;
        },

        htoIndex: function(val, panel) {
            if (Ext4.isNumeric(val)) {
                //indexes are named D7XX.  accept rows named '1', '12', etc.
                var type = panel.down('#hashingType').getValue();
                if (type === 'CD298') {
                    val = parseInt(val);
                    if (val < 100) {
                        val = val + 700;
                    }
                    return 'D' + val;
                }
                else if (type === 'MultiSeq' && !panel.down('#useDualMsIndex').getValue()) {
                    val = parseInt(val);

                    return 'MultiSeq-Idx-RP' + val;
                }
                else {
                    LDK.Utils.logError('Unknown or missing hashingType: ' + type);
                }
            }
            else if (val) {
                var type = panel.down('#hashingType').getValue();
                if (type === 'MultiSeq') {
                    val = String(val);
                    if (val.match(/^MS-[0-9]+$/i)) {
                        val = val.replace(/^MS(-)*/ig, 'MultiSeq-Idx-RP');
                    }

                    val = val.replace(/^MS[- ]Idx/ig, 'MultiSeq-Idx');
                    val = val.replace(/^MultiSeq[- ]Idx[- ]RP/ig, 'MultiSeq-Idx-RP');

                    if (val.length <= 3 && panel.down('#useDualMsIndex').getValue()) {
                        val = 'MS-TN-' + val;
                    }
                    else {
                        LDK.Utils.logError('Unexpected value with single-end hashing: ' + val);
                    }

                    return val;
                }
                else if (val && type === 'BioLegend') {
                    val = String(val);
                    if (val.indexOf('SI-TN') === -1) {
                        val = 'SI-TN-' + val;
                    }
                }
            }

            return val;
        },

        citeSeqPanel: function(val, panel) {
            if (val && (val.toLowerCase() === 'no' || val.toLowerCase() === 'n' || val.toLowerCase() === 'na' || val.toLowerCase() === 'n/a')) {
                return null;
            }
            else if (val && (val.toLowerCase() === 'yes' || val.toLowerCase() === 'y')) {
                var panel = panel.down('#defaultCiteSeqPanel').getValue();
                return panel || val;
            }

            return val;
        },

        citeSeqTenXBarcode: function(val, panel, row){
            if (!val){
                return null;
            }

            var isDual = panel.down('#useDualIndex').getValue()
            if (Ext4.isDefined(row.dualIndex10x)) {
                isDual = row.dualIndex10x;
            }

            var barcodeSeries = isDual ? 'SI-TN' : 'SI-NA';
            val = val.toUpperCase();
            var re = new RegExp('^' + barcodeSeries + '-', 'i');
            if (!val.match(re)) {
                if (val.length > 3) {
                    //errorMsgs.push('Every row must have name, application and proper barcodes');
                }
                else {
                    val = barcodeSeries + '-' + val;
                }
            }

            return val;
        },

        tenXBarcode: function(val, panel, row){
            if (!val){
                return;
            }

            var isDual = panel.down('#useDualIndex').getValue()
            if (Ext4.isDefined(row.dualIndex10x)) {
                isDual = row.dualIndex10x;
            }

            var barcodeSeries = isDual ? 'SI-TT' : 'SI-GA';
            val = val.toUpperCase();
            var re = new RegExp('^' + barcodeSeries + '-', 'i');
            if (!val.match(re)) {
                if (val.length > 3) {
                    //errorMsgs.push('Every row must have name, application and proper barcodes');
                }
                else {
                    val = barcodeSeries + '-' + val;
                }
            }

            return val;
        },

        hto: function(val, panel){
            if (val === 'N/A' || val === 'NA') {
                return null;
            }

            if (Ext4.isNumeric(val)){
                var type = panel.down('#hashingType').getValue();
                if (type === 'CD298') {
                    return 'HTO-' + val;
                }
                else if (type === 'MultiSeq') {
                    return 'MS-' + val;
                }
                else if (type === 'BioLegend') {
                    return 'BL-' + val;
                }
            }
            else if (val) {
                //Normalize hyphen use
                val = String(val);
                val = val.replace(/^MS(-)*/, 'MS-');
                val = val.replace(/^HTO(-)*/, 'HTO-');
                val = val.replace(/^BL(-)*/, 'BL-');
            }

            return val;
        },

        expt: function(val, panel){
            return val || panel.EXPERIMENT;
        },

        celltype: function(val, panel){
            return val || panel.CELL_TYPE;
        },

        cells: function(val, panel){
            return val ? Ext4.data.Types.INTEGER.convert(val) : val;
        },

        pool: function(val, panel, row){
            var workbook = row.workbook || panel.EXPERIMENT;
            //Note: convert values like 2B -> 2
            if (val && !Ext4.isNumeric(val)) {
                val = val.replace(/[^0-9]+/, '');
            }

            if (workbook && Ext4.isNumeric(val)){
                return workbook + '-' + val;
            }

            return val;
        },

        tetramer: function(val, panel, row){
            if (val) {
                if (['Tet+', 'Tetramer+', 'Tetramer'].indexOf(val) > -1) {
                    row.population = null;
                }

                row.population = row.population || val;
            }

            return val;
        },

        population: function(val, panel, row){
            if (val && ['Tet+', 'Tetramer+', 'Tetramer'].indexOf(val) > -1) {
                val = row.tetramer;
            }

            return val;
        },

        sampleDate: function(val, panel){
            return val || panel.SAMPLE_DATE;
        }
    },

    COLUMN_MAP: null,

    initComponent: function () {
        this.COLUMN_MAP = {};
        Ext4.Array.forEach(this.COLUMNS, function(col){
            this.COLUMN_MAP[col.name.toLowerCase()] = col;
            Ext4.Array.forEach(col.labels, function(alias){
                this.COLUMN_MAP[alias.toLowerCase()] = col;
            }, this);
        }, this);

        Ext4.apply(this, {
            title: null,
            border: false,
            defaults: {
                border: false
            },
            items: this.getPanelItems()
        });

        this.callParent(arguments);

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('singlecell', 'getTenXImportDefaults'),
            scope: this,
            success: function(response){
                LDK.Utils.decodeHttpResponseJson(response);
                if (response.responseJSON){
                    this.configDefaults = response.responseJSON;
                    for (var name in this.configDefaults){
                        var item = this.down('#' + name);
                        if (item){
                            item.setValue(this.configDefaults[name]);
                        }
                    }

                    Ext4.Msg.hide();
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    getPanelItems: function(){
        return [{
            layout: {
                type: 'hbox'
            },
            items: [{
                xtype: 'ldk-integerfield',
                style: 'margin-right: 5px;',
                fieldLabel: 'Current Folder/Workbook',
                labelWidth: 200,
                minValue: 1,
                value: LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.name : null,
                emptyText: LABKEY.Security.currentContainer.type === 'workbook' ? null : 'Showing All',
                listeners: {
                    afterRender: function(field){
                        new Ext4.util.KeyNav(field.getEl(), {
                            enter : function(e){
                                var btn = field.up('panel').down('#goButton');
                                btn.handler(btn);
                            },
                            scope : this
                        });
                    }
                }
            },{
                xtype: 'button',
                itemId: 'goButton',
                scope: this,
                text: 'Go',
                handler: function(btn){
                    var wb = btn.up('panel').down('ldk-integerfield').getValue();
                    if (!wb){
                        wb = '';
                    }

                    var container = LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.parentPath + '/' + wb : LABKEY.Security.currentContainer.path + '/' + wb;
                    window.location = LABKEY.ActionURL.buildURL('singlecell', 'poolImport', container);
                }
            },{
                xtype: 'button',
                scope: this,
                hidden: !LABKEY.Security.currentUser.canInsert,
                text: 'Create Workbook',
                handler: function(btn){
                    Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                        abortIfContainerIsWorkbook: false,
                        canAddToExistingExperiment: false,
                        controller: 'singlecell',
                        action: 'poolImport',
                        title: 'Create Workbook'
                    }).show();
                }
            }]
        }, {
            style: 'padding-top: 10px;',
            html: 'This page is designed to help import 10x data, including pooled samples. Each sample tends to create many libraries with many indexes/barcodes to track.  Use the fields below to download the excel template and paste data to import.<p>'
        },{
            layout: 'hbox',
            items: [{
                xtype: 'button',
                text: 'Download Template',
                border: true,
                scope: this,
                href: LABKEY.ActionURL.getContextPath() + '/singlecell/exampleData/ImportTemplate.xlsx'
            },{
                xtype: 'button',
                text: 'Download Example Import',
                border: true,
                scope: this,
                href: LABKEY.ActionURL.getContextPath() + '/singlecell/exampleData/ImportExample.xlsx'
            }]
        }, {
            xtype: 'ldk-linkbutton',
            text: 'Manage Allowable Values for Stim Types',
            linkCls: 'labkey-text-link',
            href: LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'singlecell', 'query.queryName': 'stim_types'}),
            style: 'margin-top: 10px;'
        }, {
            xtype: 'ldk-linkbutton',
            width: null,
            hidden: !LABKEY.Security.currentUser.isAdmin,
            text: 'Set Page Defaults',
            itemId: 'copyPrevious',
            linkCls: 'labkey-text-link',
            scope: this,
            handler: function (btn) {
                Ext4.create('Ext.window.Window', {
                    title: 'Set Page Defaults',
                    items: [{
                        xtype: 'singlecell-tenxsettingspanel',
                        border: false,
                        hidePageLoadWarning: false,
                        hideButtons: true
                    }],
                    buttons: SingleCell.panel.TenxSettingsPanel.getButtons().concat([{
                        text: 'Cancel',
                        handler: function (btn) {
                            btn.up('window').close();
                        }
                    }])
                }).show();
            }
        },{
            xtype: 'textfield',
            style: 'margin-top: 20px;',
            fieldLabel: 'Expt Number',
            itemId: 'exptNum',
            value: LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.name : null
        },{
            xtype: 'datefield',
            fieldLabel: 'Sample Date',
            itemId: 'sampleDate'
        },{
            xtype: 'textfield',
            fieldLabel: 'Cell Type',
            itemId: 'cellType',
            value: 'PBMC'
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require HTO',
            itemId: 'requireHashTag',
            checked: true
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require GEX Library',
            itemId: 'requireGEX',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require TCR Library',
            itemId: 'requireTCR',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require HTO Library',
            itemId: 'requireHTO',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require Cite-Seq Library',
            itemId: 'requireCITE',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require Assay Type',
            itemId: 'requireAssayType',
            checked: true
        },{
            xtype: 'checkbox',
            fieldLabel: 'Combine Hashing and Cite-Seq Libraries',
            itemId: 'combineHashingCite',
            checked: false,
            listeners: {
                change: function(field, val) {
                    if (val) {
                        // The combined library only makes sense if using BioLegend:
                        field.up('panel').down('#hashingType').setValue('BioLegend');
                    }
                }
            }
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require Library Concentrations',
            itemId: 'requireConc',
            checked: false
        }, {
            xtype: 'checkbox',
            fieldLabel: 'Skip Readsets',
            itemId: 'skipReadsets',
            checked: true,
            listeners: {
                scope: this,
                change: function (field, val) {
                    field.up('panel').down('#requireGEX').setValue(!val);
                    field.up('panel').down('#requireTCR').setValue(!val);
                    field.up('panel').down('#requireHTO').setValue(!val);
                    field.up('panel').down('#requireCITE').setValue(!val);
                }
            }
        },{
            xtype: 'textfield',
            itemId: 'defaultCiteSeqPanel',
            fieldLabel: 'Default CITE-seq Panel',
            value: 'TB-V2'
        },{
            xtype: 'checkbox',
            fieldLabel: 'Use 10x V2/HT (Dual Index)',
            itemId: 'useDualIndex',
            checked: true
        },{
            xtype: 'checkbox',
            fieldLabel: '# Cells Indicates Total Per Lane',
            helpPopup: 'If checked, the page will assume the value reported for cells represent the lane as a whole, and it will automatically be divided by the sample number',
            itemId: 'cellsReportedAsTotalPerLane',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Use MS (Dual Index)',
            itemId: 'useDualMsIndex',
            checked: true
        },{
            xtype: 'ldk-simplelabkeycombo',
            fieldLabel: 'Hashing Type',
            itemId: 'hashingType',
            forceSelection: true,
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'singlecell',
            queryName: 'hashing_label_groups',
            displayField: 'groupName',
            valueField: 'groupName',
            value: 'MultiSeq'
        },{
            xtype: 'textarea',
            fieldLabel: 'Paste Data Below',
            labelAlign: 'top',
            itemId: 'data',
            width: 1000,
            height: 300
        },{
            xtype: 'button',
            text: 'Preview',
            border: true,
            scope: this,
            handler: this.onPreview
        },{
            itemId: 'previewArea',
            style: 'margin-top: 20px;margin-bottom: 10px;',
            autoEl: 'table',
            cls: 'stripe hover'
        }];
    },

    onPreview: function(btn) {
        var text = this.down('#data').getValue();
        if (!text) {
            Ext4.Msg.alert('Error', 'Must provide the table of data');
            return;
        }

        this.EXPERIMENT = this.down('#exptNum').getValue();
        this.CELL_TYPE = this.down('#cellType').getValue();
        this.SAMPLE_DATE = this.down('#sampleDate').getValue();
        if (this.SAMPLE_DATE) {
            this.SAMPLE_DATE = Ext4.Date.format(this.SAMPLE_DATE, 'Y-m-d');
        }

        //this is a special case.  if the first character is Tab, this indicates a blank field.  Add a placeholder so it's not trimmed:
        if (text .startsWith("\t")) {
            text = 'Column1' + text;
        }
        text = Ext4.String.trim(text);

        var rows = LDK.Utils.CSVToArray(text, '\t');
        var colArray = this.parseHeader(rows.shift());
        var parsedRows = this.parseRows(colArray, rows);
        var sampleRows = [];
        Ext4.Array.forEach(parsedRows, function(r){
            LDK.Assert.assertNotEmpty('Expected non-null workbook', r.workbook);
            sampleRows.push({
                subjectId: r.subjectId,
                sampledate: r.sampleDate,
                stim: r.stim,
                celltype: r.celltype,
                assaytype: r.assaytype || 'None',
                disposition: r.disposition,
                tissue: r.tissue,
                objectId: r.objectId,
                population: r.population,
                hto: r.hto,
                workbook: r.workbook
            });
        }, this);

        Ext4.Msg.wait('Looking for matching samples');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('singlecell', 'getMatchingSamples', Laboratory.Utils.getQueryContainerPath(), null),
            timeout: 99999,
            method: 'POST',
            jsonData: {
                sampleRows: sampleRows
            },
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();

                Ext4.Array.forEach(parsedRows, function(r){
                    if (results.sampleMap[r.objectId]){
                        r.sampleId = results.sampleMap[r.objectId];
                    }

                    if (results.sortMap[r.objectId]){
                        r.sortId = results.sortMap[r.objectId];
                    }
                }, this);

                var groupedRows = this.groupForImport(colArray, parsedRows);
                if (!groupedRows){
                    console.log('No rows after grouping');
                    return;
                }

                this.renderPreview(colArray, parsedRows, groupedRows);

                if (results.recordErrors) {
                    Ext4.Array.forEach(results.recordErrors, function(e){
                        console.error(e);
                    }, this);
                }
            }, this),
            failure: LDK.Utils.getErrorCallback()
        });
    },

    parseHeader: function(headerRow){
        var colArray = [];
        var colNames = {};
        Ext4.Array.forEach(headerRow, function(headerText, idx){
            var colData = this.COLUMN_MAP[headerText.toLowerCase()];
            if (!colData) {
                //replace common terms:
                if (headerText.match(/ng\/ul/i) || headerText.match(/qubit/i)) {
                    headerText = headerText.replace(/( )+(\()*ng\/ul(\))*/i, '');
                    headerText = headerText.replace(/( )+(\()*qubit(\))*/i, '');
                    headerText = Ext4.String.trim(headerText);
                    if (!headerText.match(/Conc/i)) {
                        headerText = headerText + ' Conc';
                    }
                }
                headerText = headerText.replace(/CiteSeq/i, 'Cite-Seq');
                headerText = headerText.replace(/Cite Seq/i, 'Cite-Seq');
                headerText = headerText.replace(/^MS /i, 'MultiSeq ');
                headerText = headerText.replace(/Multi Seq/i, 'MultiSeq');
                headerText = headerText.replace(/Multi-Seq/i, 'MultiSeq');
                headerText = headerText.replace(/Library Index/i, 'Index');
                headerText = headerText.replace(/ RP#/i, '');
                headerText = headerText.replace(/:( )+10X Plate N Set A/i, '');
                headerText = headerText.replace(/:( )+10X Plate T Kit A/i, '');

                headerText = headerText.replace(/5'[- ]*GEX/i, 'GEX');
                headerText = headerText.replace(/5[- ]GEX/i, 'GEX');
                headerText = Ext4.String.trim(headerText);

                colData = this.COLUMN_MAP[headerText.toLowerCase()];
            }

            if (colData){
                colNames[colData.name] = idx;
            }
        }, this);

        Ext4.Array.forEach(this.COLUMNS, function(colData, idx){
            if (this.IGNORED_COLUMNS.indexOf(colData.name) > -1) {
                return;
            }

            if (colData.alwaysShow || colData.allowBlank === false || colNames[colData.name]){
                colData = Ext4.apply({}, colData);
                colData.dataIdx = colNames[colData.name];

                colArray.push(colData);
            }
        },this);

        return colArray;
    },

    parseRows: function(colArray, rows){
        var lastValueByCol = new Array(colArray.length);
        var ret = [];

        var doSplitCellsByPool = false;
        var kitCol = colArray.filter(function(x){ return x.name === 'kitType'});
        kitCol = kitCol.length ? kitCol[0] : null;

        Ext4.Array.forEach(rows, function(row, rowIdx){
            var data = {
                objectId: LABKEY.Utils.generateUUID()
            };

            if (kitCol && row[kitCol.dataIdx]) {
                if (row[kitCol.dataIdx].toUpperCase() === 'V1.1') {
                    data.dualIndex10x = false;
                }
                else if (row[kitCol.dataIdx].toUpperCase() === 'V2' || row[kitCol.dataIdx].toUpperCase() === 'HT') {
                    data.dualIndex10x = true;
                }
                else {
                    console.error('Unknown kit type: ' + row[kitCol.dataIdx]);
                }
            }

            Ext4.Array.forEach(colArray, function(col, colIdx){
                var cell = Ext4.isDefined(col.dataIdx) ? Ext4.String.trim(row[col.dataIdx]) : '';
                if (cell){
                    if (col.transform && this.transforms[col.transform]){
                        cell = this.transforms[col.transform](cell, this, data);
                    }

                    data[col.name] = cell;
                    lastValueByCol[colIdx] = cell;
                }
                else if (col.allowRowSpan && lastValueByCol[colIdx]){
                    data[col.name] = lastValueByCol[colIdx];
                }
                else {
                    //allow transform even if value is null
                    if (col.transform && this.transforms[col.transform]){
                        cell = this.transforms[col.transform](cell, this, data);
                    }

                    data[col.name] = cell;

                    // This indicates that the first row from the plateId has a value for cells, but this does not.
                    if (!cell && col.name === 'cells' && lastValueByCol[colIdx]) {
                        doSplitCellsByPool = true;
                    }
                }
            }, this);

            ret.push(data);
        }, this);

        //split cells across rows
        var cellsReportedAsTotalPerLane = this.down('#cellsReportedAsTotalPerLane') && this.down('#cellsReportedAsTotalPerLane').getValue();
        if (cellsReportedAsTotalPerLane || doSplitCellsByPool) {
            var cellCountMap = {};
            Ext4.Array.forEach(ret, function(data) {
                if (data.plateId) {
                    cellCountMap[data.plateId] = cellCountMap[data.plateId] || [];
                    cellCountMap[data.plateId].push(data.cells);
                }
            }, this);

            Ext4.Array.forEach(Ext4.Object.getKeys(cellCountMap), function(plateId) {
                var arr = cellCountMap[plateId];
                var size = arr.length;

                // Two allowable patterns:
                // 1) the first row has a value and rest are blank. Take this as the lane total
                // 2) all rows have the same value, so take the first as the lane total
                arr = Ext4.Array.remove(arr, null);
                arr = Ext4.Array.remove(arr, '');

                // Only attempt to collapse if this was selected:
                if (cellsReportedAsTotalPerLane) {
                    arr = Ext4.unique(arr);
                }

                if (arr.length === 1) {
                    cellCountMap[plateId] = arr[0] / size;
                }
                else {
                    delete cellCountMap[plateId];
                }
            }, this);

            Ext4.Array.forEach(ret, function(data) {
                if (data.plateId && cellCountMap[data.plateId]) {
                    data.cells = cellCountMap[data.plateId];
                }
            }, this);
        }

        return ret;
    },

    groupForImport: function(colArray, parsedRows){
        var ret = {
            sampleRows: [],
            sortRows: [],
            cDNARows: [],
            readsetRows: []
        };

        var errorsMsgs = [];

        //samples:
        var sampleMap = {};
        var sampleIdxs = {};
        var sampleIdx = 0;
        Ext4.Array.forEach(parsedRows, function(row){
            var key = this.getSampleKey(row);
            if (!sampleMap[key]){
                var guid = LABKEY.Utils.generateUUID();
                sampleIdx++;

                sampleMap[key] = guid;
                sampleIdxs[key] = sampleIdx;
                LDK.Assert.assertNotEmpty('Expected non-null workbook', row.workbook);
                ret.sampleRows.push({
                    rowId: row.sampleId || null,
                    subjectId: row.subjectId,
                    sampledate: row.sampleDate,
                    stim: row.stim,
                    celltype: row.celltype,
                    tissue: row.tissue,
                    assaytype: row.assaytype || 'None',
                    disposition: row.disposition,
                    objectId: guid,
                    workbook: row.workbook
                });
            }

            row.sample_num = row.sample_num || sampleIdxs[key];
        }, this);

        //sorts:
        var sortMap = {};
        Ext4.Array.forEach(parsedRows, function(row){
            LDK.Assert.assertNotEmpty('Expected non-null workbook', row.workbook);
            var sampleGUID = sampleMap[this.getSampleKey(row)];
            var key = this.getSortKey(row);
            if (!sortMap[key]){
                var guid = LABKEY.Utils.generateUUID();
                sortMap[key] = guid;
                ret.sortRows.push({
                    rowId: row.sortId || null,
                    sampleGUID: sampleGUID,
                    population: row.population,
                    replicate: row.replicate,
                    cells: row.cells,
                    well: row.well || 'Pool',
                    hto: row.hto,
                    buffer: row.buffer,
                    objectId: guid,
                    workbook: row.workbook
                });
            }
        }, this);

        //cDNA/readsets: group by pool
        var poolMap = {};
        Ext4.Array.forEach(parsedRows, function(row) {
            poolMap[row.plateId] = poolMap[row.plateId] || [];
            poolMap[row.plateId].push(row);
        }, this);

        Ext4.Object.each(poolMap, function(poolName, rowArr){
            var readsetGUIDs = {};

            var requireHTO = this.down('#requireHTO').getValue();
            var hashingType = this.down('#hashingType').getValue();
            var libraryType = null;
            if (hashingType === 'CD298'){
                libraryType = 'CD298 Hashing';
            }
            else if (hashingType === 'MultiSeq'){
                libraryType = 'MultiSeq';
            }
            else if (hashingType === 'BioLegend'){
                libraryType = 'BioLegend';
            }

            var combineHashingCite = this.down('#combineHashingCite').getValue();
            var rs = this.processReadsetForGroup(poolName, rowArr, ret.readsetRows, 'hto', 'HTO', (combineHashingCite ? 'Cell Hashing/CITE-seq' : 'Cell Hashing'), libraryType, (combineHashingCite ? 'HTO-CITE' : null));
            if (Ext4.isString(rs)) {
                readsetGUIDs.hashingReadsetGUID = rs;
            }
            else if (requireHTO){
                errorsMsgs.push('Missing HTO library');
                errorsMsgs = errorsMsgs.concat(rs);
                return false;
            }

            var requireCITE = this.down('#requireCITE').getValue();
            if (combineHashingCite) {
                readsetGUIDs.citeseqReadsetGUID = readsetGUIDs.hashingReadsetGUID;
            }
            else {
                var rs = this.processReadsetForGroup(poolName, rowArr, ret.readsetRows, 'citeseq', 'CITE', 'CITE-Seq', null, null);
                if (Ext4.isString(rs)) {
                    readsetGUIDs.citeseqReadsetGUID = rs;
                }
            }

            if (requireCITE && !readsetGUIDs.citeseqReadsetGUID){
                errorsMsgs.push('Missing CITE-Seq library');
                errorsMsgs = errorsMsgs.concat(rs);
                return false;
            }

            var requireGEX = this.down('#requireGEX').getValue();
            rs = this.processReadsetForGroup(poolName, rowArr, ret.readsetRows, 'gex', 'GEX', 'RNA-seq, Single Cell', '10x 5\' GEX', null);
            if (Ext4.isString(rs)) {
                readsetGUIDs.readsetGUID = rs;
            }
            else if (requireGEX){
                errorsMsgs.push('Missing GEX library');
                errorsMsgs = errorsMsgs.concat(rs);
                return false;
            }

            var requireTCR = this.down('#requireTCR').getValue();
            rs = this.processReadsetForGroup(poolName, rowArr, ret.readsetRows, 'tcr', 'TCR', 'RNA-seq, Single Cell', '10x 5\' VDJ (Rhesus A/B/D/G)', null);
            if (Ext4.isString(rs)) {
                readsetGUIDs.tcrReadsetGUID = rs;
            }
            else if (requireTCR){
                errorsMsgs.push('Missing TCR library');
                errorsMsgs = errorsMsgs.concat(rs);
                return false;
            }

            Ext4.Array.forEach(rowArr, function(row) {
                var sortKey = this.getSortKey(row);

                LDK.Assert.assertNotEmpty('Expected non-null workbook', row.workbook);
                var cDNA = Ext4.apply({
                    sortGUID: sortMap[sortKey],
                    chemistry: null,
                    plateId: row.plateId,
                    well: row.well || 'Pool',
                    citeseqpanel: row.citeseqpanel,
                    workbook: row.workbook
                }, readsetGUIDs);

                ret.cDNARows.push(cDNA);
            }, this);
        }, this);

        if (errorsMsgs.length) {
            errorsMsgs = Ext4.unique(errorsMsgs);
            Ext4.Msg.alert('Error', errorsMsgs.join('<br>'));
            return null;
        }

        return ret;
    },

    processReadsetForGroup: function(poolName, rowArr, readsetRows, prefix, type, application, librarytype, nameSuffix){
        var idxValues = this.getUniqueValues(rowArr, prefix + '_library_index');
        var conc = this.getUniqueValues(rowArr, prefix + '_library_conc');
        var fragment = this.getUniqueValues(rowArr, prefix + '_library_fragment');
        var workbook = this.getUniqueValues(rowArr, 'workbook');
        if (workbook.length > 1) {
            return ['Error', 'Pool ' + poolName + ' uses more workbook ' + workbook.join(';')];
        }
        workbook = workbook.length === 1 ? workbook[0] : null;

        let subjectid = this.getUniqueValues(rowArr, 'subjectId');
        subjectid = subjectid.length === 1 ? subjectid[0] : null;

        const requireConc = this.down('#requireConc').getValue();

        if (idxValues.length === 1){
            if (requireConc && !conc[0]) {
                return ['Pool ' + poolName + ': did not provide concentration for library: ' + type];
            }

            var guid = LABKEY.Utils.generateUUID();
            LDK.Assert.assertNotEmpty('Expected non-null workbook', workbook);

            // These are the dual-index 10x barcode series.
            var isDualIndex = idxValues[0].startsWith('MS-TN') || idxValues[0].startsWith('SI-TN') || idxValues[0].startsWith('SI-TT');

            readsetRows.push({
                name: poolName + '-' + (nameSuffix || type),
                plateId: poolName,
                barcode5: isDualIndex ? idxValues[0] + '_F' : idxValues[0],
                barcode3: isDualIndex ? idxValues[0] + '_R' : null,
                concentration: conc[0],
                fragmentSize: fragment[0],
                platform: 'ILLUMINA',
                application: application,
                librarytype: librarytype,
                subjectid: subjectid,
                sampleType: 'mRNA',
                objectId: guid,
                workbook: workbook
            });

            return guid;
        }
        else if (idxValues.length > 1) {
            return ['Error', 'Pool ' + poolName + ' uses more than one ' + type + ' index'];
        }
        else if (idxValues.length === 0) {
            var required = this.down('#require' + type).getValue();
            if (required) {
                return ['Error', 'No index found for pool: ' + poolName + ', for library type: ' + type];
            }
        }
    },

    getUniqueValues: function(rowArr, colName){
        var ret = [];
        Ext4.Array.forEach(rowArr, function(row){
            if (row[colName] && row[colName] !== 'NA' && row[colName] != 'N/A')
                ret.push(row[colName]);
        }, this);

        return Ext4.unique(ret);
    },

    renderPreview: function(colArray, parsedRows, groupedRows){
        var previewArea = this.down('#previewArea');
        previewArea.removeAll();

        var columns = [{title: 'Row #'}];
        var colIdxs = [];
        Ext4.Array.forEach(colArray, function(col, idx){
            if (col){
                columns.push({title: col.labels[0], className: 'dt-center'});
                colIdxs.push(idx);
            }
        }, this);

        var data = [];
        var missingValues = false;
        var requireHTO = this.down('#requireHTO').getValue() || (this.down('#requireHashTag') && this.down('#requireHashTag').getValue());
        var requireAssayType = this.down('#requireAssayType') && this.down('#requireAssayType').getValue()
        Ext4.Array.forEach(parsedRows, function(row, rowIdx){
            var toAdd = [rowIdx + 1];
            Ext4.Array.forEach(colIdxs, function(colIdx){
                var colDef = colArray[colIdx];
                var propName = colDef.name;

                var allowBlank = colDef.allowBlank;
                if (requireHTO && colDef.name === 'hto') {
                    allowBlank = false;
                }

                if (requireAssayType && colDef.name == 'assaytype') {
                    allowBlank = false;
                }

                if (allowBlank === false && Ext4.isEmpty(row[propName])){
                    missingValues = true;
                    toAdd.push('MISSING');
                }
                else {
                    toAdd.push(row[propName] || 'ND');
                }

            }, this);

            data.push(toAdd);
        }, this);

        var id = '#' + previewArea.getId();
        if ( jQuery.fn.dataTable.isDataTable(id) ) {
            jQuery(id).DataTable().destroy();
        }

        jQuery(id).DataTable({
            data: data,
            pageLength: 500,
            dom: 'rt<"bottom"BS><"clear">',
            buttons: missingValues ? [] : [{
                text: 'Submit',
                action: this.onSubmit,
                rowData: {
                    colArray: colArray,
                    parsedRows: parsedRows,
                    groupedRows: groupedRows,
                    panel: this
                }
            }],
            columns: columns
        });

        previewArea.doLayout();

        if (missingValues){
            Ext4.Msg.alert('Error', 'One or more rows is missing data.  Any required cells without values are marked MISSING');
        }
    },

    onSubmit: function(e, dt, node, config){
        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('singlecell', 'importTenx', Laboratory.Utils.getQueryContainerPath()),
            method: 'POST',
            jsonData: config.rowData.groupedRows,
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Data Saved', function(){
                    window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery.view', Laboratory.Utils.getQueryContainerPath(), {'query.queryName': 'cdna_libraries', schemaName: 'singlecell', 'query.sort': '-created'})
                }, this);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    getSampleKey: function(data){
        return [data.sampleId, data.subjectId, data.stim, data.assaytype, data.disposition, data.tissue, (Ext4.isDate(data.sampleDate) ? Ext4.Date.format(data.sampleDate, 'Y-m-d') : data.sampleDate)].join('|');
    },

    getSortKey: function(data){
        return [this.getSampleKey(data), data.sortId, data.population, data.hto].join('|');
    }
});
