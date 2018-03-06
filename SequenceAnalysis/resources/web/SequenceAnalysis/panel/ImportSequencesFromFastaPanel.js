Ext4.define('SequenceAnalysis.panel.ImportSequencesFromFastaPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.sequenceanalysis-importsequencesfromfastapanel',
    actionName: 'importReferenceSequences',

    initComponent: function(){
        Ext4.QuickTips.init();

        this.fileNames = this.fileNames || [];

        Ext4.apply(this, {
            title: this.suppressTitle ? null : 'Import Sequences From FASTA',
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
                html: 'The following files will be processed: <br><ul><li>' + (this.fileNames.join('</li><li>')) + '</li></ul>',
                hidden: !this.fileNames.length,
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'form',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', this.actionName, null, null),
                fileUpload: true,
                defaults: {
                    border: false,
                    width: 450,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'filefield',
                    hidden: !!this.fileNames.length,
                    fieldLabel: 'FASTA File',
                    name: 'fasta',
                    allowBlank: !!this.fileNames.length
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Split Header By Whitespace?',
                    name: 'splitWhitespace',
                    inputValue: true,
                    helpPopup: 'If provided, the header line will be read until the first whitespace, and this value used as the sequence name.  The remaining text will be used as the description.'
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
                                    xtype: 'textfield',
                                    fieldLabel: 'Assembly Id',
                                    name: 'assemblyId',
                                    allowBlank: true
                                },{
                                    xtype: 'textarea',
                                    fieldLabel: 'Description',
                                    name: 'libraryDescription'
                                },{
                                    xtype: 'checkbox',
                                    fieldLabel: 'Skip Aligner Index Creation',
                                    name: 'skipCacheIndexes',
                                    inputValue: true
                                },{
                                    xtype: 'checkbox',
                                    fieldLabel: 'Skip Triggers',
                                    helpPopup: 'When a new genome is created, it fires events that can trigger other modules to perform work.  For example, the BLAST module will automatically create a new database.  If checked, this event will not be fired.  The primary purpose for this would be if you expect this genome to change after the initial import.  Generally speaking, it is best if this is left alone.',
                                    name: 'skipTriggers',
                                    inputValue: true
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
                },{
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                }]
            }],
            buttons: this.getButtonConfig()
        });

        this.callParent(arguments);
    },

    getButtonConfig: function(){
        return this.hideButtons ? null : [{
            text: 'Submit',
            scope: this,
            handler: this.onSubmit
        },{
            text: 'Cancel',
            handler: function(btn){
                window.location = LABKEY.ActionURL.buildURL('project', 'start');
            }
        }];
    },

    onSubmit: function(btn){
        if (!this.fileNames.length){
            var fasta = this.down('filefield[name=fasta]').getValue();
            if (!fasta) {
                Ext4.Msg.alert('Error', 'Must provide a FASTA file');
                return;
            }
        }

        var createLibrary = this.down('field[name=createLibrary]').getValue();
        if (createLibrary && !this.down('field[name=libraryName]').getValue()){
            Ext4.Msg.alert('Error', 'Must provide the genome name when \'Create Genome\' is selected');
            return;
        }

        var params = {};
        if (this.fileNames.length){
            params.fileNames = this.fileNames;
        }

        if (this.path){
            params.path = this.path;
        }

        Ext4.Msg.wait('Loading...');
        this.down('form').submit({
            params: params,
            scope: this,
            timeout: 999999999,
            success: function(){
                Ext4.Msg.hide();

                var win = this.up('win');
                if (win) {
                    win.close();
                }

                Ext4.Msg.alert('Success', 'Pipeline job started!', function(){
                    window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList', Laboratory.Utils.getQueryContainerPath());
                }, this);
            },
            failure: function(form, action){
                console.error(arguments);
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
