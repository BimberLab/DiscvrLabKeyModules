Ext4.define('SequenceAnalysis.window.DownloadOutputsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.DownloadOutputsWindow', {
                dataRegionName: dataRegionName,
                rowIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Download Files',
            width: 400,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This will download a ZIP with the selected files.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'textfield',
                fieldLabel: 'Filename',
                itemId: 'fileName',
                value: 'outputs.zip'
            }],
            buttons: [{
                text: 'Download',
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
        Ext4.Msg.wait('Loading...');

        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'outputfiles',
            columns: 'rowid,dataId',
            filterArray: [LABKEY.Filter.create('rowid', this.rowIds.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();

                if (!results || !results.rows || !results.rows.length){
                    Ext4.Msg.alert('Error', 'Error, no matching rows found');
                    return;
                }

                this.expDatas = [];
                Ext4.Array.forEach(results.rows, function(row){
                    LDK.Assert.assertNotEmpty('DataId was empty for output row: ' + row.rowid, row.dataid);
                    this.expDatas.push(row.dataid);
                }, this);

                Ext4.Msg.hide();
            }
        });
    },

    onSubmit: function(btn){
        var name = this.down('#fileName').getValue();
        if (!name){
            Ext4.Msg.alert('Error', 'Must provide a file name');
            return;
        }

        var params = {};
        params['X-LABKEY-CSRF'] = LABKEY.CSRF;
        params.zipFileName = name;
        params.dataIds = this.expDatas;

        Ext4.create('Ext.form.Basic', this, {
            url: LABKEY.ActionURL.buildURL('experiment', 'exportFiles', Laboratory.Utils.getQueryContainerPath()),
            method: 'GET',
            standardSubmit: true
        }).submit({
            params: params
        });

        this.close();
    }
});
