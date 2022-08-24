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
            },{
                xtype: 'checkbox',
                fieldLabel: 'Save Globally?',
                hidden: !LABKEY.Security.currentUser.isAdmin,
                itemId: 'saveGloballyField'
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
        var saveGlobally = this.down('#saveGloballyField').getValue();
        var json = this.sequencePanel.getJsonParams({ignoreErrors: true, skipFieldsNotSavable: true});
        delete json.protocolName;
        delete json.protocolDescription;
        delete json.jobName;
        delete json.jobDescription;
        delete json.readsetIds;
        delete json.analysisIds;
        delete json.fileGroups;
        for (var key in json) {
            if (key.match(/^readset_/) || key.match(/^sample_/)) {
                delete json[key];
            }
        }

        LDK.Assert.assertNotEmpty('unable to find jobType when saving template', this.sequencePanel.jobType);

        var params = {
            taskId: this.sequencePanel.jobType,
            name: name,
            description: description,
            json: Ext4.encode(json)
        };

        Ext4.Msg.wait('Saving...');
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'saved_analyses',
            columns: 'rowid,container,container/Path',
            sort: 'name',
            filterArray: [
                LABKEY.Filter.create('name', name),
                LABKEY.Filter.create('taskid', this.sequencePanel.jobType)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                var targetContainer = saveGlobally ? 'Shared' : Laboratory.Utils.getQueryContainerPath();

                if (results && results.rows && results.rows.length){
                    var row = results.rows[0];
                    if (row['container/Path'] != targetContainer){
                        Ext4.Msg.confirm('Overwrite Existing?', 'There is another saved template with this name; however, it has been saved in the folder: ' + row['container/Path'] + ', not ' + targetContainer + '.  You can overwrite this template, but it will remain in the original folder.  Do you want to continue?', function(val){
                            if (val === 'yes'){
                                this.doSave(params, row.container);
                            }
                        }, this);
                    }
                    else {
                        Ext4.Msg.confirm('Overwrite Existing?', 'There is another saved template with this name.  Do you want to overwrite it?', function(val){
                            if (val === 'yes'){
                                this.doSave(params, row.container);
                            }
                        }, this);
                    }
                }
                else {
                    this.doSave(params, targetContainer);
                }
            }
        });
    },

    doSave: function(params, targetContainer){
        Ext4.Msg.wait('Saving...');

        //check permissions
        LABKEY.Security.getUserPermissions({
            containerPath: targetContainer,
            userId: LABKEY.Security.currentUser.id,
            success: function(results){
                if (results.container.effectivePermissions.indexOf('org.labkey.api.security.permissions.UpdatePermission') > -1){
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'saveAnalysisAsTemplate', targetContainer),
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
                else {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'You do not have update permissions on the target folder, cannot overwrite template.');
                }
            },
            scope: this
        });
    }
});
