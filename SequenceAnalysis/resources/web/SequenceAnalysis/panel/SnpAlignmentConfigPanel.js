Ext4.define('SequenceAnalysis.panel.SnpAlignmentConfigPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.alignmentconfigpanel',

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Alignment Settings',
            width: '100%',
            bodyBorder: true,
            border: true,
            collapsible: true,
            bodyStyle:'padding:5px 5px 5px 5px',
            defaultType: 'textfield',
            monitorValid: true,
            tracks: [],
            defaults: {
                bodyStyle:'padding:5px 5px 5px 5px',
                width: 400,
                msgTarget: 'side'
            },
            items: [{
                xtype: 'fieldset',
                title: 'Reference Sequence',
                itemId: 'refSequenceField',
                collapsible: true,
                width: 'auto',
                defaults: {
                    width: 400
                },
                items: [{
                    xtype: 'combo',
                    itemId: 'virusStrainField',
                    fieldLabel: 'Virus Strain',
                    allowBlank: true,
                    queryMode: 'local',
                    displayField: 'virus_strain',
                    valueField: 'virus_strain',
                    store: {
                        type: 'labkey-store',
                        schemaName: 'sequenceanalysis',
                        queryName: 'virus_strains',
                        filterArray: [
                            LABKEY.Filter.create('virus_strain', this.allowableStrains.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                        ],
                        sort: 'virus_strain',
                        autoLoad: true,
                        listeners: {
                            scope: this,
                            load: function(store){
                                if(store.getCount() == 1){
                                    var val = store.getAt(0).get('virus_strain');
                                    var field = this.down('#virusStrainField');
                                    field.setValue(val);
                                    field.fireEvent('change', field, val);
                                }
                            }
                        }
                    },
                    listeners: {
                        change: function(combo, val){
                            if(!val)
                                return;
                            var proteinField = combo.up('panel').down('#proteinField');
                            proteinField.setValue('');
                            proteinField.setDisabled(true);

                            var filter = LABKEY.Filter.create('ref_nt_id/subset', val, LABKEY.Filter.Types.EQUAL);
                            proteinField.store.filterArray = [filter];
                            proteinField.store.load();
                            proteinField.store.on('load', function(){
                                proteinField.store.fireEvent('datachanged');
                                proteinField.bindStore(proteinField.store);
                                proteinField.setDisabled(false);
                            }, this, {single: true});
                        }
                    }
                },{
                    xtype: 'checkcombo',
                    multiSelect: true,
                    delim: ';',
                    itemId: 'proteinField',
                    fieldLabel: 'Protein(s)',
                    allowBlank: true,
                    mode: 'local',
                    triggerAction: 'all',
                    disabled: true,
                    displayField: 'name',
                    valueField: 'rowid',
                    lazyRender: false,
                    store: Ext4.create('LABKEY.ext4.Store', {
                        schemaName: 'sequenceanalysis',
                        queryName: 'ref_aa_sequences',
                        columns: 'rowid,name,ref_nt_id',
                        autoLoad: false,
                        sort: 'exons'
                    })
                }]
            },{
                xtype:'fieldset',
                title: 'Display Settings',
                width: '100%',
                itemId: 'snpFilterSettings',
                collapsible: true,
                collapsed: false,
                defaults: {
                    labelWidth: 180
                },
                items: [{
                    xtype: 'numberfield',
                    value: 1,
                    fieldLabel: 'Minimum Mutation %',
                    itemId: 'minMutationPct',
                    helpPopup: 'Only mutations present above this level will be shown. Note: this calculation considers the total mutations present at each AA residue, not the number per distinct AA. Therefore if you have a threshold of 1%, and 3 distinct mutations at one residue each present at 0.33%, these SNPs will be displayed.'
                },{
                    xtype: 'numberfield',
                    value: 5,
                    fieldLabel: 'Minimum Read Number',
                    itemId: 'minReadNum',
                    helpPopup: 'Only mutations present at least this many reads will be shown. Note: this calculation considers the total mutations present at each AA residue, not the number per distinct AA. Therefore if you have a threshold of 5, and 3 distinct mutations at one residue each present at 2 read, these SNPs will be displayed.'
                },{
                    xtype: 'numberfield',
                    value: '',
                    fieldLabel: 'Minimum Coverage',
                    itemId: 'minCoverage',
                    helpPopup: 'Positions below this coverage will be masked.  Leave blank for all'
                },{
                    xtype: 'numberfield',
                    value: 120,
                    fieldLabel: 'Row Length',
                    itemId: 'rowLength',
                    helpPopup: 'The number of based to show per row.  Leave blank for all'
                }]
            },{
                xtype: 'fieldset',
                itemId: 'snpColoration',
                collapsible: true,
                collapsed: true,
                forceLayout: true,
                title: 'SNP Coloration',
                width: '100%',
                defaults: {
                    border: false
                },
                border: true,
                items: [{
                    xtype: 'checkbox',
                    fieldLabel: 'Use Color Gradient',
                    labelWidth: 150,
                    itemId: 'useGradientField',
                    checked: true,
                    listeners: {
                        change: function(c, v){
                            if(v)
                                this.up('alignmentconfigpanel').renderSnpGradiant();
                            else
                                this.up('alignmentconfigpanel').renderSnpColorFields();
                        },
                        render: function(c){
                            this.fireEvent('change', this, this.getValue());
                        }
                    }
                },{
                    xtype: 'fieldset',
                    border: true,
                    itemId: 'snpColorSettings'
                }]
            },{
                xtype:'fieldset',
                title: 'Features To Highlight',
                width: '100%',
                itemId: 'aaFeatures',
                collapsible: true,
                collapsed: false,
                hidden: false,
                defaults: {
                    labelWidth: 140
                },
                items: [{
                    xtype: 'checkcombo',
                    multiSelect: true,
                    itemId: 'aaFeaturesCategoryField',
                    delim: ';',
                    displayField: 'category',
                    queryMode: 'local',
                    valueField: 'category',
                    store: {
                        type: 'labkey-store',
                        schemaName: 'sequenceanalysis',
                        sql: 'select distinct category as category from sequenceanalysis.aa_features_combined group by category',
                        sort: 'category',
                        autoLoad: true
                    },
                    width: 500,
                    fieldLabel: 'Feature Types',
                    emptyText: 'Show All',
                    addAllSelector: true,
                    expandToFitContent: true
                },{
//                    html: '<a target="_blank" href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'ref_aa_features', 'query.ref_nt_id~in': this.ref_nt_ids.join(';')})+'">[View AA Features]</a>',
                    border: false
                },{
                    xtype: 'checkbox',
                    itemId: 'showElispot',
                    checked: false,
                    disabled: true,
                    fieldLabel: 'Show ELISPOT Data'
                },{
//                    html: '<a target="_blank" href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'drug_resistance', 'query.ref_nt_id~in': this.ref_nt_ids.join(';')})+'">[View Drug Resistance Mutations]</a>',
                    border: false
                }]
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Refresh Alignment',
                scope: this,
                handler: function(btn){
                    this.up('panel').doAlignment();
                }
            }]
        });

        this.callParent();
    },

    renderSnpGradiant: function(){
        var target = this.down('#snpColorSettings');
        target.removeAll();
        target.add({
            xtype: 'panel',
            defaults: {
                border: false,
                labelWidth: 90,
                width: 300
            },
            border: false,
            items: [{
                html: 'Non-synonymous SNPs:',
                bodyStyle: 'padding-bottom: 10px;'
            },{
                style: 'padding-left: 20px;',
                items: [{
                    xtype: 'colorfield',
                    itemId: 'nsColorField',
                    fieldLabel: 'Color',
                    value: 'FFFF00'
                },{
                    xtype: 'numberfield',
                    itemId: 'nsColorStepsField',
                    fieldLabel: '# of Steps',
                    value: 4
                }]
            },{
                html: 'Synonymous SNPs:',
                bodyStyle: 'padding-bottom: 10px;padding-top: 10px;'
            },{
                style: 'padding-left: 20px;',
                items: [{
                    xtype: 'colorfield',
                    itemId: 'synColorField',
                    fieldLabel: 'Color',
                    value: '00FF00'
                },{
                    xtype: 'numberfield',
                    itemId: 'synColorStepsField',
                    fieldLabel: '# of Steps',
                    value: 4
                }]
            },{
                html: 'Frameshift Mutations:',
                bodyStyle: 'padding-bottom: 10px;padding-top: 10px;'
            },{
                style: 'padding-left: 20px;',
                items: [{
                    xtype: 'colorfield',
                    itemId: 'fsColorField',
                    fieldLabel: 'Color',
                    value: 'FF0000'
                },{
                    xtype: 'numberfield',
                    itemId: 'fsColorStepsField',
                    fieldLabel: '# of Steps',
                    value: 4
                }]
            },{
                html: 'No Coverage:',
                bodyStyle: 'padding-bottom: 10px;padding-top: 10px;'
            },{
                style: 'padding-left: 20px;',
                items: [{
                    xtype: 'colorfield',
                    itemId: 'nocoverColorField',
                    fieldLabel: 'Color',
                    value: 'C0C0C0'
                }]
            }]
        });
    },

    renderSnpColorFields: function(){
        var target = this.down('#snpColorSettings');
        target.removeAll();
        target.add({
            defaults: {
                labelWidth: 150,
                width: 300
            },
            border: false,
            items: [{
                fieldLabel: 'Non-synonymous SNPs',
                xtype: 'colorfield',
                itemId: 'nsColorField',
                value: 'FFFF00'
            },{
                fieldLabel: 'Synonymous SNPs',
                xtype: 'colorfield',
                itemId: 'synColorField',
                value: '00FF00'
            },{
                fieldLabel: 'Frameshift Mutations',
                border: false,
                xtype: 'colorfield',
                itemId: 'fsColorField',
                value: 'FF0000'
            },{
                fieldLabel: 'No Coverage',
                border: false,
                xtype: 'colorfield',
                itemId: 'nocoverColorField',
                value: 'C0C0C0'
            }]
        });
    }
});
