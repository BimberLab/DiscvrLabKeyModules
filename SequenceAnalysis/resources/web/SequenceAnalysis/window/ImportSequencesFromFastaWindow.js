Ext4.define('SequenceAnalysis.window.ImportSequencesFromFastaWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName, genomeChecked){
            Ext4.create('SequenceAnalysis.window.ImportSequencesFromFastaWindow', {
                dataRegionName: dataRegionName,
                genomeChecked: genomeChecked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Import Sequences From FASTA',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 450
            },
            items: [{
                html: 'This will create a reference genome from the provided FASTA file.  Each sequence of this FASTA will be imported into as a reference sequence as well.  Note: this does not check for uniqueness among the imported sequences.  By default, the FASTA header line will be used as the sequence name.  If you set any of the fields below, these values wil be applied to all incoming sequences.  These can also be left blank, and the sequences edited later.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'form',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'importFastaSequences', null, null),
                fileUpload: true,
                defaults: {
                    border: false,
                    width: 450,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'filefield',
                    fieldLabel: 'FASTA File',
                    name: 'fasta',
                    allowBlank: false
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Species',
                    name: 'species',
                    schemaName: 'laboratory',
                    queryName: 'species',
                    displayField: 'common_name',
                    valueField: 'common_name'
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Molecule Type',
                    name: 'molType',
                    schemaName: 'laboratory',
                    queryName: 'dna_mol_type',
                    displayField: 'mol_type',
                    valueField: 'mol_type'
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Create Reference Genome',
                    name: 'createLibrary',
                    checked: this.genomeChecked,
                    listeners: {
                        change: function (field, val) {
                            var target = field.up('form').down('#libraryParams');
                            target.removeAll();
                            if (val){
                                target.add([{
                                    xtype: 'textfield',
                                    fieldLabel: 'Name',
                                    name: 'libraryName',
                                    allowBlank: false
                                },{
                                    xtype: 'textarea',
                                    fieldLabel: 'Description',
                                    itemId: 'libraryDescription'
                                }]);
                            }
                        },
                        render: function(field){
                            field.fireEvent('change', field, field.getValue());
                        }
                    }
                },{
                    xtype: 'panel',
                    border: false,
                    itemId: 'libraryParams',
                    defaults: {
                        width: 450,
                        labelWidth: 150
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        var fasta = this.down('filefield[name=fasta]').getValue();
        if (!fasta){
            Ext4.Msg.alert('Error', 'Must provide a FASTA file');
            return;
        }

        var createLibrary = this.down('field[name=createLibrary]').getValue();
        if (createLibrary && !this.down('field[name=libraryName]').getValue()){
            Ext4.Msg.alert('Error', 'Must provide the genome name when \'Create Genome\' is selected');
            return;
        }

        Ext4.Msg.wait('Loading...');
        this.down('form').submit({
            scope: this,
            timeout: 999999999,
            success: function(){
                Ext4.Msg.hide();

                this.close();

                Ext4.Msg.alert('Success', 'Sequences Imported!', function(){
                    var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                    dataRegion.refresh();
                }, this);
            },
            failure: function(form, action){
                Ext4.Msg.hide();
                var msg;
                var serverMsg = [];
                if (action && action.response && action.response.responseText){
                    msg = action.response.responseText;
                    serverMsg.push(action.response.responseText);
                }

                if (!msg && action && action.result && action.result.exception){
                    msg = action.result.exception;
                    serverMsg.push(Ext4.encode(action.result));
                }

                if (!msg){
                    msg = 'There was an error uploading these sequences';
                }

                Ext4.Msg.alert('Error', msg);
                LDK.Utils.logError('Problem uploading FASTA sequences: ' + serverMsg.join(';'));
                console.error(serverMsg);
            }
        });
    }
});
