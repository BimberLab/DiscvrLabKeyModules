Ext4.define('SequenceAnalysis.window.VcfSampleWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName, checked, handlerClass) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            //first validate
            Ext4.Msg.wait('Validating files...');
            LABKEY.Ajax.request({
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatusForHandler'),
                params: {
                    handlerType: 'OutputFile',
                    handlerClass: handlerClass,
                    outputFileIds: checked
                },
                scope: this,
                failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
                success: LABKEY.Utils.getCallbackWrapper(function (results) {
                    Ext4.Msg.hide();

                    var errors = [];
                    Ext4.Array.forEach(results.files, function (r) {
                        if (!r.canProcess) {
                            if (!r.fileExists) {
                                errors.push('File does not exist for output: ' + r.outputFileId);
                            }
                            else if (!r.canProcess) {
                                errors.push('Cannot process files of extension: ' + r.extension);
                            }
                        }
                    }, this);

                    if (errors.length) {
                        errors = Ext4.Array.unique(errors);
                        Ext4.Msg.alert('Error', errors.join('<br>'));
                        return;
                    }

                    var dataRegion = LABKEY.DataRegions[dataRegionName];
                    Ext4.create('SequenceAnalysis.window.VcfSampleWindow', {
                        outputFileIds: dataRegion.getChecked()
                    }).show();
                }, this)
            });
        }
    },

    initComponent: function () {
        this.handlerConfig = this.handlerConfig || {};
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            title: 'List VCF Samples',
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'textarea',
                width: 200,
                height: 400
            }],
            buttons: [{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getSamplesFromVcf', null),
            method: 'POST',
            jsonData: {
                outputFileIds: this.outputFileIds
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.onLoad, this)
        });
    },

    onLoad: function(results){
        Ext4.Msg.hide();

        var samples = results.samples;
        var outputFileMap = results.outputFileMap;

        var lines = [];
        Ext4.Array.forEach(Ext4.Object.getKeys(outputFileMap), function(key){
            lines.push(outputFileMap[key].name + ':');
            lines = lines.concat(samples[key]);
        }, this);

        this.down('textarea').setValue(lines.join('\n'));
    }
});