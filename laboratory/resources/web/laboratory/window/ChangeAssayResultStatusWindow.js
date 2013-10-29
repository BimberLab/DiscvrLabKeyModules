/**
 * @cfg dataRegionName
 */
Ext4.define('Laboratory.window.ChangeAssayResultStatusWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName, schemaName, queryName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            if (!dataRegion || !dataRegion.getChecked() || !dataRegion.getChecked().length){
                Ext4.Msg.alert('Error', '');
                return;
            }

            LDK.Assert.assertTrue('DataRegion not suitable for ChangeAssayResultStatusWindow for: ' + LDK.DataRegionUtils.getDisplayName(dataRegion), (dataRegion.pkCols && dataRegion.pkCols.length == 1));
            if (dataRegion.pkCols.length != 1){
                Ext4.Msg.alert('Error', 'This button cannot be used with this table');
                return;
            }

            Ext4.create('Laboratory.window.ChangeAssayResultStatusWindow', {
                dataRegionName: dataRegionName,
                schemaName: schemaName,
                queryName: queryName
        }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Change Status',
            closeAction: 'destroy',
            width: 380,
            items: [{
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'labkey-combo',
                    fieldLabel: 'Status',
                    width: 350,
                    displayField: 'status',
                    valueField: 'status',
                    store: {
                        type: 'labkey-store',
                        schemaName: 'laboratory',
                        queryName: 'result_status',
                        autoLoad: true
                    },
                    itemId: 'statusField'
                }]
            }],
            buttons: [{
                text:'Submit',
                disabled:false,
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Loading...');
        var status = btn.up('window').down('#statusField').getValue();
        if(!status){
            Ext4.Msg.alert('Error', 'Must choose a status');
            return;
        }

        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        var checked = dataRegion.getChecked();
        var keyField = dataRegion.pkCols[0];

        LABKEY.Query.selectRows({
            method: 'POST',
            schemaName: this.schemaName || dataRegion.schemaName,
            queryName: this.queryName || dataRegion.queryName,
            filterArray: [
                LABKEY.Filter.create(keyField, checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            scope: this,
            success: function(data){
                var toUpdate = [];
                var dataRegion = LABKEY.DataRegions[this.dataRegionName];

                if (!data.rows || !data.rows.length){
                    Ext4.Msg.hide();
                    dataRegion.selectNone();
                    dataRegion.refresh();
                    return;
                }

                Ext4.Array.forEach(data.rows, function(row){
                    var obj = {statusflag: status};
                    obj[keyField] = row[keyField];
                    toUpdate.push(obj);
                }, this);

                if(toUpdate.length){
                    LABKEY.Query.updateRows({
                        method: 'POST',
                        schemaName: this.schemaName || dataRegion.schemaName,
                        queryName: this.queryName || dataRegion.queryName,
                        rows: toUpdate,
                        scope: this,
                        success: function(){
                            this.close();
                            Ext4.Msg.hide();
                            dataRegion.selectNone();
                            dataRegion.refresh();
                        },
                        failure: LDK.Utils.getErrorCallback()
                    });
                }
                else {
                    Ext4.Msg.hide();
                    dataRegion.selectNone();
                    dataRegion.refresh();
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});