Ext4.define('SingleCell.panel.TenxSettingsPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.singlecell-tenxsettingspanel',

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
            buttons: this.hideButtons ? null : SingleCell.panel.TenxSettingsPanel.getButtons()
        });

        this.callParent(arguments);

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('singlecell', 'getTenXImportDefaults'),
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
            },{
                xtype: 'checkbox',
                fieldLabel: 'Require Assay Type',
                labelWidth: 300,
                itemId: 'requireAssayType',
                checked: !!JSON.parse(configDefaults.requireAssayType)
            },{
                xtype: 'checkbox',
                fieldLabel: 'Combine Hashing and Cite-Seq',
                labelWidth: 300, itemId: 'combineHashingCite',
                checked: !!JSON.parse(configDefaults.combineHashingCite)
            }];

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
                    var panel = win ? win.down('singlecell-tenxsettingspanel') : btn.up('singlecell-tenxsettingspanel');

                    var params = {};
                    params['requireAssayType'] = panel.down('#requireAssayType').getValue();
                    params['combineHashingCite'] = panel.down('#combineHashingCite').getValue();

                    Ext4.Msg.wait('Saving...');
                    LABKEY.Ajax.request({
                        method: 'POST',
                        url: LABKEY.ActionURL.buildURL('singlecell', 'setTenXImportDefaults'),
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