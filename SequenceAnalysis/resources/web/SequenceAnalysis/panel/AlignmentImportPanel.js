Ext4.define('SequenceAnalysis.panel.AlignmentImportPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',

    initComponent: function(){
        this.taskId = 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:AlignmentImportPipeline';

        Ext4.apply(this, {
            buttons: [{
                text: 'Import Data',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.add(this.getItemCfg());
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
                fieldLabel: 'Description',
                xtype: 'textarea',
                width: 600,
                height: 100,
                helpPopup: 'Description for this run, such as detail about the source of the alignments (optional)',
                itemId: 'protocolDescription',
                name: 'protocolDescription',
                allowBlank: true
            },{
                fieldLabel: 'Treatment of Input Files',
                xtype: 'combo',
                helpPopup: 'This determines how the input files are handled.  By default, files are moved to a standardized location and the originals deleted to save space.  However, you can choose to copy the BAMs to the new location, but leave the originals alone.  This is not usually recommended for space reasons.',
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
                        ['Leave originals alone', 'none']
                    ]
                }
            }]
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
                    '<table class="fileNames"><tr class="fileNames"><td>Id</td><td>Filename</td><td>Folder</td><td></td><td></td></tr>',
                    '<tpl for=".">',
                    '<tr class="file_list">',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{dataId}</a></td>',
                    '<td ',
                    '<tpl if="error">style="background: red;" data-qtip="{error}"</tpl>',
                    '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.containerPath, {rowId: values.dataId})]}" target="_blank">{fileName:htmlEncode}</a></td>',
                    //'<td>{RowId:htmlEncode}</td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", null, {})]}" target="_blank">{containerPath:htmlEncode}</a></td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {dataIds: values.dataId})]}" target="_blank">View FASTQC Report</a></td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "bamStatsReport", values["container/path"], {dataIds: values.dataId})]}" target="_blank">View BamStats Report</a></td>',
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
            forceFit: true,
            bodyStyle: 'padding: 0px;',
            fileNameStore: this.distinctFileStore,
            editingPluginId: 'cellediting',
            clicksToEdit: 1,
            multiSelect: true,
            border: true,
            stripeRows: true,
            selType: 'rowmodel',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: [
                    {name: 'fileName', allowBlank: false},
                    {name: 'library_id'},
                    {name: 'readset', allowBlank: false},
                    {name: 'readsetname'},
                    {name: 'platform', allowBlank: false},
                    {name: 'application', allowBlank: false},
                    {name: 'inputmaterial'},
                    {name: 'sampletype'},
                    {name: 'subjectid'},
                    {name: 'sampledate'},
                    {name: 'sampleid'},
                    {name: 'instrument_run_id'},
                    {name: 'fileId'}
                ],
                storeId: 'metadata',
                listeners: {
                    scope: this,
                    update: function(store, rec){
                        this.validateReadsets(store, [rec]);
                    },
                    add: this.validateReadsets
                }
            }),
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
                scope: this,
                handler : function(c){
                    Ext4.Msg.wait("Loading...");
                    var grid = c.up('#sampleGrid');
                    grid.getPlugin('cellediting').completeEdit();
                    var s = this.getSelectionModel().getSelection();
                    for (var i = 0, r; r = s[i]; i++){
                        this.getStore().remove(r);
                    }
                    Ext4.Msg.hide();
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
                        storeId: 'sequenceanalysis||reference_libraries',
                        sort: 'name',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        autoLoad: true
                    }
                },
                renderer: function(data, attrs, record){
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
                width: 40,
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
                mode: 'local',
                dataIndex: 'readsetname',
                editor: {
                    xtype: 'textfield',
                    allowBlank: false
                }
            },{
                text: 'Platform',
                name: 'platform',
                width: 80,
                mode: 'local',
                dataIndex: 'platform',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'platform',
                    valueField: 'platform',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_platforms',
                        autoLoad: true
                    })
                }
            },{
                text: 'Application',
                name: 'application',
                width: 80,
                mode: 'local',
                dataIndex: 'application',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: false,
                    forceSelection: true,
                    displayField: 'application',
                    valueField: 'application',
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_applications',
                        autoLoad: true
                    })
                }
            },{
                text: 'Sample Type',
                name: 'sampletype',
                width: 80,
                dataIndex: 'sampletype',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    forceSelection: true,
                    displayField: 'type',
                    valueField: 'type',
                    plugins: ['ldk-usereditablecombo'],
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'laboratory',
                        queryName: 'sample_type',
                        autoLoad: true
                    })
                }
            },{
                text: 'Input Material',
                name: 'inputmaterial',
                width: 80,
                dataIndex: 'inputmaterial',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    forceSelection: true,
                    displayField: 'material',
                    valueField: 'material',
                    plugins: ['ldk-usereditablecombo'],
                    editable: false,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'input_material',
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
                editor: {
                    xtype: 'datefield',
                    format: 'Y-m-d',
                    allowBlank: true
                }
            },{
                text: 'Instrument Run Id',
                name: 'instrument_run_id',
                dataIndex: 'instrument_run_id',
                editor: {
                    xtype: 'labkey-combo',
                    allowBlank: true,
                    displayField: 'name',
                    valueField: 'rowid',
                    editable: false,
                    queryMode: 'local',
                    showValueInList: true,
                    store: Ext4.create('LABKEY.ext4.data.Store', {
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'instrument_runs',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
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
                'r.rowid,r.name,r.platform,r.application,r.inputmaterial,r.sampletype,r.subjectid,r.sampledate,r.sampleid,r.barcode5,r.barcode3,r.fileid,r.fileid2,r.instrument_run_id,r.fileid2.name as fileName,r.fileid.name as fileName2 \n' +
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
                        inputmaterial: row.inputmaterial,
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
        var ret = this.getJsonParams();

        if (!ret)
            return;

        this.startAnalysis(ret.json, ret.distinctIds, ret.distinctNames);
    },

    getJsonParams: function(btn){
        var fields = this.callParent(arguments);

        if (!fields)
            return;

        var error;

        //we build a hash of samples to verify there are no duplicates
        var sampleMap = {};

        var cols = this.down('#sampleGrid').columns;
        var sampleInfo = this.down('#sampleGrid').getStore();
        var inputFiles = [];
        sampleInfo.each(function(r, sampleIdx){
            var key = [r.data['fileName']];
            key = key.join(";");

            inputFiles.push({name: r.data['fileName'], id: r.data['fileId']});

            if (sampleMap[key]){
                Ext4.Msg.alert('Error', 'Duplicate Sample: '+key+'. Please remove or edit rows in the \'Readsets\' section');
                error = 1;
                return false;
            }
            sampleMap[key] = true;

            if (!r.get('fileName') || !r.get('library_id') || (!r.get('readset') && !(r.get('platform') && r.get('readsetname')))){
                Ext4.Msg.alert('Error', 'For each file, you must provide the reference library name, and either the Id of an existing, unused readset or a name/platform to create a new one');
                error = 1;
            }

            if (r.get('readset')){
                var msg = 'A readset has already been created for the file: ' + r.get('fileName');
                if (r.get('fileName2'))
                    msg += ' and paired file: ' + r.get('fileName2');

                msg += ' and you cannot import a file twice.';

                Ext4.Msg.alert('Error', msg);
                error = 1;
            }

            if (error){
                return false;
            }

            fields['sample_'+sampleIdx] = r.data;
        }, this);

        if (error){
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

        var distinctIds = [];
        var distinctNames = [];
        Ext4.each(inputFiles, function(file){
            if (!file.id)
                distinctNames.push(file.name);
            else
                distinctIds.push(file.id)
        }, this);

        return {
            json: fields,
            distinctIds: distinctIds,
            distinctNames: distinctNames
        };
    }
});