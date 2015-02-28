Ext4.define('SequenceAnalysis.window.SaveAnalysisAsTemplateWindow', {
    extend: 'Ext.window.Window',

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Save Analysis Template',
            modal: true,
            width: 400,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 370
            },
            items: [{
                html: 'This will save the parameters from this form as a template for future use.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'Name',
                itemId: 'nameField',
                listeners: {
                    afterrender: function(field){
                        field.focus.defer(100, field);
                    }
                }
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                itemId: 'descriptionField'
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
        var name = this.down('#nameField').getValue();
        if (!name){
            Ext4.Msg.alert('Error', 'Must provide a name');
            return;
        }

        var description = this.down('#descriptionField').getValue();
        var json = this.sequencePanel.getJsonParams(true);
        delete json.protocolName;
        delete json.protocolDesription;
        for (var key in json){
            if (key.match(/^readset_/)){
                delete json[key];
            }
        }

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'saveAnalysisAsTemplate', Laboratory.Utils.getQueryContainerPath()),
            method: 'POST',
            params: {
                taskId: this.sequencePanel.taskId,
                name: name,
                description: description,
                json: Ext4.encode(json)
            },
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Success!');

                this.close();
            }
        });
    }
});
