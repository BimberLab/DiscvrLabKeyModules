Ext4.define('BLAST.window.DatabaseWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('BLAST.window.DatabaseWindow', {
                dataRegionName: dataRegionName,
                libraryIds: checked
            }).show();
        }
    },

    initComponent: function () {
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Create BLAST Database',
            items: [{
                xtype: 'form',
                itemId: 'target',
                border: false,
                defaults: {
                    border: false,
                    width: 400
                },
                items: [{
                    html: 'This will create a BLAST database for each of the selected libraries.  Do you want to continue?'
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function (btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function () {
        var form = this.down('form');
        if (!form.isValid()) {
            Ext4.Msg.alert('Error', 'Missing one or more required fields');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('blast', 'createDatabase', null),
            method: 'POST',
            jsonData: {
                libraryIds: this.libraryIds
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