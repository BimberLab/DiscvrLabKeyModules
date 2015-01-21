Ext4.define('SequenceAnalysis.window.LiftoverWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
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
                    handlerClass: 'org.labkey.sequenceanalysis.analysis.LiftoverHandler',
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
                            else if (!r.libraryId){
                                errors.push('One or more files is missing a genome and cannot be processed');
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

                        Ext4.create('SequenceAnalysis.window.LiftoverWindow', {
                            dataRegionName: dataRegionName,
                            outputFileIds: checked,
                            libraryId: distinctGenomes.length == 1 ? distinctGenomes[0] : null
                        }).show();
                    }
                }, this)
            });
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Liftover File(s) To Alternate Genome',
            items: [{
                html: 'Many analyses generate data that are connected to specific sites of the genome.  These could include SNPs, BED/GFF files that show exons, or other regions of interest like methylation sites.  This helper allows you to perform a liftover (ie. translate these coordinates from one genome to another), assuming you have created and importeded the appropriate chain files for these translations.  The drop down below shows the target genomes available to the samples you selected.  Per sample, one file will be created containing those positions able to be lifted over, and a second file will contain those unable to be lifted.',
                style: 'padding-bottom: 20px;',
                border: false
            },{
                xtype: 'labkey-combo',
                itemId: 'chainFileId',
                fieldLabel: 'Target Genome',
                forceSelection: true,
                width: 450,
                store: {
                    type: 'labkey-store',
                    schemaName: 'sequenceanalysis',
                    queryName: 'chain_files',
                    columns: 'rowid,chainFile,genomeId2/name',
                    filterArray: [LABKEY.Filter.create('genomeId1', this.libraryId, LABKEY.Filter.Types.EQUAL)],
                    autoLoad: true
                },
                displayField: 'genomeId2/name',
                valueField: 'rowid',
                allowBlank: false
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

    onSubmit: function() {
        var chainFile = this.down('#chainFileId');
        if (!chainFile.getValue()) {
            Ext4.Msg.alert('Error', 'Must choose the target genome');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'runSequenceHandler'),
            jsonData: {
                handlerClass: 'org.labkey.sequenceanalysis.analysis.LiftoverHandler',
                outputFileIds: this.outputFileIds,
                params: Ext4.encode({
                    chainRowId: chainFile.getValue()
                })
            },
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                this.close();

                window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});