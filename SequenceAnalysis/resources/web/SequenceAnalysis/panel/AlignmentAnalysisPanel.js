Ext4.define('SequenceAnalysis.panel.AlignmentAnalysisPanel', {
    extend: 'SequenceAnalysis.panel.SequenceAnalysisPanel',
    jobType: 'alignmentAnalysis',

    initComponent: function(){
        this.callParent(arguments);

        this.splitJobs = false;
    },

    onDataLoad: function(results){
        var panel = this.down('#analysisOptions');

        var items = [this.getFilePanelCfg(), this.getProtocolPanelCfg()];
        items.push({
            xtype: 'sequenceanalysis-analysissectionpanel',
            title: 'Step 2: Downstream Analysis',
            sectionDescription: 'This steps in this section will act on each BAM file.  These steps may be highly application-specific, so please read the description of each step for more information.',
            stepType: 'analysis',
            toolConfig: results
        });

        items.push(this.getJobResourcesCfg(results));

        this.remove(panel);
        this.add(items);
    },

    //loads the exp.RowId for each file
    initFiles: function(sql){
        this.analysesStore = Ext4.create("LABKEY.ext4.data.Store", {
            containerPath: this.queryContainer,
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_analyses',
            columns: 'rowid,description,readset,readset/name,readset/platform,container,container/displayName,container/path,alignmentfile,alignmentfile/name,alignmentfile/fileexists,readset/subjectid,readset/sampleid,library_id,library_id/name',
            metadata: {
                queryContainerPath: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    defaultValue: this.queryContainerPath
                }
            },
            autoLoad: true,
            filterArray: [
                LABKEY.Filter.create('rowid', this.analyses.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
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
                        if (rec.get('alignmentfile')){
                            if (!rec.get('alignmentfile/fileexists')){
                                errors.push(rec);
                                errorNames.push(rec.get('readset/name'));
                            }
                            else {
                                this.fileIds.push(rec.get('alignmentfile'));
                                this.fileNames.push(rec.get('alignmentfile/name'));
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

                    if (errors.length){
                        alert('The following alignments lack a file and will be skipped: ' + errorNames.join(', '));
                    }

                    this.libraryIds = Ext4.unique(libraryIds);

                    this.down('#alignmentCount').update('Alignments to be analyzed: ' + store.getCount());
                }
            }
        });
    },

    getFilePanelCfg: function(){
        return {
            xtype: 'panel',
            border: true,
            title: 'Selected Alignments',
            itemId: 'files',
            width: 'auto',
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            items: [{
                html: 'Below are the alignments that will be analyzed.',
                itemId: 'alignmentCount',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'dataview',
                store: this.analysesStore,
                itemSelector: 'tr.file_list',
                tpl: [
                    '<table class="fileNames"><tr class="fileNames"><td>Analysis Id</td><td>Description</td><td>Readset Name</td><td>Platform</td><td>Alignment File</td><td>Genome</td><td>Folder</td><td></td></tr>',
                    '<tpl for=".">',
                    '<tr class="file_list">',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_analyses", "query.rowId~eq": values.rowid})]}" target="_blank">{rowid:htmlEncode}</a></td>',
                    '<td>{description:htmlEncode}</td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("query", "executeQuery", values.queryContainerPath, {schemaName: "sequenceanalysis", "query.queryName":"sequence_readsets", "query.rowId~eq": values.readset})]}" target="_blank">{[Ext4.htmlEncode(values["readset/name"])]}</a></td>',
                    '<td>{[Ext4.htmlEncode(values["readset/platform"])]}</td>',
                    '<td',
                    '<tpl if="values.alignmentfile && !values[\'alignmentfile/fileexists\']"> style="background: red;" data-qtip="File does not exist"</tpl>',
                    '><a href="{[LABKEY.ActionURL.buildURL("experiment", "showData", values.queryContainerPath, {rowId: values.alignmentfile})]}" target="_blank">{[Ext4.htmlEncode(values["alignmentfile/name"])]}</a></td>',

                    '<td>{[Ext4.htmlEncode(values["library_id/name"])]}</td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("project", "start", values["container/path"], {})]}" target="_blank">{[Ext4.htmlEncode(values["container/displayName"])]}</a></td>',
                    '<td><a href="{[LABKEY.ActionURL.buildURL("sequenceanalysis", "fastqcReport", values["container/path"], {dataIds: values.alignmentfile})]}" target="_blank">FASTQC Report</a></td>',
                    '</tr>',
                    '</tpl>',
                    '</table>'
                ]
            }]
        }
    },

    getJsonParams: function(ignoreErrors){
        var errors = this.getErrors();
        if (!ignoreErrors && errors.length){
            Ext4.Msg.alert('Error', errors.join('<br>'));
            return;
        }

        var json = {
            version: 2
        };

        //first add the general params
        Ext4.apply(json, this.down('#runInformation').getForm().getFieldValues());

        //and sample information
        json.analysisIds = this.analyses;

        //then append each section
        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            Ext4.apply(json, s.toJSON());
        }, this);

        return json;
    }
});