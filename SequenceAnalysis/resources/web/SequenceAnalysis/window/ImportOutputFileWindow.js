Ext4.define('SequenceAnalysis.window.ImportOutputFileWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                title: 'Import Sequence Output File',
                workbookPanelCfg: {
                    doLoad: function (containerPath) {
                        Ext4.create('SequenceAnalysis.window.ImportOutputFileWindow', {
                            containerPath: containerPath,
                            dataRegionName: dataRegionName
                        }).show();
                    }
                }
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Import Output File',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 450
            },
            items: [{
                html: 'This will import additional files or analysis products.  Files in this table are most commonly created automatically as part of an analysis pipeline in this module; however, you are able to attach the results of external analyses as well.  This provides a mechanism to organize and group these files, attache them to sample information and kick off additional analyses.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'form',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'importOutputFile', this.containerPath, null),
                fileUpload: true,
                defaults: {
                    border: false,
                    width: 450,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'filefield',
                    fieldLabel: 'File',
                    name: 'outputFile',
                    allowBlank: false
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Name',
                    name: 'name',
                    allowBlank: false
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Description',
                    name: 'description'
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Category',
                    name: 'category',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'outputfile_categories',
                    plugins: ['ldk-usereditablecombo'],
                    displayField: 'category',
                    valueField: 'category'
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Reference Genome',
                    name: 'libraryId',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                    displayField: 'name',
                    valueField: 'rowid',
                    value: LABKEY.ActionURL.getParameter('libraryId') ? parseInt(LABKEY.ActionURL.getParameter('libraryId')) : null,
                    allowBlank: false
                },{
                    xtype: 'sequenceanalysis-readsetfield',
                    fieldLabel: 'Readset',
                    name: 'readset'
                },{
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                }]
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
        var fasta = this.down('filefield[name=outputFile]').getValue();
        if (!fasta){
            Ext4.Msg.alert('Error', 'Must provide a file');
            return;
        }

        if (!this.down('field[name=libraryId]').getValue() || !this.down('field[name=name]').getValue()){
            Ext4.Msg.alert('Error', 'Must provide the genome and name');
            return;
        }

        if (!this.down('form').isValid()){
            Ext4.Msg.alert('Error', 'There are errors in the form.  Hover over the red fields for more information.');
            return;
        }

        Ext4.Msg.wait('Loading...');
        this.down('form').submit({
            scope: this,
            timeout: 999999999,
            success: function(){
                Ext4.Msg.hide();

                this.close();

                Ext4.Msg.alert('Success', 'File Imported!', function(){
                    var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                    dataRegion.refresh();
                }, this);
            },
            failure: function(form, action){
                Ext4.Msg.hide();
                var msg;
                var serverMsg = [];
                if (action && action.response && action.response.responseText){
                    msg = action.response.responseText;
                    serverMsg.push(action.response.responseText);
                }

                if (!msg && action && action.result && action.result.exception){
                    msg = action.result.exception;
                    serverMsg.push(Ext4.encode(action.result));
                }

                if (!msg){
                    msg = 'There was an error uploading this file';
                }

                Ext4.Msg.alert('Error', msg);
                LDK.Utils.logError('Problem uploading sequence output file: ' + serverMsg.join(';'));
                console.error(serverMsg);
            }
        });
    }
});
