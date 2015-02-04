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

            if (checked.length > 1){
                Ext4.Msg.alert('Error', 'Can only select 1 reference at a time');
                return;
            }

            Ext4.create('JBrowse.window.DatabaseWindow', {
                dataRegionName: dataRegionName,
                libraryId: parseInt(checked[0])
            }).show();
        },

        outputFilesHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            //first validate
            Ext4.Msg.wait('Validating files...');
            LABKEY.Ajax.request({
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatus'),
                params: {
                    handlerClass: 'org.labkey.jbrowse.JBrowseSequenceOutputHandler',
                    outputFileIds: checked
                },
                scope: this,
                failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
                success: LABKEY.Utils.getCallbackWrapper(function(results){
                    Ext4.Msg.hide();

                    var errors = [];
                    var distinctGenomes = [];
                    Ext4.Array.forEach(results.files, function(r){
                        if (!r.canProcess){
                            if (!r.fileExists){
                                errors.push('File does not exist for output: ' + r.outputFileId);
                            }
                            else if (!r.canProcess){
                                errors.push('Cannot process files of extension: ' + r.extension);
                            }
                        }
                        else if (r.libraryId){
                            distinctGenomes.push(r.libraryId);
                        }
                    }, this);

                    if (errors.length){
                        errors = Ext4.Array.unique(errors);
                        Ext4.Msg.alert('Error', errors.join('<br>'));
                    }
                    else {
                        distinctGenomes = Ext4.Array.unique(distinctGenomes);

                        Ext4.create('JBrowse.window.DatabaseWindow', {
                            dataRegionName: dataRegionName,
                            outputFileIds: checked,
                            libraryId: distinctGenomes.length == 1 ? distinctGenomes[0] : null
                        }).show();
                    }
                }, this)
            });
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
                hideCreate: true,
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
                        var win = field.up('window');
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

                            toAdd.push({
                                xtype: win.libraryId ? 'hidden' : 'ldk-simplelabkeycombo',
                                name: 'libraryId',
                                fieldLabel: 'Reference Genome',
                                schemaName: 'sequenceanalysis',
                                queryName: 'reference_libraries',
                                displayField: 'name',
                                valueField: 'rowid',
                                allowBlank: false,
                                value: win.libraryId
                            });

                            toAdd.push({
                                xtype: 'checkbox',
                                fieldLabel: 'Temporary Session?',
                                helpPopup: 'By default, custom sessions are deleted after 24H.  It is usually very quick to re-created them.  Uncheck this if you want to keep this session for a longer period of time',
                                checked: true,
                                name: 'isTemporary'
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
                                filterArray: this.libraryId ? [LABKEY.Filter.create('libraryId', this.libraryId)] : null,
                                valueField: 'objectid',
                                displayField: 'name'
                            });
                        }

                        var target = win.down('#target');
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
                    width: 400,
                    labelWidth: 130
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

        if (mode == 'createNew' && !vals.libraryId){
            Ext4.Msg.alert('Error', 'Must provide the reference genome');
            return;
        }

        if (mode == 'addToExisting' && !vals.databaseId){
            Ext4.Msg.alert('Error', 'Must choose a session');
            return;
        }

        if (this.trackIds){
            vals.trackIds = [];
            Ext4.Array.forEach(this.trackIds, function(i){
                vals.trackIds.push(parseInt(i));
            }, this);
        }

        if (this.outputFileIds){
            vals.outputFileIds = [];
            Ext4.Array.forEach(this.outputFileIds, function(i){
                vals.outputFileIds.push(parseInt(i));
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
                    Ext4.Msg.alert('Success', 'The session is being created.  The time it takes to process the files will depend on their size.  The next screen will show the active pipeline job, which is preparing the files in the background.  Once it completes, click the description to load it.', function(){
                        window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
                    }, this);
                }
                else {
                    Ext4.Msg.alert('Success', 'The session is being updated.  The time it takes to process the new files will depend on their size.  The next screen will show the active pipeline job, which is preparing the files in the background.  Once it completes, click the description to load it.', function(){
                        window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
                    }, this);
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});