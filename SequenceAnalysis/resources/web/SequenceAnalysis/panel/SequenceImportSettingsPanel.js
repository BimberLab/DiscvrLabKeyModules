Ext4.define('SequenceAnalysis.panel.SequenceImportSettingsPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.sequenceanalysis-sequenceimportsettingspanel',

    hidePageLoadWarning: true,
    hideButtons: false,
    maxWidth: 650,

    initComponent: function(){
        Ext4.applyIf(this, {
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'Loading...',
                border: false
            }],
            buttons: this.hideButtons ? null : SequenceAnalysis.panel.SequenceImportSettingsPanel.getButtons()
        });

        this.callParent(arguments);

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getSequenceImportDefaults'),
            scope: this,
            success: this.onDataLoad,
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onDataLoad: function(response){
        LDK.Utils.decodeHttpResponseJson(response);
        this.removeAll();

        if (response.responseJSON){
            var configDefaults = response.responseJSON;
            var items = [{
                html: 'Note: you must reload this page before any change will be applied.',
                border: false,
                hidden: !!this.hidePageLoadWarning
            }, SequenceAnalysis.panel.SequenceImportPanel.getInputFileTreatmentField({
                itemId: 'inputTreatment',
                value: configDefaults.inputFileTreatment || 'delete'
            })];

            this.add(items);
        }
        else {
            this.add({html: 'Something went wrong loading saved data'});
        }
    },

    statics: {
        getButtons: function () {
            return [{
                text: 'Submit',
                handler: function (btn) {
                    var win = btn.up('window');
                    var panel = win ? win.down('sequenceanalysis-sequenceimportsettingspanel') : btn.up('sequenceanalysis-sequenceimportsettingspanel');

                    var params = {};
                    params['inputFileTreatment'] = panel.down('#inputTreatment').getValue();

                    Ext4.Msg.wait('Saving...');
                    LABKEY.Ajax.request({
                        method: 'POST',
                        url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'setSequenceImportDefaults'),
                        jsonData: params,
                        scope: panel,
                        success: panel.onSuccess,
                        failure: LDK.Utils.getErrorCallback()
                    })
                }
            }];
        }
    },

    onSuccess: function(){
        Ext4.Msg.hide();
        Ext4.Msg.alert('Success', 'Settings have been saved');

        if (this.up('window')){
            this.up('window').close();
        }
    }
});