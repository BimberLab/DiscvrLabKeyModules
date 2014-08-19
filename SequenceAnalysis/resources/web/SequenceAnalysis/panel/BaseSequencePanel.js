/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.BaseSequencePanel', {
    extend: 'Ext.form.Panel',
    analysisController: 'pipeline-analysis',

    initComponent: function(){
        //NOTE: if we're in a workbook, default to serch against the parent, since it will include children by default
        this.queryContainer = LABKEY.Security.currentContainer.type == 'workbook' ? LABKEY.Security.currentContainer.parentId : LABKEY.Security.currentContainer.id;
        this.queryContainerPath = LABKEY.Security.currentContainer.type == 'workbook' ? LABKEY.Security.currentContainer.parentPath : LABKEY.Security.currentContainer.path;

        this.initFiles();

        this.protocolStore = Ext4.create('Ext.data.ArrayStore', {
            fields: ['protocol'],
            data: new Array()
        });

        this.fieldDefaults = {
            bodyStyle: 'padding:5px',
            width: 360,
            msgTarget: 'qtip',
            labelWidth: 205,
            bubbleEvents: ['add', 'remove']
        };

        Ext4.QuickTips.init({
            constrainPosition: true,
            dismissDelay: 0
        });

        delete Ext4.tip.Tip.prototype.minWidth;

        Ext4.apply(this, {
            itemId: 'sequenceAnalysisPanel',
            protocols: {},
            width: '100%',
            bodyBorder: false,
            border: false,
            bodyStyle:'padding:5px 5px 5px 5px',
            defaultType: 'textfield',
            monitorValid: false,
            defaults: Ext4.Object.merge({
                style: 'margin-bottom: 20px;'
            }, this.fieldDefaults),
            listeners: {
                add: function(item){
                    if (this.rendered)
                        this.getForm().isValid();
                    var fields = [];
                    this.cascade(function(item){
                        if (item.isFormField && item.validate){
                            item.validate();
                        }
                    }, this);
                }
            }
        });

        this.addEvents('midchange', 'pairedendchange', 'dataready');

        this.barcodeStore = Ext4.create('LABKEY.ext4.data.Store', {
            schemaName: 'sequenceanalysis',
            queryName: 'barcodes',
            autoLoad: true,
            nullRecord: {
                nullCaption: '[None]'
            }
        });

        this.containerId = LABKEY.Security.currentContainer.id;

        this.callParent(arguments);

        this.warningMessage = this.warningMessage || "Your changes have not yet been saved. Choose Cancel to stay on the page and save your changes.";

        LABKEY.Pipeline.getProtocols({
            taskId: this.taskId,
            successCallback: function (protocols, defaultProtocolName){
                //create the store and save protocol info
                for (var i = 0; i < protocols.length; i++){
                    this.protocolStore.add(this.protocolStore.createModel({protocol: protocols[i].name}));
                    this.protocols[protocols[i].name] = protocols[i];
                }
            },
            scope: this
        });

        this.on('afterrender', this.checkProtocol, this);
    },

    checkProtocol: function(){
        if (this.down('#protocolName').getValue()){
            LABKEY.Pipeline.getFileStatus({
                taskId: this.taskId,
                path: this.path,
                files: this.fileNames,
                scope: this,
                success: this.validateProtocol,
                failure: LDK.Utils.getErrorCallback(),
                protocolName: this.down('#protocolName').getValue()
            });
        }
    },

    validateProtocol: function(status){
        var field = this.down('#protocolName');

        for (var j = 0; j < status.length; j++){
            if (status[j].status != 'UNKNOWN'){
                field.markInvalid('Job&nbsp;Name&nbsp;Already&nbsp;In&nbsp;Use.');
                this.validProtocolName = false;
                field.isValidProtocol = false;
                return;
            }
        }
        this.validProtocolName = true;

        field.isValidProtocol = true;
        field.validate();
    },

    getBasename: function(str){
        var base = new String(str).substring(str.lastIndexOf('/') + 1);
        if (base.lastIndexOf(".") != -1)
            base = base.substring(0, base.lastIndexOf("."));
        return base;
    },

    getJsonParams: function(btn){
        //this will allow requests to be sent if we have not yet determined whether the name is valid.
        //they still should get rejected server-side
        if (this.validProtocolName === false){
            alert('This protocol name is already in use.  Please select a different name.');
            return;
        }

        var fieldInputs = this.form.getFields();
        var fields = {};
        var error;
        fieldInputs.each(function(field){
            if (field.isFormField === false)
                return;

            var val = field.getValue();
            if (field.allowBlank === false && Ext4.isEmpty(val) && !field.isDisabled()){
                //little ugly, crude proxy for gridpanel fields.
                if (field.up('editor')){
                    //console.log('field is part of grid: ' + field.name);
                }
                else {
                    Ext4.Msg.alert('Error', 'The field: ' + (field.fieldLabel || field.header || field.name) + ' cannot be blank');
                    error = 1;
                    return false;
                }
            }

            fields[field.name] = val;
        }, this);

        fields.fileNames = this.fileNames.join(';');
        fields.containerId = this.containerId;
        fields.containerPath = LABKEY.ActionURL.getContainer();
        fields.userId = LABKEY.Security.currentUser.id;
        fields.baseUrl = LABKEY.ActionURL.getBaseURL();
        fields.debugMode = !Ext4.isEmpty(LABKEY.ActionURL.getParameter('debugMode'));

        Ext4.iterate(fields, function(key){
            if (key.match(/^dna\./) && Ext4.isArray(fields[key])){
                fields[key] = fields[key].join(';');
            }
        }, this);

        if (!error)
            return fields;
    },

    startAnalysis: function(jsonParameters, fileIds, fileNames){
        if ((!fileIds || !fileIds.length) && (!fileNames || !fileNames.length)){
            alert('No files selected');
            return;
        }

        this.doStartAnalysis({
            taskId: this.taskId,
            path: this.path,
            files: fileNames,
            fileIds: fileIds,
            saveProtocol: jsonParameters.saveProtocol || false,
            protocolName: jsonParameters.protocolName,
            jsonParameters: jsonParameters,
            protocolDescription: jsonParameters.protocolDescription || jsonParameters.description,
            scope: this,
            successCallback: function() {
                Ext4.Msg.alert('Success', 'Analysis Started!', function(){
                    window.location = LABKEY.ActionURL.buildURL("pipeline-status", "showList.view", Laboratory.Utils.getQueryContainerPath(), {'StatusFiles.Status~neqornull': 'COMPLETE'});
                });
            },
            failure: function(error){
                this.checkProtocol();

                alert('There was an error: ' + error.exception);
                console.log(error);
            }
        });
    },

    /**
     * this is basically cut/paste from LABKEY.Pipeline.startAnalysis, for the purpose of using a custom action
     */
    doStartAnalysis: function(config){
        if (!config.protocolName){
            throw "Invalid config, must include protocolName property";
        }

        var params = {
            taskId: config.taskId,
            path: config.path,
            protocolName: config.protocolName,
            protocolDescription: config.protocolDescription,
            file: config.files,
            fileIds: config.fileIds,
            allowNonExistentFiles: config.allowNonExistentFiles,
            saveProtocol: config.saveProtocol == undefined || config.saveProtocol
        };

        if (config.xmlParameters){
            // Convert from an Element to a string if needed
            // params.configureXml = Ext4.DomHelper.markup(config.xmlParameters);
            if (typeof config.xmlParameters == "object")
                throw new Error('The xml configuration is deprecated, please user the jsonParameters option to specify your protocol description.');
            else
                params.configureXml = config.xmlParameters;
        }
        else if (config.jsonParameters){
            if (Ext4.isString(config.jsonParameters)){
                // We already have a string
                params.configureJson = config.jsonParameters;
            }
            else {
                // Convert from JavaScript object to a string
                params.configureJson = Ext4.encode(config.jsonParameters);
            }
        }

        var containerPath = config.containerPath ? config.containerPath : LABKEY.ActionURL.getContainer();
        var url = LABKEY.ActionURL.buildURL(this.analysisController, "startAnalysis", containerPath);
        LABKEY.Ajax.request({
            url: url,
            method: 'POST',
            params: params,
            timeout: 60000000,
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    },

    onSubmit: function(btn){
        //prevent double submission.  probably would be better to make a load mask, but this is quicker for now
        btn.setDisabled(true);
        btn.setDisabled.defer(500, btn, [false]);

        var json = this.getJsonParams();
        if (!json)
            return false;

        this.startAnalysis(json, this.fileIds);
    }
});
