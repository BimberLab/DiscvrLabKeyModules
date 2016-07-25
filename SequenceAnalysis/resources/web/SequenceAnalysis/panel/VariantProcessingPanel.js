/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.VariantProcessingPanel', {
	extend: 'SequenceAnalysis.panel.BaseSequencePanel',
	analysisController: 'sequenceanalysis',
	alias: 'widget.sequenceanalysis-variantprocessingpanel',
	splitJobs: true,
	statics: {
		TASKID: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline'
	},

	taskId: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline',

	initComponent: function(){
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

					if (errors.length){
						alert('The following alignments lack a file and will be skipped: ' + errorNames.join(', '));
					}

					this.libraryIds = Ext4.unique(libraryIds);

					this.checkProtocol();
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
					'<table class="fileNames"><tr class="fileNames"><td>File Id</td><td>Description</td><td>Readset Name</td><td>VCF File</td><td>Genome</td><td>Folder</td></tr>',
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
		items.push({
			xtype: 'sequenceanalysis-analysissectionpanel',
			title: 'Variant Preprocessing',
			stepType: 'variantProcessing',
			sectionDescription: 'This steps in this section will act on VCF files.  They will take an input VCF, perform an action such as annotation or filtering, and produce an output VCF.  The steps will be executed in the order listed.  Use the button below to add steps.',
			toolConfig: results
		});

		items.push({
			xtype: 'panel',
			style: 'padding-bottom: 0px;',
			width: '100%',
			border: false,
			items: [{
				border: false,
				width: '100%',
				style: 'text-align: center',
				html: 'Powered By DISCVR-Seq.  <a href="https://github.com/bbimber/discvr-seq/wiki">Click here to learn more.</a>'
			}]
		});

		this.remove(panel);
		this.add(items);

		var btn = this.down('#copyPrevious');
		btn.handler.call(this, btn);
	},
	
	onSubmit: function(){
		LABKEY.Ajax.request({
			url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'runSequenceHandler'),
			jsonData: {
				handlerClass: 'org.labkey.sequenceanalysis.analysis.CoverageDepthHandler',
				outputFileIds: this.outputFileIds,
				params: Ext4.encode(values)
			},
			scope: this,
			success: function(){
				Ext4.Msg.hide();

				window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
			},
			failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
		});
	}

});
