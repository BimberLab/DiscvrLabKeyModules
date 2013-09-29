Ext4.define('SequenceAnalysis.panel.RefSequencePanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.refsequencepanel',
    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            width: '100%',
            itemId: 'refLibraryType',
            name: 'refLibraryType',
            defaults: {
                border: false
            },
            items: [{
                xtype: 'combo',
                fieldLabel: 'Reference Library Type',
                itemId: 'refLibraryCombo',
                renderData: {
                    helpPopup: 'Choose the reference sequence(s)'
                },
                displayField: 'name',
                valueField: 'value',
                value: 'Virus',
                width: 400,
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['name', 'value'],
                    data: [
                        ['DNA Sequences','DNA'],
                        ['Viral Strains','Virus'],
                        ['Custom Sequence','Custom']
                    ]
                }),
                listeners: {
                    scope: this,
                    change: this.onRefSelect,
                    afterrender: {
                        fn: this.onRefSelect,
                        scope: this,
                        delay: 250
                    }
                },
                forceSelection:false,
                typeAhead: true,
                mode: 'local',
                triggerAction: 'all'
            },{
                xtype: 'panel',
                width: '100%',
                itemId: 'refLibraryPanel'
            }]
        });

        this.callParent(arguments);
    },

    onRefSelect: function(field){
        var panel = field.up('#refLibraryType').down('#refLibraryPanel');
        panel.removeAll();
        this['render' + field.getValue()](panel);
    },

    renderVirus: function(panel){
        panel.add([{
            xtype: 'fieldset',
            style: 'padding:5px;',
            itemId: 'virusReference',
            defaults: {
                width: 350
            },
            width: '100%',
            items: [{
                fieldLabel: 'Virus Strain',
                width: 450,
//                name: 'virus.virus_strain',
                name: 'dna.subset',
                //triggerAction: 'all',
                itemId: 'viralStrain',
                xtype: 'labkey-combo',
                forceSelection: true,
                allowBlank: false,
                displayField:'virus_strain',
                valueField:'virus_strain',
                store: new LABKEY.ext4.data.Store({
                    schemaName: 'sequenceanalysis',
                    queryName: 'virus_strains',
                    sort: 'virus_strain',
                    autoLoad: true
                }),
                listeners: {
                    scope: this,
                    change: function(combo, val){
                        this.down('#dbprefix').setValue(val);
                    }
                }
            },{
                xtype: 'hidden',
                name: 'dna.category',
                value: 'Virus'
            },{
                xtype: 'hidden',
                name: 'dbprefix',
                itemId: 'dbprefix',
                value: ''
            },{
                xtype: 'ldk-linkbutton',
                text: 'Click here to view reference sequences',
                linkPrefix: '[',
                linkSuffix: ']',
                handler: function(b){
                    window.open(LABKEY.ActionURL.buildURL("query", "executeQuery.view", null, {
                        schemaName: 'sequenceanalysis',
                        'query.queryName': 'virus_strains'
                    }));
                },
                scope: this
            }]
        }]);
    },

    renderDNA: function(panel){
        panel.add({
            name: 'sbt.dna',
            xtype: 'dnapanel',
            defaults: {
                width: 350
            }
        });
    },

    renderCustom: function(panel){
        panel.add({
            border: true,
            bodyStyle: 'padding: 5px;',
            defaults: {
                width: 450
            },
            items: [{
                html: 'Note: if selected, AA translations will not be calculated. The name of this reference should not match one of the other reference sequences.  You will also be unable to import into the DB',
                border: false,
                width: 800
            },{
                xtype: 'hidden',
                name: 'dna.isCustomReference',
                itemId: 'customReference',
                value: true
            },{
                xtype: 'textfield',
                itemId: 'customSequenceName',
                name: 'dna.customReferenceName',
                fieldLabel: 'Reference Name',
                allowBlank: false
            },{
                xtype: 'textarea',
                width: 450,
                height: 250,
                itemId: 'customSequence',
                name: 'dna.refSequence',
                allowBlank: false,
                maskRe: new RegExp('[ATGCN]', 'i'),
                listeners: {
                    change: function(c){
                        var val = c.getValue();
                        val = val.replace(/\s+/g,'');
                        c.setValue(val);
                    }
                },
                fieldLabel: 'Sequence',
                renderData: {
                    helpPopup: 'Paste the sequence only (ie. no FASTA header, title, etc)'
                }
            }]
        });
    }
});
