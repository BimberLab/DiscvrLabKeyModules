/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.SequenceImportPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',

    initComponent: function(){
        this.taskId = 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceImportPipeline';

        Ext4.apply(this, {
            buttons: [{
                text: 'Import Data',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.add([
            this.getRunInfoCfg()
        ,
            this.getFilePanelCfg()
        ,{
            xtype:'panel',
            border: true,
            title: 'Step 1: Choose Input File Processing',
            itemId: 'inputFileFieldset',
            width: 'auto',
            defaults: {
                width: 350,
                labelWidth: 235
            },
            items :[{
                width: '700',
                html: 'Note: all input files will be automatically converted to FASTQ if not already',
                style: 'padding-bottom:10px;',
                border: false
            },{
                fieldLabel: 'Treatment of Input Files',
                xtype: 'combo',
                helpPopup: 'This determines how the input files are handled.  By default, files are normalized to FASTQ and the originals deleted to save space.  However, you can choose to keep the original (compressed), or leave the originals alone.',
                itemId: 'originalFileTreatment',
                name: 'inputfile.inputTreatment',
                width: 600,
                editable: false,
                displayField: 'display',
                valueField: 'value',
                value: 'delete',
                store: {
                    type: 'array',
                    fields: ['display', 'value'],
                    data: [
                        ['Delete originals', 'delete'],
                        ['Move and compress', 'compress'],
                        ['Do nothing', 'none']
                    ]
                }
            },{
                xtype: 'checkbox',
                name: 'deleteIntermediateFiles',
                fieldLabel: 'Delete Intermediate Files',
                checked: true,
                helpPopup: 'If selected, all intermediate sequence files will be deleted.  These files are usually only needed for debugging purposes, and it is selected by default to save space.'
            },{
                fieldLabel: 'Merge Files',
                disabled: !(this.fileNames.length > 1 || this.fileIds.length > 1),
                name: 'inputfile.merge',
                helpPopup: 'If selected, all input files will be merged into a single FASTQ file.  The original files will not be altered.',
                itemId: 'mergeCheckbox',
                xtype: 'checkbox',
                scope: this,
                hideMode: 'offsets',
                enabled: this.fileNameStore ? this.fileNameStore.getCount()>1 : false,
                originalState: this.fileNameStore ? this.fileNameStore.getCount()>1 : false,
                handler: function(c, val){
                    var form = c.up('form');
                    var treatmentField = form.down('#originalFileTreatment');
                    treatmentField.setValue(val ? 'compress' : 'delete');

                    var o = form.down('#mergeContainer');
                    o.setVisible(c.checked);

                    var pairedCheckbox = c.up('#inputFileFieldset').down('#usePairedEnd');
                    pairedCheckbox.setDisabled(c.checked);

                    var grid = form.down('#sampleGrid');
                    if(c.checked){
                        Ext4.each(grid.store.getRange(), function(rec, idx){
                            if(idx > 0)
                                grid.store.remove(rec);
                        }, this);
                    }

                    if(!c.checked){
                        c.up('form').down('#mergeName').reset();
                        pairedCheckbox.setValue(false);
                    }
                    else {
                        c.up('form').down('#mergeName').setValue("MergedFile");
                    }
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'mergeContainer',
                name: 'inputfile.mergeContainer',
                defaults: {
                    width: 350
                },
                items: [{
                    fieldLabel: 'Name For Merged File',
                    xtype: 'textfield',
                    width: 450,
                    name: 'inputfile.merge.basename',
                    itemId: 'mergeName',
                    listeners: {
                        change: function(field, val){
                            var grid = field.up('form').down('#sampleGrid');
                            grid.store.each(function(rec){
                                if(rec){
                                    rec.set('fileName', val);
                                    rec.set('fileId', null);
                                    rec.set('fileId2', null);
                                }
                            }, this);
                        }
                    }
                }]
            },{
                fieldLabel: 'Data Is Paired End',
                name: 'inputfile.pairedend',
                helpPopup: 'Check this if the input data is paired end.  At the moment, the pipeline expects 2 FASTQ input files per readset.',
                itemId: 'usePairedEnd',
                xtype: 'checkbox',
                scope: this,
                hidden: false,
                hideMode: 'offsets',
                handler: function(btn, val){
                    this.fireEvent('pairedendchange', btn, val);

                    //only toggle the merge checkbox if merge would be allowed
                    var mergeCheckbox = btn.up('#inputFileFieldset').down('#mergeCheckbox');
                    if (mergeCheckbox.originalState){
                        mergeCheckbox.setDisabled(val);
                        if(!val)
                            mergeCheckbox.setValue(false);
                    }
                }
            },{
                fieldLabel: 'Use Barcodes',
                helpPopup: 'Use this option if your sequences have been tagged using an MID tag (molecular barcode).  Currently only Roche\'s official tags are supported.  Contact Ben if you would like custom tags added.',
                name: 'inputfile.barcode',
                xtype:'checkbox',
                itemId: 'useBarcode',
                scope: this,
                handler: function(btn, val){
                    var treatmentField = btn.up('form').down('#originalFileTreatment');
                    treatmentField.setValue(val ? 'compress' : 'delete');

                    this.fireEvent('midchange', btn, val);
                    btn.up('form').down('#barcodeOptions').setVisible(btn.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'barcodeOptions',
                items: [{
                    xtype: 'combo',
                    store: {
                        type: 'labkey-store',
                        schemaName: 'sequenceanalysis',
                        queryName: 'barcode_groups',
                        autoLoad: true
                    },
                    editable: false,
                    displayField: 'group_name',
                    valueField: 'group_name',
                    multiSelect: true,
                    fieldLabel: 'Additional Barcodes',
                    name: 'inputfile.barcodeGroups',
                    itemId: 'scanAllBarcodes',
                    helpPopup: 'Samples will be checked for all barcodes in the selected groups, at both 5\' and 3\' ends.  A summary report will be created, but no FASTQ files will be made for combinations not associated with a sample.  This is sometimes useful to be sure no contaminants are in your sample.'
                },{
                    xtype: 'numberfield',
                    fieldLabel: 'Mismatches Tolerated',
                    name: 'inputfile.barcodeEditDistance',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'When identifying barcodes, up to the following number of mismatches will be tolerated.  Note: if too lax, multiple barcodes will be detected and the sample discarded'
                },{
                    xtype: 'numberfield',
                    fieldLabel: 'Deletions Tolerated',
                    name: 'inputfile.barcodeDeletions',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'If provided, the barcode can have up to this many deletions from the outer end of the barcode.  This allows partial matches if a barcode was clipped by quality trimming or poor sequence.  If a matching barcode is found without deletions, it will be preferentially used.'
                },{
                    xtype: 'numberfield',
                    fieldLabel: 'Allowed Distance From Read End',
                    name: 'inputfile.barcodeOffset',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'If provided, the barcode can be located up to this many bases from the end of the read'
                }]
            }]
        },{
            xtype:'panel',
            border: true,
            title: 'Step 2: Enter Sample/Readset Information',
            itemId: 'sampleInformation',
            bodyStyle: 'padding:5px;',
            width: '100%',
            defaults: {
                style: 'padding-bottom:10px;padding-left:5px',
                width: 350
            },
            items: [{
                border: false,
                preventBodyReset: true,
                html:
                    'The purpose of this import process is to normalize the sequence data into a common format (FASTQ), create one file per sample, and capture sample metadata (name, sample type, subject name, etc).  Reads are organized into readsets.  ' +
                    'Each readset is roughly equals to one input file, and it connects the sequences in this file with sample attributes, such as subject name, sample type, platform, etc.  ' +

                    'This tool is heavily linked to the lab management system, and information about the subject and/or sample are entered separately from this page.  The Subject Id and Sample Id fields connect your data with specific subjects or samples.  To view or enter these, click the links below:' +
                    '<br><br><ul style="padding-left: 20px;">' +
                    '<li style="list-style: circle;"><a href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'laboratory', 'query.queryName': 'Samples'})+'" target="_blank">View/Enter Samples</a></li>' +
                    '<li style="list-style: circle;"><a href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'sequenceanalysis', 'query.queryName': 'Sequence_Readsets'})+'" target="_blank">View/Edit Readsets</a></li>' +
                    '</ul>' +
                    '<br><br>There are two ways to create readsets: ' +
                    '<br><br><ol style="padding-left: 20px;">' +
                    '<li style="list-style: decimal;">You are able to enter the readset information ahead of time using the \'Enter Readsets\' link above.  When you create those readsets, you can will be able to enter sample information and expected barcode usage.  You will not be able to pick the file, because often the file does not exist yet (for example, if the raw input needs to be converted to FASTQ or demultiplexed).  If you previously created the readsets, just enter the readset Id into the correct row below and the rest of the readset information should populate automatically.</li>' +
                    '<li style="list-style: decimal;">If there is not an existing readset, just supply the readset name and platform (and optional sample/subject Id), and a readset will be automatically created when the pipeline runs.  You do not need to enter anything separately.</li>' +
                    '</ol><br>',

                width: '100%'
            },{
                name: 'samples',
                parent: this,
                itemId: 'sampleGrid',
                xtype: 'samplepanel',
                fileNameStore: this.distinctFileStore
            }]
        }]);
   },

    populateSamples: function(){
        var merge = this.down('#mergeCheckbox').getValue();
        var sampleGrid = this.down('#sampleGrid');
        sampleGrid.store.removeAll();

        //find file names
        var filenames = [];
        if(merge){
            filenames.push(this.down('#mergeName').getValue())
        }
        else {
            this.fileNameStore.each(function(rec){
                filenames.push(rec.get('fileName'))
            }, this);
        }

        Ext4.each(filenames, function(fn){
            var info = this.fileNameMap[fn];

            var obj = {fileName: fn};
            var error = false;
            if(info){
                obj.fileId = info.dataId;
                if (info.error)
                    error = true;
            }

            if (!error)
                sampleGrid.store.add(sampleGrid.store.createModel(obj));
        }, this);

        this.fireEvent('dataready');
    },

    getJsonParams: function(btn){
        var fields = this.callParent(arguments);

        if(!fields)
            return;

        var error;

        //we build a hash of samples to verify there are no duplicates
        var sampleMap = {};
        var barcodes = {};
        var barcodeUsage;
        var useBarcode = this.down('#useBarcode').getValue();
        var usePairedEnd = this.down('#usePairedEnd').getValue();
        var doMerge = this.down('#mergeCheckbox').getValue();

        var cols = this.down('#sampleGrid').columns;
        var sampleInfo = this.down('#sampleGrid').getStore();
        var inputFiles = [];
        sampleInfo.each(function(r, sampleIdx){
            var key = [r.data['fileName']];
            inputFiles.push({name: r.data['fileName'], id: r.data['fileId']});
            if(usePairedEnd){
                if(!r.data['fileName2']){
                    Ext4.Msg.alert('Error', 'Missing paired file for: ' + r.get('fileName') + '. Either choose a file or unchecked paired-end.');
                    error = 1;
                    return false;
                }

                if(r.data['fileName'] == r.data['fileName2']){
                    Ext4.Msg.alert('Error', 'Forward and reverse files cannot have the same name: ' + r.get('fileName'));
                    error = 1;
                    return false;
                }
                key.push(r.data['fileName2']);
                inputFiles.push({name: r.data['fileName2'], id: r.data['fileId2']});
            }
            else {
                delete r.data['fileName2'];
                delete r.data['fileId2'];
            }

            if(!useBarcode){
                delete r.data['mid5'];
                delete r.data['mid3'];
            }
            else {
                key.push(r.data['mid5']);
                key.push(r.data['mid3']);
            }

            key = key.join(";");

            if(sampleMap[key]){
                Ext4.Msg.alert('Error', 'Duplicate Sample: '+key+'. Please remove or edit rows in the \'Readsets\' section');
                error = 1;
                return false;
            }
            sampleMap[key] = true;

            //verify we use the same pattern of barcoding
            var barcodeUsageTmp = {
                5: (!Ext4.isEmpty(r.data['mid5'])) ,
                3: (!Ext4.isEmpty(r.data['mid3']))
            };

            if(barcodeUsage){
                if(barcodeUsageTmp[3]!=barcodeUsage[3] || barcodeUsageTmp[5]!=barcodeUsage[5]){
                    Ext4.Msg.alert('Error', 'All samples must either use no barcodes, 5\' only, 3\' only or both ends');
                    error = 1;
                }
            }
            barcodeUsage = barcodeUsageTmp;

            if(!r.get('fileName') || (!r.get('readset') && (!r.get('platform') && !r.get('readsetname')))){
                Ext4.Msg.alert('Error', 'For each file, you must provide either the Id of an existing, unused readset or a name/platform to create a new one');
                error = 1;
            }

            var id = [r.get('fileid2'), r.get('mid5'), r.get('mid3')].join('_');


            //handle barcodes
            var rec;
            if(r.data['mid5']){
                rec = this.barcodeStore.getAt(this.barcodeStore.find('tag_name', r.data['mid5']));
                if(!barcodes[r.data['mid5']]){
                    barcodes[r.data['mid5']] = [r.data['mid5'], rec.get('sequence')];
                }
                else {
                    barcodes[r.data['mid5']][2] = 1;
                }
            }
            if(r.data['mid3']){
                rec = this.barcodeStore.getAt(this.barcodeStore.find('tag_name', r.data['mid3']));
                if(rec && !barcodes[r.data['mid3']]){
                    barcodes[r.data['mid3']] = [r.data['mid3'], rec.get('sequence')];
                }
                else {
                    barcodes[r.data['mid5']][3] = 1;
                }
            }

            if(r.get('readset')){
                var msg = 'A readset has already been created for the file: ' + r.get('fileName');
                if(r.get('fileName2'))
                    msg += ' and paired file: ' + r.get('fileName2');

                if(r.get('mid5') || r.get('mid3'))
                    msg += ' using barcodes: ' + (r.get('mid5') ? r.get('mid5') : '') + (r.get('mid3') ? ', ' + r.get('mid3') : '');
                msg += ' and you cannot import a file twice.';

                Ext4.Msg.alert('Error', msg);
                error = 1;
            }

            if(error){
                return false;
            }

            fields['sample_'+sampleIdx] = r.data;
        }, this);

        for (var i in barcodes){
            fields['barcode_'+i] = barcodes[i];
        }

        if(error){
            return false;
        }

        //make sure input files are valid
        var total = 0;
        this.fileNameStore.each(function(rec){
            if (!rec.get('error'))
                total++;
        }, this);

        if (!total){
            Ext4.Msg.alert('Error', 'All input files had errors and cannot be used.  Please hover over the red cells near the top of the page to see more detail on these errors');
            return;
        }

        if(doMerge){
            inputFiles = [];
            this.fileNameStore.each(function(rec){
                //skip files flagged w/ errors
                if (rec.get('error'))
                    return;

                inputFiles.push({
                    name: rec.get('fileName'),
                    id: rec.get('dataId')
                });
            }, this);
        }

        var distinctIds = [];
        var distinctNames = [];
        Ext4.each(inputFiles, function(file){
            if(!file.id)
                distinctNames.push(file.name);
            else
                distinctIds.push(file.id)
        }, this);

        return {
            json: fields,
            distinctIds: distinctIds,
            distinctNames: distinctNames
        };
    },

    onSubmit: function(btn){
        var ret = this.getJsonParams();

        if (!ret)
            return;

        this.startAnalysis(ret.json, ret.distinctIds, ret.distinctNames);
    },

    getFilePanelCfg: function(){
        return {
            xtype:'panel',
            border: true,
            title: 'Selected Files',
            itemId: 'files',
            width: 'auto',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                xtype: 'dataview',
                itemId: 'fileListView',
                store: this.fileNameStore,
                itemSelector:'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Id</td><td>Filename</td><td>Folder</td><td></td></tr>',
                    '<tpl for=".">',
                        '<tr class="file_list">',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{dataId}</a></td>',
                        '<td ',
                            '<tpl if="error">style="background: red;" data-qtip="{error}"</tpl>',
                            '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{fileName:htmlEncode}</a></td>',
                        //'<td>{RowId:htmlEncode}</td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", null, {})]}" target="_blank">{containerPath:htmlEncode}</a></td>',
                        '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            }]
        }
    },

    getRunInfoCfg: function(){
        return {
            xtype:'form',
            border: true,
            bodyBorder: false,
            title: 'Run Information',
            name: 'protocols',
            width: 'auto',
            defaults: Ext4.Object.merge({}, this.fieldDefaults, {bodyStyle: 'padding:5px;'}),
            defaultType: 'textfield',
            items :[{
                fieldLabel: 'Job Name',
                width: 600,
                helpPopup: 'This is the name assigned to this job, which must be unique.  Results will be moved into a folder with this name.',
                name: 'protocolName',
                itemId: 'protocolName',
                allowBlank:false,
                value: 'SequenceImport_'+new Date().format('Ymd'),
                maskRe: new RegExp('[A-Za-z0-9_]'),
                validator: function(val){
                    return (this.isValidProtocol === false ? 'Job Name Already In Use' : true);
                },
                listeners: {
                    scope: this,
                    change: {
                        fn: this.checkProtocol,
                        buffer: 200,
                        scope: this
                    }
                }
            },{
                fieldLabel: 'Protocol Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this protocol (optional)',
                itemId: 'protocolDescription',
                name: 'protocolDescription',
                allowBlank:true
            }]
        }
    },

    initFiles: function(sql){
        this.fileNameMap = {};

        this.distinctFileStore = Ext4.create('Ext.data.ArrayStore', {
            fields: ['fileName'],
            idIndex: 0,
            data: []
       });

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'validateReadsetFiles'),
            params: {
                path: LABKEY.ActionURL.getParameter('path'),
                fileNames: this.fileNames,
                fileIds: this.fileIds
            },
            scope: this,
            success: this.onFileLoad,
            failure: LDK.Utils.getErrorCallback
        });

        this.fileNameStore = Ext4.create('Ext.data.Store', {
            fields: ['fileName', 'filePath', 'basename', 'container', 'containerPath', 'dataId', 'error']
        });
    },

    onFileLoad: function(response){
        LDK.Utils.decodeHttpResponseJson(response);

        if (response.responseJSON.validationErrors.length){
            Ext4.Msg.alert('Error', 'There are errors with the input files:<br>' + response.responseJSON.validationErrors.join('<br>'));
        }

        Ext4.each(response.responseJSON.fileInfo, function(f){
            this.fileNameStore.add(LDK.StoreUtils.createModelInstance(this.fileNameStore, f));

            if (!f.error){
                var model = LDK.StoreUtils.createModelInstance(this.distinctFileStore, f);
                this.distinctFileStore.add(model);
            }
            this.fileNameMap[f.fileName] = f;
        }, this);

        this.down('#fileListView').refresh();

        this.checkProtocol();
        this.populateSamples();
    }
});