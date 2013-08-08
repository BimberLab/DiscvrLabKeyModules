Ext4.define('Laboratory.ext.SaveStandardsWindow', {
    extend: 'Ext.window.Window',
    config: {
        targetStore: null,
        allowableCategories: ['Pos Control', 'Neg Control', 'Standard', 'Control'],
        categoryFieldName: 'category'
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Save Standards',
            width: 500,
            modal: true,
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                items: [{
                    html: 'This helper allows you to append a pre-defined set of standards to this plate.  The standards will be appended to the end of the plate.',
                    style: 'padding-bottom: 10px',
                    border: false
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Choose Name',
                    width: 400,
                    itemId: 'nameField',
                    allowBlank: false,
                    listeners: {
                        afterrender: function(field){
                            field.focus(null, 500);
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: function(btn){
                    var win = btn.up('window');
                    win.onSubmit();
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();
    },

    onSubmit: function(){
        var name = this.down('#nameField').getValue();
        if (!name){
            Ext4.Msg.alert('Error', 'Must choose a name');
            return;
        }

        var rows = [];
        this.targetStore.each(function(rec){
            var category = rec.get(this.categoryFieldName);
            if (this.allowableCategories.indexOf(category) != -1){
                rows.push(Ext4.apply({}, rec.data));
            }
        }, this);

        if (!rows.length){
            Ext4.Msg.alert('No rows', 'There are no standards to save');
            this.close();
            return;
        }

        var json = Ext4.encode(rows);
        LABKEY.Query.insertRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'laboratory',
            queryName: 'plate_templates',
            rows: [{
                name: name,
                category: 'standards',
                json: rows
            }],
            scope: this,
            failure: LDK.Utils.getErrorCallback({
                showAlertOnError: true
            }),
            success: function(){
                this.close();
                Ext4.Msg.alert('Success', 'Save Successful');
            }
        });
    }
});
