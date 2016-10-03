Ext4.define('GeneticsCore.window.PublishResultsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dr = LABKEY.DataRegions[dataRegionName];
            LDK.Assert.assertNotEmpty('Unable to find dataregion in PublishResultsWindow', dr);

            if (!dr.getChecked().length) {
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('GeneticsCore.window.PublishResultsWindow', {
                dataRegionName: dataRegionName,
                actionName: 'cacheAnalyses'
            }).show();
        }

        // haplotypeButtonHandler: function(dataRegionName){
        //     var dr = LABKEY.DataRegions[dataRegionName];
        //     LDK.Assert.assertNotEmpty('Unable to find dataregion in PublishResultsWindow', dr);
        //
        //     if (!dr.getChecked().length) {
        //         Ext4.Msg.alert('Error', 'No rows selected');
        //         return;
        //     }
        //
        //     Ext4.create('GeneticsCore.window.PublishResultsWindow', {
        //         dataRegionName: dataRegionName,
        //         actionName: 'cacheHaplotypes'
        //     }).show();
        // }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Publish/Cache Results',
            bodyStyle: 'padding: 5px;',
            closeAction: 'destroy',
            width: 500,
            defaults: {
                border: false
            },
            items: [{
                html: 'This will take a snapshot of the selected rows and store the summary in the Genotyping assay.  Note: this will append these results into the assay, rather than replace existing cached results.  If you want to delete these, you can do this from the assay itself.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'combo',
                itemId: 'protocolField',
                width: 400,
                fieldLabel: 'Choose Target Assay',
                disabled: true,
                displayField: 'Name',
                valueField: 'RowId',
                queryMode: 'local',
                triggerAction: 'all',
                store: {
                    type: 'array',
                    proxy: {
                        type: 'memory'
                    },
                    fields: ['Name', 'RowId']
                }
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

        LABKEY.Assay.getByType({
            type: 'Genotype Assay',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: this.onLoad
        });

        this.callParent(arguments);
    },

    onLoad: function(results){
        if (!results || !results.length){
            Ext4.Msg.alert('Error', 'No suitable assays found');
            return;
        }

        var field = this.down('#protocolField');
        Ext4.Array.forEach(results, function(r){
            field.store.add({
                RowId: r.id,
                Name: r.name
            });
        }, this);

        if (results.length == 1){
            field.setValue(results[0].id);
        }
        field.setDisabled(false);
    },

    onSubmit: function(){
        var alleleNames = this.alleleNames || (this.dataRegionName ? LABKEY.DataRegions[this.dataRegionName].getChecked() : null) || [];
        var protocol = this.down('#protocolField').getValue();
        if ((!alleleNames.length && !this.json) || !protocol){
            Ext4.Msg.alert('Error', 'Missing either alleles or the target assay');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('geneticscore', this.actionName, null),
            method: 'post',
            timeout: 10000000,
            scope: this,
            jsonData: {
                alleleNames: alleleNames,
                json: Ext4.encode(this.json),
                protocolId: protocol
            },
            failure: LDK.Utils.getErrorCallback(),
            success: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();
                this.close();

                if (results){
                    Ext4.Msg.alert('Success', 'Success!  The results have been cached.');
                    Ext4.each(Ext4.Object.getKeys(LABKEY.DataRegions), function(name){
                        var dr = LABKEY.DataRegions[name];
                        if (dr.schemaName.match(/^assay.GenotypeAssay/g) || dr.queryName.match(/^alignment_summary/g)){
                            dr.clearSelected();
                            dr.refresh();
                        }
                    }, this);

                    if (this.dataRegionName){
                        LABKEY.DataRegions[this.dataRegionName].clearSelected();
                        LABKEY.DataRegions[this.dataRegionName].refresh();
                    }
                }
                else {
                    Ext4.Msg.alert('Error', 'Something may have gone wrong');
                }
            }, this)
        });
    }
});
