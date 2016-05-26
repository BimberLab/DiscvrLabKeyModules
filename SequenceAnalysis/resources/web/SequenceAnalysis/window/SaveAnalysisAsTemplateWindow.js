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
                itemId: 'nameField'
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                itemId: 'descriptionField'
            }],
            listeners: {
                show: function(win){
                    var field = win.down('#nameField');
                    Ext4.defer(field.focus, 100, field);

                    new Ext4.util.KeyNav(win.getEl(), {
                        "enter" : function(e){
                            win.onSubmit();
                        },
                        scope : this
                    });
                }
            },
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

    onSubmit: function() {
        var name = this.down('#nameField').getValue();
        if (!name) {
            Ext4.Msg.alert('Error', 'Must provide a name');
            return;
        }

        var description = this.down('#descriptionField').getValue();
        var json = this.sequencePanel.getJsonParams(true);
        delete json.protocolName;
        delete json.protocolDesription;
        for (var key in json) {
            if (key.match(/^readset_/) || key.match(/^sample_/)) {
                delete json[key];
            }
        }

        LDK.Assert.assertNotEmpty('unable to find taskid when saving template', this.sequencePanel.taskId);

        var params = {
            taskId: this.sequencePanel.taskId,
            name: name,
            description: description,
            json: Ext4.encode(json)
        };

        Ext4.Msg.wait('Saving...');
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'saved_analyses',
            columns: 'rowid',
            filterArray: [
                LABKEY.Filter.create('name', name),
                LABKEY.Filter.create('taskid', this.sequencePanel.taskId)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                if (results && results.rows && results.rows.length){
                    Ext4.Msg.confirm('Overwrite Existing?', 'There is another saves templated with this name.  Do you want to overwrite it?', function(val){
                        if (val === 'yes'){
                            this.doSave(params);
                        }
                    }, this);
                }
                else {
                    this.doSave(params);
                }
            }

        })
    },

    doSave: function(params){
        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'saveAnalysisAsTemplate', Laboratory.Utils.getQueryContainerPath()),
            method: 'POST',
            params: params,
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
