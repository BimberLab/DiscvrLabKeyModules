/**
 * @dataIds
 * @outputFileIds
 */
Ext4.define('JBrowse.window.ViewOutputsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.ViewOutputsWindow', {
                outputFileIds: checked
            }).show();
        }
    },

    initComponent: function () {
        Ext4.apply(this, {
            title: 'View In JBrowse',
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            defaults: {
                border: false,
                labelWidth: 140,
                width: 475
            },
            items: [{
                html: 'Loading...'
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

        this.loadData();
    },

    loadData: function(){
        var obj = {
            handlerClass: 'org.labkey.jbrowse.JBrowseSequenceFileHandler'
        };

        if (this.dataIds){
            obj.dataIds = this.dataIds;
        }

        if (this.outputFileIds){
            obj.outputFileIds = this.outputFileIds;
        }

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatus'),
            params: obj,
            scope: this,
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
            success: LABKEY.Utils.getCallbackWrapper(this.onDataLoad, this)
        });
    },

    onDataLoad: function(results){
        this.fileInfo = [];
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
            else {
                this.fileInfo.push(r.dataId);
                if (r.libraryId){
                    distinctGenomes.push(r.libraryId);
                }
            }
        }, this);

        distinctGenomes = Ext4.Array.unique(distinctGenomes);
        this.removeAll();
        if (errors.length){
            errors = Ext4.Array.unique(errors);
            this.add({
                html: errors.join('<br>')
            });
        }
        else {
            this.add([{
                xtype: 'textfield',
                allowBlank: false,
                fieldLabel: 'Session Name',
                name: 'name',
                listeners: {
                    afterrender: function(field){
                        field.focus.defer(100, field);
                    }
                }
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'description'
            },{
                xtype: 'ldk-simplelabkeycombo',
                name: 'libraryId',
                fieldLabel: 'Reference Genome',
                schemaName: 'sequenceanalysis',
                queryName: 'reference_libraries',
                displayField: 'name',
                valueField: 'rowid',
                value: distinctGenomes[0]
            }]);
        }
    },

    onSubmit: function(btn){
        if (!this.fileInfo || !this.fileInfo.length){
            Ext4.Msg.alert('Error', 'There are no suitable files to display');
            return;
        }

        var name = this.down('field[name=name]').getValue();
        var description = this.down('field[name=description]').getValue();
        var libraryId = this.down('field[name=libraryId]').getValue();

        if (!name || !libraryId){
            Ext4.Msg.alert('Error', 'Must provide a name and library Id');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('jbrowse', 'createDatabase', null),
            jsonData: {
                name: name,
                libraryId: libraryId,
                outputFileIds: this.outputFileIds
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