/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.BaseSequencePanel', {
    alias: 'widget.sequenceanalysis-basesequencepanel',
    extend: 'Ext.form.Panel',
    analysisController: 'pipeline-analysis',
    splitJobs: false,

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

        this.on('afterrender', this.onAfterRender);
        this.on('afterrender', this.checkProtocol, this);
    },

    onAfterRender: function(panel){
        this.originalWidth = this.getWidth();
    },

    doResize: function(itemWidth){
        var width2 = this.getWidth();
        if (itemWidth > width2){
            this.setWidth(itemWidth);
            this.doLayout();
        }
        else if (itemWidth < width2) {
            if (this.originalWidth && width2 != this.originalWidth){
                this.setWidth(Math.max(this.originalWidth, itemWidth));
                this.doLayout();
            }
        }
    },

    checkProtocol: function(){
        var field = this.down('#protocolName');
        if (field && field.getValue()){
            var protocolName = field.getValue();

            //NOTE: is using split jobs, we will always append to the end of the protocolName.  this isnt a perfect check, but we at least test the first possible job name
            if (this.splitJobs){
                protocolName = protocolName + '_1';
            }

            LABKEY.Pipeline.getFileStatus({
                taskId: this.taskId,
                path: this.path,
                files: this.fileNames,
                scope: this,
                success: this.validateProtocol,
                failure: LDK.Utils.getErrorCallback(),
                protocolName: protocolName
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
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Analysis Started!', function(){
                    window.location = LABKEY.ActionURL.buildURL("pipeline-status", "showList.view", Laboratory.Utils.getQueryContainerPath(), {'StatusFiles.Status~neqornull': 'COMPLETE'});
                });
            },
            failure: function(error){
                Ext4.Msg.hide();
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
            splitJobs: this.splitJobs,
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

        Ext4.Msg.wait('Submitting...');

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
        var json = this.getJsonParams();
        if (!json)
            return false;

        this.startAnalysis(json, this.fileIds);
    },

    getSaveTemplateCfg: function() {
        return {
            xtype: 'container',
            layout: 'hbox',
            width: null,
            items: [{
                xtype: 'ldk-linkbutton',
                style: 'margin-left: ' + (this.fieldDefaults.labelWidth + 4) + 'px;',
                width: null,
                text: 'Copy Previous Run',
                itemId: 'copyPrevious',
                linkCls: 'labkey-text-link',
                scope: this,
                handler: function (btn) {
                    Ext4.create('Ext.window.Window', {
                        taskId: this.taskId,
                        modal: true,
                        sequencePanel: this,
                        title: 'Copy Previous Run?',
                        width: 700,
                        bodyStyle: 'padding: 5px;',
                        defaults: {
                            border: false
                        },
                        items: [{
                            html: 'This will allow you to apply saved settings from a previous run.  Use the toggle below to select from runs bookmarked as templates, or you can choose any previous run to apply to this form.  You can click cancel to manually choose your options.',
                            style: 'padding-bottom: 10px;'
                        },{
                            xtype: 'radiogroup',
                            name: 'selector',
                            columns: 1,
                            defaults: {
                                name: 'selector'
                            },
                            items: [{
                                boxLabel: 'Choose From Saved Templates',
                                inputValue: 'bookmarkedRuns',
                                checked: true
                            },{
                                boxLabel: 'Choose From All Runs',
                                inputValue: 'allRuns'
                            }],
                            listeners: {
                                change: function (field, val) {
                                    var win = field.up('window');
                                    var target = win.down('#selectionArea');
                                    var toAdd = [];
                                    if (val.selector == 'bookmarkedRuns') {
                                        toAdd.push({
                                            xtype: 'labkey-combo',
                                            width: 450,
                                            fieldLabel: 'Select Run',
                                            editable: true,
                                            forceSeletion: true,
                                            store: {
                                                type: 'labkey-store',
                                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                schemaName: 'sequenceanalysis',
                                                queryName: 'saved_analyses',
                                                filterArray: [LABKEY.Filter.create('taskid', win.sequencePanel.taskId)],
                                                autoLoad: true,
                                                columns: 'rowid,name,json'
                                            },
                                            displayField: 'name',
                                            valueField: 'rowid',
                                            queryMode: 'local',
                                            listeners: {
                                                afterrender: function (field) {
                                                    field.focus.defer(200, field);
                                                }
                                            }
                                        });
                                    }
                                    else if (val.selector == 'allRuns') {
                                        toAdd.push({
                                            xtype: 'combo',
                                            width: 450,
                                            fieldLabel: 'Select Run',
                                            editable: false,
                                            store: {
                                                type: 'json',
                                                fields: ['rowid', 'name', 'json']
                                            },
                                            displayField: 'name',
                                            valueField: 'rowid',
                                            queryMode: 'local',
                                            taskId: win.taskId,
                                            listeners: {
                                                afterrender: function (field) {
                                                    field.focus.defer(100, field);
                                                },
                                                render: function (field) {
                                                    Ext4.Msg.wait('Loading...');
                                                    LABKEY.Pipeline.getProtocols({
                                                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                        taskId: field.taskId,
                                                        path: './',
                                                        includeWorkbooks: true,
                                                        scope: this,
                                                        success: function (results) {
                                                            Ext4.Msg.hide();
                                                            var records = [];
                                                            if (results && results.length) {
                                                                Ext4.Array.forEach(results, function (r, idx) {
                                                                    records.push(field.store.createModel({
                                                                        name: r.name,
                                                                        rowid: idx + 1,
                                                                        json: r.jsonParameters
                                                                    }));
                                                                }, this);
                                                            }

                                                            field.store.removeAll();
                                                            if (records.length) {
                                                                field.store.add(records);
                                                            }
                                                        },
                                                        failure: LDK.Utils.getErrorCallback()
                                                    });
                                                }
                                            }
                                        });
                                    }
                                    else {
                                        console.error('Unknown type: ' + val.selector);
                                    }

                                    target.removeAll();
                                    target.add(toAdd);
                                },
                                render: function (field) {
                                    field.fireEvent('change', field, field.getValue());
                                }
                            }
                        },{
                            xtype: 'panel',
                            itemId: 'selectionArea',
                            bodyStyle: 'padding-top: 10px;padding-left: 5px;'
                        }],
                        buttons: [{
                            text: 'Submit',
                            handler: function (btn) {
                                var win = btn.up('window');
                                var combo = win.down('combo');
                                if (!combo.getValue()) {
                                    Ext4.Msg.alert('Error', 'Must choose a protocol');
                                    return;
                                }

                                var recIdx = combo.store.find('rowid', combo.getValue());
                                var rec = combo.store.getAt(recIdx);
                                var json = rec.get('json');
                                if (Ext4.isString(rec.get('json'))) {
                                    json = Ext4.decode(json);
                                }

                                win.sequencePanel.applySavedValues(json);
                                win.close();
                            }
                        },{
                            text: 'Cancel',
                            handler: function (btn) {
                                btn.up('window').close();
                            }
                        }],
                        listeners: {
                            show: function(win){
                                var field = win.down('combo');
                                if (field) {
                                    field.focus.defer(100, field);
                                }

                                new Ext4.util.KeyNav(win.getEl(), {
                                    "enter" : function(e){
                                        var btn = win.down('button[text=Submit]');
                                        btn.handler(btn);
                                    },
                                    scope : this
                                });
                            }
                        }
                    }).show(btn);
                }
            },{
                xtype: 'ldk-linkbutton',
                text: 'Save Form As Template',
                linkCls: 'labkey-text-link',
                scope: this,
                handler: function (btn) {
                    Ext4.create('SequenceAnalysis.window.SaveAnalysisAsTemplateWindow', {
                        sequencePanel: this
                    }).show(btn);
                }
            }]
        }
    },

    applySavedValues: function(values){
        //allows for subclasses to exclude this panel
        var alignPanel = this.down('sequenceanalysis-alignmentpanel');
        if (alignPanel) {
            alignPanel.down('#doAlignment').setValue(!!values.alignment || !!values['alignment.doAlignment']);
        }

        var sections = this.query('sequenceanalysis-analysissectionpanel');
        Ext4.Array.forEach(sections, function(s){
            s.applySavedValues(values);
        }, this);
    }
});
