Ext4.define('CancerStudies.panel.MutationMapperPanel', {
	alias: 'widget.cancerstudies-mutationmapperpanel',
	extend: 'LDK.panel.ContentResizingPanel',
	
	initComponent: function(){
		this.callParent(arguments);

		LABKEY.Query.selectRows({
			schemaName: 'study',
			queryName: 'variants',
			filterArray: this.filterArray,
			scope: this,
			failure: LDK.Utils.getErrorCallback(),
			success: this.onDataLoad
		})
	},

	onDataLoad: function(results){
		console.log(results);

		if (!results.rows.length){
			this.add({
				html: 'No mutations found',
				border: false
			});
		}
		else {
			
		}
	},
	
	doRenderView: function(){
		var standaloneView = new StandaloneMutationView({el: this.renderTarget});
		standaloneView.render();
		standaloneView.addInitCallback(this.processInput);
	},

	processInput: function (input){
		var parser = new MutationInputParser();
		
		// parse the provided input string
		var mutationData = parser.parseInput(input);
		var sampleArray = parser.getSampleArray();
		var geneList = parser.getGeneList();
		
		// No data to visualize...
		if (geneList.length == 0) {
			this.add({html: "No data to visualize. Please make sure your input format is valid."});
			return;
		}
		
		// customized table options
		var tableOpts = {
			columnVisibility: {
				startPos: function (util, gene) {
					if (util.containsStartPos(gene)) {
						return "visible";
					}
					else {
						return "hidden";
					}
				},
				endPos: function (util, gene) {
					if (util.containsEndPos(gene)) {
						return "visible";
					}
					else {
						return "hidden";
					}
				},
				variantAllele: function (util, gene) {
					if (util.containsVarAllele(gene)) {
						return "visible";
					}
					else {
						return "hidden";
					}
				},
				referenceAllele: function (util, gene) {
					if (util.containsRefAllele(gene)) {
						return "visible";
					}
					else {
						return "hidden";
					}
				},
				chr: function (util, gene) {
					if (util.containsChr(gene)) {
						return "visible";
					}
					else {
						return "hidden";
					}
				}
			},
			columnRender: {
				caseId: function(datum) {
					var mutation = datum.mutation;
					var caseIdFormat = MutationDetailsTableFormatter.getCaseId(mutation.get("caseId"));
					var vars = {};
					vars.linkToPatientView = mutation.get("linkToPatientView");
					vars.caseId = caseIdFormat.text;
					vars.caseIdClass = caseIdFormat.style;
					vars.caseIdTip = caseIdFormat.tip;
					var templateFn;
					if (mutation.get("linkToPatientView"))
					{
						templateFn = _.template($("#mutation_table_case_id_template").html());
					}
					else
					{
						templateFn = _.template($("#standalone_mutation_case_id_template").html());
					}
					return templateFn(vars);
				}
			}
		};
		
		// customized main mapper options
		var options = {
			el: "#standalone_mutation_details",
			data: {
				geneList: geneList,
				sampleList: sampleArray
			},
			proxy: {
				mutationProxy: {
					options: {
						initMode: "full",
						data: mutationData
					}
				}
			},
			view: {
				mutationTable: tableOpts
			}
		};
		
		//TODO:
		options = jQuery.extend(true, cbio.util.baseMutationMapperOpts(), options);
	
		// init mutation mapper
		var mutationMapper = new MutationMapper(options);
		mutationMapper.init();
	}
});