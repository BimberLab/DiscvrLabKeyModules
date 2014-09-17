/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.SequenceAnalysisPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    analysisController: 'sequenceanalysis',
    alias: 'widget.sequenceanalysis-sequenceanalysispanel',
    statics: {
        TASKID: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline'
    },

    taskId: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline',

    initComponent: function(){
        this.readsets = this.readsets ? this.readsets.split(';') : [];

        Ext4.apply(this, {
            itemId: 'sequenceAnalysisPanel',
            buttons: [{
                text: 'Start Analysis',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getAnalysisToolDetails'),
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.onDataLoad, this),
            failure: LDK.Utils.getErrorCallback()
        });

        this.addEvents('sectiontoggle');

        this.add([this.getFilePanelCfg(),
            {
            xtype: 'form',
            border: true,
            bodyBorder: false,
            title: 'Step 1: Run Information',
            itemId: 'runInformation',
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
            },{
                fieldLabel: 'Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this analysis (optional)',
                name: 'protocolDescription',
                allowBlank:true
            },{
                fieldLabel: 'Delete Intermediate Files',
                helpPopup: 'Check to delete the intermediate files created by this pipeline.  In general these are not needed and it will save disk space.  These files might be useful for debugging though.',
                name: 'deleteIntermediateFiles',
                inputValue: true,
                checked: true,
                xtype: 'checkbox'
            },{
                xtype: 'ldk-linkbutton',
                style: 'margin-left: ' + (this.fieldDefaults.labelWidth + 4) + 'px;',
                text: 'Copy Settings From Previous Run',
                linkCls: 'labkey-text-link',
                scope: this,
                handler: function(btn){
                    Ext4.create('Ext.window.Window', {
                        taskId: this.taskId,
                        modal: true,
                        sequencePanel: this,
                        title: 'Copy Settings From Previous Run',
                        width: 700,
                        bodyStyle: 'padding: 5px;',
                        defaults: {
                            border: false
                        },
                        items: [{
                            html: 'This will allow you to apply saved settings from a previous run.  Use the toggle below to select from runs bookmarked as templates, or you can choose any previous run to apply to this form.',
                            style: 'padding-bottom: 10px;'
                        },{
                            xtype: 'radiogroup',
                            name: 'selector',
                            columns: 1,
                            defaults: {
                                name: 'selector'
                            },
                            items: [{
                                boxLabel: 'Choose From Bookmarked Runs',
                                inputValue: 'bookmarkedRuns',
                                checked: true
                            },{
                                boxLabel: 'Choose From All Runs',
                                inputValue: 'allRuns'
                            }],
                            listeners: {
                                change: function (field, val) {
                                    var win = field.up('window');
                                    var target = win.down('#selectionArea');
                                    var toAdd = [];
                                    if (val.selector == 'bookmarkedRuns'){
                                        toAdd.push({
                                            xtype: 'labkey-combo',
                                            width: 450,
                                            fieldLabel: 'Select Run',
                                            store: {
                                                type: 'labkey-store',
                                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                schemaName: 'sequenceanalysis',
                                                queryName: 'saved_analyses',
                                                autoLoad: true,
                                                columns: 'rowid,name,json'
                                            },
                                            displayField: 'name',
                                            valueField: 'rowid',
                                            queryMode: 'local'
                                        });
                                    }
                                    else if (val.selector == 'allRuns'){
                                        toAdd.push({
                                            xtype: 'combo',
                                            width: 450,
                                            fieldLabel: 'Select Run',
                                            store: {
                                                type: 'json',
                                                fields: ['rowid', 'name', 'json']
                                            },
                                            displayField: 'name',
                                            valueField: 'rowid',
                                            queryMode: 'local',
                                            taskId: win.taskId,
                                            listeners: {
                                                render: function(field){
                                                    Ext4.Msg.wait('Loading...');
                                                    LABKEY.Pipeline.getProtocols({
                                                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                        taskId: field.taskId,
                                                        path: './',
                                                        includeWorkbooks: true,
                                                        scope: this,
                                                        success: function(results){
                                                            Ext4.Msg.hide();
                                                            var records = [];
                                                            if (results && results.length){
                                                                Ext4.Array.forEach(results, function(r, idx){
                                                                    records.push(field.store.createModel({
                                                                        name: r.name,
                                                                        rowid: idx + 1,
                                                                        json: r.jsonParameters
                                                                    }));
                                                                }, this);
                                                            }

                                                            field.store.removeAll();
                                                            if (records.length) {
                                                                field.store.add(records);
                                                            }
                                                        },
                                                        failure: LDK.Utils.getErrorCallback()
                                                    })
                                                }
                                            }
                                        });
                                    }
                                    else {
                                        console.error('Unknown type: ' + val.selector);
                                    }

                                    target.removeAll();
                                    target.add(toAdd);
                                },
                                render: function(field){
                                    field.fireEvent('change', field, field.getValue());
                                }
                            }
                        },{
                            xtype: 'panel',
                            itemId: 'selectionArea',
                            bodyStyle: 'padding-top: 10px;padding-left: 5px;'
                        }],
                        buttons: [{
                            text: 'Submit',
                            handler: function(btn){
                                var win = btn.up('window');
                                var combo = win.down('combo');
                                if (!combo.getValue()){
                                    Ext4.Msg.alert('Error', 'Must choose a protocol');
                                    return;
                                }

                                var recIdx = combo.store.find('rowid', combo.getValue());
                                var rec = combo.store.getAt(recIdx);
                                var json = rec.get('json');
                                if (Ext4.isString(rec.get('json'))){
                                    json = Ext4.decode(json);
                                }

                                win.sequencePanel.applySavedValues(json);
                                win.close();
                            }
                        },{
                            text: 'Cancel',
                            handler: function(btn){
                                btn.up('window').close();
                            }
                        }]
                    }).show(btn);
                }
            }]
        },{
            xtype: 'panel',
            title: 'Analysis Options',
            width: 'auto',
            itemId: 'analysisOptions',
            items: [{
                border: false,
                html: 'Loading...'
            }]
        }]);
    },

    onDataLoad: function(results){
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

        this.remove(panel);
        this.add(items);
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
                        if (rec.get('fileid')){
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

                    if (errors.length){
                        alert('The follow readsets lack an input file and will be skipped: ' + errorNames.join(', '));
                    }

                    this.checkProtocol();
                }
            }
        });
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

    getJsonParams: function(){
        var errors = this.getErrors();
        if (errors.length){
            Ext4.Msg.alert('Error', errors.join('<br>'));
            return;
        }

        var json = {
            version: 2
        };

        //first add the general params
        Ext4.apply(json, this.down('#runInformation').getForm().getValues());

        //and readset information
        this.readsetStore.each(function(rec, idx){
            json['sample_' + idx] = {
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

        //then append each section
        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            Ext4.apply(json, s.toJSON());
        }, this);

        return json;
    },

    populateSamples: Ext4.emptyFn,

    getFilePanelCfg: function(){
        return {
            xtype: 'panel',
            border: true,
            title: 'Selected Readsets',
            itemId: 'files',
            width: 'auto',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                html: 'Below are the readsets that will be analyzed.  It is recommended that you view the FASTQC report (link on the right) for these samples prior to analysis.  This report provides a variety of useful information that informs how to preprocess the reads, including per-cycle quality scores (informs trimming), and overrepresented sequences (which may identify adapters not yet clipped).',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'dataview',
                store: this.readsetStore,
                itemSelector: 'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Readset Id</td><td>Readset Name</td><td>Sample Id</td><td>Platform</td><td>File 1</td><td>File 2</td><td>Folder</td><td></td></tr>',
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
                        '<td',
                            '<tpl if="values.fileid && !values[\'fileid/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                        '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.fileid})]}" target="_blank">{[Ext4.htmlEncode(values["fileid/name"])]}</a></td>',
                        '<td',
                            '<tpl if="values.fileid2 && !values[\'fileid2/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                        '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.fileid2})]}" target="_blank">{[Ext4.htmlEncode(values["fileid2/name"])]}</a></td>',

                        '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", values["container/path"], {})]}" target="_blank">{[Ext4.htmlEncode(values["container/displayName"])]}</a></td>',
                        '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {readsets: values.rowid})]}" target="_blank">View FASTQC Report</a></td>',
                        '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            }]
        }
    },

    applySavedValues: function(values){
        //allows for subclasses to exclude this panel
        var alignPanel = this.down('sequenceanalysis-alignmentpanel');
        if (alignPanel) {
            alignPanel.down('#doAlignment').setValue(!!values.alignment);
        }

        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            s.applySavedValues(values);
        }, this);
    }
});
