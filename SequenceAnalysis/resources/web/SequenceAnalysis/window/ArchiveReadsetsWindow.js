Ext4.define('SequenceAnalysis.window.ArchiveReadsetsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('SequenceAnalysis.window.ArchiveReadsetsWindow', {
                dataRegionName: dataRegionName,
                readsetIds: LABKEY.DataRegions[dataRegionName].getChecked()
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Archive Readsets',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This helper will delete the actual FASTQ files associated with the selected readsets. It will error unless each readdata row has an SRA accession listed. You selected ' + this.readsetIds.length + ' readsets.',
                style: 'padding-bottom: 10px;'
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
        if (!this.readsetIds.length) {
            Ext4.Msg.alert('Error', 'No readsets selected!');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'archiveReadsets', null),
            method: 'POST',
            jsonData: {
                readsetIds: this.readsetIds
            },
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                this.close();
                Ext4.Msg.alert('Success', 'Readsets archived!', function(){
                    if (this.dataRegionName){
                        LABKEY.DataRegions[this.dataRegionName].clearSelected();
                    }

                    LABKEY.DataRegions[this.dataRegionName].refresh();
                }, this);
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});