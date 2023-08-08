Ext4.define('SequenceAnalysis.panel.VariantFilterPanel', {
	extend: 'Ext.form.FieldSet',
	alias: 'widget.sequenceanalysis-variantfilterpanel',
	mode: 'FILTER',
	showFilterName: true,
    title: 'Filters',

	initComponent: function(){
		Ext4.apply(this, {
			style: 'padding: 10px;margins: 5px;',
            minWidth: 1150,
			border: true,
			items: [{
				html: 'Variant selecting/filtering is a more advanced GATK feature; however, we have tried to simplify the more common filters.  It uses <a href="https://software.broadinstitute.org/gatk/guide/article?id=1255" target="_blank">JEXL syntax</a> to select or filter individual genotypes based on thresholds.  Should you include more advanced expressions, be aware that this tool does very little checking on the validity of your input.<br><br>' +
                'GATK has several tutorials that help to: <a href="https://software.broadinstitute.org/gatk/guide/article?id=6925" target="_blank">explain hard filters</a> and <a href="https://software.broadinstitute.org/gatk/guide/article?id=3225" target="_blank">recommendations (which can change)</a>.<br><br>' +
				'Please note: you should not wrap your filter expressions in quotes (either single or double).  You can use quotes within the expression, if you would normally do this with GATK, for example around sample names.',
				maxWidth: 1000,
				border: false,
				style: 'padding-bottom: 10px;'
			},{
				xtype: 'ldk-gridpanel',
				clicksToEdit: 1,
				width: 1060,
				tbar: [{
					text: 'Add',
					menu: [{
						text: 'Quality-By-Depth',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('QualityFilter', 'vc.hasAttribute(\'QD\') && QD < 2.0');
						}
					},{
						text: 'Strand Bias (FisherStrand)',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('FisherStrand', 'vc.hasAttribute(\'FS\') && ((!vc.isIndel() && FS > 60.0) || (vc.isIndel() && FS > 200.0))');
						}
					},{
						text: 'StrandOddsRatio',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('StrandOddsRatio', 'vc.hasAttribute(\'SOR\') && ((!vc.isIndel() && SOR > 3.0) || (vc.isIndel() && SOR > 10.0))');
						}
					},{
						text: 'MappingQuality',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('MappingQuality', '(!vc.isIndel() && MQ < 40.0)');
						}
					},{
						text: 'MQRankSum',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('MQRankSum', 'vc.hasAttribute(\'MQRankSum\') && (!vc.isIndel() && MQRankSum < -12.5)');
						}
					},{
						text: 'ReadPosRankSum',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('ReadPosRankSum', 'vc.hasAttribute(\'ReadPosRankSum\') && ((!vc.isIndel() && ReadPosRankSum < -8.0) || (vc.isIndel() && ReadPosRankSum < -20.0))');
						}
					},{
						text: 'High Depth',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('Depth100', 'vc.hasAttribute(\'DP\') && DP > 100.0');
						}
					},{
						text: 'High IMPACT',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('High-IMPACT', 'IMPACT == \'HIGH\'');
						}
					},{
						text: 'CADD PH',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('CADD_PH', 'CADD_PH > 25');
						}
					},{
						text: 'No Genotypes Called',
						scope: this,
						handler: function(gridBtn){
							this.addFilter('NoneCalled', 'vc.getCalledChrCount() == 0');
						}
					},{
						text: 'Advanced Filter (JEXL)',
						scope: this,
						handler: function(){
							this.addFilter(null, null);
						}
					}]
				},LABKEY.ext4.GRIDBUTTONS.DELETERECORD()],
				store: {
					type: 'array',
					fields: ['filterName', 'jexl', 'invert']
				},
				columns: [{
					dataIndex: 'filterName',
					width: 200,
					header: 'Filter Name',
                    hidden: this.showFilterName === false,
					editor: {
						xtype: 'textfield',
						allowBlank: false,
						stripCharsRe: /(^['"]+)|(['"]+$)/g
					}
				},{
					dataIndex: 'jexl',
					width: 700,
					header: 'Filter Expression',
					editor: {
						xtype: 'textfield',
						allowBlank: false,
						stripCharsRe: /(^["]+)|(["]+$)/g
					}
				//}, {
				//	dataIndex: 'invert',
				//	width: 100,
				//	xtype: 'checkcolumn',
				//	header: 'Invert Filter?'
				}]
			}]
		});

		this.callParent(arguments);
	},

	addFilter: function(filterName, jexl){
		var grid = this.down('grid');
		var store = grid.store;
		var recs = store.add(store.createModel({
			filterName: filterName,
			jexl: jexl
		}));

		var idx = store.indexOf(recs[0]);
		var cellEditing = grid.getPlugin(grid.editingPluginId);
		cellEditing.completeEdit();
        var jexlIdx = this.showFilterName ? 1 : 0;
		cellEditing.startEditByPosition({row: idx, column: jexlIdx});
	},

	getValue: function(){
		var ret = [];
		this.down('ldk-gridpanel').store.each(function(r, i) {
			ret.push([r.data.filterName, r.data.jexl, r.data.invert || false]);
		}, this);

		return Ext4.isEmpty(ret) ? null : ret;
	},

	getErrors: function(){
		var msgs = [];
		this.down('ldk-gridpanel').store.each(function(r, i){
			if (this.mode == 'FILTER' && !r.data.filterName){
				msgs.push('Missing filter name for one or more variant filters');
			}
			if (!r.data.jexl){
				msgs.push('Missing filter expression for one or more variant filters');
			}
		}, this);

		if (this.mode == 'FILTER' && !this.down('ldk-gridpanel').store.getCount()){
			msgs.push('Must provide at least one variant filter or remove this step');
		}

		return msgs;
	},

	setValue: function(val){
		var grid = this.down('ldk-gridpanel');
		if (val){
			if (!Ext4.isArray(val)){
				val = Ext4.JSON.decode(val);
			}

			Ext4.Array.forEach(val, function(row){
				var rec = grid.store.createModel({
					filterName: row[0],
					jexl: row[1]
				});
				grid.store.add(rec);
			}, this);
		}
		else {
			grid.store.removeAll();
		}
	}
});