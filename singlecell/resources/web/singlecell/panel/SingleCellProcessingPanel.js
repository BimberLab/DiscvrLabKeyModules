Ext4.define('SingleCell.panel.SingleCellProcessingPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    alias: 'widget.singlecell-singlecellprocessingpanel',
    handlerClass: 'ProcessSingleCellHandler',

    jobType: 'singleCell',

    initComponent: function(){
        this.showPrepareRawData = this.handlerClass === 'ProcessSingleCellHandler';

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

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getAnalysisToolDetails'),
            method: 'POST',
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.onDataLoad, this),
            failure: LDK.Utils.getErrorCallback()
        });

        this.addEvents('sectiontoggle');
    },

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
                value: 'SingleCell_' + (Ext4.Date.format(new Date(), 'Ymd')),
                maskRe: new RegExp('[A-Za-z0-9_]')
            },{
                fieldLabel: 'Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this analysis (optional)',
                name: 'jobDescription',
                allowBlank:true,
                value: LABKEY.ActionURL.getParameter('jobDescription')
            },{
                xtype: 'combo',
                name: 'submissionType',
                width: 600,
                fieldLabel: 'Job Submission',
                displayField: 'label',
                valueField: 'value',
                store: {
                    fields: ['value', 'label'],
                    data: [
                        {value: 'individual', label: 'Run Separately (One Job/Input)'},
                        {value: 'merged', label: 'Merged Input (One Job/Batch)'}
                    ]
                },
                listeners: {
                    change: function(field, val, oldVal) {
                        var useOutputFileContainer = field.up('panel').down('#useOutputFileContainer');
                        useOutputFileContainer.setVisible(val === 'individual');
                        useOutputFileContainer.setValue(val !== 'individual');
                    }
                },
                value: this.outputFileIds.length === 1 ? 'individual' : null,
                allowBlank: false
            },{
                xtype: 'checkbox',
                width: 600,
                itemId: 'useOutputFileContainer',
                name: 'useOutputFileContainer',
                hidden: true,
                fieldLabel: 'Submit to Source File Workbook',
                description: 'If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if Run Separately is selected.'
            },{
                fieldLabel: 'Delete Intermediate Files',
                helpPopup: 'Check to delete the intermediate files created by this pipeline.  In general these are not needed and it will save disk space.  These files might be useful for debugging though.',
                name: 'deleteIntermediateFiles',
                inputValue: true,
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
            }, this.getSaveTemplateCfg()]
        };
    },

    //loads the exp.RowId for each file
    initFiles: function(){
        this.outputFileStore = Ext4.create("LABKEY.ext4.data.Store", {
            containerPath: this.queryContainer,
            schemaName: 'sequenceanalysis',
            queryName: 'outputfiles',
            columns: 'rowid,name,readset,readset/name,readset/platform,container,container/displayName,container/path,dataid,dataid/name,dataid/fileexists,readset/subjectid,readset/sampleid,library_id,library_id/name',
            metadata: {
                queryContainerPath: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    defaultValue: this.queryContainerPath
                }
            },
            autoLoad: true,
            filterArray: [
                LABKEY.Filter.create('rowid', this.outputFileIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            sort: 'name',
            listeners: {
                scope: this,
                load: function(store){
                    this.fileNames = [];
                    this.fileIds = [];
                    var errors = [];
                    var errorNames = [];
                    var libraryIds = [];
                    store.each(function(rec){
                        if (rec.get('dataid')){
                            if (!rec.get('dataid/fileexists')){
                                errors.push(rec);
                                errorNames.push(rec.get('readset/name'));
                            }
                            else {
                                this.fileIds.push(rec.get('dataid'));
                                this.fileNames.push(rec.get('dataid/name'));
                            }
                        }
                        else {
                            errors.push(rec);
                            errorNames.push(rec.get('readset/name'))
                        }

                        if (rec.get('library_id')){
                            libraryIds.push(rec.get('library_id'));
                        }
                    }, this);

                    //TODO
                    //if (errors.length){
                    //    alert('The following inputs lack a file and will be skipped: ' + errorNames.join(', '));
                    //}

                    this.libraryIds = Ext4.unique(libraryIds);
                }
            }
        });
    },

    getFilePanelCfg: function(){
        return {
            xtype: 'panel',
            border: true,
            title: 'Selected Files',
            itemId: 'files',
            width: 'auto',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                html: 'Below are the files that will be processed.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'dataview',
                store: this.outputFileStore,
                itemSelector: 'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>File Id</td><td>Description</td><td>Readset Name</td><td>Loupe File</td><td>Genome</td><td>Folder</td></tr>',
                    '<tpl for=".">',
                    '<tr class="file_list">',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"outputfiles", "query.rowId~eq": values.rowid})]}" target="_blank">{rowid:htmlEncode}</a></td>',
                    '<td>{description:htmlEncode}</td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.readset})]}" target="_blank">{[Ext4.htmlEncode(values["readset/name"])]}</a></td>',
                    '<td',
                    '<tpl if="values.dataid && !values[\'dataid/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                    '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.dataid})]}" target="_blank">{[Ext4.htmlEncode(values["dataid/name"])]}</a></td>',

                    '<td>{[Ext4.htmlEncode(values["library_id/name"])]}</td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", values["container/path"], {})]}" target="_blank">{[Ext4.htmlEncode(values["container/displayName"])]}</a></td>',
                    '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            }]
        }
    },

    onDataLoad: function(results){
        this.add([this.getFilePanelCfg(), this.getProtocolPanelCfg(),{
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
        if (this.showPrepareRawData){
            items.push({
                xtype: 'sequenceanalysis-analysissectionpanel',
                title: 'Prepare Raw Data',
                stepType: 'singleCellRawData',
                singleTool: true,
                comboValue: 'PrepareRawCounts',
                sectionDescription: 'This section allows you to control the parsing of the raw 10x count data',
                toolConfig: results
            });
        }

        items.push({
            xtype: 'sequenceanalysis-analysissectionpanel',
            title: 'Seurat Processing',
            stepType: 'singleCell',
            allowDuplicateSteps: true,
            sectionDescription: 'This steps in this section will act on the seurat object(s). The initial loading from raw counts will create one seurat object per input dataset. Individual steps can either join, split, or act on each input object.  The steps will be executed in the order listed.  Use the button below to add steps.',
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
    },

    onSubmit: function(){
        var values = this.getJsonParams();
        if (!values){
            return;
        }

        Ext4.Msg.wait('Submitting...');
        var json = {
            handlerClass: 'org.labkey.singlecell.analysis.' + this.handlerClass,
            outputFileIds: this.outputFileIds,
            useOutputFileContainer: !!values.useOutputFileContainer,
            params: Ext4.encode(values)
        };

        if (Ext4.isDefined(values.submissionType)) {
            json.doSplitJobs = values.submissionType === 'individual';
        }

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'runSequenceHandler'),
            jsonData: json,
            scope: this,
            success: function(){
                Ext4.Msg.hide();

                window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
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

        //first add the general params
        Ext4.apply(json, this.down('#runInformation').getForm().getFieldValues());

        //then append each section
        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            Ext4.apply(json, s.toJSON(config));
        }, this);

        return json;
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

        if (!this.down('#runInformation').isValid()) {
            errors.push('One or more fields is invalid');
        }

        errors = Ext4.unique(errors);
        return errors;
    }
});
