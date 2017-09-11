Ext4.define('JBrowse.window.SearchCustomizeWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(webPartRowId, webPartProperties){
            Ext4.create('JBrowse.window.SearchCustomizeWindow', {
                webPartRowId: webPartRowId,
                webPartProperties: webPartProperties || {}
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Customize Webpart',
            bodyStyle: 'padding: 5px;',
            defaults: {
                width: 400,
                labelWidth: 140
            },
            items: [{
                xtype: 'textfield',
                itemId: 'title',
                fieldLabel: 'Title',
                value: this.webPartProperties['webpart.title']
            },{
                xtype: 'textfield',
                itemId: 'databaseId',
                fieldLabel: 'Target JBrowse DB',
                helpPopup: 'This is the entityId of the JBrowse database to use',
                value: this.webPartProperties.databaseId
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

        this.callParent();
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Saving...');
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, {
                webPartId: this.webPartRowId,
                'webpart.title': this.down('#title').getValue(),
                databaseId: this.down('#databaseId').getValue()
            }),
            method : 'POST',
            failure : LABKEY.Utils.onError,
            scope : this,
            success: function(){
                Ext4.Msg.hide();
                LABKEY.Utils.setWebpartTitle(this.down('#title').getValue(), this.webPartRowId);
                btn.up('window').close();
            }
        });
    }
});