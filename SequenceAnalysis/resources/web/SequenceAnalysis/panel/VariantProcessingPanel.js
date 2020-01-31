/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.VariantProcessingPanel', {
	extend: 'SequenceAnalysis.panel.BaseSequencePanel',
	alias: 'widget.sequenceanalysis-variantprocessingpanel',
	showGenotypeGVCFs: false,
	jobType: 'variantProcessing',

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
				value: 'VariantProcessing_' + (Ext4.Date.format(new Date(), 'Ymd')),
				maskRe: new RegExp('[A-Za-z0-9_]')
			},{
				fieldLabel: 'Description',
				xtype: 'textarea',
				width: 600,
				height: 100,
				helpPopup: 'Description for this analysis (optional)',
				name: 'jobDescription',
				allowBlank:true
			},{
				xtype: 'sequenceanalysis-variantscattergatherpanel',
				width: 620,
				defaultFieldWidth: 600,
				bodyStyle: ''
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

        results.variantCalling = [{
            description: 'This will run GATK\'s GenotypeGVCFs on a set of GVCF files',
            label: 'GenotypeGVCFs',
            name: 'GenotypeGVCFs',
            parameters: [{
                fieldXtype: 'textfield',
                name: 'fileBaseName',
                label: 'Filename',
                description: 'This is the basename that will be used for the output gzipped VCF',
                commandLineParam: false,
				additionalExtConfig: {
					allowBlank: false
				},
                defaultValue: null
            },{
                fieldXtype: 'ldk-integerfield',
                name: 'stand_call_conf',
                label: 'Threshold For Calling Variants',
                description: 'The minimum phred-scaled confidence threshold at which variants should be called',
                commandLineParam: '-stand_call_conf',
                defaultValue: 30
            },{
                fieldXtype: 'ldk-integerfield',
                name: 'max_alternate_alleles',
                label: 'Max Alternate Alleles',
                description: 'Maximum number of alternate alleles to genotype',
                commandLineParam: '--max_alternate_alleles',
                defaultValue: 12
            },{
                fieldXtype: 'checkbox',
                name: 'includeNonVariantSites',
                label: 'Include Non-Variant Sites',
                description: 'If checked, all sites will be output into the VCF, instead of just those where variants are detected.  This can dramatically increase the size of the VCF.',
                commandLineParam: '--includeNonVariantSites',
                defaultValue: false
			},{
				fieldXtype: 'ldk-expdatafield',
				name: 'forceSitesFile',
				label: 'Force Output At Sites',
				description: 'If provided, the output VCF will includes all intervals in this file (even if all samples are wild-type)',
				defaultValue: false
			},{
				fieldXtype: 'checkbox',
				name: 'allowOldRmsMappingData',
				label: 'Allow Old RMS Mapping Data',
				description: 'This must be checked to allow processing of gVCFs generated by GATK3.',
				defaultValue: false
			},{
				fieldXtype: 'checkbox',
				name: 'doCopyInputs',
				label: 'Copy gVCFs Locally',
				description: 'If checked, the gVCFs will be copied to the local working directory prior to running GenotypeVCFs.  This can be a good idea if a large number of input files are used.',
				defaultValue: false
            }]
        }];

		var ddGroup = Ext4.id() + '-dd';
		results.variantMerging = [{
			description: 'This will run GATK\'s CombineVariants on the set of VCF files',
			label: 'Merge Variants',
			name: 'CombineVCFs',
			parameters: [{
				fieldXtype: 'checkbox',
				name: 'doCombine',
				label: 'Combine VCFs Prior To Processing',
				description: 'If checked, the VCFs will be merged into a single VCF before processing.',
				defaultValue: false,
				additionalExtConfig: {
					listeners: {
						change: function (field, val) {
							val = !!val;

							var field1 = field.up('panel').down('textfield');
							var field2 = field.up('panel').down('multiselect');
							var field3 = field.up('panel').down('hidden');

							field1.isToolParam = val;
							field1.allowBlank = !val;

							field2.isToolParam = val;
							field3.isToolParam = val;

							field1.setVisible(val);
							field2.setVisible(val);
						}
					}
				}
			},{
				fieldXtype: 'textfield',
				name: 'fileBaseName',
				label: 'Filename',
				description: 'This is the basename that will be used for the output gzipped VCF',
				commandLineParam: false,
				additionalExtConfig: {
					hidden: true,
					isToolParam: false,
					width: 500,
					allowBlank: true //toggle value based on checkbox
				},
				defaultValue: null
			},{
				fieldXtype: 'hidden',
				name: 'doSplitJobs',
				label: 'Split Jobs',
				description: 'If combine is selected, jobs should not be split',
				commandLineParam: false,
				additionalExtConfig: {
					hidden: true,
					isToolParam: false,
					name: 'doSplitJobs',  //override the more specific name auto-assigned
					getToolParameterValue: function(){
						var ret = this.getValue();

						return ret ? JSON.parse(ret) : ret;
					}
				},
				defaultValue: false
			},{
				name: 'priority',
				fieldXtype: 'multiselect',
				additionalExtConfig: {
					hidden: true,
					isToolParam: false,
					store: this.outputFileStore,
					fieldLabel: 'Merge Priority Order',
					style: 'padding: 10px;padding-bottom: 30px;',
					border: true,
					width: 500,
					autoScroll: true,
					helpPopup: 'This is the priority order in which files will be merged, top being highest priority',
					displayField: 'name',
					valueField: 'rowid',
					multiSelect: false,
					dragGroup: ddGroup,
					dropGroup: ddGroup,
					getToolParameterValue: function(){
						var val = this.setupValue(this.store.getRange());
						val = val == null ? null : val.join(this.delimiter);

						return val;
					}
				}
			}]
		}];

		var items = [];
        if (this.showGenotypeGVCFs){
            items.push({
                xtype: 'sequenceanalysis-analysissectionpanel',
                title: 'Variant Calling',
                stepType: 'variantCalling',
                singleTool: true,
                sectionDescription: 'This section allows you to call variants from a set of gVCF inputs',
                toolConfig: results
            })
        }
        else {
			items.push({
				xtype: 'sequenceanalysis-analysissectionpanel',
				title: 'Merge VCFs',
				stepType: 'variantMerging',
				singleTool: false,
				toolIdx: 0,
				sectionDescription: 'This section allows you to optionally merge the input VCFs prior to processing',
				toolConfig: results
			})
		}

		items.push({
			xtype: 'sequenceanalysis-analysissectionpanel',
			title: 'Variant Preprocessing',
			stepType: 'variantProcessing',
			allowDuplicateSteps: true,
			sectionDescription: 'This steps in this section will act on VCF files.  They will take an input VCF, perform an action such as annotation or filtering, and produce an output VCF.  The steps will be executed in the order listed.  Use the button below to add steps.',
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
				html: 'Powered By DISCVR-Seq.  <a href="https://github.com/BimberLab/discvr-seq/wiki">Click here to learn more.</a>'
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
			handlerClass: 'org.labkey.sequenceanalysis.' + (this.showGenotypeGVCFs ? 'analysis.GenotypeGVCFHandler' : 'pipeline.ProcessVariantsHandler'),
			outputFileIds: this.outputFileIds,
			params: Ext4.encode(values)
		}

		if (Ext4.isDefined(values.doSplitJobs)) {
			json.doSplitJobs = !!values.doSplitJobs;
		}

		var actionName = 'runSequenceHandler';
		var failedTools = [];
		if (values.scatterGatherMethod && values.scatterGatherMethod !== 'none') {
			json.scatterGather = true;
			actionName = 'runVariantProcessing';

			var steps = this.down('sequenceanalysis-analysissectionpanel[stepType="variantProcessing"]').getActiveSteps();
			if (!Ext4.Object.isEmpty(steps)) {
				for (var name in steps) {
					var tool = steps[name].toolConfig;
					if (!tool.supportsScatterGather) {
						failedTools.push(tool.label);
					}
				}
			}

			failedTools = Ext4.unique(failedTools);
			if (failedTools.length) {
				Ext4.Msg.alert('Error', 'The follow tools do not support scatter/gather: ' + failedTools.join((', ')));
				return;
			}
		}

		LABKEY.Ajax.request({
			url: LABKEY.ActionURL.buildURL('sequenceanalysis', actionName),
			jsonData: json,
			scope: this,
			success: function(){
				Ext4.Msg.hide();

				window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
			},
			failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
		});
	},

	getJsonParams: function(ignoreErrors){
		var errors = this.getErrors();
		if (errors.length && !ignoreErrors){
			Ext4.Msg.alert('Error', errors.join('<br>'));
			return;
		}

		var json = {
			version: 2
		};

		//first add the general params
		Ext4.apply(json, this.down('#runInformation').getForm().getValues());

		//then append each section
		var sections = this.query('sequenceanalysis-analysissectionpanel');
		Ext4.Array.forEach(sections, function(s){
			Ext4.apply(json, s.toJSON());
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

		errors = Ext4.unique(errors);
		return errors;
	}
});
