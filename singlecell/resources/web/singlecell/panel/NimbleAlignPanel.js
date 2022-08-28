Ext4.define('SingleCell.panel.NimbleAlignPanel', {
	extend: 'Ext.form.FieldSet',
	alias: 'widget.singlecell-nimblealignpanel',
    title: 'Nimble Genomes',

	genomeField: Ext4.widget({
		xtype: 'sequenceanalysis-genomefield',
		allowBlank: false
	}),
	initComponent: function(){
		Ext4.apply(this, {
			style: 'padding: 10px;margins: 5px;',
            minWidth: 750,
			border: true,
			items: [{
				html: 'This step will first run cellranger using the primary genome (selected above). The resulting BAM will be passed to nimble, which will align using each of the genomes selected below, creating supplemental feature counts. By default, the original cellranger output is discarded.',
				maxWidth: 700,
				border: false,
				style: 'padding-bottom: 10px;'
			},{
				xtype: 'ldk-gridpanel',
				clicksToEdit: 1,
				width: 700,
				tbar: [{
					text: 'Add',
					handler: function(btn){
						var grid = btn.up('grid');
						var store = grid.store;
						var recs = store.add(store.createModel({
							genomeId: null,
							template: 'lenient',
							grouping: false
						}));

						var idx = store.indexOf(recs[0]);
						var cellEditing = grid.getPlugin(grid.editingPluginId);
						cellEditing.completeEdit();
						var jexlIdx = this.showFilterName ? 1 : 0;
						cellEditing.startEditByPosition({row: idx, column: jexlIdx});
					}
				},LABKEY.ext4.GRIDBUTTONS.DELETERECORD()],
				store: {
					type: 'array',
					fields: ['genomeId', 'template', 'grouping', 'scoreThreshold']
				},
				columns: [{
					dataIndex: 'genomeId',
					width: 510,
					header: 'Genome',
					editor: this.genomeField,
					renderer: function(val){
						const store = this.up('singlecell-nimblealignpanel').genomeField.store
						if (val && store) {
							const recIdx = store.find('rowid', val);
							return recIdx === -1 ? '[' + val + ']' : store.getAt(recIdx).get('name');
						}
						else if (val) {
							return '[' + val + ']';
						}
						else {
							return '[None]';
						}
					}
				},{
					dataIndex: 'template',
					width: 175,
					header: 'Alignment Template',
					editor: {
						xtype: 'ldk-simplecombo',
						allowBlank: false,
						queryMode: 'local',
						forceSelection: true,
						storeValues: 'strict;lenient',
						initialValues: 'lenient',
						delimiter: ';'
					}
				},{
					dataIndex: 'grouping',
					width: 175,
					header: 'Group By Lineage',
					editor: {
						xtype: 'checkbox'
					}
				},{
					dataIndex: 'scoreThreshold',
					width: 175,
					header: 'Score Threshold',
					editor: {
						xtype: 'ldk-integerfield',
						minValue: 0
					}
				}]
			}]
		});

		this.callParent(arguments);
	},

	getValue: function(){
		var ret = [];
		this.down('ldk-gridpanel').store.each(function(r, i) {
			ret.push([r.data.genomeId, r.data.template, r.data.grouping || false, r.data.scoreThreshold || '']);
		}, this);

		return Ext4.isEmpty(ret) ? null : JSON.stringify(ret);
	},

	getErrors: function(){
		var msgs = [];
		this.down('ldk-gridpanel').store.each(function(r, i){
			if (!r.data.genomeId){
				msgs.push('Missing genome for one or more rows');
			}

			if (!r.data.template){
				msgs.push('Missing alignment template for one or more rows');
			}
		}, this);

		if (!this.down('ldk-gridpanel').store.getCount()){
			msgs.push('Must provide at least one genome for nimble to align');
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
					genomeId: row[0],
					template: row[1],
					grouping: row[2],
					scoreThreshold: row.length > 3 ? row[3] : null
				});
				grid.store.add(rec);
			}, this);
		}
		else {
			grid.store.removeAll();
		}
	}
});