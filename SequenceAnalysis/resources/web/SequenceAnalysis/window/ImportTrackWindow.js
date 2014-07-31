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
                html: 'This will import annotations or feature tracks for the selected library. Note: the names used in the track must match those in the library itself.',
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
                    fieldLabel: 'Library',
                    name: 'libraryId',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    displayField: 'name',
                    valueField: 'rowid',
                    value: LABKEY.ActionURL.getParameter('libraryId') ? parseInt(LABKEY.ActionURL.getParameter('libraryId')) : null
                },{
                    xtype: 'filefield',
                    fieldLabel: 'File',
                    name: 'track',
                    allowBlank: false,
                    validator: function(){
                        var matcher = /^.*\.(bed|gff|gff3|gtf|bigwig|bw)$/i;
                        return matcher.test(this.getValue()) ? true : 'File must be either: .bed, .gff, .gff3, .gtf, .bigwig, or .bw';
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Track Name',
                    name: 'trackName'
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Description',
                    name: 'trackDescription'
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

                Ext4.Msg.alert('Success', 'Track Imported!', function(){
                    var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                    dataRegion.refresh();
                }, this);
            },
            failure: function(form, action){
                Ext4.Msg.hide();
                if (action && action.result){
                    Ext4.Msg.alert('Error', action.result.exception);
                }
            }
        });
    }
});
