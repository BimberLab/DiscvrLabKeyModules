Ext4.define('SequenceAnalysis.panel.AlignmentImportPanel', {
    alias: 'widget.sequenceanalysis-alignmentimportpanel',
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    jobType: 'alignmentImport',

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            buttonAlign: 'left',
            buttons: [{
                text: 'Import Data',
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

        this.add(this.getItemCfg());

        this.on('afterrender', this.updateColWidth, this, {single: true});
        this.down('#sampleGrid').on('columnresize', this.updateColWidth, this);
    },

    onDataLoad: function(results) {
        this.add({
            xtype: 'sequenceanalysis-analysissectionpanel',
            title: 'BAM Processing (optional)',
            sectionDescription: 'The steps below will act on the BAM file(s).  They can be used to filter reads, sort the alignments, correct alignment information, etc.  They will be executed in the order selected.',
            stepType: 'bamPostProcessing',
            toolConfig: results
        });
    },

    getItemCfg: function(){
        return [{
            xtype: 'panel',
            title: 'Instructions',
            width: '100%',
            items: [{
                html: 'The purpose of this pipeline is to import the alignment files and associate them with sample attributes such as sample Id, reference, etc.  This module typically expects BAMs to be created through its alignment pipeline; however, BAMs can be imported with or without an associated readset (ie. primary read data).  If the incoming BAMs are not associated with an existing readset, you will need to enter the sample information below.',
                style: 'padding-bottom: 10px;',
                border: false
            }],
            border: true
        }, this.getRunInfoCfg(), this.getFilePanelCfg(), this.getSamplePanelCfg()];
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
                fieldLabel: 'Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this run, such as detail about the source of the alignments (optional)',
                itemId: 'jobDescription',
                name: 'jobDescription',
                allowBlank: true
            },{
                fieldLabel: 'Delete Intermediate Files',
                helpPopup: 'Check to delete the intermediate files created by this pipeline.  In general these are not needed and it will save disk space.  These files might be useful for debugging though.',
                name: 'deleteIntermediateFiles',
                inputValue: true,
                checked: true,
                xtype: 'checkbox'
            },{
                fieldLabel: 'Treatment of Input Files',
                xtype: 'combo',
                helpPopup: 'This determines how the input files are handled.  By default, files are moved to a standardized location and the originals deleted to save space.  However, you can choose to copy the BAMs to the new location, but leave the originals alone.  This is not usually recommended for space reasons.',
                itemId: 'originalFileTreatment',
                name: 'inputFileTreatment',
                width: 600,
                editable: false,
                displayField: 'display',
                valueField: 'value',
                value: 'delete',
                store: {
                    type: 'array',
                    fields: ['display', 'value'],
                    data: [
                        ['Copy, delete originals', 'delete'],
                        ['Copy, leave originals alone', 'none'],
                        ['Leave in place', 'leaveInPlace']
                    ]
                }
            },{
                fieldLabel: 'Collect WGS Metrics',
                helpPopup: 'Check to run Picard tools CollectWGSMetrics, which will generate and save various metrics, including coverage.',
                name: 'collectWgsMetrics',
                inputValue: true,
                checked: true,
                xtype: 'checkbox'
            }, this.getSaveTemplateCfg()]
        }
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
            }]
        }
    },

    getSamplePanelCfg: function(){
        return {
            itemId: 'sampleGrid',
            title: 'Sample Information',
            xtype: 'ldk-gridpanel',
            width: '100%',
            bodyStyle: 'padding: 0px;',
            fileNameStore: this.distinctFileStore,
            editingPluginId: 'cellediting',
            clicksToEdit: 1,
            multiSelect: true,
            border: true,
            stripeRows: true,
            selType: 'rowmodel',
            store: Ext4.create('Ext.data.ArrayStore', {
                model: Ext4.define('SequenceAnalysis.model.AlignmentImportModel', {
                    extend: 'Ext.data.Model',
                    fields: [
                        {name: 'fileName', allowBlank: false},
                        {name: 'library_id'},
                        {name: 'readset', allowBlank: false},
                        {name: 'readsetname'},
                        {name: 'platform', allowBlank: false},
                        {name: 'application', allowBlank: false},
                        {name: 'sampletype'},
                        {name: 'subjectid'},
                        {name: 'sampledate'},
                        {name: 'sampleid'},
                        {name: 'instrument_run_id'},
                        {name: 'fileId'}
                    ],
                    validations: [
                        {type: 'presence', field: 'fileName'},
                        {type: 'presence', field: 'library_id'},
                        {type: 'presence', field: 'readsetname'},
                        {type: 'presence', field: 'platform'},
                        {type: 'presence', field: 'application'}
                    ],
                    validate: function(options) {
                        var errors = this.callParent(arguments);
                        var name = this.get('readsetname');
                        if (this.store){
                            this.store.each(function(r){
                                if (r !== this && r.get('readsetname') === name){
                                    errors.add({
                                        field: 'readsetname',
                                        message: 'All names must be unique'
                                    });
                                }
                            }, this);
                        }

                        return errors;
                    }
                }),
                storeId: 'metadata',
                listeners: {
                    scope: this,
                    update: function(store, rec){
                        this.validateReadsets(store, [rec]);
                    },
                    add: this.validateReadsets
                }
            }),
            getEditingPlugin: function() {
                return {
                    ptype: 'ldk-cellediting',
                    pluginId: 'cellediting',
                    clicksToEdit: 1
                }
            },
            tbar: [{
                text: 'Add',
                itemId: 'sampleAddBtn',
                handler : function(c){
                    var grid = c.up('#sampleGrid');
                    // access the Record constructor through the grid's store
                    var r = grid.getStore().createModel({});

                    grid.getStore().insert(0, [r]);
                    var plugin = grid.getPlugin('cellediting');
                    LDK.Assert.assertNotEmpty('Unable to find cellediting plugin', plugin);
                    plugin.startEditByPosition({row: 0, column: 0});
                }
            },{
                text: 'Remove',
                itemId: 'sampleRemoveBtn',
                handler : function(c){
                    Ext4.Msg.wait("Loading...");
                    var grid = c.up('#sampleGrid');
                    grid.getPlugin('cellediting').completeEdit();
                    var s = grid.getSelectionModel().getSelection();
                    for (var i = 0, r; r = s[i]; i++){
                        grid.getStore().remove(r);
                    }
                    Ext4.Msg.hide();
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
            },{
                text: 'Apply Metadata From Spreadsheet',
                tooltip: 'Click this to populate the rows of the grid using a spreadsheet',
                itemId: 'addBatchBtn',
                scope: this,
                handler: function(btn){
                    var grid = btn.up('grid');
                    grid.getPlugin('cellediting').completeEdit();
                    grid.up('sequenceanalysis-alignmentimportpanel').showExcelImportWin(btn);
                }
            }],
            columns: [{
                name: 'fileName',
                text: 'Source File',
                dataIndex: 'fileName',
                renderer: function(data, attrs, record){
                    if (record.get('isValid') === false){
                        attrs.style = 'background: red;';
                        attrs.tdAttr = attrs.tdAttr || '';
                        if (record.get('qtip'))
                            attrs.tdAttr += 'data-qtip="' + record.get('qtip') + '"';
                    }
                    return data;
                },
                editor: {
                    xtype: 'labkey-combo',
                    triggerAction: 'all',
                    displayField: 'fileName',
                    valueField: 'fileName',
                    allowBlank: false,
                    forceSelection: true,
                    typeAhead: true,
                    lazyRender: false,
                    queryMode: 'local',
                    store: this.fileNameStore
                }
            },{
                name: 'library_id',
                text: 'Reference Genome',
                dataIndex: 'library_id',
                width: 180,
                editor: {
                    xtype: 'labkey-combo',
                    triggerAction: 'all',
                    displayField: 'name',
                    valueField: 'rowid',
                    allowBlank: false,
                    forceSelection: true,
                    typeAhead: true,
                    queryMode: 'local',
                    store: {
                        type: 'labkey-store',
                        schemaName: 'sequenceanalysis',
                        queryName: 'reference_libraries',
                        filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                        storeId: 'sequenceanalysis||reference_libraries',
                        sort: 'name',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        autoLoad: true
                    }
                },
                renderer: function(data, cellMetaData, record){
                    var errors = record.validate();
                    if (!errors.isValid()) {
                        var msgs = errors.getByField('library_id');
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

                    if (data){
                        var store = Ext4.StoreMgr.get('sequenceanalysis||reference_libraries');
                        if (store){
                            var recIdx = store.findExact('rowid', data);
                            if (recIdx > -1){
                                return store.getAt(recIdx).get('name');
                            }
                        }
                    }

                    return data;
                }
            },{
                text: 'Readset Id',
                name: 'readset',
                queryMode: 'local',
                hidden: false,
                dataIndex: 'readset',
                width: 90,
                editable: true,
                editor: {
                    //TODO: add lookup feature
                    xtype: 'ldk-numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            },{
                text: 'Readset Name',
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
                name: 'application',
                width: 180,
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
                text: 'Sample Type',
                name: 'sampletype',
                width: 120,
                dataIndex: 'sampletype',
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
                name: 'subjectid',
                dataIndex: 'subjectid',
                editor: {
                    xtype: 'textfield',
                    allowBlank: true
                }
            },{
                text: 'Sample Date',
                name: 'sampledate',
                xtype: 'datecolumn',
                format: 'Y-m-d',
                dataIndex: 'sampledate',
                width: 160,
                editor: {
                    xtype: 'datefield',
                    format: 'Y-m-d',
                    allowBlank: true
                }
            },{
                text: 'Instrument Run Id',
                name: 'instrument_run_id',
                dataIndex: 'instrument_run_id',
                width: 140,
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
            },{
                text: 'File Id',
                name: 'fileId',
                hidden: true,
                dataIndex: 'fileId',
                editor: {
                    xtype: 'numberfield',
                    minValue: 1,
                    allowBlank: true
                }
            }]
        };
    },

    validateReadsets: function(store, recs){
        var readsetMap = {};
        var readsets = [];
        Ext4.each(recs, function(rec){
            rec.data.isValid = null;
            var id = rec.get('readset');
            if (id){
                readsetMap[id] = rec;
                readsets.push(id);
            }
        }, this);

        if (!readsets.length){
            return;
        }

        var sql = 'select  ' +
                'r.rowid,r.name,r.platform,r.application,r.sampletype,r.subjectid,r.sampledate,r.sampleid,r.barcode5,r.barcode3,r.instrument_run_id \n' +
                'from sequenceanalysis.sequence_readsets r \n';

        sql += 'WHERE rowid IN (' + readsets.join(',') + ')';

        LABKEY.Query.executeSql({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            sql: sql,
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                var msgs = [];
                var rowMap = {};
                for (var i=0;i<results.rows.length;i++){
                    rowMap[results.rows[i].rowid] = results.rows[i];
                }

                //NOTE: dont use set() so we dont trigger an update event for any records
                Ext4.each(readsets, function(readset){
                    var record = readsetMap[readset];
                    if (!rowMap[readset]){
                        msgs.push('Unable to find readset with rowid: ' +  readset);
                        record.data.readset = null;
                        return;
                    }

                    var row = rowMap[readset];

                    //update row based on saved readset.  avoid firing event
                    Ext4.apply(record.data, {
                        readsetname: row.name,
                        platform: row.platform,
                        application: row.application,
                        sampleid: row.sampleid,
                        subjectid: row.subjectid,
                        sampledate: row.sampledate,
                        sampletype: row.sampletype,
                        instrument_run_id: row.instrument_run_id,
                        isValid: true
                    });

                }, this);

                if (msgs.length){
                    Ext4.Msg.alert('Error', msgs.join('<br>'));
                }

                this.down('#sampleGrid').getView().refresh();
            }
        });
    },

    initFiles: function(){
        this.fileNameMap = {};

        this.distinctFileStore = Ext4.create('Ext.data.ArrayStore', {
            fields: ['fileName'],
            idIndex: 0,
            data: []
        });

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
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

        this.fileNameStore = Ext4.create('Ext.data.Store', {
            fields: ['fileName', 'relPath', 'filePath', 'basename', 'container', 'containerPath', 'dataId', 'error']
        });
    },

    onFileLoad: function(response){
        LDK.Utils.decodeHttpResponseJson(response);

        if (response.responseJSON.validationErrors.length){
            Ext4.Msg.hide();
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
        this.populateSamples();

        Ext4.Msg.hide();
    },

    populateSamples: function(){
        var sampleGrid = this.down('#sampleGrid');
        sampleGrid.store.removeAll();

        //find file names
        var filenames = [];
        this.fileNameStore.each(function(rec){
            filenames.push(rec.get('fileName'))
        }, this);

        Ext4.each(filenames, function(fn){
            var info = this.fileNameMap[fn];

            var obj = {fileName: fn};
            var error = false;
            if (info){
                obj.fileId = info.dataId;
                if (info.error)
                    error = true;
            }

            if (!error)
                sampleGrid.store.add(sampleGrid.store.createModel(obj));
        }, this);

        this.fireEvent('dataready');
    },

    onSubmit: function(btn){
        var json = this.getJsonParams();
        if (!json)
            return;

        this.startAnalysis(json);
    },

    getJsonParams: function(ignoreErrors){
        var fields = this.callParent(arguments);

        if (!fields)
            return;

        var errors = Ext4.create('Ext.data.Errors');
        this.down('#sampleGrid').getStore().each(function(r, sampleIdx) {
            var recErrors = r.validate();
            if (recErrors.getCount()){
                errors.add(recErrors.getRange());
            }

            fields['readset_' + sampleIdx] = r.data;
        }, this);

        //then append each section
        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            Ext4.apply(fields, s.toJSON());
        }, this);

        if (errors.getCount() && !ignoreErrors){
            Ext4.Msg.alert('Error', 'There are ' + errors.getCount() + ' errors.  Please review the cells highlighted in red.  Note: you can hover over the cell for more information on the issue.');
            return;
        }

        //make sure input files are valid
        var total = 0;
        this.fileNameStore.each(function(rec){
            if (!rec.get('error'))
                total++;
        }, this);

        if (!total && !ignoreErrors){
            Ext4.Msg.alert('Error', 'All input files had errors and cannot be used.  Please hover over the red cells near the top of the page to see more detail on these errors');
            return;
        }

        var totalErrors = 0;
        fields.inputFiles = [];
        this.down('#sampleGrid').getStore().each(function(rec) {
            if (rec.get('error'))
                totalErrors++;
            else {
                if (!rec.get('fileId')) {
                    var recIdx = this.fileNameStore.find('fileName', rec.get('fileName'));
                    if (recIdx > -1) {
                        var r = this.fileNameStore.getAt(recIdx);
                        fields.inputFiles.push({
                            fileName: r.get('fileName'),
                            relPath: r.get('relPath')
                        });
                    }
                }
                else {
                    fields.inputFiles.push({dataId: rec.get('fileId')});
                }
            }
        }, this);

        return fields;
    },

    updateColWidth: function(){
        var readsetGrid = this.down('#sampleGrid');
        readsetGrid.reconfigure();

        var width = 60;
        Ext4.Array.forEach(readsetGrid.columns, function(c){
            if (c.isVisible()){
                width += c.getWidth();
            }
        }, this);

        this.doResize(width);
    },

    showExcelImportWin: function(btn){
        Ext4.create('Ext.window.Window', {
            fileNameStore: this.fileNameStore,
            sampleStore: this.down('#sampleGrid').store,
            sequencePanel: this,
            width: 800,
            modal: true,
            closeAction: 'destroy',
            scope: this,
            title: 'Add Metadata',
            items: [{
                border: false,
                bodyStyle:'padding:5px',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'This allows you to add metadata from an excel file.  Just download the template using the link below, fill it out, then cut/paste it into the box below.  Note that all fields are case-sensitive and the import will fail if the case does not match.',
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
                        Ext4.each(this.down('#sampleGrid').columns, function(col){
                            if (!col.hidden) {
                                header.push(col.text);
                                dataIndexes.push(col.dataIndex);
                            }
                        }, this);

                        var data = [header];
                        this.down('#sampleGrid').store.each(function(rec){
                            var row = [];
                            Ext4.Array.forEach(dataIndexes, function(di){
                                row.push(Ext4.isEmpty(rec.get(di)) ? null : rec.get(di));
                            }, this);

                            data.push(row);
                        }, this);

                        LABKEY.Utils.convertToExcel({
                            fileName : 'AlignmentImport_' + Ext4.util.Format.date(new Date(), 'Y-m-d H_i_s') + '.xls',
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
                        if (val == 'yes'){
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

                text = Ext4.String.trim(text);
                var data = LDK.Utils.CSVToArray(text, '\t');
                var columns = [];
                var errors = [];

                //translate column text into name
                var cols = this.sequencePanel.down('#sampleGrid').columns;
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
                var success = true;
                Ext4.each(data, function(row){
                    var obj = {};

                    if (!success){
                        return false;
                    }

                    Ext4.each(columns, function(col, idx){
                        if (col.hidden === true){
                            errors.push('The column: ' + col.text + ' cannot be set.');
                            return;
                        }

                        var value = row[idx];

                        if (col.editor){
                            var editor = Ext4.ComponentManager.create(col.editor);

                            if (col.editor.store && !Ext4.isEmpty(value)){
                                //proxy for store creation
                                if (!col.editor.store.find) {
                                    col.editor.store.autoLoad = true;
                                    col.editor.store = Ext4.data.AbstractStore.create(col.editor.store);
                                }

                                if (col.editor.store.isLoading()){
                                    console.log('waiting for store load');
                                    col.editor.store.on('load', function(){
                                        this.processExcel(win);
                                    }, this, {single: true});

                                    success = false;
                                    return;  //allow us to proceed through all columns in case more stores need to load
                                }

                                var recIdx = col.editor.store.find(col.editor.valueField, value, null, false, false);

                                //attempt to resolve by displayField
                                if (recIdx === -1) {
                                    recIdx = col.editor.store.find(col.editor.displayField, value, null, false, false);
                                }

                                if (recIdx === -1) {
                                    errors.push('Invalid value for field ' + col.text + ': ' + value);
                                }
                                else {
                                    //ensure correct case
                                    value = col.editor.store.getAt(recIdx).get(col.editor.valueField);
                                }
                            }
                        }

                        if (value && col.dataIndex === 'readset' && !Ext4.isNumeric(value)) {
                            errors.push('Readset Id should be an integer: ' + value);
                        }

                        if (!Ext4.isEmpty(value)){
                            obj[col.dataIndex] = value;
                        }
                    }, this);

                    if (!LABKEY.Utils.isEmptyObj(obj))
                        toAdd.push(this.sampleStore.createModel(obj));
                }, this);

                if (!success){
                    return;
                }

                if (errors.length){
                    errors = Ext4.unique(errors);
                    Ext4.Msg.alert('Error', errors.join('<br>'));
                    return;
                }

                win.close();
                textarea.reset();

                if (!toAdd.length){
                    Ext4.Msg.alert('Error', 'No rows to add');
                    return;
                }

                this.sampleStore.removeAll();
                this.sampleStore.add(toAdd);

                this.sampleStore.each(function(r){
                    r.validate();
                }, this);
            }
        }).show(btn);
    }
});