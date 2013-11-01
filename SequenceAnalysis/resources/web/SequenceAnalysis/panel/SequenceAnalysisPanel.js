/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.SequenceAnalysisPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',

    initComponent: function(){
        this.taskId = 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline';
        this.readsets = this.readsets ? this.readsets.split(';') : [];

        Ext4.apply(this, {
            buttons: [{
                text: 'Start Analysis',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.add([
            this.getFilePanelCfg()
        ,{
            xtype:'form',
            border: true,
            bodyBorder: false,
            title: 'Step 1: Run Information',
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
                value: 'SequenceAnalysis_'+new Date().format('Ymd'),
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

//                },{
//                    xtype: 'combo',
//                    name: 'protocol',
//                    itemId: 'protocol',
//                    store: this.protocolStore,
//                    listeners: {
//                        scope: this,
//                        select: function(field){
//                            this.setFieldValues(this.protocols[field.getValue()].jsonParameters);
//                        }
//                    },
//                    forceSelection:false,
//                    typeAhead: true,
//                    lazyInit: false,
//                    queryMode: 'local',
//                    triggerAction: 'all',
//                    displayField: 'protocol',
//                    fieldLabel: 'Select Defaults',
//                    helpPopup: 'Select from a set of saved parameters'
                },{
                    fieldLabel: 'Description',
                    xtype: 'textarea',
                    width: 600,
                    height: 100,
                    helpPopup: 'Description for this analysis (optional)',
                    itemId: 'analysisDescription',
                    name: 'analysisDescription',
                    allowBlank:true
                },{
                    fieldLabel: 'Save Settings',
                    hidden: true,
                    helpPopup: 'Check to save these settings for future use.  They will appear under the \'defaults\' menu',
                    name: 'saveProtocol',
                    itemId: 'saveProtocol',
                    xtype: 'checkbox'
                },{
                    xtype: 'combo',
                    fieldLabel: 'Specialized Analysis',
                    helpPopup: 'Selecting one of these pre-defined analyses will set the form with recommended defaults for that application.  This is not required.',
                    displayField: 'name',
                    valueField: 'value',
                    name: 'analysisType',
                    value: 'Virus',
                    itemId: 'analysisType',
                    width: 600,
                    //TODO: data driven?
                    store: Ext4.create('Ext.data.ArrayStore', {
                        fields: ['name', 'value'],
                        data: [
                            ['[none]',''],
                            ['Sequence Based Genotyping','SBT'],
                            ['Viral Analysis','Virus']
                        ]
                    }),
                    listeners: {
                        scope: this,
                        change: function(field, val){
                            //val = val[0].get('value');

                            var alignPanel = this.down('#alignment');
                            var alignField = alignPanel.down('#alignerField');

                            var reportPanel = this.down('#reportPanel');
                            var databasePanel = this.down('#databasePanel');

                            if(val == 'SBT'){
                                alignPanel.down('#doAlignment').setValue(true);
                                alignPanel.down('#refLibraryCombo').setValue('DNA');
                                alignField.setValue('mosaik');
                                alignPanel.down('#alignerpanel').setAligner(alignField);

                                reportPanel.down('#sbtAnalysis').setValue(true);
                                reportPanel.down('#ntCoverage').setValue(false);
                                reportPanel.down('#aaSnpByCodon').setValue(false);
                                reportPanel.down('#ntSnpByPosition').setValue(false);
                            }
                            else if (val == 'Virus'){
                                alignPanel.down('#doAlignment').setValue(true);
                                alignPanel.down('#refLibraryCombo').setValue('Virus');
                                alignPanel.down('#alignerField').setValue('bwasw');
                                alignPanel.down('#alignerpanel').setAligner(alignField);

                                reportPanel.down('#sbtAnalysis').setValue(false);
                                reportPanel.down('#ntCoverage').setValue(true);
                                reportPanel.down('#aaSnpByCodon').setValue(true);
                                reportPanel.down('#ntSnpByPosition').setValue(true);
                            }
                            else {
                                reportPanel.down('#sbtAnalysis').setValue(false);
                                reportPanel.down('#ntCoverage').setValue(false);
                                reportPanel.down('#aaSnpByCodon').setValue(false);
                                reportPanel.down('#ntSnpByPosition').setValue(false);
                            }
                        }
                    },
                    forceSelection:false,
                    typeAhead: true,
                    mode: 'local',
                    triggerAction: 'all'
                }]
        },{
            xtype:'panel',
            border: true,
            title: 'Step 2: Pre-Processing of Reads (optional)',
            name: 'preprocessing',
            itemId: 'preprocessing',
            width: 'auto',
            defaults: {
                msgTarget:'qtip'
            },
            items :[{
                fieldLabel: 'Downsample Reads',
                helpPopup: 'If selected, up to the specified number of reads will be randomly selected from each input file.  It can be useful for debugging or trying new settings, as fewer reads will run faster.  Note: this will occur prior to barcode separation, but after merging.',
                name: 'preprocessing.downsample',
                xtype:'checkbox',
                itemId: 'doDownsample',
                scope: this,
                handler: function(btn, val){
                    btn.up('form').down('#downsampleOptions').setVisible(btn.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                hidden: true,
                hideMode: 'offsets',
                width: 'auto',
                itemId: 'downsampleOptions',
                items: [{
                    xtype: 'numberfield',
                    fieldLabel: 'Total Reads',
                    name: 'preprocessing.downsampleReadNumber',
                    value: 200,
                    minValue: 0,
                    helpPopup: 'For each input file, up to the this number of reads will be randomly retained.'
                }]
            },{
                fieldLabel: 'Minimum Read Length',
                helpPopup: 'If selected, any reads shorter than this value will be discarded from analysis',
                xtype: 'numberfield',
                minValue: 0,
                name: 'preprocessing.minLength',
                itemId: 'minLength'
            },{
                xtype: 'checkbox',
                fieldLabel: 'Adapter Trimming',
                scope: this,
                handler: function(c, v){
                    c.up('form').down('#adapterPanel').setVisible(v);
                },
                itemId: 'trimAdapters',
                name: 'preprocessing.trimAdapters'
            },{
                xtype: 'adapterpanel',
                itemId: 'adapterPanel'
            },{
                fieldLabel: 'Quality Trimming (by sliding window)',
                helpPopup: 'This uses a sliding window to trim from the 3\' ends.  Starting at the 3\' end, the algorithm takes a window of the last X bases.  If their average quality score does not pass the specified value, the algorithm moves one base forward and repeats.  This continues until the average of the window passes the minimal quality provided.',
                name: 'preprocessing.qual2',
                itemId: 'qual2',
                xtype: 'checkbox',
                scope: this,
                handler: function(c){
                    c.up('form').down('#qual2Container').setVisible(c.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                width: '100%',
                hidden: true,
                hideMode: 'offsets',
                hideLabel: true,
                name: 'preprocessing.qual2Container',
                itemId: 'qual2Container',
                items: [{
                    fieldLabel: 'Window Size',
                    helpPopup: 'The length of the sliding window used in quality trimming',
                    xtype: 'numberfield',
                    name: 'preprocessing.qual2_windowSize',
                    itemId: 'qual2_windowSize',
                    minValue: 0,
                    value: 4
                },{
                    fieldLabel: 'Avg Qual',
                    helpPopup: 'The average quality score for the window that must be obtained',
                    xtype: 'numberfield',
                    name: 'preprocessing.qual2_avgQual',
                    itemId: 'qual2_avgQual',
                    minValue: 0,
                    value: 15
                }]
            },{
                fieldLabel: 'Crop Reads',
                helpPopup: 'If selected, any reads above the selected length will be cropped.  This is sometimes useful for Illumina data when read quality drops beyond a given read cycle.',
                name: 'preprocessing.crop',
                itemId: 'crop',
                xtype: 'checkbox',
                scope: this,
                handler: function(c){
                    c.up('form').down('#cropContainer').setVisible(c.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                width: '100%',
                hidden: true,
                hideMode: 'offsets',
                hideLabel: true,
                name: 'preprocessing.cropContainer',
                itemId: 'cropContainer',
                items: [{
                    fieldLabel: 'Crop Length',
                    helpPopup: 'Reads will be cropped to this length',
                    xtype: 'numberfield',
                    name: 'preprocessing.crop_cropLength',
                    itemId: 'crop_cropLength',
                    minValue: 0,
                    value: 250
            },{
                fieldLabel: '5\' Cropping',
                helpPopup: 'Cuts the specified number of bases from the 5\' end of each read',
                xtype: 'numberfield',
                name: 'preprocessing.crop_headcropLength',
                itemId: 'crop_headcropLength',
                minValue: 0,
                value: null
            }]
        },{
            xtype: 'fieldset',
            style: 'padding:5px;',
            width: '100%',
            hidden: true,
            hideMode: 'offsets',
            hideLabel: true,
            name: 'preprocessing.maskContainer',
            itemId: 'maskContainer',
            items: [{
                fieldLabel: 'Min Qual',
                helpPopup: 'If specified, any bases with quality scores below this value will be replaced with N',
                xtype: 'numberfield',
                minValue: 0,
                name: 'preprocessing.mask_minQual',
                itemId: 'mask_minQual'
            }]
            },{
                fieldLabel: 'Delete Intermediate Files',
                helpPopup: 'Check to delete the intermediate files created by this pipeline.  In general these are not needed and it will save disk space.  These files might be useful for debugging though',
                name: 'deleteIntermediateFiles',
                itemId: 'deleteIntermediateFiles',
                checked: true,
                hidden: false,
                xtype: 'checkbox'

            }]
        },{
            xtype:'panel',
            border: true,
            title: 'Step 3: Alignment (optional)',
            itemId: 'alignment',
            collapsible: false,
            width: '100%',
            defaults: {
                width: 350
            },
            items: [{
                xtype: 'checkbox',
                fieldLabel: 'Perform Alignment',
                name: 'doAlignment',
                itemId: 'doAlignment',
                checked: true,
                listeners: {
                    change: this.onPerformAlignmentCheck,
                    afterrender: {
                        fn: this.onPerformAlignmentCheck,
                        scope: this,
                        delay: 100
                    },
                    scope: this
                }
            }]
        },{
            xtype:'panel',
            border: true,
            title: 'Step 4: SNP Inspection Settings (will only be used if additional analyses are chosen below)',
            itemId: 'snpPanel',
            collapsible: false,
            width: '100%',
            defaults: {
                border: false
            },
            items: [{
                html: 'Many applications require calling SNPs.  This pipeline will perform phased SNP calling on the data, including AA translation in certain cases.  The options below allow you to set thresholds for SNP calling, which determine whether a SNP will be included in the analysis or not.',
                width: 800,
                style: 'margin-bottom: 10px;padding-left:5px;'
            },{
                xtype: 'snpcallpanel'
            }]
        },{
            xtype:'panel',
            border: true,
            title: 'Step 5: Additional Analyses (optional)',
            itemId: 'reportPanel',
            collapsible: false,
            width: '100%',
            defaults: {
                width: 350,
                labelWidth: 250
            },
            items: [{
                xtype: 'checkbox',
                fieldLabel: 'Calculate and Save NT SNPs',
                helpPopup: 'If selected, a summary of the NT SNPs at each position, will be saved, grouped by base.',
                name: 'ntSnpByPosition',
                itemId: 'ntSnpByPosition',
                checked: true
            },{
                xtype: 'checkbox',
                fieldLabel: 'Calculate and Save Coverage Depth',
                helpPopup: 'If selected, a summary of the NT depth at each position will be saved.',
                name: 'ntCoverage',
                itemId: 'ntCoverage',
                checked: true
            },{
                fieldLabel: 'Calculate and Save AA SNPs',
                xtype: 'checkbox',
                checked: true,
                helpPopup: 'If selected, for each NT SNP the flanking bases of the read will be identified and translated.  This is currently only possible for reference sequences where their coding regions have been annotated.',
                name: 'aaSnpByCodon',
                itemId: 'aaSnpByCodon'
            },{
                xtype: 'checkbox',
                fieldLabel: 'Sequence Based Genotyping',
                helpPopup: 'If selected, each alignment will be inspected, and those alignments lacking any high quality SNPs will be retained.  A report will be generated summarizing these matches, per read.',
                name: 'sbtAnalysis',
                itemId: 'sbtAnalysis',
                listeners: {
                    change: function(cb, val){
                        var panel = cb.up('panel').down('#sbtOptions');
                        panel.setVisible(val);

                        if(!val)
                            panel.down('#maxAlignMismatch').setValue(null);
                    }
                }
            },{
                hidden: true,
                xtype: 'fieldset',
                itemId: 'sbtOptions',
                width: '100%',
                bodyStyle: 'padding: 5px;',
                style: 'padding: 5px;',
                defaults: {
                    width: 350
                },
                items: [{
//                    xtype: 'numberfield',
//                    minValue: 0,
//                    hidden: false,
//                    fieldLabel: 'Max Alignment Mismatches',
//                    name: 'sbt.maxAlignMismatch',
//                    itemId: 'maxAlignMismatch',
//                    helpPopup: 'If a number is entered, alignments with greater than the selected number of high-confidence SNPs will not be imported.  Leave blank to import all (normal for most applications).  For sequence-based genotyping, this value will usually be set to zero, assuming you are interested in perfect hits only.',
//                    value: 0
//                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Only Import Valid Pairs',
                    name: 'sbt.onlyImportPairs',
                    helpPopup: 'If selected, only alignments consisting of valid forward/reverse pairs will be imported.  Do not check this unless you are using paired-end sequence.'
                },{
                    xtype: 'numberfield',
                    minValue: 0,
                    fieldLabel: 'Min Read # Per Reference',
                    name: 'sbt.minCountForRef',
                    helpPopup: 'If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this many reads across each sample.  This can be a way to reduce ambiguity among allele calls.'
                },{
                    xtype: 'numberfield',
                    minValue: 0,
                    maxValue: 100,
                    fieldLabel: 'Min Read Pct Per Reference',
                    name: 'sbt.minPctForRef',
                    helpPopup: 'If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total from each sample.  This can be a way to reduce ambiguity among allele calls.'
                },{
                    xtype: 'numberfield',
                    minValue: 0,
                    fieldLabel: 'Min Pct Retained To Import',
                    disabled: true,
                    name: 'sbt.minPctToImport',
                    helpPopup: 'If a value is provided, at least this percent of input sequences (post pre-processing) must pass filters, or no data will be imported for that sample.'
                }]
            }]
        }]);
    },

    //loads the exp.RowId for each file
    initFiles: function(sql){
        this.readsetStore = Ext4.create("LABKEY.ext4.data.Store", {
            containerPath: this.queryContainer,
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_readsets',
            columns: 'rowid,name,platform,container,container/displayName,container/path,fileid,fileid/name,fileid/fileexists,fileid2,fileid2/name,fileid2/fileexists,mid5.mid3,subjectid,sampleid,instrument_run_id',
            metadata: {
                queryContainerPath: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    defaultValue: this.queryContainerPath
                }
            },
            autoLoad: true,
            filterArray: [
                LABKEY.Filter.create('rowid', this.readsets.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            sort: 'name',
            listeners: {
                scope: this,
                load: function(store){
                    this.fileNames = [];
                    this.fileIds = [];
                    var errors = [];
                    var errorNames = [];
                    store.each(function(rec){
                        if(rec.get('fileid')){
                            if (!rec.get('fileid/fileexists')){
                                errors.push(rec);
                                errorNames.push(rec.get('name'));
                            }
                            else {
                                this.fileIds.push(rec.get('fileid'));
                                this.fileNames.push(rec.get('fileid/name'));
                            }
                        }
                        else {
                            errors.push(rec);
                            errorNames.push(rec.get('name'))
                        }
                        if(rec.get('fileid2')){
                            if (!rec.get('fileid2/fileexists')){
                                errors.push(rec);
                                errorNames.push(rec.get('name'))
                            }
                            else {
                                this.fileIds.push(rec.get('fileid2'));
                                this.fileNames.push(rec.get('fileid2/name'));
                            }
                        }
                    }, this);

                    if(errors.length){
                        alert('The follow readsets lack an input file and will be skipped: ' + errorNames.join(', '));
                    }

                    this.checkProtocol();
                }
            }
        });


//        var sql = 'select RowId, Name, Folder, folder as container, Folder.displayName as displayName from exp.data d where ';
//        var pathString;
//        if(this.fileNames.length){
//            sql += ' (container = \''+LABKEY.container.id+'\' and run is null AND (';
//            var sep = '';
//            Ext.each(this.fileNames, function(f){
//                pathString = this.path.replace(/^\./, '') + f;
//                sql += sep + '(name = \''+f+'\' AND (DataFileUrl LIKE \'%@files' + pathString + '\' OR DataFileUrl LIKE \'%@pipeline' + pathString + '\'))';
//                sep = ' OR ';
//            }, this);
//            sql += '))';
//        }
//
//        if(this.fileIds.length){
//            if(this.fileNames.length)
//                sql += ' OR ';
//            sql += 'rowid IN (\''+(this.fileIds.join('\',\''))+'\')';
//        }
//        //console.log(sql);
//
//        this.callParent([sql]);
    },

    getJsonParams: function(){
        var fields = this.callParent();

        if(!fields)
            return;

        this.readsetStore.each(function(rec, idx){
            fields['sample_'+idx] = {
                readsetname: rec.get('name'),
                readset: rec.get('rowid'),
                platform: rec.get('platform'),
                fileName: rec.get('fileid/name'),
                fileName2: rec.get('fileid2/name'),
                fileId: rec.get('fileid'),
                fileId2: rec.get('fileid2'),
                mid5: rec.get('mid5'),
                mid3: rec.get('mid3'),
                subjectid: rec.get('subjectid'),
                sampleid: rec.get('sampleid'),
                instrument_run_id: rec.get('instrument_run_id')
            };
        }, this);
        var error;

//        if(this.down('#usePairedEnd').getValue()){
//            var aligner = this.down('#alignerField');
//            if(aligner){
//                var recIdx = aligner.store.find('name', aligner.getValue(), null, null, null, true);
//                var rec = aligner.store.getAt(recIdx);
//                var items = rec.get('jsonconfig') ? Ext4.JSON.decode(rec.get('jsonconfig')) : new Array();
//                if(!items || !items[0] || items[0].name != 'pairedEnd'){
//                    alert('The aligner ' + aligner.getValue() + ' does not support paired end reads');
//                    return;
//                }
//            }
//        }

        //a dirty fix to allow user-supplied reference sequences
        if(this.down('#virusRefType') && this.down('#virusRefType').checked){
            fields['virus.virus_strain'] = fields['virus.custom_strain_name'];
        }

        this.down('#adapterPanel').down('gridpanel').store.each(function(r, i){
            if(!r.data.adapterName){
                alert('Missing name for one or more adapters');
                error = 1;
            }
            if(!r.data.adapterSequence){
                alert('Missing sequence for one or more adapters');
                error = 1;
            }
            if(!r.data.trim5 && !r.data.trim3){
                alert('Adapter: '+r.name+' must be trimmed from either 5\' or 3\' end');
                error = 1;
            }

            fields['adapter_'+i] = [r.data.adapterName, r.data.adapterSequence, r.data.trim5==true, r.data.trim3==true, r.data.palindrome];
        });

        return fields;
    },

    onPerformAlignmentCheck: function(btn, val, oldVal){
        val = btn.getValue();
        var panel = btn.up('#alignment');
        panel.items.each(function(item){
            if(item.name != 'doAlignment')
                panel.remove(item);
        }, this);

        if(val){
            panel.add({
                xtype: 'refsequencepanel'
            },{
                xtype: 'displayfield',
                fieldLabel: 'Alignment Settings'
            },{
                xtype: 'alignerpanel',
                value: 'bwasw'
            });

            this.down('#reportPanel').setDisabled(false);
            this.down('#snpPanel').setDisabled(false);
        }
        else {
            this.down('#reportPanel').setDisabled(true);
            this.down('#snpPanel').setDisabled(true);
        }
    },

    populateSamples: Ext4.emptyFn,

    getFilePanelCfg: function(){
        return {
            xtype:'panel',
            border: true,
            title: 'Selected Readsets',
            itemId: 'files',
            width: 'auto',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                xtype: 'dataview',
                store: this.readsetStore,
                itemSelector:'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Id</td><td>Readset Name</td><td>Sample Id</td><td>Platform</td><td>File 1</td><td>File 2</td><td>Folder</td></tr>',
                    '<tpl for=".">',
                        '<tr class="file_list">',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.rowid})]}" target="_blank">{rowid:htmlEncode}</a></td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.rowid})]}" target="_blank">{name:htmlEncode}</a></td>',
                        '<td>',
                            '<tpl if="sampleid &gt; 0">',
                                '<a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "laboratory", "query.queryName":"samples", "query.rowid~eq": values.sampleid})]}" target="_blank">{sampleid:htmlEncode}</a>',
                            '</tpl>',
                        '</td>',
                        //'<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "laboratory", "query.queryName":"subjects", "query.rowid~eq": values.subjectid})]}" target="_blank">{subjectid:htmlEncode}</a></td>',
                        '<td>{platform:htmlEncode}</td>',
                        '<td',
                            '<tpl if="values.fileid && !values[\'fileid/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                        '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.fileid})]}" target="_blank">{[Ext4.htmlEncode(values["fileid/name"])]}</a></td>',
                        '<td',
                            '<tpl if="values.fileid2 && !values[\'fileid2/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                        '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.fileid2})]}" target="_blank">{[Ext4.htmlEncode(values["fileid2/name"])]}</a></td>',

                        '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", values["container/path"], {})]}" target="_blank">{[Ext4.htmlEncode(values["container/displayName"])]}</a></td>',
                        //'<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", null, {schemaName: "sequenceanalysis", queryName: "sequence_analyses", "query.inputfile~eq":values.RowId, "query.containerFilterName":"AllFolders"})]}" target="_blank">Click to View Other Analyses</a></td>',
                        '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            }]
        }
    }
});
