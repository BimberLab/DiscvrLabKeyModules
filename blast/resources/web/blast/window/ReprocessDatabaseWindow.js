Ext4.define('BLAST.window.ReprocessDatabaseWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('BLAST.window.ReprocessDatabaseWindow', {
                dataRegionName: dataRegionName,
                databaseIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Reprocess BLAST Databases',
            items: [{
                html: 'This will cause the server to reprocess the selected databases, which should be used if the data or attributes have changed.',
                border: false,
                style: 'padding-bottom: 10px;'
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(){
        Ext4.Msg.wait('Saving...');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('blast', 'recreateDatabase'),
            jsonData: {
                databaseIds: this.databaseIds
            },
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                this.close();

                Ext4.Msg.alert('Success', 'Pipeline job started!', function(){
                    window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
                }, this);
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});