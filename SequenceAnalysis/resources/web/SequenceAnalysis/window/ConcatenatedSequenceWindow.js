Ext4.define('SequenceAnalysis.window.ConcatenatedSequenceWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            Ext4.create('SequenceAnalysis.window.ConcatenatedSequenceWindow', {
                dataRegionName: dataRegionName,
                ntIds: checked
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Concatenate Sequences',
            width: 800,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This helper was originally written to help concatenate smaller unplaced contigs into a single pseudo-chromosome; however, it can be used for any situation in which you want to concatenate sequences together to form a new sequence.  Any sequences will be separated by a series of Ns.  In addition to the sequence (which wil be added to the DB), a file will be written containing the offet for each original sequence.  You have selected ' + this.ntIds.length + ' sequences.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'Name',
                itemId: 'name',
                allowBlank: false,
                width: 400
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                width: 400,
                height: 200,
                allowBlank: false,
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

    onSubmit: function(){
        var name = this.down('#name').getValue();
        var description = this.down('#description').getValue();

        if (!name || !description){
            Ext4.Msg.alert('Error', 'Must provide the name and description');
            return;
        }

        Ext4.Msg.wait('Loading...');

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'concatenateSequences'),
            jsonData: {
                sequenceIds: this.ntIds,
                name: name,
                description: description
            },
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: LABKEY.Utils.getCallbackWrapper(function(){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Pipeline Job Started', function(){
                    window.location = LABKEY.ActionURL.buildURL("pipeline-status", "showList.view", Laboratory.Utils.getQueryContainerPath(), {'StatusFiles.Status~neqornull': 'COMPLETE'});
                });
            }, this, false)
        });
    }
});