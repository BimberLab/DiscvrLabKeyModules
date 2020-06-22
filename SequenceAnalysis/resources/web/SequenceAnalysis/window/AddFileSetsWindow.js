/**
 * @param dataRegionName
 * @param containerId
 *
 */
Ext4.define('SequenceAnalysis.window.AddFileSetsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandlerForOutputFiles: function(dataRegionName){
            var checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length) {
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.AddFileSetsWindow', {
                targetTable: 'outputfiles',
                targetField: 'outputFileId',
                dataRegionName: dataRegionName,
                pks: checked
            }).show();
        }
    },

    initComponent: function(){
        if (!this.pks.length){
            Ext4.Msg.alert('Error', 'No rows selected');
            return;
        }

        Ext4.apply(this, {
            title: 'Add To Fileset',
            border: false,
            width: 420,
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'panel',
                border: false,
                defaults: {
                    width: 400,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'combo',
                    fieldLabel: 'Choose Existing Fileset',
                    emptyText: 'Choose a pre-defined set',
                    itemId: 'existing',
                    store: {
                        type: 'labkey-store',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'laboratory',
                        sql: 'SELECT DISTINCT rowid, name FROM sequenceanalysis.analysisSets',
                        autoLoad: true
                    },
                    valueField: 'rowid',
                    displayField: 'name',
                    listeners: {
                        change: function(cmp){
                            var field = cmp.up('panel').down('#newSet');
                            field.suspendEvents();
                            field.setValue(null);
                            field.resumeEvents();
                        }
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Create New Set',
                    itemId: 'newSet',
                    listeners: {
                        change: function(cmp){
                            var field = cmp.up('panel').down('#existing');
                            field.suspendEvents();
                            field.setValue(null);
                            field.resumeEvents();
                        }
                    }
                }]
            }],
            buttons:  [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();
    },

    onSubmit: function(){
        var existingField = this.down('#existing').getValue();
        var newField = this.down('#newSet').getValue();

        if (!existingField && !newField){
            Ext4.Msg.alert('Error', 'Must provide a fileset name');
            return;
        }

        Ext4.Msg.wait('Saving...');

        //if a new set is chosen, first create it
        //otherwise go direct to updating membership
        if (!existingField){
            LABKEY.Query.insertRows({
                //TODO: consider whether this should really be limited to folder-level or not
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'analysisSets',
                rows: [{
                    name: newField
                }],
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function(response){
                    var rowId = response.rows[0].rowid;
                    LDK.Assert.assertNotEmpty('RowId not found in AddFileSetsWindow', rowId);

                    this.updateMembership(rowId);
                }
            });
        }
        else {
            this.updateMembership(existingField);
        }
    },

    updateMembership: function(rowId) {
        //first find existing fileset membership for these files against these filesets
        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'analysisSetMembers',
            columns: this.targetField,
            filterArray: [
                LABKEY.Filter.create(this.targetField, this.pks.join(';'), LABKEY.Filter.Types.IN),
                LABKEY.Filter.create('analysisSet', rowId)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function (response) {
                var toAdd = [].concat(this.pks);
                Ext4.Array.forEach(response.rows, function(row){
                    if (row[this.targetField]){
                        Ext4.Array.remove(toAdd, row[this.targetField].toString());
                    }
                }, this);

                if (toAdd.length) {
                    var rows = [];
                    Ext4.Array.forEach(toAdd, function (pk) {
                        var obj = {analysisSet: rowId};
                        obj[this.targetField] = pk;
                        rows.push(obj);
                    }, this);

                    var dataRegionName = this.dataRegionName;
                    LABKEY.Query.insertRows({
                        //TODO: consider whether this should really be limited to folder-level or not
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        schemaName: 'sequenceanalysis',
                        queryName: 'analysisSetMembers',
                        rows: rows,
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: this.refreshDataRegion
                    });
                }
                else {
                    this.refreshDataRegion();
                }
            }
        });
    },

    refreshDataRegion: function(){
        Ext4.Msg.hide();
        if (this.dataRegionName) {
            LABKEY.DataRegions[this.dataRegionName].refresh();
        }

        this.close();
    }
});