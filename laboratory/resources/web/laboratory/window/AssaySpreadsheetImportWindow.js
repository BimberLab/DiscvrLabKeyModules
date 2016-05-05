Ext4.define('Laboratory.ext.AssaySpreadsheetImportWindow', {
    extend: 'Ext.window.Window',

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Spreadsheet Import',
            width: 620,
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'This allows you to upload records to this grid using an excel template.  Click the link below to download the template.',
                    style: 'padding-bottom: 10px;'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Template',
                    linkPrefix: '[',
                    linkSuffix: ']',
                    style: 'padding-bottom: 10px;',
                    handler: function(btn){
                        var win = btn.up('window');
                        var fields = win.getFieldsInTemplate();

                        LDK.StoreUtils.createExcelTemplate({
                            fields: fields,
                            skippedFields: [],
                            fileName: win.fileNamePrefix + '_' + (new Date().format('Y-m-d H_i_s')) + '.xls'
                        });
                    }
                },{
                    xtype: 'textarea',
                    itemId: 'textField',
                    width: 600,
                    height: 300
                },{
                    xtype: 'numberfield',
                    minValue: 1,
                    allowBlank: false,
                    fieldLabel: 'Replicates',
                    itemId: 'replicates',
                    helpPopup: 'If greater than 1, each row of the incoming spreadsheet will be imported multiple times',
                    value: 1
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    var win = btn.up('window');
                    var text = win.down('#textField').getValue();

                    if (!text){
                        Ext4.Msg.alert('Error', 'Must provide text');
                        return;
                    }

                    var models = LDK.StoreUtils.getModelsFromText({
                        store: win.targetGrid.store,
                        text: text
                    });

                    var replicates = this.down('#replicates').getValue();
                    var toAdd = [];
                    Ext4.each(models, function(model){
                        for (var i=0;i<replicates;i++){
                            toAdd.push(LDK.StoreUtils.createModelInstance(win.targetGrid.store, model.data, true));
                        }
                    }, this);

                    win.targetGrid.store.add(toAdd);

                    win.close();
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    getFieldsInTemplate: function(){
        var fields = [];
        if (this.includeVisibleColumnsOnly){
            var map = LDK.StoreUtils.getFieldMap(this.targetGrid.store);
            Ext4.each(this.targetGrid.columns, function(col){
                if (!col.hidden){
                    if (!col.dataIndex){
                        console.error(col);
                    }
                    fields.push(map[col.dataIndex]);
                }
            }, this);
        }
        else {
            fields = this.targetGrid.store.model.getFields()
        }
        return fields;
    },

    //used by automated tests
    getFieldsInTemplateTest: function(){
        var fieldNames = [];
        var fields = this.getFieldsInTemplate();
        Ext4.each(fields, function(field){
            if (!field){
                console.error(fields);
                alert('Field Was Null');
            }
            fieldNames.push(field.name);
        }, this);
        return fieldNames.join(';');
    }
});
