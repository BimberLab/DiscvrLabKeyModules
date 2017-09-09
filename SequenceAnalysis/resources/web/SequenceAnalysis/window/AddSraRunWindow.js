Ext4.define('SequenceAnalysis.window.AddSraRunWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('SequenceAnalysis.window.AddSraRunWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Add/Update SRA Run Information',
            width: 800,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This helper allows you to add SRA run information for readsets in bulk.  You can use the box below to cut/paste a tab-delimited table listing the readset ID in the first column and the SRA Run number into the second.  This table should not have a header row.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textarea',
                width: 780,
                height: 300,
                itemId: 'tableField'
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
        var data = this.down('#tableField').getValue();
        if (!data){
            Ext4.Msg.alert('Error', 'Must provide a table listing readset IDs and SRA run');
            return;
        }

        data = LDK.Utils.CSVToArray(data, '\t');

        var distinctReadsets = [];
        var readsetToSraMap = {};
        Ext4.Array.forEach(data, function(row){
            distinctReadsets.push(row[0]);
            readsetToSraMap[row[0]] = row[1];
        }, this);

        Ext4.Msg.wait('Loading...');

        LABKEY.Query.selectRows({
            method: 'POST',
            schemaName: 'sequenceanalysis',
            queryName: 'readdata',
            columns: 'rowid,readset,container',
            filterArray: [LABKEY.Filter.create('readset', distinctReadsets.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                if (!results || !results.rows){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'Unable to find matching rows');
                    return;
                }

                var toUpdate = [];
                var distinctReadsetsFound = [];
                Ext4.Array.forEach(results.rows, function(row){
                    toUpdate.push({
                        rowid: row.rowid,
                        container: row.container,
                        sra_accession: readsetToSraMap[row.readset]
                    });

                    Ext4.Array.remove(distinctReadsets, String(row.readset));
                }, this);

                if (distinctReadsets.length){
                    Ext4.Msg.alert('Error', 'The following readset IDs are not valid or lack imported data: ' + distinctReadsets.join(', '));
                    return;
                }

                if (toUpdate.length) {
                    LABKEY.Query.updateRows({
                        method: 'POST',
                        schemaName: 'sequenceanalysis',
                        queryName: 'readdata',
                        rows: toUpdate,
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: function (results) {
                            Ext4.Msg.hide();

                            if (this.dataRegionName){
                                LABKEY.DataRegions[this.dataRegionName].clearSelected();
                            }

                            Ext4.Msg.alert('Success', 'Readsets updated', function () {
                                LABKEY.DataRegions[this.dataRegionName].refresh();
                            }, this);
                        }
                    });
                }
                else {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'No matching readsets found for the selected rows');
                }
            }
        });
    }
});