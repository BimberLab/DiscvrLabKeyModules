/**
 * @param dataRegionName
 * @param containerId
 *
 */
Ext4.define('SequenceAnalysis.window.ManageFileSetsWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.sequenceanalysis-managefilesetwindow',

    statics: {
        buttonHandlerForOutputFiles: function(outputFileId, dataRegionName){
            Ext4.create('SequenceAnalysis.window.ManageFileSetsWindow', {
                dataRegionName: dataRegionName,
                targetTable: 'outputfiles',
                targetField: 'outputFileId',
                pks: [outputFileId]
            }).show();
        }
    },

    hasChanges: false,

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Manage Filesets',
            border: false,
            width: 420,
            closeAction: 'destroy',
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'panel',
                itemId: 'target',
                border: false,
                items: [{
                    html: '<i class="fa fa-spinner fa-pulse"></i> Loading...',
                    border: false
                }]
            }],
            buttons: [{
                text: 'Add Filesets',
                scope: this,
                handler: function(btn){
                    btn.up('window').close();

                    Ext4.create('SequenceAnalysis.window.AddFileSetsWindow', {
                        targetTable: 'outputfiles',
                        targetField: 'outputFileId',
                        dataRegionName: this.dataRegionName,
                        pks: this.pks
                    }).show();
                }
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }],
            listeners: {
                close: function(win){
                    if (win.hasChanges){
                        this.refreshDataRegion();
                    }
                }
            }
        });

        this.callParent();
        this.doLoadData();
    },

    doLoadData: function(){
        Ext4.Msg.wait('Loading...');
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'analysisSetMembers',
            columns: 'rowid,outputFileId,analysisSet,analysisSet/name',
            filterArray: [LABKEY.Filter.create('outputFileId', this.pks.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: this.onDataLoad
        });
    },

    onDataLoad: function(results){
        Ext4.Msg.hide();

        var toAdd = [];
        Ext4.Array.forEach(results.rows, function(row){
            toAdd.push({
                html: row['analysisSet/name'],
                style: 'padding-right: 10px',
                border: false,
                analysisSetRowId: row.rowid.toString()
            });

            LDK.Assert.assertNotEmpty('Missing analysisSet in ManageFileSetsWindow', row.rowid);
            toAdd.push({
                xtype: 'button',
                text: 'Remove',
                style: 'margin-bottom: 5px;',
                analysisSetRowId: row.rowid.toString(),
                handler: this.doRemoveItem
            });
        }, this);

        var target = this.down('#target');
        target.removeAll();
        target.add({
            layout: {
                type: 'table',
                columns: 2
            },
                border: false,
            items: toAdd
        });
    },

    doRemoveItem: function(btn){
        Ext4.Msg.wait('Removing...');
            LABKEY.Query.deleteRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'analysisSetMembers',
            rows: [{
                rowId: btn.analysisSetRowId
            }],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(){
                Ext4.Msg.hide();

                var win = btn.up('window');
                var items = win.query('[analysisSetRowId=\'' + btn.analysisSetRowId + '\']');
                win.down('#target').removeAll(items);

                win.hasChanges = true;
            }
        });
    },

    refreshDataRegion: function(){
        if (this.dataRegionName) {
            LABKEY.DataRegions[this.dataRegionName].refresh();
        }
    }
});