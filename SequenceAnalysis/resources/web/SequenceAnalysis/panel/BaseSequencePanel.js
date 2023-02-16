/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.BaseSequencePanel', {
    alias: 'widget.sequenceanalysis-basesequencepanel',
    extend: 'Ext.form.Panel',
    jobType: null,

    initComponent: function(){
        //NOTE: if we're in a workbook, default to serch against the parent, since it will include children by default
        this.queryContainer = LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.parentId : LABKEY.Security.currentContainer.id;
        this.queryContainerPath = LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.parentPath : LABKEY.Security.currentContainer.path;

        this.initFiles();

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

        this.on('afterrender', this.onAfterRender);
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

    getJobResourcesCfg: function(results){
        if (results.resourceSettings){
            return {
                xtype: 'sequenceanalysis-analysissectionpanel',
                title: 'Job Resources',
                stepType: 'resourceSettings',
                sectionDescription: '',
                toolConfig: results,
                toolIdx: 0
            }
        }

        return null;
    },

    getBasename: function(str){
        var base = String(str).substring(str.lastIndexOf('/') + 1);
        if (base.lastIndexOf(".") !== -1)
            base = base.substring(0, base.lastIndexOf("."));
        return base;
    },

    getJsonParams: function(config){
        config = config || {};
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

        fields.containerId = this.containerId;
        fields.containerPath = LABKEY.ActionURL.getContainer();
        fields.userId = LABKEY.Security.currentUser.id;
        fields.baseUrl = LABKEY.ActionURL.getBaseURL();
        fields.debugMode = !Ext4.isEmpty(LABKEY.ActionURL.getParameter('debugMode'));

        if (!error)
            return fields;
    },

    startAnalysis: function(jsonParameters){
        if (!jsonParameters){
            return;
        }

        this.doStartAnalysis({
            type: this.jobType,
            jobName: jsonParameters.jobName,
            jobParameters: jsonParameters,
            scope: this,
            successCallback: function() {
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Analysis Started!', function(){
                    window.location = LABKEY.ActionURL.buildURL("pipeline-status", "showList.view", Laboratory.Utils.getQueryContainerPath(), {'StatusFiles.Status~neqornull': 'COMPLETE'});
                });
            },
            failure: function(error){
                Ext4.Msg.hide();

                alert('There was an error: ' + error.exception);
                console.log(error);
            }
        });
    },

    doStartAnalysis: function(config){
        if (!config.jobName){
            Ext4.Msg.alert('Error', 'Must provide a job name');
            return;
        }

        LDK.Assert.assertNotEmpty('this.jobType is null', this.jobType);

        var params = {
            type: this.jobType,
            jobName: config.jobName,
            description: config.jobDescription,
            jobParameters: config.jobParameters
        };

        Ext4.Msg.wait('Submitting...');

        var containerPath = config.containerPath ? config.containerPath : LABKEY.ActionURL.getContainer();
        var url = LABKEY.ActionURL.buildURL('sequenceanalysis', 'startPipelineJob', containerPath);
        LABKEY.Ajax.request({
            url: url,
            method: 'POST',
            jsonData: params,
            timeout: 60000000,
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    },

    onSubmit: function(){
        var json = this.getJsonParams();
        if (!json)
            return false;

        this.startAnalysis(json);
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
                        jobType: this.jobType,
                        modal: true,
                        sequencePanel: this,
                        title: 'Copy Previous Run?',
                        width: 700,
                        bodyStyle: 'padding: 5px;',
                        defaults: {
                            border: false
                        },
                        items: [{
                            html: 'This will allow you to apply saved settings from a previous run.  Use the combo below to select from runs bookmarked as templates.  You can click cancel to manually choose your options.',
                            style: 'padding-bottom: 10px;'
                        },{
                            xtype: 'panel',
                            itemId: 'selectionArea',
                            bodyStyle: 'padding-top: 10px;padding-left: 5px;',
                            items: [{
                                xtype: 'labkey-combo',
                                width: 600,
                                fieldLabel: 'Select Run',
                                editable: true,
                                forceSelection: true,
                                store: {
                                    type: 'labkey-store',
                                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                                    schemaName: 'sequenceanalysis',
                                    queryName: 'saved_analyses',
                                    sort: 'name',
                                    filterArray: [LABKEY.Filter.create('taskid', this.jobType)],
                                    autoLoad: true,
                                    columns: 'rowid,name,json'
                                },
                                displayField: 'name',
                                valueField: 'rowid',
                                queryMode: 'local',
                                listeners: {
                                    afterrender: function (field) {
                                        Ext4.defer(field.focus, 200, field);
                                    }
                                },
                                labelWidth: 200
                            },{
                                xtype: 'checkbox',
                                itemId: 'useReadsetContainer',
                                helpPopup: 'By default, the pipelines jobs and their outputs will be created in the workbook you selected. However, in certain cases, such as bulk submission of many jobs, it might be preferable to submit each job to the source folder/workbook for each input. Checking this box will enable this.',
                                fieldLabel: 'Submit Jobs to Same Folder/Workbook as Readset',
                                labelWidth: 200
                            }]
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

                                // can occur in rare cases, probably when store is loading
                                var recIdx = combo.store.find('rowid', combo.getValue());
                                if (recIdx < 0) {
                                    Ext4.Msg.alert('Error', 'Must choose a protocol');
                                    return;
                                }

                                var rec = combo.store.getAt(recIdx);
                                var json = rec.get('json');
                                if (Ext4.isString(rec.get('json'))) {
                                    json = Ext4.decode(json);
                                }

                                var useReadsetContainer = win.down('#useReadsetContainer').getValue();
                                if (!useReadsetContainer) {
                                    delete json.useOutputFileContainer;
                                    delete json.submitJobToReadsetContainer;
                                }

                                win.sequencePanel.applySavedValues(json);

                                var submitJobToReadsetContainer = win.sequencePanel.down('[name="submitJobToReadsetContainer"]');
                                if (submitJobToReadsetContainer) {
                                    submitJobToReadsetContainer.setValue(useReadsetContainer);
                                }

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
                                    Ext4.defer(field.focus, 100, field);
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
            },{
                xtype: 'ldk-linkbutton',
                text: 'Manage Saved Templates',
                linkCls: 'labkey-text-link',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'sequenceanalysis', 'query.queryName': 'saved_analyses', 'query.taskid~eq': this.jobType}),
                linkTarget: '_blank',
                visible: LABKEY.Security.currentUser.isAdmin,
                scope: this
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

        // For top-level properties:
        Ext4.Array.forEach(['submissionType', 'useOutputFileContainer'], function(val) {
            if (values[val]) {
                var field = this.down('[name="' + val + '"]');
                if (field) {
                    field.setValue(values[val]);
                }
            }
        }, this);
    }
});
