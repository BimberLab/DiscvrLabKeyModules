Ext4.define('JBrowse.window.DatabaseWindow', {
    extend: 'Ext.window.Window',

    statics: {
        libraryHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.DatabaseWindow', {
                dataRegionName: dataRegionName,
                libraryIds: checked
            }).show();
        },

        sequenceHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.DatabaseWindow', {
                dataRegionName: dataRegionName,
                sequenceIds: checked
            }).show();
        },

        trackHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.DatabaseWindow', {
                dataRegionName: dataRegionName,
                hideCreate: false,
                trackIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Create/Modify JBrowse Session',
            items: [{
                xtype: 'radiogroup',
                hidden: this.hideCreate,
                defaults: {
                    name: 'mode',
                    xtype: 'radio'
                },
                columns: 1,
                items: [{
                    boxLabel: 'Create New Session',
                    inputValue: 'createNew',
                    checked: !this.hideCreate
                },{
                    boxLabel: 'Add To Existing Session',
                    inputValue: 'addToExisting',
                    checked: !!this.hideCreate
                }],
                listeners: {
                    change: function (field, val){
                        var toAdd = [];
                        if (val.mode == 'createNew'){
                            toAdd.push({
                                xtype: 'textfield',
                                allowBlank: false,
                                fieldLabel: 'Name',
                                name: 'name'
                            });

                            toAdd.push({
                                xtype: 'textarea',
                                fieldLabel: 'Description',
                                name: 'description'
                            });
                        }
                        else {
                            toAdd.push({
                                xtype: 'ldk-simplelabkeycombo',
                                allowBlank: false,
                                fieldLabel: 'Session',
                                name: 'databaseId',
                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                schemaName: 'jbrowse',
                                queryName: 'databases',
                                valueField: 'objectid',
                                displayField: 'name'
                            });
                        }

                        var target = field.up('window').down('#target');
                        target.removeAll();
                        target.add(toAdd);
                    },
                    afterrender: function (field){
                        field.fireEvent('change', field, field.getValue());
                    }
                }
            },{
                xtype: 'form',
                itemId: 'target',
                bodyStyle: 'padding: 5px;padding-top: 15px;',
                border: false,
                defaults: {
                    width: 400
                }
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
        var form = this.down('form');
        if (!form.isValid()){
            Ext4.Msg.alert('Error', 'Missing one or more required fields');
            return;
        }

        var mode = this.down('radiogroup').getValue().mode;
        var vals = form.getValues();

        if (this.libraryIds){
            vals.libraryIds = [];
            Ext4.Array.forEach(this.libraryIds, function(i){
                vals.libraryIds.push(parseInt(i));
            }, this);
        }

        if (this.sequenceIds){
            vals.sequenceIds = [];
            Ext4.Array.forEach(this.sequenceIds, function(i){
                vals.sequenceIds.push(parseInt(i));
            }, this);
        }

        if (this.trackIds){
            vals.trackIds = [];
            Ext4.Array.forEach(this.trackIds, function(i){
                vals.trackIds.push(parseInt(i));
            }, this);
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('jbrowse', (mode == 'createNew' ? 'createDatabase' : 'addDatabaseMember'), null),
            jsonData: vals,
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                this.close();

                if (mode == 'createNew'){
                    Ext4.Msg.alert('Success', 'Pipeline job started!', function(){
                        window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
                    }, this);
                }
                else {
                    Ext4.Msg.alert('Success', 'Session updated');
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});