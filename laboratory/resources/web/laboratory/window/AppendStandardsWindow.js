Ext4.define('Laboratory.ext.AppendStandardsWindow', {
    extend: 'Ext.window.Window',
    config: {
        targetStore: null,
        targetGrid: null,
        allowableCategories: ['Pos Control', 'Neg Control', 'Standard', 'Control'],
        categoryFieldName: 'category'
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Append Standards',
            width: 600,
            modal: true,
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                fieldDefaults: {
                    labelWidth: 180,
                    width: 450
                },
                items: [{
                    html: 'This helper allows you to append a pre-defined set of standards to this plate.  The standards will be appended to the end of the plate.  If no exists sets of standards have been saved, you can create one by configuring the standards on the grid, then using \'Save Standards\' under the \'More Actions\' button to save it for future use.',
                    style: 'padding-bottom: 10px',
                    border: false
                },{
                    xtype: 'combo',
                    fieldLabel: 'Choose Template',
                    displayField: 'name',
                    valueField: 'rowid',
                    store: {
                        type: 'labkey-store',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'laboratory',
                        queryName: 'plate_templates',
                        columns: '*',
                        autoLoad: true,
                        filterArray: [LABKEY.Filter.create('category', 'standards', LABKEY.Filter.Types.EQUAL)]
                    },
                    editable: false,
                    itemId: 'template'
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Remove Existing Standards',
                    itemId: 'removeExisting'
                }],
                buttons: [{
                    text: 'Submit',
                    handler: function(btn){
                        var win = btn.up('window');
                        win.onSubmit(btn);
                    }
                },{
                    text: 'Cancel',
                    handler: function(btn){
                        btn.up('window').close();
                    }
                }]
            }]
        });

        this.callParent();
    },

    onSubmit: function(btn){
        btn.setDisabled(true);
        var remove = this.down('#removeExisting').getValue();
        var templateField = this.down('#template');
        var templateRecIdx = templateField.store.findExact(templateField.valueField, templateField.getValue());
        var rec = templateField.store.getAt(templateRecIdx);
        var json = Ext4.decode(rec.get('json'));

        var toAdd = [];
        Ext4.each(json, function(r){
            toAdd.push(LDK.StoreUtils.createModelInstance(this.targetStore, r));
        }, this);

        if(remove){
            var toRemove = [];
            this.targetStore.each(function(r){
                if(this.allowableCategories.indexOf(r.get(this.categoryFieldName)) != -1){
                    toRemove.push(r);
                }
            }, this);

            if(toRemove.length){
                this.targetStore.remove(toRemove);
            }
        }
        this.targetStore.add(toAdd);

        this.close();
    }
});
