/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.window.RunExportWindow', {
    extend: 'Ext.window.Window',

    statics: {
        downloadFilesForReadset: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');

            LABKEY.Query.selectRows({
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'readdata',
                filterArray: [
                    LABKEY.Filter.create('readset', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'rowid,fileid1,fileid2',
                scope: this,
                success: function(result){
                    Ext4.Msg.hide();

                    if (result && result.rows.length){
                        Ext4.create('SequenceAnalysis.window.RunExportWindow', {
                            autoShow: true,
                            dataRegionName: dataRegionName,
                            records: result.rows,
                            fileTypes: [
                                {name: 'Forward Reads', fields: ['fileid1'], checked: true},
                                {name: 'Reverse Reads', fields: ['fileid2'], checked: true}
                            ]
                        });
                    }
                    else {
                        Ext4.Msg.alert('Error', 'No sequence files found for the selected readsets');
                    }
                },
                failure: LDK.Utils.getErrorCallback()
            });
        },

        downloadFilesForAnalysis: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');

            LABKEY.Query.selectRows({
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_analyses',
                filterArray: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'rowid,inputfile,inputfile2,readset/fileid,readset/fileid2,alignmentfile,alignmentfileindex,reference_library',
                scope: this,
                success: function(result){
                    Ext4.Msg.hide();

                    if (result && result.rows.length){
                        Ext4.create('SequenceAnalysis.window.RunExportWindow', {
                            autoShow: true,
                            dataRegionName: dataRegionName,
                            records: result.rows,
                            fileTypes: [
                                {name: 'Alignment File', fields: ['alignmentfile'], checked: true},
                                {name: 'Reference Genome', fields: ['reference_library'], checked: true}
                            ]
                        });
                    }
                    else {
                        Ext4.Msg.alert('Error', 'No sequence files found for the selected analyses');
                    }
                },
                failure: LDK.Utils.getErrorCallback()
            });
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Export Files',
            modal: true,
            width: 400,
            itemId: 'exportFilesWin',
            defaults: {
                border: false
            },
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    width: 350
                },
                items: [{
                    xtype: 'textfield',
                    allowBlank: false,
                    fieldLabel: 'File Prefix',
                    itemId: 'fileName',
                    value: 'Sequences'
                },
                    this.getFileTypeItems()
                ,{
                    xtype: 'radiogroup',
                    itemId: 'exportType',
                    columns: 1,
                    fieldLabel: 'Export Files As',
                    items: [{
                        xtype: 'radio',
                        boxLabel: 'ZIP Archive of Individual Files',
                        name: 'exportType',
                        checked: true,
                        inputValue: 'zip'
                    },{
                        xtype: 'radio',
                        name: 'exportType',
                        boxLabel: 'Merge into Single FASTQ File',
                        inputValue: 'fastq'
                    }]
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();

        //button should require selection, so this should never happen...
        var dr = LABKEY.DataRegions[this.dataRegionName];
        var selected = dr.getChecked();
        if(!selected.length){
            this.close();
            alert('No Files Selected');
        }
    },

    getFileTypeItems: function(){
        var items = [];
        Ext4.Array.forEach(this.fileTypes, function(ft){
            items.push({
                xtype: 'checkbox',
                boxLabel: ft.name,
                name: ft.name,
                inputValue: ft.fields,
                checked: ft.checked !== false
            });
        }, this);

        var config = {
            xtype: 'checkboxgroup',
            itemId: 'fileTypes',
            fieldLabel: 'Choose Files To Include',
            columns: 1,
            items: items
        }

        if(items.length == 1){
            config.hidden = true;
            config.items[0].checked = true;
        }

        return config;
    },

    getDataIds: function(){
        var ids = [];
        Ext4.each(this.records, function(r){
            for (var ft in this.fileTypes){
                var types = this.down('#fileTypes').getValue();
                for (var ft in types){
                    Ext4.each(types[ft], function(prop){
                        if(r[prop])
                            ids.push(r[prop]);
                    }, this);
                }
            }
        }, this);
        ids = Ext4.unique(ids);
        return ids;
    },

    onSubmit: function(btn){
        var win = btn.up('window');
        var url = win.getURL();

        if(!url)
            return;
        var form = Ext4.create('Ext.form.Panel', {
            url: url,
            standardSubmit: true,
            items : [{ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }]
        });
        form.submit({
            method: 'POST',
            params: {
                dataIds: this.getDataIds()
            }
        });

        win.close();
    },

    getURL: function(){
        var fileNameField = this.down('#fileName');
        if(!fileNameField.getValue()){
            var msg = 'Must provide a filename';
            fileName.markInvalid(msg);
            alert(msg);
            return;
        }

        var exportType = this.down('#exportType').getValue().exportType;
        var fileName = fileNameField.getValue();
        var dataIds = this.getDataIds();
        if(!dataIds.length){
            alert('No Files Selected');
            return;
        }

        var url;
        if (exportType == 'zip'){
            url = LABKEY.ActionURL.buildURL('sequenceanalysis', 'exportSequenceFiles', null, {zipFileName: fileName});
        }
        else {
            url = LABKEY.ActionURL.buildURL('sequenceanalysis', 'mergeFastqFiles', null, {zipFileName: fileName});
        }

        return url;
    }
});