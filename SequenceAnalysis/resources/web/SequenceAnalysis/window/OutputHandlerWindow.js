Ext4.define('SequenceAnalysis.window.OutputHandlerWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName, checked, handlerClass){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            checked = dataRegion.getChecked();
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
                    handlerClass: handlerClass,
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

                    distinctGenomes = Ext4.Array.unique(distinctGenomes);

                    if (errors.length){
                        errors = Ext4.Array.unique(errors);
                        Ext4.Msg.alert('Error', errors.join('<br>'));
                    }
                    else if (distinctGenomes.length > 1){
                        Ext4.Msg.alert('Error', 'The selected files use more than one genome.  This step requires all files be based on the same genome.');
                    }
                    else {
                        distinctGenomes = Ext4.Array.unique(distinctGenomes);

                        Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                            title: 'Create New Workbook or Add To Existing?',
                            workbookPanelCfg: {
                                doLoad: function (containerPath) {
                                    Ext4.create('SequenceAnalysis.window.OutputHandlerWindow', {
                                        containerPath: containerPath,
                                        dataRegionName: dataRegionName,
                                        handlerClass: handlerClass,
                                        outputFileIds: checked,
                                        title: results.name,
                                        handlerConfig: results,
                                        toolParameters: results.toolParameters,
                                        libraryId: distinctGenomes.length == 1 ? distinctGenomes[0] : null
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
        this.handlerConfig = this.handlerConfig || {};
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            items: [{
                xtype: 'form',
                border: false,
                defaults: {
                    border: false
                },
                items: [{
                    html: this.handlerConfig.description,
                    style: 'padding-bottom: 20px;',
                    border: false
                }].concat(this.getParams())
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
            }],
            listeners: {
                show: function(win){
                    new Ext4.util.KeyNav(win.getEl(), {
                        "enter" : function(e){
                            win.onSubmit();
                        },
                        scope : this
                    });
                }
            }
        });

        this.callParent(arguments);
    },

    getParams: function(){
        if (this.handlerConfig.toolParameters && this.handlerConfig.toolParameters.length){
            return this.getCfgForToolParameters(this.handlerConfig.toolParameters);
        }

        return [];
    },

    getCfgForToolParameters: function(parameters){
        var paramCfg = [];
        Ext4.Array.forEach(parameters, function (i, idx) {
            var o = {
                xtype: i.fieldXtype,
                labelWidth: 200,
                isToolParam: true,
                fieldLabel: i.label,
                helpPopup: (i.description || '') + (i.commandLineParam ? '<br>Parameter name: \'' + i.commandLineParam + '\'' : ''),
                name: i.name,
                value: i.defaultValue
            };

            if (i.additionalExtConfig){
                for (var prop in i.additionalExtConfig){
                    var val = i.additionalExtConfig[prop];
                    if (Ext4.isString(val) && val.match(/^js:/)){
                        val = val.replace(/^js:/, '');
                        val = eval(val);

                        i.additionalExtConfig[prop] = val;
                    }
                }
                Ext4.apply(o, i.additionalExtConfig);
            }

            //force checkboxes to submit true instead of 'on'
            if (o.xtype == 'checkbox' && !Ext4.isDefined(o.inputValue)){
                o.inputValue = true;
            }

            paramCfg.push(o);
        }, this);

        return paramCfg;
    },

    onSubmit: function() {
        if (!this.down('form').getForm().isValid()){
            Ext4.Msg.alert('Error', 'There are one or more errors in the fields');
            return;
        }

        var params = this.down('form').getForm().getValues();

        Ext4.Msg.wait('Submitting...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'runSequenceHandler', this.containerPath),
            jsonData: {
                handlerClass: this.handlerClass,
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