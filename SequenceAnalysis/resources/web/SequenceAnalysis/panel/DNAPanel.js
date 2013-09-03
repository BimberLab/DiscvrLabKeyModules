Ext4.define('SequenceAnalysis.panel.DNAPanel', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.dnapanel',

    initComponent: function(){
        Ext4.apply(this, {
            width: 'auto'
            ,bodyBorder: false
            ,style: 'padding: 5px;'
            ,border: true
            ,items: [{
                xtype: 'labkey-combo',
                multiSelect: true,
                fieldLabel: 'Species',
                displayField: 'common_name',
                valueField: 'common_name',
                name: 'dna.species',
                renderData: {
                    helpPopup: 'Select the desired species to use in the reference library'
                },
                bodyStyle:'padding:0px 0px 5px 0px;',
                store: Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'laboratory',
                    queryName: 'species',
                    autoLoad: true,
                    nullRecord: {
                        displayColumn: 'common_name',
                        nullCaption: "All"
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                value: 'All',
                triggerAction: 'all',
                listeners: {
                    change: function(combo, val, oldVal){
                        if(val && val.length){
                            if(val.indexOf('All')!=-1 && oldVal && oldVal.indexOf('All')==-1){
                                combo.setValue(); //if we choose All, reset
                            }
                            else if(val.indexOf('All')!=-1 && val.length>1){
                                val.remove('All');
                                combo.setValue(val);
                            }
                        }
                    }
                }
            },{
                xtype: 'labkey-combo',
                multiSelect: true,
                fieldLabel: 'Subset',
                displayField: 'region',
                valueField: 'region',
                name: 'dna.subset',
                bodyStyle:'padding:0px 0px 5px 0px;',
                renderData: {
                    helpPopup: 'Select the DNA regions to use in the reference library'
                },
                store: Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'sequenceanalysis',
                    queryName: 'dna_region',
                    autoLoad: true,
                    nullRecord: {
                        displayColumn: 'region',
                        nullCaption: "All"
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                value: 'All',
                triggerAction: 'all',
                listeners: {
                    change: function(combo, val, oldVal){
                        if(val && val.length){
                            if(val.indexOf('All')!=-1 && oldVal && oldVal.indexOf('All')==-1){
                                combo.setValue(); //if we choose All, reset
                            }
                            else if(val.indexOf('All')!=-1 && val.length>1){
                                val.remove('All');
                                combo.setValue(val);
                            }
                        }
                    }
                }
            },{
                xtype: 'labkey-combo',
                multiSelect: true,
                fieldLabel: 'Molecule Type',
                displayField: 'mol_type',
                valueField: 'mol_type',
                name: 'dna.mol_type',
                renderData: {
                    helpPopup: 'Select the desired molecules types to use in the reference library'
                },
                bodyStyle:'padding:0px 0px 5px 0px;',
                store: Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'laboratory',
                    queryName: 'dna_mol_type',
                    autoLoad: true,
                    nullRecord: {
                        displayColumn: 'mol_type',
                        nullCaption: "All"
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                value: 'All',
                triggerAction: 'all',
                listeners: {
                    change: function(combo, val, oldVal){
                        if(val && val.length){
                            if(val.indexOf('All')!=-1 && oldVal && oldVal.indexOf('All')==-1){
                                combo.setValue(); //if we choose All, reset
                            }
                            else if(val.indexOf('All')!=-1 && val.length>1){
                                val.remove('All');
                                combo.setValue(val);
                            }
                        }
                    }
                }
            },{
                xtype: 'labkey-combo',
                multiSelect: true,
                fieldLabel: 'Geographic Origin',
                displayField: 'geographic_origin',
                valueField: 'geographic_origin',
                name: 'dna.geographic_origin',
                renderData: {
                    helpPopup: 'Select the desired geographic origins to use in the reference library.  Leave blank for all.'
                },
                bodyStyle:'padding:0px 0px 5px 0px;',
                store: Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'laboratory',
                    queryName: 'geographic_origins',
                    sort: 'locus',
                    autoLoad: true,
                    nullRecord: {
                        displayColumn: 'geographic_origin',
                        nullCaption: "All"
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                triggerAction: 'all' ,
                listeners: {
                    change: function(combo, val, oldVal){
                        if(val && val.length){
                            if(val.indexOf('All')!=-1 && oldVal && oldVal.indexOf('All')==-1){
                                combo.setValue(); //if we choose All, reset
                            }
                            else if(val.indexOf('All')!=-1 && val.length>1){
                                val.remove('All');
                                combo.setValue(val);
                            }
                        }
                    }
                }
            },{
                xtype: 'labkey-combo',
                multiSelect: true,
                fieldLabel: 'Loci',
                displayField: 'locus',
                valueField: 'locus',
                name: 'dna.locus',
                renderData: {
                    helpPopup: 'Select the desired loci to use in the reference library'
                },
                bodyStyle:'padding:0px 0px 5px 0px;',
                store: Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'sequenceanalysis',
                    queryName: 'dna_loci',
                    sort: 'locus',
                    autoLoad: true,
                    nullRecord: {
                        displayColumn: 'locus',
                        nullCaption: "All"
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                triggerAction: 'all',
                listeners: {
                    change: function(combo, val, oldVal){
                        if(val && val.length){
                            if(val.indexOf('All')!=-1 && oldVal && oldVal.indexOf('All')==-1){
                                combo.setValue(); //if we choose All, reset
                            }
                            else if(val.indexOf('All')!=-1 && val.length>1){
                                val.remove('All');
                                combo.setValue(val);
                            }
                        }
                    }
                }
            },{
                xtype: 'hidden',
                name: 'dna.category',
                value: ''
            },{
                xtype: 'hidden',
                name: 'dbprefix',
                value: ''
            },{
                xtype: 'ldk-linkbutton',
                text: 'Click here to view reference sequences',
                linkPrefix: '[',
                linkSuffix: ']',
                handler: function(b){
                    window.open(LABKEY.ActionURL.buildURL("query", "executeQuery.view", null, {
                        schemaName: 'sequenceanalysis',
                        'query.queryName': 'ref_nt_sequences'
                    }));
                },
                scope: this
            }
            ]
        });

        this.callParent(arguments);
    }
});
