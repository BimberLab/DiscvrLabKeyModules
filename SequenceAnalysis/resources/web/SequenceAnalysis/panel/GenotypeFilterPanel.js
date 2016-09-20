Ext4.define('SequenceAnalysis.panel.GenotypeFilterPanel', {
	extend: 'Ext.panel.Panel',
	alias: 'widget.sequenceanalysis-genotypefilterpanel',


	initComponent: function(){
		Ext4.apply(this, {
			width: '100%',
			border: false,
			items: [{
				html: 'Genotype filtering is a more advanced GATK feature; however, we have tried to simplify the more common filters.  It uses <a href="https://software.broadinstitute.org/gatk/guide/article?id=1255" target="_blank">JEXL syntax</a> to filter individual genotypes based on thresholds.  Should you include more advanced expressions, be aware that this tool does very little checking on the validity of those filters.',
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
						text: 'Depth Filter',
						scope: this,
						handler: this.addDepthFilter
					}, {
						text: 'Quality Filter',
						scope: this,
						handler: this.addQualityFilter
					}, {
						text: 'Advanced Filter (JEXL)',
						scope: this,
						handler: this.addJexlFilter
					}]
				},LABKEY.ext4.GRIDBUTTONS.DELETERECORD()],
				store: {
					type: 'array',
					fields: ['filterName', 'jexl']
				},
				columns: [{
					dataIndex: 'filterName',
					width: 200,
					header: 'Filter Name',
					editor: {
						xtype: 'textfield',
						allowBlank: false
					}
				},{
					dataIndex: 'jexl',
					width: 700,
					header: 'Filter Expression',
					editor: {
						xtype: 'textfield',
						allowBlank: false
					}
				}]
			}]
		});

		this.callParent(arguments);
	},

	addDepthFilter: function(gridBtn){
		this.createFilterWindow(gridBtn, 'Depth', 'DP');
	},

	addQualityFilter: function(gridBtn){
		this.createFilterWindow(gridBtn, 'Quality', 'GQ');
	},

	addJexlFilter: function(gridBtn){
		var grid = gridBtn.up('grid');
		var store = grid.store;
		var recs = store.add(store.createModel({
			filterName: null,
			jexl: null
		}));

		var idx = store.indexOf(recs[0]);
		var cellEditing = grid.getPlugin(grid.editingPluginId);
		cellEditing.completeEdit();
		cellEditing.startEditByPosition({row: idx, column: 1});
	},

	createFilterWindow: function(gridBtn, noun, filterName){
		var grid = gridBtn.up('grid');
		var operatorMap = {
			'>': 'GT',
			'>=': 'GTE',
			'<': 'LT',
			'<=': 'LTE'
		};

		Ext4.create('Ext4.window.Window', {
			title: 'Add ' + noun + ' Filter',
			bodyStyle: 'padding: 5px;',
			listeners: {
				scope: this,
				show: function(win){
					new Ext4.util.KeyNav(win.getEl(), {
						"enter" : function(e){
							var btn = win.down('#submit');
							btn.handler.call(this, btn);
						},
						scope : this
					});
				}
			},
			items: [{
				xtype: 'form',
				border: false,
				defaults: {
					border: false
				},
				items: [{
					html: 'Filter any genotypes where ' + noun.toLowerCase() + ':',
					style: 'padding-bottom: 10px;'
				},{
					layout: 'hbox',
					border: false,
					defaults: {
						border: false,
						style: 'margin-left: 5px;'
					},
					items: [{
						xtype: 'ldk-simplecombo',
						fieldLabel: 'Operator',
						labelAlign: 'top',
						forceSelection: true,
						itemId: 'operator',
						storeValues: ['<', '<=', '>', '>='],
						width: 100,
						helpPopup: 'This can be any numeric operator, such as <, >, <=, etc.',
						allowBlank: false
					}, {
						xtype: 'ldk-numberfield',
						fieldLabel: noun,
						labelAlign: 'top',
						itemId: 'filter',
						allowBlank: false,
						width: 100
					}]
				}]
			}],
			buttons: [{
				text: 'Submit',
				itemId: 'submit',
				scope: this,
				handler: function(btn){
					var form = btn.up('window').down('form');
					if (!form.isValid()){
						Ext4.Msg.alert('Error', 'One or more fields is invalid');
						return;
					}

					var jexl = ' ' + filterName + ' ' + form.down('#operator').getValue() + ' ' + form.down('#filter').getValue();

					var store = grid.store;
					store.add(store.createModel({
						filterName: filterName + '-' + (operatorMap[form.down('#operator').getValue()]) + form.down('#filter').getValue(),
						jexl: jexl
					}));

					btn.up('window').close();
				}
			},{
				text: 'Close',
				handler: function(btn){
					btn.up('window').close();
				}
			}]
		}).show();
	},

	getValue: function(){
		var ret = [];
		this.down('ldk-gridpanel').store.each(function(r, i) {
			ret.push([r.data.filterName, r.data.jexl]);
		}, this);

		return Ext4.isEmpty(ret) ? null : ret;
	},

	getErrors: function(){
		var msgs = [];
		this.down('ldk-gridpanel').store.each(function(r, i){
			if (!r.data.filterName){
				msgs.push('Missing filter name for one or more genotype filters');
			}
			if (!r.data.jexl){
				msgs.push('Missing filter expression for one or more genotype filters');
			}
		}, this);

		if (!this.down('ldk-gridpanel').store.getCount()){
			msgs.push('Must provide at least one genotype filter or remove this step');
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