Ext4.define('GeneticsCore.window.ChangeReadsetStatusWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dr = LABKEY.DataRegions[dataRegionName];
            LDK.Assert.assertNotEmpty('Unable to find dataregion in ChangeReadsetStatusWindow', dr);

            var checked = dr.getChecked();
            if (!checked.length) {
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('GeneticsCore.window.ChangeReadsetStatusWindow', {
                dataRegionName: dataRegionName,
                checked: checked
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Change Readset Status',
            bodyStyle: 'padding: 5px;',
            closeAction: 'destroy',
            width: 500,
            defaults: {
                border: false
            },
            items: [{
                html: 'This will update the status for the readsets associated with the analyses selected.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'labkey-combo',
                itemId: 'statusField',
                forceSelection: true,
                width: 400,
                fieldLabel: 'Status',
                displayField: 'status',
                valueField: 'status',
                queryMode: 'local',
                store: {
                    type: 'labkey-store',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'readset_status',
                    autoLoad: true
                }
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function (btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(){
        var analysisIds = this.checked;
        var status = this.down('#statusField').getValue();
        //NOTE: allow a blank value
        //if (!status){
        //
        //}

        Ext4.Msg.wait('Loading...');

        LABKEY.Query.selectRows({
            method: 'POST',
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_analyses',
            columns: 'readset/rowid,container',
            filterArray: [LABKEY.Filter.create('rowid', analysisIds.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                if (!results || !results.rows){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'Unable to find matching rows');
                    return;
                }

                var readsetIds = [];
                Ext4.Array.forEach(results.rows, function(row){
                    if (row['readset/rowid']) {
                        readsetIds.push(row['readset/rowid'] + '<>' + row.container);
                    }
                }, this);

                readsetIds = Ext4.unique(readsetIds);

                var toUpdate = [];
                Ext4.Array.forEach(readsetIds, function(r){
                    var tokens = r.split('<>');
                    toUpdate.push({
                        rowid: tokens[0],
                        container: tokens[1],
                        status: status
                    });
                }, this);

                if (toUpdate.length) {
                    LABKEY.Query.updateRows({
                        method: 'POST',
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_readsets',
                        rows: toUpdate,
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: function (results) {
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Success', 'Readsets updated', function () {
                                window.location.reload();
                            });
                        }
                    });
                }
                else {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'No matching readsets found for the selected analyses');
                }
            }
        });
    }
});