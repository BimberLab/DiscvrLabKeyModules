Ext4.define('SequenceAnalysis.window.MarkDisabledWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dr = LABKEY.DataRegions[dataRegionName];
            var checked = dr.getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.MarkDisabledWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Mark Disabled',
            width: 700,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This will mark the selected rows as disabled.  This is a flag that can be helpful to disable older versions of a sequence.  They are not deleted, simply flagged and not shown in the default grid.  You can customize the view and remove the filter on the date removed column to see these sequences.',
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

    onSubmit: function(){
        Ext4.Msg.wait('Saving...');

        var dr = LABKEY.DataRegions[this.dataRegionName];
        var checked = dr.getChecked();

        LABKEY.Query.selectRows({
            containerPath: dr.containerPath,
            schemaName: 'sequenceanalysis',
            queryName: 'ref_nt_sequences',
            columns: 'rowid,container',
            filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function (results){
                if (!results.rows){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'No rows found');
                    return;
                }

                var containerMap = {};
                var date = new Date();
                Ext4.Array.forEach(results.rows, function (c){
                    var rows = containerMap[c.container] || [];
                    rows.push({
                        rowid: c.rowid,
                        container: c.container,
                        disabledby: LABKEY.Security.currentUser.id,
                        datedisabled: date
                    });

                    containerMap[c.container] = rows;
                }, this);

                var multi = new LABKEY.MultiRequest();
                Ext4.Array.forEach(Ext4.Object.getKeys(containerMap), function(containerId){
                    multi.add(LABKEY.Query.updateRows, {
                        containerPath: containerId,
                        schemaName: 'sequenceanalysis',
                        queryName: 'ref_nt_sequences',
                        rows: containerMap[containerId],
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: function(response){
                            //this must exist for LABKEY.MultiRequest to work properly
                        }
                    });
                }, this);

                multi.send(function(){
                    Ext4.Msg.hide();

                    this.close();
                    Ext4.Msg.alert('Success', 'Records have been updated', function (){
                        if (this.dataRegionName){
                            LABKEY.DataRegions[this.dataRegionName].refresh();
                        }
                    }, this);
                }, this);
            }
        });
    }
});