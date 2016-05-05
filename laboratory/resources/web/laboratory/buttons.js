/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.namespace('Laboratory.buttonHandlers');

Laboratory.buttonHandlers = new function(){
    return {
        importDataHandler: function(dataregionName, target, schema, query, keyField){
            if (LABKEY.Security.currentContainer.type == 'workbook'){
                window.location = LABKEY.ActionURL.buildURL('query', 'importData', null, {schemaName: schema, queryName: query, keyField: keyField})
            }
            else {
                Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                    controller: 'query',
                    action: 'importData',
                    urlParams: {
                        schemaName: schema,
                        queryName: query,
                        keyField: keyField
                    },
                    title: 'Import Data'
                }).show(target);
            }
        },

        markSamplesRemoved: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if(!checked || !checked.length){
                alert('No records selected');
                return;
            }

            LABKEY.Query.selectRows({
                containerPath: dataRegion.containerPath,
                schemaName: 'laboratory',
                queryName: 'samples',
                columns: 'rowid,container',
                ignoreFilter: 1,
                filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    Ext4.create('Ext.Window', {
                        width: 315,
                        autoHeight: true,
                        modal: true,
                        bodyStyle:'padding:5px',
                        closeAction:'hide',
                        dataRegion: dataRegion,
                        keys: [{
                            key: Ext4.EventObject.ENTER,
                            handler: this.onSubmit,
                            scope: this
                        }],
                        title: 'Mark Removed',
                        items: [{
                            xtype: 'form',
                            fieldDefaults: {
                                width: 290,
                                labelAlign: 'top'
                            },
                            border: false,
                            items: [{
                                xtype: 'datefield',
                                fieldLabel: 'Date Removed',
                                value: new Date()
                            },{
                                xtype: 'textarea',
                                fieldLabel: 'Comment',
                                itemId: 'remove_comment',
                                height: 150
                            }]
                        }],
                        buttons: [{
                            xtype: 'button',
                            text: 'Submit',
                            handler: function(btn){
                                var win = btn.up('window');
                                var df = win.down('datefield');
                                var date = df.getValue();
                                if(!date){
                                    alert('Must enter a date');
                                    return;
                                }

                                var textarea = win.down('textarea');
                                var comment = textarea.getValue();

                                var rows = [];
                                Ext4.each(results.rows, function(row){
                                    rows.push({
                                        rowid: row.rowid,
                                        container: row.container,
                                        dateremoved: date,
                                        remove_comment: comment,
                                        removedby: LABKEY.Security.currentUser.id
                                    });
                                }, this);

                                Ext4.Msg.wait('Saving...');

                                LABKEY.Query.updateRows({
                                    containerPath: dataRegion.containerPath,
                                    schemaName: dataRegion.schemaName,
                                    queryName: dataRegion.queryName,
                                    rows: rows,
                                    scope: this,
                                    success: function(){
                                        Ext4.Msg.hide();

                                        var win = this.up('window');
                                        win.dataRegion.refresh();
                                        win.hide();
                                    },
                                    failure: LDK.Utils.getErrorCallback()
                                });
                            }
                        },{
                            xtype: 'button',
                            text: 'Cancel',
                            handler: function(btn){
                                btn.up('window').hide();
                            }
                        }]
                    }).show();
                }
            });
        },
        appendCommentToSamples: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if(!checked || !checked.length){
                alert('No records selected');
                return;
            }

            LABKEY.Query.selectRows({
                containerPath: dataRegion.containerPath,
                schemaName: 'laboratory',
                queryName: 'samples',
                columns: 'rowid,container,comment',
                ignoreFilter: 1,
                filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    LDK.Assert.assertTrue('no results after selecting rows for appendCommentToSamples()', results && results.rows && results.rows.length);

                    Ext4.create('Ext.Window', {
                        width: 315,
                        autoHeight: true,
                        modal: true,
                        bodyStyle:'padding:5px',
                        closeAction:'hide',
                        dataRegion: dataRegion,
                        keys: [{
                            key: Ext4.EventObject.ENTER,
                            handler: this.onSubmit,
                            scope: this
                        }],
                        title: 'Append Comment',
                        items: [{
                            xtype: 'form',
                            fieldDefaults: {
                                width: 290,
                                labelAlign: 'top'
                            },
                            border: false,
                            items: [{
                                html: 'This will append the following comment to all selected samples.  If the row has an existing comment, the new one will be appended and the original comment unchanged',
                                style: 'padding-bottom: 10px;',
                                border: false
                            },{
                                xtype: 'textarea',
                                fieldLabel: 'Comment',
                                itemId: 'comment',
                                height: 150
                            }]
                        }],
                        buttons: [{
                            xtype: 'button',
                            text: 'Submit',
                            scope: this,
                            handler: function(btn){
                                var win = btn.up('window');
                                var textarea = win.down('textarea');
                                var comment = textarea.getValue();
                                if (!comment){
                                    Ext4.Msg.alert('Error', 'Must enter a comment');
                                    return;
                                }

                                var rows = [];
                                Ext4.each(results.rows, function(row){
                                    row.comment = row.comment ? (row.comment + '\n' + comment) : comment;

                                    rows.push({
                                        rowid: row.rowid,
                                        container: row.container,
                                        comment: row.comment
                                    });
                                }, this);

                                Ext4.Msg.wait('Saving...');

                                LABKEY.Query.updateRows({
                                    containerPath: dataRegion.containerPath,
                                    schemaName: dataRegion.schemaName,
                                    queryName: dataRegion.queryName,
                                    rows: rows,
                                    scope: this,
                                    success: function(){
                                        Ext4.Msg.hide();

                                        var win = this.up('window');
                                        win.dataRegion.refresh();
                                        win.hide();
                                    },
                                    failure: LDK.Utils.getErrorCallback()
                                });
                            }
                        },{
                            xtype: 'button',
                            text: 'Cancel',
                            handler: function(btn){
                                btn.up('window').hide();
                            }
                        }]
                    }).show();
                }
            });
        },
        deriveSamples: function(dataRegionName, btn){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if(!checked || !checked.length){
                alert('No records selected');
                return;
            }

            //TODO: I should be able to do this off metadata
            var columns = ['freezerid', 'samplename', 'subjectid', 'sampledate',
                'sampletype', 'samplesubtype', 'samplesource',
                'location', 'freezer', 'cane', 'box', 'box_row', 'box_column',
                'preparationmethod', 'processdate',
                'additive', 'concentration', 'concentration_units', 'quantity', 'quantity_units', 'passage_number', 'ratio',
                'molecule_type', 'dna_vector', 'dna_insert', 'sequence', 'labwareIdentifier', 'comment', 'parentsample',
                'dateremoved', 'removedby', 'remove_comment'
            ];

            Ext4.QuickTips.init();
            Ext4.create('LABKEY.ext4.data.Store', {
                containerPath: dataRegion.containerPath,
                schemaName: dataRegion.schemaName,
                queryName: dataRegion.queryName,
                autoLoad: true,
                columns: columns.join(','),
                filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
                scope: this,
                listeners: {
                    scope: this,
                    load: function(store){
                        if (!store.getCount()){
                            Ext4.Msg.alert('No matching records were found');
                            return;
                        }

                        Ext4.create('Ext.Window', {
                            width: 425,
                            modal: true,
                            closeAction: 'hide',
                            dataRegion: dataRegion,
                            keys: [{
                                key: Ext4.EventObject.ENTER,
                                handler: this.onSubmit,
                                scope: this
                            }],
                            title: 'Duplicate/Derive Samples',
                            items: [{
                                xtype: 'form',
                                bodyStyle: 'padding:5px',
                                border: false,
                                defaults: {
                                    border: false,
                                    labelWidth: 150
                                },
                                items: [{
                                    html: 'This will generate an excel template that can be used to import samples, pre-populated based on the rows you selected.  Because you selected to derive samples, the \'parentSample\' column will be pre-populated with the IDs of the samples you selected.',
                                    style: 'padding-bottom: 20px;'
                                },{
                                    xtype: 'radiogroup',
                                    itemId: 'exportType',
                                    columns: 1,
                                    fieldLabel: 'Export Type',
                                    helpPopup: 'The determines how the excel file will be created.  If you select \'duplicate\', then the rows will be copies as-in.  If you select \'derive\', then the sampleId of the original record will be pre-filled out in the parentSample column in the excel file.  This can be used to retain a record of how a sample was created.',
                                    items: [{
                                        boxLabel: 'Duplicate Samples',
                                        name: 'exportType',
                                        inputValue: 'duplicate',
                                        checked: true
                                    },{
                                        boxLabel: 'Derive Samples',
                                        name: 'exportType',
                                        inputValue: 'derive'
                                    }]
                                },{
                                    xtype: 'numberfield',
                                    itemId: 'copies',
                                    helpPopup: 'This determines how many new copies will be made of each row you selected.  If you checked 2 rows, then selected 3 copies, an excel file with 6 rows will be created',
                                    fieldLabel: 'Copies Per Sample',
                                    value: 1
                                }]
                            }],
                            buttons: [{
                                xtype: 'button',
                                text: 'Submit',
                                handler: function(btn){
                                    btn.up('window').makeExcel();
                                    btn.up('window').hide();
                                }
                            },{
                                xtype: 'button',
                                text: 'Cancel',
                                handler: function(btn){
                                    btn.up('window').hide();
                                }
                            }],

                            makeExcel: function(){
                                var df = this.down('#copies');
                                var total = df.getValue();
                                if(!total){
                                    alert('Must enter a quantity');
                                    return;
                                }

                                var exportType = this.down('#exportType').getValue().exportType
                                //var columns = LDK.StoreUtils.getExcelTemplateColumns(store);
                                var header = [];
                                var fieldMap = LDK.StoreUtils.getFieldMap(store);
                                Ext4.each(columns, function(name){
                                    header.push(fieldMap[name].caption);
                                }, this);
                                var data = [header];
                                var parentFieldIdx = header.indexOf('parentsample');

                                store.each(function(sourceRec){
                                    var newRow = [];
                                    Ext4.each(columns, function(colName){
                                        if (colName == 'rowid' || colName == 'freezerid')
                                            return;

                                        //if the user selected
                                        if(exportType == 'derive'){
                                            if (colName == 'parentsample')
                                                newRow.push(sourceRec.get('freezerid'));
                                            else
                                                newRow.push(sourceRec.get(colName));
                                        }
                                        else {
                                            newRow.push(sourceRec.get(colName));
                                        }
                                    }, this);

                                    for (var i=0;i<total;i++)
                                        data.push(newRow);
                                }, this);

                                LABKEY.Utils.convertToExcel({
                                    fileName : 'Samples_' + Ext4.util.Format.date(new Date(), 'Y-m-d H_i_s') + '.xls',
                                    sheets : [{
                                        name: 'samples',
                                        data: data
                                    }]
                                });
                            }
                        }).show(btn);
                    }
                }
            });
        },

        publishSamples: function(dataRegionName, btn){
            var dr = LABKEY.DataRegions[dataRegionName];
            var checked = dr.getChecked();

            Ext4.create('Ext.window.Window', {
                dataRegionName: dataRegionName,
                model: true,
                closeAction: 'destroy',
                checkedRecords: checked,
                width: 400,
                items: [{
                    style: 'padding: 5px;',
                    border: false,
                    items: [{
                        html: 'The selected records will either be marked as public or hidden, depending on your selection below.  All records will be changed to the same state.  To mark these records as private, uncheck the box and hit submit.  Note: marking a record as public is simply a flag and does not inherently share it with other users outside of those who already have read access to the table.  In certain applications, this flag it used to denote records to be shared; however, it is unlikely this is occurring if you are not explicitly aware of it.<br>',
                        style: 'margin-bottom: 10px;',
                        border: false
                    },{
                        fieldLabel: 'Set Records as Public',
                        labelWidth: 160,
                        xtype: 'checkbox',
                        itemId: 'public',
                        checked: false
                    },{
                        height: 10,
                        border: false
                    }]
                }],
                buttons: [{
                    text: 'Submit',
                    scope: this,
                    handler: function(btn){
                        Ext4.Msg.wait('Saving...');

                        //b/c these records might be of different containers, we select to get containerId, then update
                        LABKEY.Query.selectRows({
                            containerPath: LABKEY.DataRegions[dataRegionName].containerPath,
                            schemaName: 'laboratory',
                            queryName: 'samples',
                            columns: 'rowid,container',
                            ignoreFilter: 1,
                            filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
                            scope: this,
                            failure: LDK.Utils.getErrorCallback(),
                            success: function(results){
                                if (results && results.rows.length){
                                    var win = btn.up('window');
                                    var state = win.down('#public').getValue();

                                    var rows = [];
                                    Ext4.each(results.rows, function(rec){
                                        rows.push({
                                            rowid: rec.rowid,
                                            container: rec.container,
                                            makePublic: state
                                        });
                                    }, this);

                                    if (rows.length){
                                        LABKEY.Query.updateRows({
                                            containerPath: LABKEY.DataRegions[dataRegionName].containerPath,
                                            schemaName: 'laboratory',
                                            queryName: 'samples',
                                            rows: rows,
                                            scope: this,
                                            failure: LDK.Utils.getErrorCallback(),
                                            success: function(results){
                                                Ext4.Msg.hide();
                                                win.close();
                                                Ext4.Msg.alert('Success', 'Records updated', function(){
                                                    LABKEY.DataRegions[dataRegionName].refresh();
                                                }, this);
                                            }
                                        });
                                    }
                                }
                                else {
                                    Ext4.Msg.hide();
                                }
                            }
                        });
                    }
                },{
                    text: 'Cancel',
                    handler: function(btn){
                        btn.up('window').close();
                    }
                }]
            }).show(btn);
        }
    }
}

