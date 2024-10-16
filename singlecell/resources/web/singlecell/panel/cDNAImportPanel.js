Ext4.define('SingleCell.panel.cDNAImportPanel', {
    extend: 'SingleCell.panel.PoolImportPanel',

    IGNORED_COLUMNS: ['subjectId', 'sampleDate', 'sampleId', 'population', 'sortId', 'hto', 'cells', 'stim', 'celltype', 'assaytype', 'tissue', 'tube_num'],

    initComponent: function () {
        this.COLUMN_MAP = {};
        Ext4.Array.forEach(this.COLUMNS, function (col) {
            if (this.IGNORED_COLUMNS.indexOf(col.name) > -1) {
                return;
            }

            //Do not allow rowspan for this type of import
            col.allowRowSpan = false;

            this.COLUMN_MAP[col.name.toLowerCase()] = col;
            Ext4.Array.forEach(col.labels, function (alias) {
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
                    afterRender: function (field) {
                        new Ext4.util.KeyNav(field.getEl(), {
                            enter: function (e) {
                                var btn = field.up('panel').down('#goButton');
                                btn.handler(btn);
                            },
                            scope: this
                        });
                    }
                }
            }, {
                xtype: 'button',
                itemId: 'goButton',
                scope: this,
                text: 'Go',
                handler: function (btn) {
                    var wb = btn.up('panel').down('ldk-integerfield').getValue();
                    if (!wb) {
                        wb = '';
                    }

                    var container = LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.parentPath + '/' + wb : LABKEY.Security.currentContainer.path + '/' + wb;
                    window.location = LABKEY.ActionURL.buildURL('singlecell', 'poolImport', container);
                }
            }, {
                xtype: 'button',
                scope: this,
                hidden: !LABKEY.Security.currentUser.canInsert,
                text: 'Create Workbook',
                handler: function (btn) {
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
            html: 'This page is designed to help import the readset/index information for 10x libraries, after the sample data has already been imported.<p>'
        }, {
            layout: 'hbox',
            items: [{
                xtype: 'button',
                text: 'Download Template',
                border: true,
                scope: this,
                href: LABKEY.ActionURL.getContextPath() + '/singlecell/exampleData/ImportReadsetTemplate.xlsx'
            }]
        },{
            xtype: 'ldk-linkbutton',
            hidden: !LABKEY.Security.currentUser.isAdmin,
            style: 'margin-top: 10px;',
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
        }, {
            xtype: 'checkbox',
            fieldLabel: 'Require GEX Library',
            itemId: 'requireGEX',
            checked: true
        }, {
            xtype: 'checkbox',
            fieldLabel: 'Require TCR Library',
            itemId: 'requireTCR',
            checked: true
        }, {
            xtype: 'checkbox',
            fieldLabel: 'Require HTO Library',
            itemId: 'requireHTO',
            checked: true
        },{
            xtype: 'checkbox',
            fieldLabel: 'Require Cite-Seq Library',
            itemId: 'requireCITE',
            checked: false
        },{
            xtype: 'checkbox',
            fieldLabel: 'Combined Hashing and Cite-Seq Libraries',
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
            checked: true
        },{
            xtype: 'checkbox',
            fieldLabel: 'Use 10x V2/HT (Dual Index)',
            itemId: 'useDualIndex',
            checked: true,
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
        }, {
            xtype: 'textarea',
            fieldLabel: 'Paste Data Below',
            labelAlign: 'top',
            itemId: 'data',
            width: 1000,
            height: 300
        }, {
            xtype: 'button',
            text: 'Preview',
            border: true,
            scope: this,
            handler: this.onPreview
        }, {
            style: 'margin-top: 20px;margin-bottom: 10px;',
            itemId: 'previewArea',
            autoEl: 'table',
            cls: 'stripe hover'
        }];
    },

    onPreview: function (btn) {
        var text = this.down('#data').getValue();
        if (!text) {
            Ext4.Msg.alert('Error', 'Must provide the table of data');
            return;
        }

        this.EXPERIMENT = this.down('#exptNum').getValue();

        text = Ext4.String.trim(text);

        var rows = LDK.Utils.CSVToArray(text, '\t');
        var colArray = this.parseHeader(rows.shift());
        var parsedRows = this.parseRows(colArray, rows);

        var groupedRows = this.groupForImport(colArray, parsedRows);
        if (!groupedRows) {
            console.log('No rows after grouping');
            return;
        }

        var workbooks = [];
        var hadError = false;
        Ext4.Array.forEach(parsedRows, function(row){
            if (!row.workbook) {
                hadError = true;
            }
            else {
                workbooks.push(row.workbook);
            }
        }, this);

        if (hadError) {
            Ext4.Msg.alert('Error', 'One or more rows missing a workbook ID');
            return;
        }

        Ext4.Msg.wait('Loading workbooks');
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            method: 'POST',
            schemaName: 'core',
            queryName: 'workbooks',
            columns: 'Name,EntityId',
            filterArray: [LABKEY.Filter.create('Name', Ext4.unique(workbooks).join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            success: function(results) {
                Ext4.Msg.hide();

                var workbookMap = {};
                Ext4.Array.forEach(results.rows, function(r){
                    workbookMap[r.Name] = r.EntityId;
                }, this);

                Ext4.Array.forEach(groupedRows.cDNARows, function(r){
                    LDK.Assert.assertNotEmpty('Unable to find workbook in map: ' + r.workbook, workbookMap[r.workbook]);
                    r.container = workbookMap[r.workbook];
                }, this);

                Ext4.Array.forEach(groupedRows.readsetRows, function(r){
                    LDK.Assert.assertNotEmpty('Unable to find workbook in map: ' + r.workbook, workbookMap[r.workbook]);
                    r.container = workbookMap[r.workbook];
                }, this);

                this.onWorkbookQueryLoad(colArray, parsedRows, groupedRows);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onWorkbookQueryLoad: function(colArray, parsedRows, groupedRows) {
        var plateIDs = [];
        var hadError = false;
        Ext4.Array.forEach(parsedRows, function(row){
            if (!row.plateId) {
                hadError = true;
            }
            else {
                plateIDs.push(row.plateId);
            }
        }, this);

        if (hadError) {
            Ext4.Msg.alert('Error', 'One or more rows missing plate ID');
            return;
        }

        plateIDs = Ext4.unique(plateIDs);

        Ext4.Msg.wait('Looking for matching cDNA');
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            method: 'POST',
            schemaName: 'singlecell',
            queryName: 'cdna_libraries',
            columns: 'rowid,plateid,readsetId,tcrReadsetId,hashingReadsetId,citeseqReadsetId',
            filterArray: [LABKEY.Filter.create('plateId', plateIDs.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            success: function(results) {
                Ext4.Msg.hide();

                if (!results.rows || !results.rows.length) {
                    Ext4.Msg.alert('Error', 'No matching rows found');
                    return;
                }

                var plateToCDNAMap = {};
                Ext4.Array.forEach(results.rows, function(row) {
                    plateToCDNAMap[row.plateId] = plateToCDNAMap[row.plateId] || [];
                    plateToCDNAMap[row.plateId].push(row);
                }, this);

                var missing = [];
                Ext4.Array.forEach(plateIDs, function (r) {
                    if (!plateToCDNAMap[r]) {
                        missing.push(r);
                    }
                }, this);

                if (missing.length) {
                    Ext4.Msg.alert('Error', 'No cDNA records found for plates: ' + missing.join(', '));
                    return;
                }

                function getUnique(value, index, self) {
                    return self.indexOf(value) === index;
                }
                
                Ext4.Array.forEach(groupedRows.cDNARows, function(r){
                    r.rowIds = plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r.rowid) : null;
                    r.readsetId = plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r.readsetId).filter(r => !!r).filter(getUnique).join('') : null;
                    r.tcrReadsetId = plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r.tcrReadsetId).filter(r => !!r).filter(getUnique).join('') : null;
                    r.hashingReadsetId = plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r.hashingReadsetId).filter(r => !!r).filter(getUnique).join('') : null;
                    r.citeseqReadsetId = plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r.citeseqReadsetId).filter(r => !!r).filter(getUnique).join('') : null;
                }, this);

                Ext4.Array.forEach(groupedRows.readsetRows, function(r){
                    var fieldName;
                    if (r.application === 'CITE-Seq') {
                        fieldName = 'citeseqReadsetId';
                    }
                    else if (r.application === 'Cell Hashing' || r.application === 'Cell Hashing/CITE-seq') {
                        fieldName = 'hashingReadsetId';
                    }
                    else if (r.librarytype === '10x 5\' GEX') {
                        fieldName = 'readsetId';
                    }
                    else if (r.librarytype === '10x 5\' VDJ (Rhesus A/B/D/G)') {
                        fieldName = 'tcrReadsetId';
                    }
                    else {
                        console.error('Unknown row type: ' + r.application);
                        console.error(r);
                    }

                    r.doInsert = !(plateToCDNAMap[r.plateId] ? plateToCDNAMap[r.plateId].map(r => r[fieldName]).filter(r => !!r).filter(getUnique).join('') : null);
                }, this);

                this.renderPreview(colArray, parsedRows, groupedRows);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onSubmit: function (e, dt, node, config) {
        Ext4.Msg.wait('Saving...');

        var data = config.rowData.groupedRows;
        var toInsert = data.readsetRows.filter(r => r.doInsert)
        if (!toInsert.length) {
            Ext4.Msg.hide();
            Ext4.Msg.alert('No Rows To Save', 'None of the rows needed to be saved. This might mean they already exist, or could indicate a problem with the import', function(){
                window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery.view', Laboratory.Utils.getQueryContainerPath(), {'query.queryName': 'cdna_libraries', schemaName: 'singlecell', 'query.sort': '-created'});
            }, this);
        }
        else {
            LABKEY.Query.insertRows({
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_readsets',
                rows: toInsert,
                success: function (results) {
                    var readsetMap = {};
                    Ext4.Array.forEach(results.rows, function (row) {
                        readsetMap[row.name] = row.rowId;
                    }, this);

                    var toUpdate = [];
                    Ext4.Array.forEach(data.cDNARows, function (row) {
                        var baseRow = {
                            readsetId: row.readsetId,
                            tcrReadsetId: row.tcrReadsetId,
                            hashingReadsetId: row.hashingReadsetId,
                            citeseqReadsetId: row.citeseqReadsetId
                        };

                        var gexReadsetId = readsetMap[row.plateId + '-GEX'];
                        if (gexReadsetId) {
                            baseRow.readsetId = gexReadsetId;
                        }

                        var tcrReadsetId = readsetMap[row.plateId + '-TCR'];
                        if (tcrReadsetId) {
                            baseRow.tcrReadsetId = tcrReadsetId;
                        }

                        var htoReadsetId = readsetMap[row.plateId + '-HTO'] || readsetMap[row.plateId + '-HTO-CITE'];
                        if (htoReadsetId) {
                            baseRow.hashingReadsetId = htoReadsetId;
                        }

                        var citeseqReadsetId = readsetMap[row.plateId + '-CITE'] || readsetMap[row.plateId + '-HTO-CITE'];
                        if (citeseqReadsetId) {
                            baseRow.citeseqReadsetId = citeseqReadsetId;
                        }

                        baseRow.container = row.container;

                        if (row.rowIds) {
                            Ext4.Array.forEach(row.rowIds, function (r) {
                                var toAdd = Ext4.apply({
                                    rowId: r
                                }, baseRow);

                                toUpdate.push(toAdd);
                            }, this);
                        }
                    }, this);

                    if (toUpdate.length) {
                        LABKEY.Query.updateRows({
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'singlecell',
                            queryName: 'cdna_libraries',
                            rows: toUpdate,
                            success: function (results) {
                                Ext4.Msg.hide();
                                Ext4.Msg.alert('Success', 'Data Saved', function () {
                                    window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery.view', Laboratory.Utils.getQueryContainerPath(), {
                                        'query.queryName': 'cdna_libraries',
                                        schemaName: 'singlecell',
                                        'query.sort': '-created'
                                    });
                                }, this);
                            },
                            failure: LDK.Utils.getErrorCallback(),
                            scope: this
                        });
                    }
                    else {
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Success', 'Data Saved', function () {
                            window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery.view', Laboratory.Utils.getQueryContainerPath(), {
                                'query.queryName': 'cdna_libraries',
                                schemaName: 'singlecell',
                                'query.sort': '-created'
                            });
                        }, this);
                    }
                },
                failure: LDK.Utils.getErrorCallback(),
                scope: this
            });
        }
    }
});