Ext4.define('Laboratory.ext.CopyRunWindow', {
    extend: 'Ext.window.Window',
    config: {
        assayName: null,
        targetPanel: null
    },
    initComponent: function(){
        Ext4.apply(this, {
            title: 'Copy Previous Run',
            modal: true,
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    border: false
                },
                fieldDefaults: {
                    width: 500,
                    labelWidth: 180
                },
                border: false,
                items: [{
                    xtype: 'labkey-combo',
                    nullCaption: '[No Runs]',
                    displayField: 'Name',
                    valueField: 'RowId',
                    store: {
                        type: 'labkey-store',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: LDK.AssayUtils.SCHEMA_NAME,
                        queryName: LDK.AssayUtils.getRunQueryName(this.assayName),
                        columns: 'Name,RowId',
                        autoLoad: true
                    },
                    fieldLabel: 'Choose Run',
                    itemId: 'run'
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Include This Workbook Only',
                    checked: false,
                    listeners: {
                        change: function(field, val){
                            var containerPath;
                            if(val){
                                containerPath = LABKEY.Security.currentContainer.path;
                            }
                            else {
                                containerPath = Laboratory.Utils.getQueryContainerPath();
                            }

                            var combo = field.up('form').down('labkey-combo');
                            combo.reset();
                            combo.store.containerPath = containerPath;
                            combo.store.load();
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    var win = btn.up('window');
                    var val = win.down('labkey-combo').getValue();
                    if (!val){
                        Ext4.Msg.alert('Error', 'Must choose a run to copy');
                        return;
                    }

                    win.close();
                    this.targetPanel.loadPreviousRun({
                        rowId: val
                    })
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();
    }
});
