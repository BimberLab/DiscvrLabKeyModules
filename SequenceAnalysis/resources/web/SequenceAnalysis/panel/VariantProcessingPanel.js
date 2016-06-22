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

	initFiles: function(){

	},

	onDataLoad: function(results){
		this.add([this.getProtocolPanelCfg(),{
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
	}

});
