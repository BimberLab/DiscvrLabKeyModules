Ext4.define('SingleCell.panel.NimbleAppendPanel', {
	extend: 'Ext.form.FieldSet',
	alias: 'widget.singlecell-nimbleappendpanel',
    title: 'Nimble Genomes',

	genomeField: Ext4.widget({
		xtype: 'sequenceanalysis-genomefield',
		allowBlank: false
	}),
	initComponent: function(){
		Ext4.apply(this, {
			style: 'padding: 10px;margins: 5px;',
            minWidth: 650,
			border: true,
			items: [{
				html: 'This step will query nimble results for the selected genome(s). It will then append these results to the seurat object on the target assay.',
				maxWidth: 600,
				border: false,
				style: 'padding-bottom: 10px;'
			},{
				xtype: 'ldk-gridpanel',
				clicksToEdit: 1,
				width: 600,
				tbar: [{
					text: 'Add',
					handler: function(btn){
						var grid = btn.up('grid');
						var store = grid.store;
						var recs = store.add(store.createModel({
							genomeId: null,
							targetAssay: 'Nimble'
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
					fields: ['genomeId', 'targetAssay']
				},
				columns: [{
					dataIndex: 'genomeId',
					width: 325,
					header: 'Genome',
					editor: this.genomeField,
					renderer: function(val){
						const store = this.up('singlecell-nimbleappendpanel').genomeField.store
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
					dataIndex: 'targetAssay',
					width: 175,
					header: 'Target Assay',
					editor: {
						xtype: 'textfield',
						allowBlank: false
					}
				}]
			}]
		});

		this.callParent(arguments);
	},

	getValue: function(){
		var ret = [];
		this.down('ldk-gridpanel').store.each(function(r, i) {
			ret.push([r.data.genomeId, r.data.targetAssay]);
		}, this);

		return Ext4.isEmpty(ret) ? null : JSON.stringify(ret);
	},

	getErrors: function(){
		var msgs = [];
		this.down('ldk-gridpanel').store.each(function(r, i){
			if (!r.data.genomeId){
				msgs.push('Missing genome for one or more rows');
			}

			if (!r.data.targetAssay){
				msgs.push('Missing target assay for one or more rows');
			}
		}, this);

		if (!this.down('ldk-gridpanel').store.getCount()){
			msgs.push('Must provide at least one genome for nimble');
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
					targetAssay: row[1]
				});
				grid.store.add(rec);
			}, this);
		}
		else {
			grid.store.removeAll();
		}
	}
});