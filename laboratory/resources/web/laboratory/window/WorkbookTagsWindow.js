/**
 * @param dataRegionName
 * @param containerId
 *
 */
Ext4.define('Laboratory.window.WorkbookTagsWindow', {
    extend: 'Ext.window.Window',

    initComponent: function(){
        if (this.dataRegionName){
            var dr = LABKEY.DataRegions[this.dataRegionName];
            this.containerIds = dr.getChecked();
        }
        else {
            this.containerIds = [this.containerId];
        }

        if (!this.containerIds.length){
            Ext4.Msg.alert('Error', 'No workbooks selected');
            return;
        }

        Ext4.apply(this, {
            border: false,
            width: 420,
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'panel',
                border: false,
                defaults: {
                    width: 400,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'combo',
                    fieldLabel: 'Choose Existing Tag',
                    emptyText: 'Choose a pre-defined tag',
                    itemId: 'existingTag',
                    store: {
                        type: 'labkey-store',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'laboratory',
                        sql: 'SELECT DISTINCT tag FROM laboratory.workbook_tags',
                        autoLoad: true
                    },
                    valueField: 'tag',
                    displayField: 'tag',
                    listeners: {
                        change: function(cmp){
                            var field = cmp.up('panel').down('#newTag');
                            field.suspendEvents();
                            field.setValue(null);
                            field.resumeEvents();
                        }
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Enter New Tag',
                    itemId: 'newTag',
                    listeners: {
                        change: function(cmp){
                            var field = cmp.up('panel').down('#existingTag');
                            field.suspendEvents();
                            field.setValue(null);
                            field.resumeEvents();
                        }
                    }
                }]
            }],
            buttons:  [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();
    },

    getValue: function(){
        return this.down('#existingTag').getValue() || this.down('#newTag').getValue();
    },

    onSubmit: function(){
        var val = this.getValue();
        if (!val){
            Ext4.Msg.alert('Error', 'Must provide a tag to apply to these workbooks');
            return;
        }

        var multi = new LABKEY.MultiRequest();
        Ext4.Array.forEach(this.containerIds, function(containerId){
            multi.add(Ext4.Ajax.request, {
                url: LABKEY.ActionURL.buildURL('laboratory', 'updateWorkbookTags', containerId),
                params: {
                    merge: true,
                    tags: [val]
                },
                method : 'POST',
                scope: this,
                failure: function(response){
                    console.error('Unable to log message to server');
                    console.error(response);

                    Ext4.Msg.alert('Error', 'There was an error adding tags');
                },
                success: function(response){
                    //this must exist for LABKEY.MultiRequest to work properly
                }
            });
        }, this);

        multi.send(function(){
            if (this.dataRegionName){
                LABKEY.DataRegions[this.dataRegionName].refresh();
            }

            this.close();
        }, this);
    }


});