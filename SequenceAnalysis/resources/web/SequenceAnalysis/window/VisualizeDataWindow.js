Ext4.define('SequenceAnalysis.window.VisualizeDataWindow', {
    extend: 'Ext.window.Window',
    statics: {
        buttonHandler: function(dataRegionName){
            var checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length) {
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.Msg.wait('Loading...');
            LABKEY.Ajax.request({
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getAvailableHandlers', null),
                params: {
                    handlerType: 'OutputFile',
                    outputFileIds: checked
                },
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: LABKEY.Utils.getCallbackWrapper(function (results) {
                    Ext4.Msg.hide();

                    Ext4.create('SequenceAnalysis.window.VisualizeDataWindow', {
                        dataRegionName: dataRegionName,
                        handlers: results.handlers,
                        partialHandlers: results.partialHandlers,
                        outputFileIds: checked
                    }).show();
                }, this)
            });
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Visualize/Analyze Files',
            modal: true,
            bodyStyle: 'padding: 5px;',
            width: 800,
            minHeight: 400,
            items: this.getItems(),
            buttons: [{
                text: 'Submit',
                disabled: !this.handlers.length,
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

    getItems: function(){
        var ret = [];
        var store = Ext4.create('Ext.data.ArrayStore', {
            fields: ['name', 'description', 'json', 'files']
        });

        if (!this.handlers.length){
            ret.push({
                html: 'There are not actions supported for all of the selected files.  Please uncheck some of the files and try again.  The list below summarizes actions available to some, but not all selected files:',
                border: false,
                style: 'padding-bottom: 10px;'
            });

            console.log(this.partialHandlers);

            LDK.Utils.sortByProperty(this.partialHandlers, 'name');
            Ext4.Array.forEach(this.partialHandlers, function(h){
                store.add(store.createModel({
                    name: h.name,
                    description: h.description,
                    files: h.files,
                    json: h
                }));
            }, this);


            ret.push(this.getGridCfg(store, true));
        }
        else {
            ret.push({
                html: 'The following actions are supported for the ' + this.outputFileIds.length + ' selected files.  Select the desired action then hit submit.',
                border: false,
                style: 'padding-bottom: 10px;'
            });

            LDK.Utils.sortByProperty(this.handlers, 'name');
            Ext4.Array.forEach(this.handlers, function(h){
                store.add(store.createModel({
                    name: h.name,
                    description: h.description,
                    json: h
                }));
            }, this);


            ret.push(this.getGridCfg(store));
        }

        return ret;
    },

    getGridCfg: function(store, showPartial){
        return {
            border: false,
            xtype: 'grid',
            maxHeight: 800,
            hideHeaders: true,
            listeners: showPartial ? null : {
                scope: this,
                itemdblclick: function(panel, record){
                    this.runHandler(record.get('json'));
                }
            },
            store: store,
            columns: [{
                dataIndex: 'name',
                tdCls: 'ldk-wrap-text',
                width: 180,
                text: 'Name'
            },{
                dataIndex: 'description',
                tdCls: 'ldk-wrap-text',
                //style: 'text-decoration:line-through;',
                width: 560,
                text: 'Description'
            },{
                tdCls: 'ldk-wrap-text',
                hidden: !showPartial,
                width: 120,
                handler: function(val, attrs, record){

                    return val;
                }
            }]
        };
    },

    runHandler: function(handler){
        this.close();
        if (handler.successUrl) {
            if (handler.useWorkbooks){
                Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                    controller: handler.successUrl.controller,
                    action: handler.successUrl.action,
                    urlParams: handler.successUrl.urlParams
                }).show();
            }
            else {
                window.location = LABKEY.ActionURL.buildURL(handler.successUrl.controller, handler.successUrl.action, null, handler.successUrl.urlParams);
            }
        }
        else if (handler.jsHandler){
            var handlerFn = eval(handler.jsHandler);
            LDK.Assert.assertTrue('Unable to find JS handler: ' + handler.jsHandler, Ext4.isFunction(handlerFn));

            handlerFn(this.dataRegionName, this.outputFileIds, handler.handlerClass);
        }
        else {
            LDK.Utils.logError('Handler did not provide successUrl or jsHandler: ' + handler.handlerClass);

            Ext4.Msg.alert('Error', 'There was an error with this handler.  Please contact your site administrator');
        }
    },

    onSubmit: function(){
        var s = this.down('grid').getSelectionModel().getSelection();
        if (!s.length){
            Ext4.Msg.alert('Error', 'Must select an action');
            return;
        }

        this.runHandler(s[0].get('json'));
    }
});