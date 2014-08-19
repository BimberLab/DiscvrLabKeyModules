Ext4.define('JBrowse.window.ReprocessResourcesWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.ReprocessResourcesWindow', {
                dataRegionName: dataRegionName,
                jsonFiles: checked
            }).show();
        },

        sessionHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.ReprocessResourcesWindow', {
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
            title: 'Reprocess Resources',
            items: [{
                html: 'This will cause the server to reprocess the selected resources, which should be used if the data or attributes have changed.',
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
        var jsonData = {};
        if (this.jsonFiles){
            jsonData.jsonFiles =this.jsonFiles
        }

        if (this.databaseIds){
            jsonData.databaseIds = this.databaseIds;
        }

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('jbrowse', 'reprocessResources'),
            jsonData: jsonData,
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