Ext4.define('SequenceAnalysis.window.SaveAnalysisAsTemplateWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName, btn){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            if (checked.length > 1){
                Ext4.Msg.alert('Error', 'Can only select 1 record at a time');
                return;
            }

            Ext4.create('SequenceAnalysis.window.SaveAnalysisAsTemplateWindow', {
                dataRegionName: dataRegionName,
                analysisId: checked[0]
            }).show(btn);
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Save Analysis As Template',
            modal: true,
            width: 400,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 370
            },
            items: [{
                html: 'This will save the parameters of the selected analysis as a template for future use.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'Name',
                itemId: 'nameField'
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

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'saveAnalysisAsTemplate', null, {analysisId: this.analysisId, name: name, description: description}),
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
