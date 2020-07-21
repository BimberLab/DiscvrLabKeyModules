/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.SequenceImportPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    alias: 'widget.sequenceanalysis-sequenceimportpanel',
    jobType: 'readsetImport',

    initComponent: function(){
        Ext4.override(Ext4.data.validations, {
            presenceMessage: 'Field is required'
        });

        this.fileGroupStore = Ext4.create('Ext.data.ArrayStore', {
            model: Ext4.define('SequenceAnalysis.model.FileSetModel', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'fileGroupId'}
                ]
            })
        });

        this.readsetStore = Ext4.create('Ext.data.ArrayStore', {
            validateAll: function(){
                this.each(function(r){
                    r.validate();
                })
            },
            model: Ext4.define('SequenceAnalysis.model.ReadsetModel', {
                sequenceImportPanel: this,
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'fileGroupId', allowBlank: false},
                    {name: 'readset', allowBlank: false},
                    {name: 'readsetname', useNull: true},
                    {name: 'barcode5', useNull: true},
                    {name: 'barcode3', useNull: true},
                    {name: 'platform', allowBlank: false},
                    {name: 'application', allowBlank: false},
                    {name: 'chemistry', allowBlank: false},
                    {name: 'concentration', type: 'float', useNull: true},
                    {name: 'fragmentSize', type: 'float', useNull: true},
                    {name: 'librarytype', useNull: true},
                    {name: 'sampletype', useNull: true},
                    {name: 'subjectid', useNull: true},
                    {name: 'sampledate', type: 'date'},
                    {name: 'comments', useNull: true},
                    {name: 'sampleid', useNull: true},
                    {name: 'instrument_run_id', type: 'int', useNull: true}
                ],
                validations: [
                    {type: 'presence', field: 'fileGroupId'},
                    {type: 'presence', field: 'readsetname'},
                    {type: 'presence', field: 'platform'},
                    {type: 'presence', field: 'application'}
                ],
                getFileId: function(){
                    return [this.get('fileGroupId'), this.get('barcode5'), this.get('barcode3')].join('||');
                },
                validate: function(options) {
                    var errors = this.callParent(arguments);

                    var id = this.getFileId();
                    var name = this.get('readsetname');
                    var doDemultiplex = this.sequenceImportPanel.down('#doDemultiplex').getValue();
                    if (doDemultiplex && !this.get('barcode5') && !this.get('barcode3')){
                        var msg = 'Demultiplexing was selected, must enter at least one barcode.';
                        errors.add({
                            field: 'barcode5',
                            message: msg
                        });
                        errors.add({
                            field: 'barcode3',
                            message: msg
                        });
                    }

                    if (this.store){
                        this.store.each(function(r){
                            if (r !== this && r.getFileId() === id){
                                var msg = 'Only one readset may use each file group, unless barcoding is used.';
                                errors.add({
                                    field: 'fileGroupId',
                                    message: msg
                                });

                                errors.add({
                                    field: 'barcode5',
                                    message: msg
                                });

                                errors.add({
                                    field: 'barcode3',
                                    message: msg
                                });
                            }

                            if (r !== this && r.get('readsetname') === name){
                                errors.add({
                                    field: 'readsetname',
                                    message: 'All names must be unique'
                                });
                            }
                        }, this);

                        if (this.sequenceImportPanel.down('#importReadsetIds').getValue()){
                            if (!this.get('readset')){
                                errors.add({
                                    field: 'readset',
                                    message: 'This field is required.'
                                });
                            }
                        }
                    }

                    return errors;
                },
                hasMany: {model: 'SequenceAnalysis.model.ReadsetDataModel', name: 'files'}
            })
        });

        this.readDataStore = Ext4.create('Ext.data.ArrayStore', {
            groupField: 'fileGroupId',
            model: Ext4.define('SequenceAnalysis.model.ReadsetDataModel', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'id'},
                    {name: 'fileGroupId', allowBlank: false},
                    {name: 'fileRecord1'},
                    {name: 'fileRecord2'},
                    {name: 'platformUnit'},
                    {name: 'centerName'},
                    {name: 'comments'}
                ],
                validations: [
                    {type: 'presence', field: 'fileGroupId'},
                    {type: 'presence', field: 'fileRecord1'}
                ],
                validate: function(options) {
                    var errors = this.callParent(arguments);

                    var file1 = this.get('fileRecord1');
                    var file2 = this.get('fileRecord2');
                    if (file1 === file2){
                        errors.add({
                            field: 'fileRecord1',
                            message: 'The forward and reverse reads cannot use the same file'
                        });
                    }

                    if (this.store){
                        var otherFiles = [];
                        this.store.each(function(r) {
                            if (r !== this) {
                                if (r.get('fileRecord1')){
                                    otherFiles.push(r.get('fileRecord1'));
                                }

                                if (r.get('fileRecord2')){
                                    otherFiles.push(r.get('fileRecord2'));
                                }
                            }
                        }, this);

                        otherFiles = Ext4.unique(otherFiles);

                        if (file1 && otherFiles.indexOf(file1) !== -1){
                            errors.add({
                                field: 'fileRecord1',
                                message: 'This file is being used by another group'
                            });
                        }

                        if (file2 && otherFiles.indexOf(file2) !== -1){
                            errors.add({
                                field: 'fileRecord2',
                                message: 'This file is being used by another group'
                            });
                        }
                    }

                    return errors;
                },
                belongsTo: 'SequenceAnalysis.model.ReadsetModel'
            })
        });

        Ext4.apply(this, {
            configDefaults: {},
            buttonAlign: 'left',
            buttons: [{
                text: 'Import Data',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        Ext4.QuickTips.init();
        this.callParent(arguments);

        this.add([{
            xtype: 'panel',
            width: '100%',
            title: 'Instructions',
            defaults: {
                border: false
            },
            items: [{
                style: 'padding-bottom:10px;',
                html: 'The purpose of this import process is to normalize the sequence data into a common format (FASTQ), create one file per sample, and capture sample metadata (name, sample type, subject name, etc).  Reads are organized into readsets.  ' +
                        'Each readset is roughly equals to one input file (or 2 for pair-end data), and it connects the sequences in this file with sample attributes, such as subject name, sample type, platform, etc.'
            },{
                html: '<h4><a href="https://github.com/BimberLab/discvr-seq/wiki/Sequence-Management" target="_blank">Click here for more detailed instructions</a></h4>'
            }]
        }, this.getRunInfoCfg(), this.getFilePanelCfg(),{
            xtype:'panel',
            border: true,
            title: 'Step 1: General Options',
            itemId: 'sampleInformation',
            bodyStyle: 'padding:5px;',
            width: '100%',
            defaults: {
                border: false,
                width: '100%'
            },
            items :[{
                width: '700',
                html: 'Note: all input files will be automatically converted to FASTQ (ASCII offset 33) if not already.',
                style: 'padding-bottom:10px;',
                border: false
            },SequenceAnalysis.panel.SequenceImportPanel.getInputFileTreatmentField({itemId: 'originalFileTreatment', name: 'inputFileTreatment'}),{
                xtype: 'checkbox',
                name: 'deleteIntermediateFiles',
                fieldLabel: 'Delete Intermediate Files',
                checked: true,
                helpPopup: 'If selected, all intermediate sequence files will be deleted.  These files are usually only needed for debugging purposes, and it is selected by default to save space.'
            },{
                fieldLabel: 'Run FastQC',
                helpPopup: 'Use this option to automatically run and cache a FastQC report.  For large input files, FastQC can take a long time to run.  Pre-caching this information may be useful later.',
                name: 'inputfile.runFastqc',
                xtype:'checkbox',
                itemId: 'runFastqc',
                checked: true
            },{
                xtype: 'checkbox',
                name: 'inputfile.flagLowReads',
                inputValue: true,
                fieldLabel: 'Flag Readsets With Low Reads',
                helpPopup: 'If checked, readsets with fewer than the provided number of reads will be automatically flagged with the specified status.',
                scope: this,
                handler: function(btn, val){
                    btn.up('form').down('#lowReadThresholdOptions').setVisible(btn.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'lowReadThresholdOptions',
                fieldDefaults: {
                    width: 350
                },
                items: [{
                    xtype: 'ldk-numberfield',
                    fieldLabel: 'Read Threshold',
                    helpPopup: 'Readsets with fewer than this many reads will be flagged',
                    minValue: 0,
                    name: 'inputfile.lowReadThreshold',
                    value: 1000
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Status',
                    schemaName: 'sequenceanalysis',
                    queryName: 'readset_status',
                    sortField: 'status',
                    valueField: 'status',
                    value: 'Failed',
                    displayField: 'status',
                    plugins: ['ldk-usereditablecombo'],
                    name: 'inputfile.lowReadStatusLabel'
                }]
            }]
        },this.getReadDataSection(), this.getReadsetSection(), {
            xtype: 'panel',
            style: 'padding-bottom: 0px;',
            width: '100%',
            border: false,
            items: [{
                border: false,
                width: '100%',
                style: 'text-align: center',
                html: 'Powered By DISCVR-Seq.  <a href="https://github.com/BimberLab/discvr-seq/wiki">Click here to learn more.</a>'
            }]
        }]);

        this.on('afterrender', this.updateColWidth, this, {single: true});
        this.down('#readsetGrid').on('columnresize', this.updateColWidth, this);
        this.on('midchange', this.onMidChange, this);

        this.mon(this.readDataStore, 'update', this.onReadDataUpdate, this, {buffer: 200, delay: 200});
    },

    onReadDataUpdate: function(){
        //NOTE: if actively editing, dont update the grid.  defer until either edit or cancel
        var readDataGrid = this.down('#readDataGrid');
        if (readDataGrid.editingPlugin.editing){
            var callback = function(){
                this.mun(readDataGrid.editingPlugin, 'edit', callback, this);
                this.mun(readDataGrid.editingPlugin, 'canceledit', callback, this);

                this.onReadDataUpdate();
            };

            this.mon(readDataGrid.editingPlugin, 'edit', callback, this, {single: true, delay: 100});
            this.mon(readDataGrid.editingPlugin, 'canceledit', callback, this, {single: true, delay: 100});

            return;
        }

        this.down('#readsetGrid').getView().refresh();

        //update fileGroupIds
        var distinctGroups = [];
        this.readDataStore.each(function(r){
            if (r.get('fileGroupId')){
                distinctGroups.push(r.get('fileGroupId'));
            }
        }, this);
        distinctGroups = Ext4.unique(distinctGroups);

        var found = [];
        Ext4.Array.forEach(this.fileGroupStore.getRange(), function(fg){
            if (fg.get('fileGroupId')) {
                if (distinctGroups.indexOf(fg.get('fileGroupId')) === -1) {
                    this.fileGroupStore.remove(fg);
                }
                else {
                    found.push(fg.get('fileGroupId'));
                }
            }
        }, this);

        Ext4.Array.forEach(distinctGroups, function(name){
            if (found.indexOf(name) === -1){
                this.fileGroupStore.add(this.fileGroupStore.createModel({
                    fileGroupId: name
                }));
            }
        }, this);

        this.readsetStore.each(function(r){
            if (distinctGroups.indexOf(r.get('fileGroupId')) === -1){
                r.set('fileGroupId', null);
            }
        }, this);
    },

    statics: {
        getInputFileTreatmentField: function(config){
            return Ext4.apply({
                fieldLabel: 'Treatment of Input Files',
                xtype: 'combo',
                helpPopup: 'This determines how the input files are handled.  By default, files are normalized to FASTQ and the originals deleted to save space.  However, you can choose to keep the original (compressed), or leave the originals alone.',
                width: 600,
                forceSelection: true,
                displayField: 'display',
                valueField: 'value',
                value: 'delete',
                store: {
                    type: 'array',
                    fields: ['display', 'value'],
                    data: [
                        ['Copy, delete originals', 'delete'],
                        ['Move originals and compress', 'compress'],
                        ['Copy, leave originals in place', 'none'],
                        ['Leave in place', 'leaveInPlace']
                    ]
                }
            }, config || {});
        },

        getRenderer: function (colName) {
            var column;
            return function (value, cellMetaData, record, rowIndex, colIndex, store) {
                var errors = record.validate();
                if (!errors.isValid()) {
                    var msgs = errors.getByField(colName);
                    if (msgs.length) {
                        var texts = [];
                        Ext4.Array.forEach(msgs, function (m) {
                            texts.push(m.message);
                        }, this);
                        texts = Ext4.unique(texts);

                        cellMetaData.tdCls = cellMetaData.tdCls ? cellMetaData.tdCls + ' ' : '';
                        cellMetaData.tdCls += 'labkey-grid-cell-invalid';

                        cellMetaData.tdAttr = cellMetaData.tdAttr || '';
                        cellMetaData.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(texts.join('<br>')) + "\"";
                    }
                }

                return value;
            }
        }
    },

    //<sample name>_<barcode sequence>_L<lane (0-padded to 3 digits)>_R<read number>_<set number (0-padded to 3 digits>.fastq.gz
    ILLUMINA_REGEX: /^(.+)_L(.+)_(R){0,1}([0-9])(_[0-9]+){0,1}(\.f(ast){0,1}q)(\.gz)?$/i,

    //Example from NextSeq: RNA160915BB_34A_22436_Gag120_Clone-10_S10_R1_001.fastq.gz
    //This should also allow simple pairs, like: file1_1.fq.gz and file1_2.fq.gz
    ILLUMINA_REGEX_NO_LANE: /^(.+)_(R){0,1}([0-9])(_[0-9]+){0,1}(\.f(ast){0,1}q)(\.gz)?$/i,

    //example: 214-3-6-GEX_1_S29_L002_R1_001.fastq.gz
    //<sample name>_<barcode sequence>_L<lane (0-padded to 3 digits)>_R<read number>_<set number (0-padded to 3 digits>.fastq.gz
    TENX_REGEX: /^(.+?)(_[0-9]+){0,1}_S(.+)_L(.+)_(R){0,1}([0-9])(_[0-9]+){0,1}(\.f(ast){0,1}q)(\.gz)?$/i,

    populateSamples: function(orderType, isPaired){
        this.fileNameStore.sort('displayName', 'ASC');
        this.readDataStore.removeAll();
        var errorMsgs = [];

        if (!orderType || orderType === 'illumina' || orderType === '10x'){
            var map = {};
            this.fileNameStore.each(function(rec, i) {
                if (rec.get('readgroup'))
                {
                    var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                    m.set('fileRecord1', rec.get('id'));
                    m.set('platformUnit', rec.get('readgroup').platformUnit);
                    m.set('fileGroupId', rec.get('readgroup').sample);
                    m.set('centerName', rec.get('readgroup').centerName);

                    map[rec.get('readgroup').sample] = map[rec.get('readgroup').sample] || [];
                    map[rec.get('readgroup').sample].push(m);
                }
                else if (orderType === '10x' && this.TENX_REGEX.test(rec.get('fileName'))){
                    var match = this.TENX_REGEX.exec(rec.get('fileName'));
                    var sample = match[1];
                    var platformUnit = match[1];  //force concat
                    var setNo = match[7];
                    var setId = platformUnit + '_' + setNo;
                    var direction = match[6];
                    this.processIlluminaMatch(sample, platformUnit, setNo, setId, direction, rec, map, errorMsgs);
                }
                else if (this.ILLUMINA_REGEX.test(rec.get('fileName'))){
                    var match = this.ILLUMINA_REGEX.exec(rec.get('fileName'));
                    var sample = match[1];
                    var platformUnit = match[1] + '_L' + match[2];
                    var setNo = match[5];
                    var setId = platformUnit + '_' + setNo;
                    var direction = match[4];
                    this.processIlluminaMatch(sample, platformUnit, setNo, setId, direction, rec, map, errorMsgs);
                }
                else if (this.ILLUMINA_REGEX_NO_LANE.test(rec.get('fileName'))){
                   var match = this.ILLUMINA_REGEX_NO_LANE.exec(rec.get('fileName'));
                   var setNo = match[4] ? match[4].replace(/^_/, '') : null;
                   var sample = match[1];
                   var platformUnit = match[1];
                   var setId = match[1] + (setNo ? '-' + setNo : '');
                   var direction = match[3];
                   this.processIlluminaMatch(sample, platformUnit, null, setId, direction, rec, map, errorMsgs);
                }
                else {
                    var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                    m.set('fileRecord1', rec.get('id'));
                    var fileArr = rec.get('fileName').replace(/\.gz$/, '').split('.');
                    fileArr.pop();
                    var fileGroupId = fileArr.length == 1 ? fileArr[0] : fileArr.join('.');
                    m.set('fileGroupId', fileGroupId);
                    //m.set('platformUnit', fileGroupId);

                    map[rec.get('fileName')] = map[rec.get('fileName')] || [];
                    map[rec.get('fileName')].push(m);
                }
            }, this);

            var keys = Ext4.Object.getKeys(map);
            keys.sort();

            Ext4.Array.forEach(keys, function(key){
                if (Ext4.isArray(map[key])){
                    Ext4.Array.forEach(map[key], function(r){
                        this.readDataStore.add(r);
                    }, this);
                }
                else {
                    Ext4.Array.forEach(Ext4.Object.getKeys(map[key]), function(pu, idx){
                        this.readDataStore.add(map[key][pu]);
                    }, this);
                }
            }, this);
        }
        else if (orderType === 'row'){
            this.fileNameStore.each(function(rec, i) {
                if (isPaired){
                    if (i % 2 === 0){
                        var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                        var fileArr = rec.get('fileName').replace(/\.gz$/, '').split('.');
                        fileArr.pop();
                        m.set('fileRecord1', rec.get('id'));

                        var fileGroupId = fileArr.length === 1 ? fileArr[0] : fileArr.join('.');
                        m.set('fileGroupId', fileGroupId);
                        this.readDataStore.add(m);
                    }
                    else
                    {
                        this.readDataStore.getAt(this.readDataStore.getCount() - 1).set('fileRecord2', rec.get('id'));
                    }
                }
                else {
                    var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                    var fileArr = rec.get('fileName').replace(/\.gz$/, '').split('.');
                    fileArr.pop();

                    m.set('fileRecord1', rec.get('id'));
                    var fileGroupId = fileArr.length === 1 ? fileArr[0] : fileArr.join('.');
                    m.set('fileGroupId', fileGroupId);
                    this.readDataStore.add(m);
                }
            }, this);
        }
        else if (orderType === 'column') {
            if (isPaired){
                var colSize = Math.ceil(this.fileNameStore.getCount() / 2);
                this.fileNameStore.each(function (rec, i){
                    if (i < colSize) {
                        var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                        var fileArr = rec.get('fileName').replace(/\.gz$/, '').split('.');
                        fileArr.pop();
                        m.set('fileRecord1', rec.get('id'));

                        var fileGroupId = fileArr.length === 1 ? fileArr[0] : fileArr.join('.');
                        m.set('fileGroupId', fileGroupId);
                        this.readDataStore.add(m);
                    }
                    else {
                        this.readDataStore.getAt(i - colSize).set('fileRecord2', rec.get('id'));
                    }
                }, this);
            }
            else {
                //this should never get called.  column/paired is the same thing as row/paired
                this.fileNameStore.each(function (rec, i){
                    var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                    var fileArr = rec.get('fileName').replace(/\.gz$/, '').split('.');
                    fileArr.pop();

                    m.set('fileRecord1', rec.get('id'));
                    var fileGroupId = fileArr.length === 1 ? fileArr[0] : fileArr.join('.');
                    m.set('fileGroupId', fileGroupId);
                    this.readDataStore.add(m);
                }, this);
            }
        }

        this.down('#readDataGrid').getView().refresh();

        //populate readsets
        var distinctNames = [];
        this.readDataStore.each(function(r){
            distinctNames.push(r.get('fileGroupId'));
        }, this);
        distinctNames = Ext4.unique(distinctNames);

        //update fileGroupIds
        Ext4.Array.forEach(distinctNames, function(name){
            this.fileGroupStore.add(this.fileGroupStore.createModel({
                fileGroupId: name
            }));
        }, this);

        Ext4.Array.forEach(distinctNames, function(name){
            if (this.readsetStore.findExact('fileGroupId', name) === -1) {
                this.readsetStore.add(this.readsetStore.createModel({
                    fileGroupId: name
                }));
            }
        }, this);

        Ext4.Array.forEach(this.readsetStore.getRange(), function(r){
            if (distinctNames.indexOf(r.get('fileGroupId')) === -1){
                this.readsetStore.remove(r);
            }
        }, this);

        if (this.readDataStore.getCount() === 0 && this.fileNameStore.getCount() !== 0){
            var msg = 'Possible error parsing file groups on SequenceImportPanel.  Names were:\n';
            this.fileNameStore.each(function(f){
                msg += '[' + f.get('fileName') + ']\n';
            }, this);

            LDK.Utils.logError(msg);
        }

        return errorMsgs;
    },

    processIlluminaMatch: function(sample, platformUnit, lane, setId, readSet, rec, map, errorMsgs){
        map[sample] = map[sample] || {};
        map[sample][setId] = map[sample][setId] || [];

        if (readSet == 1){
            var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
            m.set('fileRecord1', rec.get('id'));
            m.set('fileGroupId', sample);
            m.set('platformUnit', platformUnit);
            map[sample][setId].push(m);
        }
        else {
            var arr = map[sample][setId];
            if (!arr.length){
                var msg = 'Possible error parsing file groups on SequenceImportPanel.  Encountered reverse reads prior to forward for file: [' + setId + '].  Names were:\n';
                this.fileNameStore.each(function(f){
                    msg += '[' + f.get('fileName') + ']\n';
                }, this);

                LDK.Utils.logError(msg);
                errorMsgs.push('Possible error: Encountered reverse reads prior to forward reads for ' + setId + ', which can indicate files are out of alphabetical order, one file from a pair is missing, or a problem parsing the filenames.  Please check over the file groups carefully.');

                var m = Ext4.create('SequenceAnalysis.model.ReadsetDataModel', {});
                m.set('fileRecord2', rec.get('id'));
                m.set('fileGroupId', sample);
                m.set('platformUnit', platformUnit);
                map[sample][setId].push(m);
            }
            else {
                arr[arr.length - 1].set('fileRecord2', rec.get('id'));
            }
        }
    },

    getJsonParams: function(btn){
        var values = this.callParent(arguments);
        if (!values) {
            return;
        }

        //make sure input files are valid
        var totalErrors = 0;
        values.inputFiles = [];
        this.fileNameStore.clearFilter();
        this.fileNameStore.each(function(rec){
            if (rec.get('error'))
                totalErrors++;
            else {
                if (!rec.get('dataId'))
                    values.inputFiles.push({
                        fileName: rec.get('fileName'),
                        relPath: rec.get('relPath')
                    });
                else
                    values.inputFiles.push({dataId: rec.get('dataId')});
            }
        }, this);

        if (totalErrors){
            Ext4.Msg.alert('Error', 'Some of the sequence files had errors and cannot be used.  Please hover over the red cells near the top of the page to see more detail on these errors');
            return;
        }

        if (this.getReadsetParams(values) === false){
            return false;
        }

        return values;
    },

    getReadsetParams: function(values){
        var barcodes = {};
        var doDemultiplex = this.down('#doDemultiplex').getValue();
        var showBarcodes = this.down('#showBarcodes').getValue();

        var errors = Ext4.create('Ext.data.Errors');
        this.readDataStore.each(function(r, sampleIdx) {
            var recErrors = r.validate();
            if (recErrors.getCount()){
                errors.add(recErrors.getRange());
            }
        }, this);

        this.fileNameStore.clearFilter();
        this.barcodeStore.clearFilter();

        var fileGroupMap = {};
        this.readDataStore.each(function(r, idx){
            var recErrors = r.validate();
            if (recErrors.getCount()){
                errors.add(recErrors.getRange());
            }

            var i = 1;
            var readData = {
                fileGroupId: r.get('fileGroupId'),
                centerName: r.get('centerName'),
                platformUnit: r.get('platformUnit')
            };
            while (i <= 2){
                if (r.get('fileRecord' + i)){
                    var fileDataRecIdx = this.fileNameStore.findExact('id', r.get('fileRecord' + i));
                    var fileDataRec = this.fileNameStore.getAt(fileDataRecIdx);
                    if (fileDataRec){
                        readData['file' + i] = {
                            fileName: fileDataRec.get('fileName'),
                            relPath: fileDataRec.get('relPath'),
                            dataId: fileDataRec.get('dataId')
                        }
                    }
                }

                i++;
            }

            fileGroupMap[r.get('fileGroupId')] = fileGroupMap[r.get('fileGroupId')] || [];
            fileGroupMap[r.get('fileGroupId')].push(readData);
        }, this);

        var fileIdx = 1;
        for (var group in fileGroupMap){
            values['fileGroup_' + fileIdx] = {
                name: group,
                files: fileGroupMap[group]
            };
            fileIdx++;
        }

        if (Ext4.Object.isEmpty(fileGroupMap)){
            Ext4.Msg.alert('Error', 'There are no file groups.  You must complete the section showing how your files are grouped into lanes/groups');
            return false;
        }

        if (!this.readsetStore.getCount()){
            Ext4.Msg.alert('Error', 'There are no readsets defined.  Please fill out the readset grid');
            return false;
        }

        this.readsetStore.each(function(r, sampleIdx){
            var recErrors = r.validate();
            if (recErrors.getCount()){
                errors.add(recErrors.getRange());
            }

            var key = [r.get('fileRecord1')];
            key.push(r.get('fileRecord2'));

            if (!doDemultiplex && !showBarcodes){
                delete r.data['barcode5'];
                delete r.data['barcode3'];
            }
            else {
                key.push(r.data['barcode5']);
                key.push(r.data['barcode3']);
            }

            key = key.join("||");

            //handle barcodes
            var rec;
            if (doDemultiplex && r.get('barcode5')){
                rec = this.barcodeStore.getAt(this.barcodeStore.find('tag_name', r.get('barcode5')));
                if (!barcodes[r.get('barcode5')]){
                    barcodes[r.get('barcode5')] = [r.get('barcode5'), rec.get('sequence')];
                }
            }

            if (doDemultiplex && r.get('barcode3')){
                rec = this.barcodeStore.getAt(this.barcodeStore.find('tag_name', r.get('barcode3')));
                if (rec && !barcodes[r.get('barcode3')]){
                    barcodes[r.get('barcode3')] = [r.get('barcode3'), rec.get('sequence')];
                }
            }

            if (doDemultiplex && (!r.get('barcode5') && !r.get('barcode3'))){
                Ext4.Msg.alert('Error', 'One or more readsets are missing barcodes.  Please either enter these or uncheck the option to demultiplex.');
                return false;
            }

            var sampleDate = r.get('sampledate');
            if (sampleDate){
                sampleDate = Ext4.Date.format(sampleDate, 'Y-m-d');
            }

            values['readset_' + sampleIdx] = {
                barcode5: r.get('barcode5'),
                barcode3: r.get('barcode3'),
                readset: r.get('readset'),
                readsetname: r.get('readsetname'),
                platform: r.get('platform'),
                application: r.get('application'),
                chemistry: r.get('chemistry'),
                concentration: r.get('concentration'),
                fragmentSize: r.get('fragmentSize'),
                librarytype: r.get('librarytype'),
                sampletype: r.get('sampletype'),
                subjectid: r.get('subjectid'),
                sampledate: sampleDate,
                comments: r.get('comments'),
                sampleid: r.get('sampleid'),
                instrument_run_id: r.get('instrument_run_id'),
                fileGroupId: r.get('fileGroupId')
            };
        }, this);

        for (var i in barcodes){
            values['barcode_'+i] = barcodes[i];
        }

        console.log(values);

        if (errors.getCount()){
            Ext4.Msg.alert('Error', 'There are ' + errors.getCount() + ' errors.  Please review the cells highlighted in red.  Note: you can hover over the cell for more information on the issue.');
            return false;
        }
    },

    onSubmit: function(btn){
        var ret = this.getJsonParams();
        if (!ret)
            return;

        this.startAnalysis(ret);
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
                store: this.fileNameStoreCopy,
                itemSelector:'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Id</td><td>Filename</td><td>Read Group (BAMs Only)</td><td>Folder</td><td></td></tr>',
                    '<tpl for=".">',
                        '<tr class="file_list">',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{dataId}</a></td>',
                        '<td ',
                            '<tpl if="error">style="background: red;" data-qtip="{error}"</tpl>',
                            '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{fileName:htmlEncode}</a></td>',
                        '<td>{[values.readgroup ? values.readgroup.platformUnit : ""]}</td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", null, {})]}" target="_blank">{containerPath:htmlEncode}</a></td>',
                        '<tpl if="dataId">',
                            '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {dataIds: values.dataId, cacheResults: false})]}" target="_blank">View FASTQC Report</a></td>',
                        '</tpl>',
                        '<tpl if="!dataId">',
                            '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {filenames: values.fileName, cacheResults: false})]}" target="_blank">View FASTQC Report</a></td>',
                        '</tpl>',
                        '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            },{
                html: '',
                itemId: 'totalFiles',
                border: false,
                style: 'padding-left: 5px;padding-top: 10px;'
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
                name: 'jobName',
                itemId: 'jobName',
                allowBlank:false,
                value: 'SequenceImport_'+ Ext4.Date.format(new Date(), 'Ymd'),
                maskRe: new RegExp('[A-Za-z0-9_]'),
                validator: function(val){
                    return (this.isValidProtocol === false ? 'Job Name Already In Use' : true);
                }
            },{
                fieldLabel: 'Run Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this protocol (optional)',
                itemId: 'runDescription',
                name: 'runDescription',
                allowBlank:true
            }, {
                xtype: 'ldk-linkbutton',
                style: 'margin-left: ' + (this.fieldDefaults.labelWidth + 4) + 'px;',
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
                            xtype: 'sequenceanalysis-sequenceimportsettingspanel',
                            border: false,
                            hidePageLoadWarning: false,
                            hideButtons: true
                        }],
                        buttons: SequenceAnalysis.panel.SequenceImportSettingsPanel.getButtons().concat([{
                            text: 'Cancel',
                            handler: function (btn) {
                                btn.up('window').close();
                            }
                        }])
                    }).show();
                }
            }]
        }
    },

    initFiles: function(){
        this.fileNameStore = Ext4.create('Ext.data.Store', {
            fields: ['id', 'displayName', 'fileName', 'filePath', 'relPath', 'basename', 'container', 'containerPath', 'dataId', 'readgroup', 'error', 'uses', 'info']
        });

        this.fileNameStoreCopy = Ext4.create('Ext.data.Store', {
            model: this.fileNameStore.model
        });

        this.fileNames.sort();
        Ext4.Msg.wait('Loading...');
        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Ajax.request, {
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'validateReadsetFiles'),
            params: {
                path: LABKEY.ActionURL.getParameter('path'),
                fileNames: this.fileNames,
                fileIds: this.fileIds
            },
            scope: this,
            success: this.onFileLoad,
            failure: LDK.Utils.getErrorCallback()
        });

        multi.add(LABKEY.Ajax.request, {
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getSequenceImportDefaults'),
            scope: this,
            success: function(response){
                LDK.Utils.decodeHttpResponseJson(response);
                if (response.responseJSON){
                    this.configDefaults = response.responseJSON;
                    for (var name in this.configDefaults){
                        var item = this.down('component[name=' + name + ']');
                        LDK.Assert.assertNotEmpty('Unable to find SequenceImportField with name: ' + name, item);
                        if (item){
                            item.setValue(this.configDefaults[name]);
                        }
                    }
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        multi.add(LABKEY.Ajax.request, {
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getResourceSettingsJson'),
            scope: this,
            success: function(response){
                LDK.Utils.decodeHttpResponseJson(response);
                if (response.responseJSON){
                    var json = response.responseJSON;
                    var cfg = this.getJobResourcesCfg(response.responseJSON);
                    if (cfg) {
                        cfg.title = 'Step 4: Job Resources (optional)';
                        var idx = this.items.length - 1;
                        this.insert(idx, cfg);
                    }
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        multi.send(this.onSendComplete, this);
    },

    getJobResourcesCfg: function(results){
        if (results.resourceSettings){
            return {
                xtype: 'sequenceanalysis-analysissectionpanel',
                title: 'Job Resources',
                stepType: 'resourceSettings',
                sectionDescription: '',
                toolConfig: results,
                toolIdx: 0
            }
        }

        return null;
    },

    onSendComplete: function(){
        if (this.fileLoadSuccess) {
            var warnings = this.populateSamples();

            Ext4.Msg.hide();

            if (warnings && warnings.length) {
                warnings = Ext4.unique(warnings);
                Ext4.Msg.alert('Warning', warnings.join('<br>'));
            }
        }
    },

    onFileLoad: function(response){
        LDK.Utils.decodeHttpResponseJson(response);

        if (response.responseJSON.validationErrors.length){
            response.responseJSON.validationErrors = Ext4.unique(response.responseJSON.validationErrors);
            Ext4.Msg.hide();
            Ext4.Msg.alert('Error', 'There are errors with the input files.  Please backup, remove these files, and then retry import:<br>' + response.responseJSON.validationErrors.join('<br>'));
            return;
        }

        Ext4.each(response.responseJSON.fileInfo, function(f){
            f.id = LABKEY.Utils.generateUUID();
            f.displayName = f.fileName;

            if (f.readgroups && f.readgroups.length){
                Ext4.Array.forEach(f.readgroups, function(rg){
                    var vals = {
                        id: LABKEY.Utils.generateUUID(),
                        filePath: f.filePath,
                        displayName: f.fileName + ' (Read Group: ' + (rg.readGroupId || rg.id) + ')',
                        fileName: f.fileName,
                        relPath: f.relPath,
                        dataId: f.dataId,
                        container: f.container,
                        containerPath: f.containerPath,
                        basename: f.basename,
                        error: f.error,
                        readgroup: rg
                    };

                    this.fileNameStore.add(this.fileNameStore.createModel(vals));
                    this.fileNameStoreCopy.add(this.fileNameStoreCopy.createModel(vals));
                }, this);
            }
            else {
                this.fileNameStore.add(this.fileNameStore.createModel(f));
                this.fileNameStoreCopy.add(this.fileNameStoreCopy.createModel(f));
            }
        }, this);

        this.fileNameStore.sort('displayName');
        this.fileNameStoreCopy.sort('displayName');

        this.down('#fileListView').refresh();
        this.down('#totalFiles').update('Total files: ' + this.fileNameStore.getCount());
        this.fileLoadSuccess = true;
    },

    getReadDataSection: function(){
        return {
            xtype: 'panel',
            width: '100%',
            title: 'Step 2: File/Lane Groups',
            items: [{
                html: 'In this step you tell the system how your sequence files go together.  The most common scenarios are either one file per group (single end data), or a pair of files per group (paired end).  However, in some cases a single library is spread across multiple lanes or the sequencer produced multiple files per lane, in which case you have more than 2 files of reads per group.  Why does this matter?  This information can be important for proper sample processing, such as marking PCR duplicates.  <a target="_blank" href="https://www.broadinstitute.org/gatk/guide/article?id=3059">Click here to view a good explanation from GATK</a>.  For the purposes of this system, each group of files is given an identifier (typically inferred from the filename).<br><br>NOTE: this will auto-populate with a best guess of how to group the files.  Most of this should be correct and you can proceed to step 3 without doing anything else.',
                style: 'padding-bottom: 10px;',
                border: false
            },{
                xtype: 'ldk-gridpanel',
                itemId: 'readDataGrid',
                features: [{
                    ftype: 'grouping'
                }],
                minHeight: 200,
                clicksToEdit: 1,
                border: true,
                store: this.readDataStore,
                readsetStore: this.readsetStore,
                fileNameStore: this.fileNameStore,
                columns: [{
                    text: 'File Group',
                    tdCls: 'ldk-wrap-text',
                    dataIndex: 'fileGroupId',
                    name: 'fileGroupId',
                    width: 180,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('fileGroupId'),
                    editor: {
                        xtype: 'textfield',
                        listeners: {
                            scope: this,
                            beforerender: function (field) {
                                var editor = field.up('editor');
                                LDK.Assert.assertNotEmpty('Unable to find editor for fileGroupField', editor);
                                if (editor){
                                    field.mon(editor, 'complete', function (editor, value, startValue) {
                                        if (value !== startValue) {
                                            editor.field.updateReadset.apply(editor.field, [editor.field, value, startValue]);
                                        }
                                    }, this);
                                }
                            }
                        },

                        updateReadset: function(field, val, oldValue){
                            var othersExist = false;
                            var record = field.up('grid').getPlugin('cellediting').activeRecord;
                            var readDataStore = field.up('grid').store;
                            var readsetStore = field.up('grid').readsetStore;

                            readDataStore.each(function (rec) {
                                if (record !== rec && rec.get('fileGroupId') === oldValue) {
                                    othersExist = true;
                                }
                            }, this);

                            if (!othersExist) {
                                readsetStore.each(function (rec) {
                                    if (rec.get('fileGroupId') === oldValue) {
                                        rec.data['fileGroupId'] = val;
                                    }
                                }, this);

                                var matches = [];
                                readsetStore.each(function (rec) {
                                    if (rec.get('fileGroupId') === val) {
                                        matches.push(rec);
                                    }
                                }, this);

                                if (matches.length > 1) {
                                    var matchMap = {};
                                    var doDeMultiplex = field.up('sequenceanalysis-sequenceimportpanel').down('#doDemultiplex').getValue();
                                    var toRemove = [];
                                    Ext4.Array.forEach(matches, function(r){
                                        var key = r.get('fileGroupId') + (doDeMultiplex ? '-' + (r.get('barcode5') || '') + '-' + (r.get('barcode3') || '') : '');
                                        if (!matchMap[key]) {
                                            matchMap[key] = true;
                                        }
                                        else {
                                            toRemove.push(r);
                                        }
                                    }, this);

                                    if (toRemove.length) {
                                        readsetStore.remove(toRemove);
                                    }
                                }
                            }
                        }
                    }
                },{
                    text: 'Forward Reads',
                    tdCls: 'ldk-wrap-text',
                    name: 'fileRecord1',
                    dataIndex: 'fileRecord1',
                    width: 300,
                    editor: {
                        xtype: 'labkey-combo',
                        //triggerAction: 'all',
                        displayField: 'displayName',
                        valueField: 'id',
                        allowBlank: true,
                        forceSelection: true,
                        typeAhead: true,
                        lazyRender: false,
                        queryMode: 'local',
                        store: this.fileNameStore
                    },
                    renderer: function(val, cellMetaData, record) {
                        SequenceAnalysis.panel.SequenceImportPanel.getRenderer('fileRecord1').apply(this, arguments);

                        var recId = record.get('fileRecord1');
                        var data = this.fileNameStore.snapshot || this.fileNameStore.data;
                        var fileDataRecIdx = data.findIndexBy(function(r){
                            return r.get('id') == recId;
                        });
                        var fileDataRec = data.getAt(fileDataRecIdx);
                        return fileDataRec ? fileDataRec.get('displayName') : 'No file';
                    }
                },{
                    text: 'Reverse Reads',
                    tdCls: 'ldk-wrap-text',
                    name: 'fileRecord2',
                    dataIndex: 'fileRecord2',
                    width: 300,
                    editor: {
                        xtype: 'labkey-combo',
                        //triggerAction: 'all',
                        displayField: 'displayName',
                        valueField: 'id',
                        allowBlank: true,
                        forceSelection: true,
                        typeAhead: true,
                        lazyRender: false,
                        queryMode: 'local',
                        store: this.fileNameStore
                    },
                    renderer: function(val, cellMetaData, record) {
                        SequenceAnalysis.panel.SequenceImportPanel.getRenderer('fileRecord2').apply(this, arguments);

                        var recId = record.get('fileRecord2');
                        var data = this.fileNameStore.snapshot || this.fileNameStore.data;
                        var fileDataRecIdx = data.findIndexBy(function(r){
                            return r.get('id') == recId;
                        });
                        var fileDataRec = this.fileNameStore.getAt(fileDataRecIdx);
                        return fileDataRec ? fileDataRec.get('displayName') : 'No file';
                    }
                },{
                    text: 'Platform Unit (Lane)',
                    tdCls: 'ldk-wrap-text',
                    name: 'platformUnit',
                    dataIndex: 'platformUnit',
                    width: 220,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('platformUnit'),
                    editor: {
                        xtype: 'textfield'
                    }
                },{
                    text: 'Center Name',
                    tdCls: 'ldk-wrap-text',
                    name: 'centerName',
                    dataIndex: 'centerName',
                    width: 200,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('centerName'),
                    editor: {
                        xtype: 'textfield'
                    }
                }],
                tbar: [{
                    text: 'Populate/Reorder Files',
                    tooltip: 'Click to reorder the files based on either name or best-guess based on Illumina filename conventions.',
                    scope: this,
                    menu: [{
                        text: 'Fill by Row (Paired End)',
                        style: 'margin-right: 5px;',
                        border: true,
                        handler: function(btn){
                            btn.up('sequenceanalysis-sequenceimportpanel').populateSamples('row', true);
                        }
                    },{
                        text: 'Fill by Column (Paired End)',
                        style: 'margin-right: 5px;',
                        border: true,
                        handler: function(btn){
                            btn.up('sequenceanalysis-sequenceimportpanel').populateSamples('column', true);
                        }
                    },{
                        text: 'Fill by Column (Single End)',
                        style: 'margin-right: 5px;',
                        border: true,
                        handler: function(btn){
                            btn.up('sequenceanalysis-sequenceimportpanel').populateSamples('column', false);
                        }
                    },{
                        text: 'Infer Based on Illumina Filename',
                        style: 'margin-right: 5px;',
                        border: true,
                        handler: function (btn) {
                            btn.up('sequenceanalysis-sequenceimportpanel').populateSamples('illumina', true);
                        }
                    },{
                        text: 'Infer Based on 10x Naming',
                        style: 'margin-right: 5px;',
                        border: true,
                        handler: function (btn) {
                            btn.up('sequenceanalysis-sequenceimportpanel').populateSamples('10x', true);
                        }
                    }]
                },{
                    text: 'Add',
                    handler : function(c){
                        var grid = c.up('grid');
                        var r = grid.getStore().createModel({});
                        grid.getStore().insert(0, [r]);

                        var plugin = grid.getPlugin('cellediting');
                        LDK.Assert.assertNotEmpty('Unable to find cellediting plugin', plugin);
                        plugin.startEditByPosition({row: 0, column: 0});

                        grid.getView().refresh();
                    }
                },{
                    text: 'Remove',
                    scope: this,
                    handler : function(btn){
                        Ext4.Msg.wait("Loading...");
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit( );
                        var s = grid.getSelectionModel().getSelection();

                        if (!s.length){
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Error', 'No records selected');
                            return;
                        }

                        for (var i = 0, r; r = s[i]; i++){
                            grid.getStore().remove(r);
                        }

                        grid.getView().refresh();
                        Ext4.Msg.hide();
                    }
                },{
                    text: 'Split/Regroup Selected',
                    scope: this,
                    handler : function(btn){
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit( );
                        var s = grid.getSelectionModel().getSelection();

                        if (!s.length) {
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Error', 'No records selected');
                            return;
                        }

                        var defaultVal = s[0].get('fileGroupId');
                        Ext4.Msg.prompt('Split/Regroup Files', 'This will separate the selected rows into their own group with the following name:', function(btn, val){
                            if (btn !== 'ok'){
                                return;
                            }

                            var originals = [];
                            for (var i = 0; i < s.length; i++){
                                var r = s[i];
                                if (r.get('fileGroupId'))
                                    originals.push(r.get('fileGroupId'));

                                r.set('fileGroupId', val);
                            }

                            //if there are no remaining records in the store with the original group, update any readsets that belonged to that group
                            originals = Ext4.unique(originals);
                            Ext4.Array.forEach(originals, function(name){
                                if (this.readDataStore.findExact('fileGroupId', name) === -1){
                                    this.readsetStore.each(function(rs){
                                        if (rs.get('fileGroupId') === name) {
                                            rs.set('fileGroupId', val);
                                        }
                                    }, this);

                                    var matches = [];
                                    this.readsetStore.each(function(rs){
                                        if (rs.get('fileGroupId') === val) {
                                            matches.push(rs);
                                        }
                                    }, this);

                                    if (matches.length > 1) {
                                        var matchMap = {};
                                        var doDeMultiplex = this.down('#doDemultiplex').getValue();
                                        var toRemove = [];
                                        Ext4.Array.forEach(matches, function(r){
                                            var key = r.get('fileGroupId') + (doDeMultiplex ? '-' + (r.get('barcode5') || '') + '-' + (r.get('barcode3') || '') : '');
                                            if (!matchMap[key]) {
                                                matchMap[key] = true;
                                            }
                                            else {
                                                toRemove.push(r);
                                            }
                                        }, this);

                                        if (toRemove.length) {
                                            this.readsetStore.remove(toRemove);
                                        }
                                    }
                                }
                            }, this);

                            grid.getView().refresh();
                        }, this, null, defaultVal);
                    }
                },{
                    text: 'Regroup By Regex (Advanced)',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit();
                        var s = grid.getSelectionModel().getSelection();
                        if (!s.length){
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Error', 'No records selected');
                            return;
                        }

                        Ext4.create('Ext.window.Window', {
                            title: 'Regroup Selected Records By RegExp',
                            bodyStyle: 'padding: 10px;',
                            width: 500,
                            items: [{
                                html: 'This helper allows you to provide a regular expression to extract the file group, and a second expression to extract platformUnit. Any file pairs with the same group will be imported as a single readset. Any files with the same non-null platform unit will be merged. Specifying different platform units can be important for read groups, and downstream operations like MarkDuplicates. Regular expressions will be tested against the first filename in each pair only. For each regex, if not match is found, the original group or platformUnit values is retained.',
                                border: false
                            },{
                                xtype: 'textfield',
                                fieldLabel: 'Group RegEx',
                                itemId: 'groupEx',
                                value: '(.*)_'
                            },{
                                xtype: 'ldk-integerfield',
                                fieldLabel: 'Group Index',
                                itemId: 'groupNum',
                                value: 1,
                                minValue: 0
                            },{
                                xtype: 'textfield',
                                fieldLabel: 'Platform Unit RegEx',
                                itemId: 'platformEx',
                                value: '(.*)_'
                            },{
                                xtype: 'ldk-integerfield',
                                fieldLabel: 'Platform Index',
                                itemId: 'platformNum',
                                value: 2,
                                minValue: 0
                            }],
                            buttons: [{
                                text: 'Test',
                                scope: this,
                                handler: function (btn) {
                                    var groupEx = btn.up('window').down('#groupEx').getValue();
                                    var groupNum = btn.up('window').down('#groupNum').getValue();

                                    var platformEx = btn.up('window').down('#platformEx').getValue();
                                    var platformNum = btn.up('window').down('#platformNum').getValue();

                                    if (groupEx) {
                                        groupEx = new RegExp(groupEx);
                                    }

                                    if (platformEx) {
                                        platformEx = new RegExp(platformEx);
                                    }

                                    var rows = [];
                                    Ext4.Array.forEach(s, function(record){
                                        var recId = record.get('fileRecord1');
                                        var data = this.fileNameStore.snapshot || this.fileNameStore.data;
                                        var fileDataRecIdx = data.findIndexBy(function(r){
                                            return r.get('id') === recId;
                                        });
                                        var fileDataRec = data.getAt(fileDataRecIdx);
                                        if (!fileDataRec) {
                                            return;
                                        }

                                        var val = fileDataRec.get('fileName');
                                        var row = [val, null, null, record];
                                        if (groupEx) {
                                            var m = val.match(groupEx);
                                            if (m && m.length) {
                                                if (groupNum && groupNum < m.length) {
                                                    row[1] = m[groupNum];
                                                }
                                            }
                                        }

                                        if (platformEx) {
                                            var m = val.match(platformEx);
                                            if (m && m.length) {
                                                if (platformNum && platformNum < m.length) {
                                                    row[2] = m[platformNum];
                                                }
                                            }
                                        }

                                        rows.push(row);
                                    }, this);

                                    rows.sort(function(a, b){
                                        if (a[1] > b[1]) return 1;
                                        if (b[1] > a[1]) return -1;

                                        return 0;
                                    });

                                    var dataItems = [{
                                        html: 'File'
                                    },{
                                        html: 'Updated Group'
                                    },{
                                        html: 'Updated Platform Unit'
                                    }];

                                    Ext4.Array.forEach(rows, function(r){
                                        dataItems.push({
                                            html: r[0]
                                        });

                                        dataItems.push({
                                            html: r[1] || ''
                                        });

                                        dataItems.push({
                                            html: r[2] || ''
                                        });
                                    }, this);
                                    Ext4.create('Ext.window.Window', {
                                        title: 'Preview',
                                        parentWindow: btn.up('window'),
                                        width: 800,
                                        bodyStyle: 'padding: 5px',
                                        defaults: {
                                            style: 'padding: 5px;',
                                            border: false
                                        },
                                        layout: {
                                            type: 'table',
                                            columns: 3
                                        },
                                        items: dataItems,
                                        buttons: [{
                                            text: 'Make Changes',
                                            scope: this,
                                            handler: function (btn) {
                                                var win = btn.up('window');
                                                var parentWindow = win.parentWindow;
                                                win.close();

                                                Ext4.Array.forEach(rows, function(row){
                                                    if (row[2]) {
                                                        row[3].set('platformUnit', row[2]);
                                                    }

                                                    if (row[1]) {
                                                        row[3].set('fileGroupId', row[1]);
                                                    }
                                                }, this);

                                                //clear/populate readsetStore
                                                var fileGroupIds = [];
                                                this.readDataStore.each(function(r){
                                                    fileGroupIds.push(r.get('fileGroupId'));
                                                }, this);
                                                fileGroupIds = Ext4.unique(fileGroupIds);

                                                this.readsetStore.removeAll();

                                                if (fileGroupIds.length){
                                                    var toAdd = [];
                                                    Ext4.Array.forEach(fileGroupIds, function(id){
                                                        toAdd.push(this.readsetStore.createModel({
                                                            fileGroupId: id
                                                        }));
                                                    }, this);

                                                    this.readsetStore.add(toAdd);
                                                }

                                                parentWindow.close();
                                            }
                                        },{
                                            text: 'Abort',
                                            scope: this,
                                            handler: function (btn) {
                                                btn.up('window').close();
                                            }
                                        }]

                                    }).show();
                                }
                            },{
                                text: 'Close',
                                scope: this,
                                handler: function (btn) {
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                },{
                    text: 'Add Missing Readsets',
                    scope: this,
                    handler : function(btn) {
                        var grid = btn.up('grid');

                        var fileGroupIds = [];
                        this.readDataStore.each(function(r){
                            fileGroupIds.push(r.get('fileGroupId'));
                        }, this);
                        fileGroupIds = Ext4.unique(fileGroupIds);

                        this.readsetStore.each(function(r){
                            Ext4.Array.remove(fileGroupIds, r.get('fileGroupId'));
                        }, this);

                        if (fileGroupIds.length){
                            var toAdd = [];
                            Ext4.Array.forEach(fileGroupIds, function(id){
                                toAdd.push(this.readsetStore.createModel({
                                    fileGroupId: id
                                }));
                            }, this);

                            this.readsetStore.add(toAdd);
                        }
                    }
                }]
            },{
                text: 'Force Refresh',
                handler : function(c){
                    var grid = c.up('grid');
                    grid.getView().refresh();
                    grid.doLayout();
                }
            }]
        }
    },

    updateForReadsetIds: function(readsetIds){
        var recordsById = {};
        this.readsetStore.each(function(r){
            if (readsetIds.indexOf(r.get('readset')) !== -1){
                recordsById[r.get('readset')] = recordsById[r.get('readset')] || [];
                recordsById[r.get('readset')].push(r);
            }
        }, this);

        Ext4.Msg.wait('Loading...');
        var doDemultiplex = this.down('#doDemultiplex').getValue();
        var showBarcodes = this.down('#showBarcodes').getValue();

        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_readsets',
            filterArray: [LABKEY.Filter.create('rowid', readsetIds.join(';'), LABKEY.Filter.Types.IN)],
            columns: 'rowid,name,platform,application,chemistry,concentration,fragmentSize,librarytype,sampletype,subjectid,sampledate,sampleid,comments,barcode5,barcode3,instrument_run_id,totalFiles',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();
                var msgs = [];
                if (results && results.rows && results.rows.length){
                    Ext4.Array.forEach(results.rows, function(row) {
                        var records = recordsById[row.rowid];
                        if (row.totalFiles) {
                            msgs.push('Readset ' + row.rowid + 'has already been associated with files and cannot be re-used.  If you would like to reanalyze this readset, load the table of readsets and look for the \'Analyze Data\' button.');
                            Ext4.Array.forEach(records, function(record) {
                                record.data.readset = null;
                            }, this);
                        }

                        if (doDemultiplex && (!row.barcode3 && !row.barcode5)) {
                            msgs.push('Readset ' + row.rowid + ' does not have barcodes, but you have selected to use barcodes');
                            Ext4.Array.forEach(records, function(record) {
                                record.data.readset = null;
                            }, this);

                            return;
                        }
                        else if (!doDemultiplex && !showBarcodes && (row.barcode3 || row.barcode5)) {
                            msgs.push('Readset ' + row.rowid + ' has barcodes, but you have not selected to either show barcodes or perform demultiplexing');
                            Ext4.Array.forEach(records, function(record) {
                                record.data.readset = null;
                            }, this);

                            return;
                        }

                        //update row based on saved readset.  avoid firing event
                        Ext4.Array.forEach(records, function(record) {
                            Ext4.apply(record.data, {
                                readsetname: row.name,
                                platform: row.platform,
                                application: row.application,
                                chemistry: row.chemistry,
                                concentration: row.concentration,
                                fragmentSize: row.fragmentSize,
                                librarytype: row.librarytype,
                                sampleid: row.sampleid,
                                subjectid: row.subjectid,
                                sampledate: row.sampledate,
                                comments: row.comments,
                                sampletype: row.sampletype,
                                instrument_run_id: row.instrument_run_id,
                                barcode5: row.barcode5,
                                barcode3: row.barcode3,
                                isValid: true
                            });
                        }, this);

                        delete recordsById[row.rowid];
                    }, this);
                }

                for (var readsetId in recordsById){
                    msgs.push('Unable to find readset with rowid: ' +  readsetId);
                    //deliberately dont fire event.  will refresh below.
                    Ext4.Array.forEach(recordsById[readsetId], function(record){
                        Ext4.apply(record.data, {
                            readset: null,
                            readsetname: null,
                            platform: null,
                            application: null,
                            chemistry: null,
                            concentration: null,
                            fragmentSize: null,
                            librarytype: null,
                            sampleid: null,
                            subjectid: null,
                            sampledate: null,
                            comments: null,
                            sampletype: null,
                            instrument_run_id: null,
                            barcode5: null,
                            barcode3: null,
                            isValid: false
                        });
                    }, this);
                }

                if (msgs.length){
                    msgs = Ext4.unique(msgs);
                    Ext4.Msg.alert('Error', 'The selected readset cannot be used:<br>' + msgs.join('<br>'));
                }

                this.down('#readsetGrid').getView().refresh();
            }
        });
    },

    getReadsetSection: function(){
        return {
            xtype: 'panel',
            width: '100%',
            title: 'Step 3: Sample/Readset Information',
            items: [{
                html: 'In this step you need to add the sample information for each file group defined above.',
                style: 'padding-bottom: 10px;',
                border: false
            },{
                fieldLabel: 'Demultiplex Data',
                helpPopup: 'Use this option if your sequences have been tagged using an MID tag (molecular barcode) and you need the system to demultiplex them at time of import.  Use the link below to view available barcodes or add new sequences.',
                name: 'inputfile.barcode',
                xtype:'checkbox',
                itemId: 'doDemultiplex',
                scope: this,
                handler: function(btn, val){
                    var treatmentField = btn.up('form').down('#originalFileTreatment');
                    treatmentField.setValue(val ? 'compress' : 'delete');

                    var showBarcodes = btn.up('form').down('#showBarcodes');
                    if (val) {
                        showBarcodes.suspendEvents(false);
                        showBarcodes.setValue(false);
                        showBarcodes.resumeEvents();
                    }

                    btn.up('form').down('#barcodeOptions').setVisible(btn.checked);
                    this.fireEvent('midchange', btn, (val || showBarcodes.getValue()));
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'barcodeOptions',
                fieldDefaults: {
                    width: 350
                },
                items: [{
                    xtype: 'labkey-combo',
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
                    fieldLabel: 'Additional Barcodes To Test',
                    name: 'inputfile.barcodeGroups',
                    itemId: 'scanAllBarcodes',
                    helpPopup: 'Samples will be checked for all barcodes in the selected groups, at both 5\' and 3\' ends.  A summary report will be created, but no FASTQ files will be made for combinations not associated with a sample.  This is sometimes useful to be sure no contaminants are in your sample.'
                },{
                    xtype: 'ldk-numberfield',
                    fieldLabel: 'Mismatches Tolerated',
                    name: 'inputfile.barcodeEditDistance',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'When identifying barcodes, up to the following number of mismatches will be tolerated.  Note: if too lax, multiple barcodes will be detected and the sample discarded'
                },{
                    xtype: 'ldk-numberfield',
                    fieldLabel: 'Deletions Tolerated',
                    name: 'inputfile.barcodeDeletions',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'If provided, the barcode can have up to this many deletions from the outer end of the barcode.  This allows partial matches if a barcode was clipped by quality trimming or poor sequence.  If a matching barcode is found without deletions, it will be preferentially used.'
                },{
                    xtype: 'ldk-numberfield',
                    fieldLabel: 'Allowed Distance From Read End',
                    name: 'inputfile.barcodeOffset',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'If provided, the barcode can be located up to this many bases from the end of the read'
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Barcodes Are In Read Header',
                    name: 'inputfile.barcodesInReadHeader',
                    value: 0,
                    minValue: 0,
                    helpPopup: 'In some cases, the sequencer has already parsed the sequence reads for barcodes and placed this information in the read header.  If true, check this.  Otherwise we will inspect the read sequence itself for the barcodes (the default).'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'View Available Barcodes',
                    linkTarget: '_blank',
                    linkCls: 'labkey-text-link',
                    style: 'margin-left: 210px;',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'barcodes'})
                }]
            },{
                fieldLabel: 'Store Barcode/Index Without Demultiplexing',
                helpPopup: 'Use this option if your sequences were originally barcoded, but have already been demutiplexed.  This will store the information in the database, but the system will not demultiplex files.',
                itemId: 'showBarcodes',
                xtype: 'checkbox',
                scope: this,
                handler: function(btn, val){
                    var doDemultiplex = btn.up('form').down('#doDemultiplex');
                    if (val){
                        doDemultiplex.suspendEvents(false);
                        doDemultiplex.setValue(!val);
                        doDemultiplex.resumeEvents();

                        btn.up('form').down('#barcodeOptions').setVisible(!val);
                    }

                    this.fireEvent('midchange', btn, (val || doDemultiplex.getValue()));
                }
            },{
                xtype: 'checkbox',
                itemId: 'importReadsetIds',
                fieldLabel: 'Use Previously Uploaded Readsets (advanced)',
                helpPopup: 'If checked, rather than supply all metadata here, you will simply enter the readset ID of previously imported readsets.  The previously imported values will be used.',
                scope: this,
                handler: function(btn, val){
                    btn.up('form').down('#existingReadsetOptions').setVisible(btn.checked);

                    var grid = btn.up('panel').down('ldk-gridpanel');
                    Ext4.Array.forEach(grid.columns, function(c){
                        if (c.dataIndex === 'readset'){
                            c.setVisible(val);
                        }
                    }, this);
                    grid.reconfigure();

                    grid.down('#inferFromName').setVisible(btn.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'existingReadsetOptions',
                fieldDefaults: {
                    width: 350
                },
                items: [{
                    html: 'It is possible to import readset information before you import the actual read data.  This is most commonly done when you plan a run upfront (such as the Illumina workflow).  If you did this, just enter the readset Id below and the details will automatically populate.  Note: when this option is selected the readset details are not editable through this form.',
                    border: false
                }]
            },{
                xtype: 'ldk-gridpanel',
                cls: 'ldk-grid',
                itemId: 'readsetGrid',
                minHeight: 200,
                clicksToEdit: 1,
                store: this.readsetStore,
                border: true,
                stripeRows: true,
                getEditingPlugin: function(){
                    return {
                        ptype: 'ldk-cellediting',
                        pluginId: 'cellediting',
                        clicksToEdit: 1,
                        listeners: {
                            beforeedit: function(cell, object){
                                var useReadset = this.getCmp().up('sequenceanalysis-sequenceimportpanel').down('#importReadsetIds').getValue();
                                if (useReadset && object.field !== 'readset' && object.field !== 'fileGroupId'){
                                    //alert('This sample is using a readset that was created previously.  You cannot edit ' + object.column.text + ' for this readset.  If you wish to update that readset, click the View/Edit Readsets link above.');
                                    return false;
                                }
                            }
                        }
                    }
                },
                columns: [{
                    text: 'File Group',
                    tdCls: 'ldk-wrap-text',
                    name: 'fileGroupId',
                    dataIndex: 'fileGroupId',
                    width: 180,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('fileGroupId'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: false,
                        editable: true,
                        queryMode: 'local',
                        triggerAction: 'all',
                        forceSelection: true,
                        typeAhead: true,
                        displayField: 'fileGroupId',
                        valueField: 'fileGroupId',
                        lazyRender: false,
                        store: this.fileGroupStore
                    }
                },{
                    text: 'Readset Id',
                    tdCls: 'ldk-wrap-text',
                    name: 'readset',
                    hidden: true,
                    dataIndex: 'readset',
                    width: 90,
                    editable: true,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('readset'),
                    editor: {
                        xtype: 'ldk-numberfield',
                        minValue: 1,
                        allowBlank: true,
                        listeners: {
                            buffer: 200,
                            scope: this,
                            beforerender: function (field) {
                                var editor = field.up('editor');
                                LDK.Assert.assertNotEmpty('Unable to find editor for readsetId', editor);
                                if (editor){
                                    field.mon(editor, 'complete', function (editor, value, startValue) {
                                        if (value !== startValue) {
                                            if (value){
                                                this.updateForReadsetIds([field.getValue()]);
                                            }
                                            else {
                                                var records = editor.grid.getSelectionModel().getSelection();
                                                LDK.Assert.assertTrue('Unable to find record from editor for readsetId', records && records.length);
                                                if (records && records.length) {
                                                    records[0].set({
                                                        readset: null,
                                                        readsetname: null,
                                                        platform: null,
                                                        application: null,
                                                        chemistry: null,
                                                        concentration: null,
                                                        fragmentSize: null,
                                                        librarytype: null,
                                                        sampleid: null,
                                                        subjectid: null,
                                                        sampledate: null,
                                                        comments: null,
                                                        sampletype: null,
                                                        instrument_run_id: null,
                                                        barcode5: null,
                                                        barcode3: null,
                                                        isValid: false
                                                    });
                                                }
                                            }
                                        }
                                    }, this);
                                }
                            }
                        }
                    }
                },{
                    text: '5\' Barcode',
                    tdCls: 'ldk-wrap-text',
                    id: 'barcode5',
                    dataIndex: 'barcode5',
                    mode: 'local',
                    width: 90,
                    hidden: true,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('barcode5'),
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
                            autoLoad: true
                        })
                    }
                },{
                    text: '3\' Barcode',
                    tdCls: 'ldk-wrap-text',
                    id: 'barcode3',
                    dataIndex: 'barcode3',
                    mode: 'local',
                    width: 90,
                    hidden: true,
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('barcode3'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: true,
                        editable: true,
                        triggerAction: 'all',
                        forceSelection: true,
                        typeAhead: true,
                        displayField: 'tag_name',
                        valueField: 'tag_name',
                        queryMode: 'local',
                        store:  Ext4.create('LABKEY.ext4.data.Store', {
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'sequenceanalysis',
                            queryName: 'barcodes',
                            sort: 'group_name,tag_name',
                            autoLoad: true
                        })
                    }
                },{
                    text: 'Readset Name',
                    tdCls: 'ldk-wrap-text',
                    name: 'readsetname',
                    width: 180,
                    mode: 'local',
                    dataIndex: 'readsetname',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('readsetname'),
                    editor: {
                        xtype: 'textfield',
                        allowBlank: false
                    }
                },{
                    text: 'Platform',
                    tdCls: 'ldk-wrap-text',
                    name: 'platform',
                    width: 170,
                    mode: 'local',
                    dataIndex: 'platform',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('platform'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: false,
                        forceSelection: true,
                        displayField: 'platform',
                        valueField: 'platform',
                        queryMode: 'local',
                        editable: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
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
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('application'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: false,
                        forceSelection: true,
                        displayField: 'application',
                        valueField: 'application',
                        plugins: ['ldk-usereditablecombo'],
                        queryMode: 'local',
                        editable: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'sequenceanalysis',
                            queryName: 'sequence_applications',
                            autoLoad: true
                        })
                    }
                },{
                    text: 'Chemistry',
                    tdCls: 'ldk-wrap-text',
                    name: 'chemistry',
                    width: 180,
                    mode: 'local',
                    dataIndex: 'chemistry',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('chemistry'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: false,
                        forceSelection: true,
                        displayField: 'chemistry',
                        valueField: 'chemistry',
                        plugins: ['ldk-usereditablecombo'],
                        queryMode: 'local',
                        editable: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'sequenceanalysis',
                            queryName: 'sequence_chemistries',
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
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('librarytype'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: true,
                        forceSelection: true,
                        displayField: 'type',
                        valueField: 'type',
                        plugins: ['ldk-usereditablecombo'],
                        editable: true,
                        queryMode: 'local',
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
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
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('sampletype'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: true,
                        forceSelection: true,
                        queryMode: 'local',
                        displayField: 'type',
                        valueField: 'type',
                        plugins: ['ldk-usereditablecombo'],
                        editable: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'laboratory',
                            queryName: 'sample_type',
                            autoLoad: true
                        })
                    }
                },{
                    text: 'Subject Id',
                    tdCls: 'ldk-wrap-text',
                    name: 'subjectid',
                    width: 100,
                    dataIndex: 'subjectid',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('subjectid'),
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
                    text: 'Concentration',
                    tdCls: 'ldk-wrap-text',
                    name: 'concentration',
                    width: 70,
                    dataIndex: 'concentration',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('concentration'),
                    editor: {
                        xtype: 'ldk-numberfield',
                        allowBlank: true,
                        editable: true,
                        minValue: 0
                    }
                },{
                    text: 'Fragment Size',
                    tdCls: 'ldk-wrap-text',
                    name: 'fragmentSize',
                    width: 70,
                    dataIndex: 'fragmentSize',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('fragmentSize'),
                    editor: {
                        xtype: 'ldk-numberfield',
                        allowBlank: true,
                        editable: true,
                        minValue: 0
                    }
                },{
                    text: 'Comments/Description',
                    tdCls: 'ldk-wrap-text',
                    name: 'comments',
                    width: 180,
                    dataIndex: 'comments',
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('comments'),
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
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('sampleid'),
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
                    renderer: SequenceAnalysis.panel.SequenceImportPanel.getRenderer('instrument_run_id'),
                    editor: {
                        xtype: 'labkey-combo',
                        allowBlank: true,
                        displayField: 'name',
                        valueField: 'rowid',
                        editable: false,
                        queryMode: 'local',
                        showValueInList: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            type: 'labkey-store',
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            schemaName: 'sequenceanalysis',
                            queryName: 'instrument_runs',
                            autoLoad: true
                        })
                    }
                }],
                tbar: [{
                    text: 'Add',
                    handler : function(c){
                        var grid = c.up('grid');
                        var r = grid.getStore().createModel({});
                        grid.getStore().insert(0, [r]);

                        var plugin = grid.getPlugin('cellediting');
                        LDK.Assert.assertNotEmpty('Unable to find cellediting plugin', plugin);
                        plugin.startEditByPosition({row: 0, column: 0});
                    }
                },{
                    text: 'Remove',
                    scope: this,
                    handler : function(btn){
                        Ext4.Msg.wait("Loading...");
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit( );
                        var s = grid.getSelectionModel().getSelection();
                        if (!s.length){
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Error', 'No records selected');
                            return;
                        }

                        for (var i = 0, r; r = s[i]; i++){
                            grid.getStore().remove(r);
                        }

                        Ext4.Msg.hide();
                    }
                },{
                    text: 'Apply Metadata From Spreadsheet',
                    tooltip: 'Click this to populate the rows of the grid using a spreadsheet',
                    itemId: 'addBatchBtn',
                    scope: this,
                    handler: function(btn){
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit();
                        grid.up('sequenceanalysis-sequenceimportpanel').showExcelImportWin(btn);
                    }
                },{
                    text: 'Bulk Edit',
                    tooltip: 'Click this to change values on all checked rows in bulk',
                    scope: this,
                    handler : function(btn){
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit();
                        var s = grid.getSelectionModel().getSelection();

                        //select all
                        if (!s.length){
                            grid.getSelectionModel().selectAll();
                        }

                        Ext4.create('LDK.window.GridBulkEditWindow', {
                            targetGrid: grid
                        }).show(btn);
                    }
                }, {
                    text: 'Assign To Instrument Run',
                    tooltip: 'Click to assign the selected rows to an existing or new instrument run',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit();
                        var s = grid.getSelectionModel().getSelection();

                        //select all
                        if (!s.length) {
                            grid.getSelectionModel().selectAll();
                            s = grid.getSelectionModel().getSelection();
                        }

                        Ext4.create('SequenceAnalysis.window.InstrumentRunWindow', {
                            targetGrid: grid,
                            selectedRows: s
                        }).show();
                    }
                },{
                    text: 'Infer Readset From File Name',
                    tooltip: 'If the file groups contain the ID of an existing readset, this can help automatically infer it',
                    scope: this,
                    itemId: 'inferFromName',
                    hidden: true,
                    handler: function(btn){
                        var grid = btn.up('grid');
                        grid.getPlugin('cellediting').completeEdit();
                        var s = grid.getSelectionModel().getSelection();

                        //select all
                        if (!s.length) {
                            grid.getSelectionModel().selectAll();
                            s = grid.getSelectionModel().getSelection();
                        }

                        Ext4.create('Ext.window.Window', {
                            sequencePanel: this,
                            targetGrid: grid,
                            readsetStore: this.readsetStore,
                            title: 'Infer Readset Id',
                            bodyStyle: 'padding: 5px;',
                            width: 500,
                            items: [{
                                html: 'If the file groups contain the readset ID within them, this can be parsed to automatically connect these to existing records.  Currently this only supports file groups beginning with readset ID or with a consistent prefix (i.e. s_).  You can enter a delimiter below and all text from the beginning through this delimiter will be used as the readset ID.',
                                style: 'padding-bottom: 10px;',
                                border: false
                            },{
                                xtype: 'textfield',
                                fieldLabel: 'Prefix',
                                helpPopup: 'This prefix will be dropped from the beginning of all sample/file names',
                                value: 's_',
                                itemId: 'prefix'
                            },{
                                xtype: 'textfield',
                                fieldLabel: 'Delimiter',
                                value: '_',
                                itemId: 'delimiter'
                            }],
                            buttons: [{
                                text: 'Submit',
                                handler: function (btn) {
                                    var prefix = btn.up('window').down('#prefix').getValue();
                                    var delim = btn.up('window').down('#delimiter').getValue();
                                    if (!delim){
                                        Ext4.Msg.alert('Error', 'Must enter a delimiter');
                                        return;
                                    }

                                    var readsetsToUpdate = [];
                                    Ext4.Array.forEach(btn.up('window').targetGrid.getSelectionModel().getSelection(), function(r){
                                        var fg = r.get('fileGroupId');
                                        if (prefix) {
                                            fg = fg.replace(new RegExp('^' + prefix), '');
                                        }
                                        fg = fg.split(delim);
                                        var id = fg[0];
                                        if (Ext4.isNumeric(id)) {
                                            r.set('readset', id);
                                            readsetsToUpdate.push(id);
                                        }
                                    }, this);

                                    if (readsetsToUpdate.length) {
                                        btn.up('window').sequencePanel.updateForReadsetIds(readsetsToUpdate);
                                    }

                                    btn.up('window').close();
                                }
                            },{
                                text: 'Cancel',
                                handler: function(btn){
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                }]
            }]
        };
    },

    showExcelImportWin: function(btn){
        Ext4.create('Ext.window.Window', {
            readsetStore: this.readsetStore,
            sequencePanel: this,
            width: 800,
            modal: true,
            closeAction: 'destroy',
            scope: this,
            title: 'Add Readsets',
            items: [{
                border: false,
                bodyStyle:'padding:5px',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'This allows you to add readsets from an excel file.  Just download the template using the link below, fill it out, then cut/paste it into the box below.  Note that all fields are case-sensitive and the import will fail if the case does not match.',
                    style: 'padding-bottom: 10px;'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Excel Template',
                    linkPrefix: '[',
                    linkSuffix: ']',
                    scope: this,
                    handler: function(btn){
                        var header = [];
                        var dataIndexes = [];
                        if (btn.up('window').sequencePanel.down('#importReadsetIds').getValue()){
                            header.push('File Group');
                            dataIndexes.push('fileGroupId');

                            header.push('Readset Id');
                            dataIndexes.push('readset');
                        }
                        else {
                            Ext4.each(this.down('#readsetGrid').columns, function (col) {
                                if (!col.hidden && col.dataIndex !== 'readset') {
                                    header.push(col.text);
                                    dataIndexes.push(col.dataIndex);
                                }
                            }, this);
                        }

                        var data = [header];
                        this.readsetStore.each(function(rec){
                            var row = [];
                            Ext4.Array.forEach(dataIndexes, function(di){
                                row.push(Ext4.isEmpty(rec.get(di)) ? null : rec.get(di));
                            }, this);

                            data.push(row);
                        }, this);

                        LABKEY.Utils.convertToExcel({
                            fileName : 'ReadsetImport_' + Ext4.util.Format.date(new Date(), 'Y-m-d H_i_s') + '.xls',
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
                    width: 775
                }]
            }],
            buttons: [{
                text:'Submit',
                disabled:false,
                formBind: true,
                itemId: 'submit',
                scope: this,
                handler: function(btn){
                    var win = btn.up('window');
                    Ext4.Msg.confirm('Import Rows', 'This will remove all existing rows and replace them with the rows you pasted into the textarea.  Continue?', function(val){
                        if (val === 'yes'){
                            win.processExcel(win);
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
            }],
            processExcel: function(win){
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
                var cols = this.sequencePanel.down('#readsetGrid').columns;
                Ext4.each(data[0], function(field){
                    if (!field)
                        return;

                    var found = false;
                    Ext4.each(cols, function(col, idx){
                        if (col.name === field || col.text === field || col.dataIndex.toLowerCase() === field.toLowerCase()){
                            columns.push(col);
                            found = true;
                            return false;
                        }
                    }, this);

                    if (!found){
                        errors.push('Unable to find column: ' + field);
                    }
                }, this);
                data.shift();

                if (errors.length){
                    alert(errors.join('\n'));
                    return;
                }

                var toAdd = [];
                var readsetsToUpdate = [];
                Ext4.each(data, function(row){
                    var obj = {};
                    Ext4.each(columns, function(col, idx){
                        if (col.hidden === true){
                            errors.push('The column: ' + col.text + ' cannot be set.');
                            return;
                        }

                        var value = row[idx];

                        if (col.editor){
                            var editor = Ext4.ComponentManager.create(col.editor);

                            if (editor.store && !Ext4.isEmpty(value)){
                                var recIdx = editor.store.find(editor.valueField, value, null, false, false, true);
                                if (recIdx === -1){
                                    errors.push('Invalid value for field ' + col.text + ': ' + value);
                                }
                                else {
                                    //ensure correct case
                                    value = editor.store.getAt(recIdx).get(col.editor.valueField);
                                }
                            }
                        }

                        if (!Ext4.isEmpty(value)){
                            //Dates for format YYYY-MM-DD not always parsed properly through Ext4 (assumed to be GMT; resulting in 1 day added for some time zones).
                            var type = this.readsetStore.model.prototype.fields.get(col.dataIndex).type;
                            if (type === Ext4.data.Types.DATE){
                                value = LDK.ConvertUtils.parseDate(value);
                            }

                            obj[col.dataIndex] = value;
                        }
                    }, this);

                    if (!LABKEY.Utils.isEmptyObj(obj)) {
                        toAdd.push(this.readsetStore.createModel(obj));
                    }

                    if (obj.readset){
                        readsetsToUpdate.push(obj.readset);
                    }
                }, this);

                if (errors.length){
                    errors = Ext4.unique(errors);
                    Ext4.Msg.alert('Error', errors.join('<br>'));
                    return;
                }

                if (!toAdd.length){
                    Ext4.Msg.alert('Error', 'No rows to add');
                    return;
                }

                this.readsetStore.removeAll();
                this.readsetStore.add(toAdd);

                this.readsetStore.validateAll();

                if (readsetsToUpdate.length){
                    this.sequencePanel.updateForReadsetIds(readsetsToUpdate);
                }
            }
        }).show(btn);
    },

    updateColWidth: function(){
        var readsetGrid = this.down('#readsetGrid');
        readsetGrid.reconfigure();

        var width = 60;
        Ext4.Array.forEach(readsetGrid.columns, function(c){
            if (c.isVisible()){
                width += c.getWidth();
            }
        }, this);

        this.doResize(width);
    },

    onMidChange: function(c, v){
        var changed = false;
        Ext4.each(this.down('#readsetGrid').columns, function(col){
            if (col.dataIndex === 'barcode5' || col.dataIndex === 'barcode3'){
                col.setVisible(v);
                if (col.isVisible() !== v)
                    changed = true;
            }
        }, this);

        if (!v){
            this.readsetStore.each(function(r){
                r.set('barcode5', null);
                r.set('barcode3', null);
            });
        }

        if (changed){
            this.updateColWidth();
        }
        else {
            this.down('#readsetGrid').reconfigure();
        }

        this.readsetStore.validateAll();
    }
});

//see bug:
//https://www.sencha.com/forum/showthread.php?265078-Broken-contracts-of-getAt-indexOf-methods-of-the-Ext.grid.feature.Grouping
Ext4.define('App.overrides.view.Table', {
    override: 'Ext.view.Table',

    getRecord: function (node) {
        node = this.getNode(node);
        if (node) {
            //var recordIndex = node.getAttribute('data-recordIndex');
            //if (recordIndex) {
            //    recordIndex = parseInt(recordIndex, 10);
            //    if (recordIndex > -1) {
            //        // The index is the index in the original Store, not in a GroupStore
            //        // The Grouping Feature increments the index to skip over unrendered records in collapsed groups
            //        return this.store.data.getAt(recordIndex);
            //    }
            //}
            return this.dataSource.data.get(node.getAttribute('data-recordId'));
        }
    },


    indexInStore: function (node) {
        node = this.getNode(node, true);
        if (!node && node !== 0) {
            return -1;
        }
        //var recordIndex = node.getAttribute('data-recordIndex');
        //if (recordIndex) {
        //    return parseInt(recordIndex, 10);
        //}
        return this.dataSource.indexOf(this.getRecord(node));
    }
});