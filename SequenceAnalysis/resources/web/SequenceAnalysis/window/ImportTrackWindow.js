Ext4.define('SequenceAnalysis.window.ImportTrackWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('SequenceAnalysis.window.ImportTrackWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            modal: true,
            title: 'Import Annotations/Tracks',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 450
            },
            items: [{
                html: 'This will import annotations or feature tracks for the selected library. Note: in general, names used in the track must match those in the library itself.  If the sequences used in the target genome have genbank accessions or refseq IDs, the incoming track will be inspected and names converted.  If the reference genome has any sequences that are concatenations of other sequences (created by this system) then incoming features will be expected and translated as well.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'form',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'importTrack', null, null),
                fileUpload: true,
                defaults: {
                    border: false,
                    width: 450,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Reference Genome',
                    name: 'libraryId',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                    displayField: 'name',
                    valueField: 'rowid',
                    value: LABKEY.ActionURL.getParameter('libraryId') ? parseInt(LABKEY.ActionURL.getParameter('libraryId')) : null
                },{
                    xtype: 'filefield',
                    fieldLabel: 'File',
                    name: 'track',
                    allowBlank: false,
                    validator: function(){
                        var matcher = /^.*\.(bed|gff|gff3|gtf|vcf|bigwig|bw)$/i;
                        return matcher.test(this.getValue()) ? true : 'File must be either: .bed, .gff, .gff3, .gtf, .vcf, .bigwig, or .bw';
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Track Name',
                    name: 'trackName'
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Description',
                    name: 'trackDescription'
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Do Simple Name Translation',
                    name: 'doChrTranslation',
                    helpPopup: 'If checked, the system will attempt to translate between common forms of chromosome names, like chr1, chr01 and 1.  If found, any of these forms will be converted to the name used by the target genome.  This includes numbers, X and Y.',
                    inputValue: true,
                    checked: true
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
        var fasta = this.down('filefield[name=track]').getValue();
        if (!fasta){
            Ext4.Msg.alert('Error', 'Must provide a file');
            return;
        }

        if (!this.down('field[name=libraryId]').getValue() || !this.down('field[name=trackName]').getValue()){
            Ext4.Msg.alert('Error', 'Must provide the library and track name');
            return;
        }

        if (!this.down('form').isValid()){
            Ext4.Msg.alert('Error', 'There are errors in the form.  Hover over the red fields for more information.');
            return;
        }

        Ext4.Msg.wait('Loading...');
        this.down('form').submit({
            scope: this,
            timeout: 999999999,
            success: function(){
                Ext4.Msg.hide();

                this.close();

                Ext4.Msg.alert('Success', 'Import Started!', function(){
                    window.location = LABKEY.ActionURL.buildURL("pipeline-status", "showList.view", Laboratory.Utils.getQueryContainerPath(), {'StatusFiles.Status~neqornull': 'COMPLETE'});
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
                    msg = 'There was an error uploading this file';
                }

                Ext4.Msg.alert('Error', msg);
                LDK.Utils.logError('Problem uploading track: ' + serverMsg.join(';'));
                console.error(serverMsg);
            }
        });
    }
});
