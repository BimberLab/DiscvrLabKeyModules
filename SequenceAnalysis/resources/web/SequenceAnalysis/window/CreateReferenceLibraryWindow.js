Ext4.define('SequenceAnalysis.window.CreateReferenceLibraryWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.CreateReferenceLibraryWindow', {
                dataRegionName: dataRegionName,
                rowIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Create Reference Library',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 400
            },
            items: [{
                html: 'This will create a reference library from the selected sequences.  This library can be reused between analyses.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'textfield',
                fieldLabel: 'Name',
                itemId: 'name',
                allowBlank: false
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                itemId: 'description'
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

    onSubmit: function(btn){
        var name = this.down('#name').getValue();
        if (!name){
            Ext4.Msg.alert('Error', 'Must provide a name');
            return;
        }

        var description = this.down('#description').getValue();

        Ext4.Msg.wait('Loading...');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'createReferenceLibrary', null, null),
            jsonData: {
                name: name,
                description: description,
                sequenceIds: this.rowIds
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
