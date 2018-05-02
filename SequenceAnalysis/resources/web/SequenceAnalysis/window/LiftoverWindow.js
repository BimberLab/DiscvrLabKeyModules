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
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatusForHandler'),
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
                        Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                            workbookPanelCfg: {
                                doLoad: function(containerPath){
                                    Ext4.create('SequenceAnalysis.window.LiftoverWindow', {
                                        containerPath: containerPath,
                                        dataRegionName: dataRegionName,
                                        outputFileIds: checked,
                                        libraryId: distinctGenomes.length == 1 ? distinctGenomes[0] : null,
                                        toolParameters: results.toolParameters
                                    }).show();
                                }
                            }
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
                    columns: 'rowid,chainFile,genomeId2,genomeId2/name',
                    filterArray: [
                        LABKEY.Filter.create('genomeId1', this.libraryId, LABKEY.Filter.Types.EQUAL),
                        LABKEY.Filter.create('dateDisabled', null, LABKEY.Filter.Types.ISBLANK)
                    ],
                    autoLoad: true
                },
                displayField: 'genomeId2/name',
                valueField: 'chainFile',
                allowBlank: false
            },{
                xtype: 'ldk-numberfield',
                minValue: 0,
                maxValue: 1.0,
                value: 0.95,
                fieldLabel: 'Min Percent Match',
                helpPopup: 'In order to lift to the target genome, the feature must have at least this percent match.  Lower this value to be more permissive; however, this risks incorrect liftovers',
                itemId: 'pctField'
            },{
                xtype: 'checkbox',
                itemId: 'discardRejected',
                checked: false,
                helpPopup: 'If checked, the variants unable to liftover will not be saved to a separate file',
                fieldLabel: 'Do Not Save Failed Liftover'
            },{
                xtype: 'checkbox',
                itemId: 'dropGenotypes',
                checked: false,
                helpPopup: 'If checked, no genotypes will be written to the output file (applies to VCFs only).  This can be useful (and necessary) when lifting VCFs with extremely high sample number.',
                fieldLabel: 'Drop Genotypes'
            }].concat(SequenceAnalysis.window.OutputHandlerWindow.getCfgForToolParameters(this.toolParameters)),
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
        var chainField = this.down('#chainFileId');
        if (!chainField.getValue()) {
            Ext4.Msg.alert('Error', 'Must choose the target genome');
            return;
        }

        var recIdx = chainField.store.findExact('chainFile', chainField.getValue());
        var rec = chainField.store.getAt(recIdx);
        LDK.Assert.assertNotEmpty('Record not found in liftoverwindow', rec);

        var params = {
            chainFileId: chainField.getValue(),
            targetGenomeId: rec.get('genomeId2')
        };

        if (this.down('#pctField').getValue()){
            params.pct = this.down('#pctField').getValue();
        }

        if (this.down('#discardRejected').getValue()){
            params.discardRejected = this.down('#discardRejected').getValue();
        }

        if (this.down('#dropGenotypes').getValue()){
            params.dropGenotypes = this.down('#dropGenotypes').getValue();
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'runSequenceHandler', this.containerPath),
            jsonData: {
                handlerClass: 'org.labkey.sequenceanalysis.analysis.LiftoverHandler',
                outputFileIds: this.outputFileIds,
                params: Ext4.encode(params)
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