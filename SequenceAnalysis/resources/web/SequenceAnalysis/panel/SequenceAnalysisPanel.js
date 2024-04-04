/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.SequenceAnalysisPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    alias: 'widget.sequenceanalysis-sequenceanalysispanel',
    jobType: 'alignment',
    statics: {
        TASKID: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline'
    },

    initComponent: function(){
        this.readsets = this.readsets ? this.readsets.split(';') : [];

        Ext4.apply(this, {
            itemId: 'sequenceAnalysisPanel',
            buttonAlign: 'left',
            buttons: [{
                text: 'Start Analysis',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.addEvents('sectiontoggle', 'dataload');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getAnalysisToolDetails'),
            method: 'POST',
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.onDataLoad, this),
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onDataLoad: function(results){
        this.add([this.getFilePanelCfg(),this.getProtocolPanelCfg(),{
            xtype: 'panel',
            title: 'Analysis Options',
            width: '100%',
            itemId: 'analysisOptions',
            items: [{
                border: false,
                html: 'Loading...'
            }]
        }]);

        var panel = this.down('#analysisOptions');

        var items = [];
        items.push({
            xtype: 'sequenceanalysis-analysissectionpanel',
            title: 'Step 2: FASTQ Preprocessing',
            stepType: 'fastqProcessing',
            sectionDescription: 'This steps in this section will act on the input FASTQ file(s), allowing you to trim adapters, filter reads, etc.  The steps will be executed in the order listed.  Use the button below to add steps.',
            toolConfig: results
        });

        items.push({
            xtype: 'sequenceanalysis-alignmentpanel',
            toolConfig: results
        });

        items.push({
            xtype: 'sequenceanalysis-analysissectionpanel',
            title: 'Step 4: Downstream Analysis (optional)',
            sectionDescription: 'This steps in this section will act on the final BAM file.  These steps may be highly application-specific, so please read the description of each step for more information.',
            stepType: 'analysis',
            toolConfig: results
        });

        items.push(this.getJobResourcesCfg(results));

        items.push({
            xtype: 'panel',
            style: 'padding-bottom: 0px;',
            width: '100%',
            border: false,
            items: [{
                border: false,
                width: '100%',
                style: 'text-align: center',
                html: 'Powered By DISCVR-Seq.  <a href="https://bimberlab.github.io/DiscvrLabKeyModules/discvr-seq/overview.html">Click here to learn more.</a>'
            }]
        });

        this.remove(panel);
        this.add(items);

        var btn = this.down('#copyPrevious');
        btn.handler.call(this, btn);

        this.fireEvent('dataload', this);
    },

    //loads the exp.RowId for each file
    initFiles: function(sql){
        this.fileNames = [];
        this.fileIds = [];

        this.readsetStore = Ext4.create("LABKEY.ext4.data.Store", {
            containerPath: this.queryContainer,
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_readsets',
            columns: 'rowid,name,platform,container,container/displayName,container/path,totalFiles,barcode5.barcode3,subjectid,sampleid,instrument_run_id',
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
                    var errors = [];
                    var errorNames = [];
                    store.each(function(rec){
                        if (!rec.get('totalFiles')){
                            errors.push(rec);
                            errorNames.push(rec.get('name'));
                        }
                    }, this);

                    this.onStoreLoad(errorNames);
                }
            }
        });

        this.readDataStore = Ext4.create("LABKEY.ext4.data.Store", {
            containerPath: this.queryContainer,
            schemaName: 'sequenceanalysis',
            queryName: 'readdata',
            columns: 'rowid,readset,readset/name,container,container/displayName,container/path,fileid1,fileid1/name,fileid1/fileexists,fileid2,fileid2/name,fileid2/fileexists',
            metadata: {
                queryContainerPath: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    defaultValue: this.queryContainerPath
                }
            },
            autoLoad: true,
            filterArray: [
                LABKEY.Filter.create('readset', this.readsets.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            sort: 'name',
            listeners: {
                scope: this,
                load: function (store) {
                    var errors = [];
                    var errorNames = [];
                    store.each(function(rec){
                        if (rec.get('fileid1')){
                            if (!rec.get('fileid1/fileexists')){
                                errors.push(rec);
                                errorNames.push(rec.get('readset/name'));
                            }
                            else {
                                this.fileIds.push(rec.get('fileid1'));
                                this.fileNames.push(rec.get('fileid1/name'));
                            }
                        }
                        else {
                            errors.push(rec);
                            errorNames.push(rec.get('readset/name'))
                        }

                        if (rec.get('fileid2')){
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

                    this.onStoreLoad(errorNames);

                    var target = this.down('#readsetCount');
                    if (target) {
                        target.update(target.initialConfig.html + '  Total to align: ' + this.readsetStore.getCount());
                    }
                }
            }
        });
    },

    storesLoaded: 0,
    errorNames: [],

    onStoreLoad: function(errorNames){
        this.storesLoaded++;
        if (errorNames){
            this.errorNames = this.errorNames.concat(errorNames);
            this.errorNames = Ext4.unique(this.errorNames);
        }
        if (this.storesLoaded === 2){
            this.afterStoreLoad();
        }
    },

    afterStoreLoad: function(){
        var dv = this.down('dataview');

        //this will occur if the stores return before onDataLoad
        if (!dv){
            this.on('dataload', this.afterStoreLoad, this, {single: true, delay: 100});
            return;
        }

        dv.refresh();

        if (this.errorNames.length){
            alert('The follow readsets lack an input file and will be skipped: ' + this.errorNames.join(', '));
        }
    },

    getErrors: function(){
        var errors = [];

        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            var errs = s.getErrors();
            if (errs.length){
                errors = errors.concat(errs);
            }
        }, this);

        errors = Ext4.unique(errors);
        return errors;
    },

    getJsonParams: function(config){
        config = config || {};
        var errors = this.getErrors();
        if (errors.length && !config.ignoreErrors){
            Ext4.Msg.alert('Error', errors.join('<br>'));
            return;
        }

        var json = {
            version: 2
        };

        //first add the general params. Note: include all to ensure we include unchecked checkboxes. Using useDataValues=false to ensure we get the string-serialized value
        Ext4.apply(json, this.down('#runInformation').getForm().getValues(false, false, false, false));

        json['alignment.doAlignment'] = this.down('#doAlignment').getValue();

        //then append each section
        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            Ext4.apply(json, s.toJSON(config));
        }, this);

        json.readsetIds = this.readsets;

        return json;
    },

    populateSamples: Ext4.emptyFn,

    getProtocolPanelCfg: function(){
        return {
            xtype: 'form',
            border: true,
            bodyBorder: false,
            title: 'Step 1: Run Information',
            itemId: 'runInformation',
            width: '100%',
            defaults: Ext4.Object.merge({}, this.fieldDefaults, {bodyStyle: 'padding:5px;'}),
            defaultType: 'textfield',
            items :[{
                fieldLabel: 'Job Name',
                width: 600,
                helpPopup: 'This is the name assigned to this job, which must be unique.  Results will be moved into a folder with this name.',
                name: 'jobName',
                itemId: 'jobName',
                allowBlank:false,
                value: 'SequenceAnalysis_'+ Ext4.Date.format(new Date(), 'Ymd'),
                maskRe: new RegExp('[A-Za-z0-9_]')
            },{
                fieldLabel: 'Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this analysis (optional)',
                name: 'jobDescription',
                allowBlank: true,
                value: LABKEY.ActionURL.getParameter('jobDescription')
            },{
                fieldLabel: 'Delete Intermediate Files',
                helpPopup: 'Check to delete the intermediate files created by this pipeline.  In general these are not needed and it will save disk space.  These files might be useful for debugging though.',
                name: 'deleteIntermediateFiles',
                inputValue: true,
                uncheckedValue: false,
                checked: true,
                xtype: 'checkbox'
            },{
                fieldLabel: 'Perform Cleanup After Each Step',
                helpPopup: 'Is selected, intermediate files from this job will be deleted after each step, instead of once at the end of the job. This can reduce the working directory size. Note: this will only apply if deleteIntermediateFiles is selected, and this is not supported across every possible pipeline type.',
                name: 'performCleanupAfterEachStep',
                inputValue: true,
                uncheckedValue: false,
                checked: true,
                xtype: 'checkbox'
            },{
                fieldLabel: 'Copy Inputs To Working Directory?',
                helpPopup: 'Check to copy the input files to the working directory.  Depending on your environment, this may or may not help performance.',
                name: 'copyInputsLocally',
                inputValue: true,
                uncheckedValue: false,
                checked: false,
                xtype: 'checkbox'
            }, this.getSaveTemplateCfg(),{
                fieldLabel: 'Submit Jobs To Same Folder/Workbook As Readset?',
                helpPopup: 'By default, the pipelines jobs and their outputs will be created in the workbook you selected. However, in certain cases, such as bulk submission of many jobs, it might be preferable to submit each job to the source folder/workbook for each input. Checking this box will enable this.',
                name: 'submitJobToReadsetContainer',
                inputValue: true,
                uncheckedValue: false,
                checked: false,
                xtype: 'checkbox'
            }]
        };
    },

    getFilePanelCfg: function(){
        return {
            xtype: 'panel',
            border: true,
            title: 'Selected Readsets',
            itemId: 'files',
            width: '100%',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                html: 'Below are the readsets that will be analyzed.  It is recommended that you view the FASTQC report (link on the right) for these samples prior to analysis.  This report provides a variety of useful information that informs how to preprocess the reads, including per-cycle quality scores (informs trimming), and overrepresented sequences (which may identify adapters not yet clipped).',
                style: 'padding-bottom: 10px;',
                itemId: 'readsetCount'
            },{
                xtype: 'dataview',
                width: '100%',
                store: this.readsetStore,
                readDatastore: this.readDataStore,
                itemSelector: 'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Readset Id</td><td>Readset Name</td><td>Sample Id</td><td>Platform</td><td>Forward Reads</td><td>Reverse Reads</td><td>Folder</td><td></td></tr>',
                    '<tpl for=".">',
                        '<tr class="file_list">',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.rowid})]}" target="_blank">{rowid:htmlEncode}</a></td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.rowid})]}" target="_blank">{name:htmlEncode}</a></td>',
                        '<td>',
                            '<tpl if="sampleid &gt; 0">',
                                '<a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "laboratory", "query.queryName":"samples", "query.rowid~eq": values.sampleid})]}" target="_blank">{sampleid:htmlEncode}</a>',
                            '</tpl>',
                        '</td>',
                        '<td>{platform:htmlEncode}</td>',
                        '{[this.getFiles(values, 1)]}',
                        '{[this.getFiles(values, 2)]}',

                        '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", values["container/path"], {})]}" target="_blank">{[Ext4.htmlEncode(values["container/displayName"])]}</a></td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {readsets: values.rowid})]}" target="_blank">View FASTQC Report</a></td>',
                        '</tr>',
                    '</tpl>',
                    '</table>',
                    {
                        readDataStore: this.readDataStore,
                        getFiles: function(values, idx){
                            var total = 0;
                            var ret = '<td>';
                            this.readDataStore.each(function(r){
                                if (r.get('readset') == values.rowid && r.get('fileid' + idx)){
                                    ret += '<span';
                                    if (!r.get('fileid' + idx + '/fileexists')){
                                        ret += ' style="background: red;" data-qtip="File does not exist"';
                                    }

                                    ret += '><a href="' + LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: r.get('fileid' + idx)}) + '" target="_blank">' + Ext4.htmlEncode(r.get('fileid' + idx + '/name')) + '</a></span>';

                                    total++;
                                    if (total){
                                        ret += '<br>';
                                    }
                                }
                            }, this);

                            ret += '</td>';

                            return ret;
                        }
                    }
                ]
            }]
        }
    }
});
