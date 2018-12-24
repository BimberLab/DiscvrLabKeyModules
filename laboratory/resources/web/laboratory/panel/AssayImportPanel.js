/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This class is designed to override the default assay upload page.  The primary purpose was to support multiple 'import methods', which are alternate configurations
 * of fields or parsers.  This can be used to support outputs from multiple instruments imported through one pathway.  It also provides the ability to directly enter results into the browser through an Ext4 grid.
 * @name Laboratory.panel.AssayImportPanel
 * @param {string} [config.assayName]
 * @param {array} [config.importMethods]
 * @param {object} [config.metadata]
 * @param {object} [config.metadataDefaults]
 * @param {string} [config.defaultImportMethod]
 *
 *
 */
Ext4.define('Laboratory.panel.AssayImportPanel', {
    extend: 'Laboratory.panel.AbstractAssayPanel',
    alias: 'widget.laboratory-assayimportpanel',
    initComponent: function(){
        Ext4.QuickTips.init();

        Laboratory.Utils.getAssayDetails({
            assayId: this.assayId,
            success: this.onMetaLoad,
            scope: this
        });

        Ext4.apply(this, {
            autoHeight: true,
            bodyBorder: false,
            border: false,
            frame: false,
            pollForChanges: true,
            style: 'background-color: transparent;',
            bodyStyle: 'background-color: transparent;',
            defaults: {
                style:'padding:5px',
                bodyStyle:'padding:5px'
            },
            items: [{
                html: 'Loading...',
                border: true
            }],
            buttonAlign: 'left',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text: 'Preview Results',
                    disabled: !LABKEY.Security.currentUser.canInsert,
                    handler: this.formSubmit,
                    hidden: true,
                    itemId: 'preview',
                    scope: this,
                    formBind: true
                },{
                    text: 'Upload',
                    disabled: !LABKEY.Security.currentUser.canInsert,
                    handler: this.formSubmit,
                    itemId: 'upload',
                    doUpload: true,
                    scope: this,
                    formBind: true
                },{
                    text: 'Cancel',
                    itemId: 'cancelBtn',
                    scope: this,
                    href: LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin'),
                    hrefTarget: '_self'
                }]
            }],
            listeners: {
                scope: this,
                actioncomplete : this.onActionComplete,
                actionfailed : function(form, action, error){
                    Ext4.Msg.hide();
                    switch(action.failureType){
                        case 'client':
                            Ext4.Msg.alert("One or more fields has a missing or improper value");
                            break;
                        default:
                            this.handleFailure(action.response);
                            break;
                    }
                }
            }
        });

        this.callParent(arguments);

        this.form.url = LABKEY.ActionURL.buildURL("laboratory", "processAssayData");

        window.onbeforeunload = LABKEY.beforeunload(function (){
            return this.isDirty();
        }, this);
    },

    onMetaLoad: function(result){
        this.removeAll();

        if (!result.domains || LABKEY.Utils.isEmptyObj(result.domains)){
            Ext4.Msg.alert('Error', 'Error: assay not found: ' + this.assayId);
            console.error(result);
            return;
        }

        this.domains = result.domains;
        this.assayDesign = result.assayDesign;
        this.importMethods = result.protocols[0].importMethods;
        this.defaultImportMethod = result.protocols[0].defaultImportMethod || 'Default Excel';
        this.templateMetadata = result.templateMetadata;

        this.handleImportMethods();

        this.add(this.getInitialItems());

        this.toggleMethod();

        var templateId = LABKEY.ActionURL.getParameter('templateId');
        if (templateId){
            this.loadTemplate(templateId);
        }
    },

    toggleMethod: function(){
        var runFields = this.down('#runFields');
        var resultFields = this.down('#resultFields');
        var templatePanel = this.down('#templatePanel');

        runFields.removeAll();
        resultFields.removeAll();
        templatePanel.removeAll();

        //we add new global fields
        runFields.add(this.getDomainConfig('Batch'));
        runFields.add(this.getDomainConfig('Run'));

        var resultItems = this.getDomainConfig('Results');
        resultFields.setVisible(resultItems && resultItems.length);
        resultFields.add(resultItems);

        templatePanel.setVisible(this.selectedMethod.supportsTemplates);
        if(this.selectedMethod.supportsTemplates){
            templatePanel.add(this.getTemplatePanelItems())
        }

        Ext4.defer(this.getForm().isValid, 500, this.getForm());

        //populate batch field, if present
        var field = this.down('#importMethod');
        if (field)
            field.setValue(this.selectedMethod.name);

        this.renderFileArea();
    },

    handleFailure: function(response){
        if (!response.responseText) {
            Ext4.Msg.alert('Error', response.statusText || 'There was an error');
            console.error(response);
            return;
        }

        //display errors
        var responseJson = Ext4.JSON.decode(response.responseText);
        if(responseJson){
            Ext4.Msg.alert("Upload Failed", responseJson.exception ? responseJson.exception : "There was an error during the upload");

            var hasWarnings = false;
            if(responseJson.errors){
                var html = '<div style="color: red;padding-bottom: 10px;">There were errors in the upload: </div>';

                if(Ext4.isArray(responseJson.errors)){
                    html += '<div style="padding-left:5px;color: red;">';
                    html += responseJson.errors.join('<br>');
                    html += '</div>';

                    Ext4.each(responseJson.errors, function(error){
                        if (error.match(/^WARN/))
                            hasWarnings = true;
                    }, this);
                }
            }

            var el = this.down('#errorArea');
            if (el){
                el.removeAll();
                el.add({
                    html: html,
                    border: false,
                    style: 'padding-bottom: 10px;'
                });

                if (hasWarnings){
                    el.add({
                        xtype: 'button',
                        text: 'Ignore Warnings and Upload',
                        doUpload: true,
                        scope: this,
                        style: 'margin-left: 10px;',
                        errorLevel: 'ERROR',
                        handler: this.formSubmit
                    })
                }

                this.doLayout();
            }
        }
    },

    renderFileArea: function(){
        var panel = this.down('#assayResults');
        if (panel)
            this.remove(panel);

        panel = this.add({
            title: 'Assay Results',
            xtype: 'panel',
            itemId: 'assayResults',
            items: [{
                xtype: 'form',
                itemId: 'sampleDataArea',
                border: false
            }]
        });

        var grid = this.down('#resultGrid');
        if(this.selectedMethod.enterResultsInGrid){
            if (!grid)
                this.add(this.getResultGridConfig());
        }
        else {
            if (grid)
                this.remove(grid);

            panel.add({
                xtype: 'laboratory-textfileuploadpanel',
                itemId: 'textFileUpload'
            });
        }

        var sampleArea = this.down('#sampleDataArea');
        sampleArea.removeAll();
        var toAdd = [];

        if (this.selectedMethod.templateInstructions){
            toAdd.push({
                html: this.selectedMethod.templateInstructions,
                maxWidth: 1000,
                border: false,
                style: 'padding:5px;padding-bottom: 20px;'
            });
        }

        if (!this.selectedMethod.hideTemplateDownload){
            toAdd.push({
                xtype: 'button',
                border: true,
                text: 'Download Excel Template',
                style: 'margin-bottom: 10px;',
                scope: this,
                handler: this.templateHandler
            });
        }

        if (this.selectedMethod.exampleDataUrl){
            toAdd.push({
                xtype: 'button',
                border: true,
                style: 'margin-bottom: 10px',
                text: 'Download Example Data',
                href: this.selectedMethod.exampleDataUrl
            });
        }

        if (toAdd.length)
            sampleArea.add({
                border: false,
                layout: 'vbox',
                items: toAdd
            });
    },

    templateHandler: function(){
        Laboratory.Utils.getAssayImportHeaders({
            assayId: this.assayId,
            importMethod: this.selectedMethod.name,
            scope: this,
            success: function(results){
                this.setDownloadPending(true);
                LABKEY.Utils.convertToExcel({
                    fileName : this.assayDesign.name + '_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                    sheets : [{
                        name: 'data',
                        data: [results.columnNames]
                    }]
                });
                Ext4.defer(this.setDownloadPending, 1000, this, [false]);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    formSubmit: function(btn){
        Ext4.Msg.wait("Uploading...");

        var el = this.down('#errorArea');
        if (el){
            el.removeAll();
        }

        this.batch = new LABKEY.Exp.ExpObject({
            batchProtocolId: this.assayDesign.id,
            properties: {},
            runs: []
        });

        var uploadType = this.down('#inputType').getValue().uploadType;
        var nameField = this.getForm().findField('Name');

        if (uploadType == 'text'){
            var text = this.down('#fileContent').getValue() || '';
            if(text.replace(/\s/g, '') == ''){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Error', 'You must either cut/paste from a spreadsheet or choose a file to import');
                return;
            }
            this.form.baseParams = {fileName: nameField.getValue() + '.tsv'};
            this.form.fileUpload = false;
        }
        else {
            this.form.fileUpload = true;
            this.form.baseParams = {};

            if(!this.down('#upload-run-field').getValue()){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Error', 'You must either cut/paste from a spreadsheet or choose a file to import');
                return;
            }
        }
        this.form.baseParams.doSave = (btn.doUpload == true);

        var json = this.getJson();
        json.errorLevel = btn.errorLevel;

        this.down('#jsonData').setValue(Ext4.encode(json));
        this.form.submit();
    },

    onActionComplete: function(form, action){
        Ext4.Msg.hide();

        if (!action){
            Ext4.Msg.alert("Upload Failed", "Something went horribly wrong when uploading.");
            console.error(arguments);
            return;
        }

        if (!action.response || !action.response.responseText){
            Ext4.Msg.alert("Upload Failed", action.statusText || "Something went wrong uploading.");
            console.error(arguments);
            return;
        }

        var json = Ext4.JSON.decode(action.response.responseText);
        if (!json.success){
            var msg = LABKEY.Utils.getMsgFromError(action.response);
            Ext4.Msg.alert("Upload Failed", "Something went wrong when uploading");
        }

        if (json.results){
            this.generatePreview(json.results);
        }
        else {
            this.onUploadComplete();
        }
    },

    generatePreview: function(results){
        var panel = this.down('#resultsPreview');
        if (panel){
            panel.destroy();
        }

        var importMethod = this.getImportMethodByName(results.importMethod);
        var resultPanel = Ext4.create(importMethod.previewPanelClass, {
            results: results,
            metadata: this.getMetadata()
        });

        this.add(resultPanel);
    },

    onUploadComplete: function(){
        this.resetDirtyState();

        //TODO: maybe give option to repeat?
        Ext4.Msg.hide();
        Ext4.Msg.alert('Success', 'Your upload was successful!', function(){
            function doLoad(){
                //NOTE: always return to the project root, ignoring srcURL
                window.location = LABKEY.ActionURL.buildURL('project', 'begin');
            }

            Ext4.defer(doLoad, 400, this);
        });
    },

    loadTemplate: function(templateId){
        Ext4.Msg.wait('Loading saved run...');
        LABKEY.Query.selectRows({
            schemaName: 'laboratory',
            queryName: 'assay_run_templates',
            columns: '*',
            filterArray: [
                LABKEY.Filter.create('rowid', templateId, LABKEY.Filter.Types.EQUALS)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();

                if (results.rows.length){
                    var row = results.rows[0];
                    if (row.runid){
                        Ext4.Msg.alert('Error', 'ERROR: This run has already been imported');
                        return;
                    }

                    if (row.importMethod){
                        this.selectedMethod = this.getImportMethodByName(row.importMethod);
                        this.toggleMethod();

                        this.down('#runFields').down('#Name').setValue(row.title);

                        var field = this.down('#templateIdField');
                        if (field.store.isLoading()){
                            field.store.on('load', function(store){
                                field.setValue(row.rowid);
                                field.fireEvent('change', field, row.rowid);
                            }, this, {single: true, delay: 50});
                        }
                        else {
                            field.setValue(row.rowid);
                            field.fireEvent('change', field, row.rowid);
                        }

                        var importField = this.down('#importMethodToggle');
                        importField.suspendEvents();
                        importField.setValue({
                            importMethodToggle: row.importMethod
                        });
                        importField.resumeEvents();
                    }
                    else {
                        console.error('Import method field is blank for template: ' + templateId)
                    }
                }
            }
        })
    }
});

Ext4.define('Laboratory.ext.AssayPreviewPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Results Preview',
            itemId: 'resultsPreview',
            items: this.getItems(),
            defaults: {
                border: false
            }
        });

        this.callParent(arguments);
    },

    getField: function(domain, name){
        domain = this.metadata[domain];
        if(domain)
            return domain[name];
    },

    getItems: function(){
        var items = [];
        Ext4.each(this.results.batches, function(batch, idx){
            items = items.concat(this.getBatchConfig(batch));

            if(idx < this.results.batches.length -1){
                items.push({
                    html: '<hr>',
                    style: 'margin-bottom: 5px;'
                });
            }
        }, this);

        return items;
    },

    getBatchConfig: function(batch){
        var items = [];
        var cfg = this.getPropertiesCfg('Batch', batch.properties);
        if (cfg){
            items.push({
                html: '<b>Batch:</b>',
                style: 'margin-bottom: 5px;'
            });
            items.push(cfg);
        }

        Ext4.each(batch.runs, function(run){
            items = items.concat(this.getRunConfig(run));
        }, this);
        return items;
    },

    getRunConfig: function(run){
        var items = [];
        var cfg = this.getPropertiesCfg('Run', run.properties);
        if (cfg){
            items.push({
                html: '<b>Run:</b>',
                style: 'margin-bottom: 5px;'
            });
            items.push(cfg);
        }

        items.push(this.getResultsPanel(run.results));
        return items;
    },

    getResultsPanel: function(results){
        var cfg = {
            border: false,
            items: [{
                html: '<b>Results:</b>',
                style: 'margin-bottom: 5px;',
                border: false
            }]
        }

        var fields = [];
        for (var name in this.metadata.Results){
            var field = this.metadata.Results[name];
            fields.push({
                text: field.caption,
                dataIndex: field.name,
                name: field.name,
                type: LDK.Utils.getExtDataType(field.jsonType)
            });
        }

        var store = Ext4.create('Ext.data.Store', {
            fields: fields,
            proxy: {
                type: 'memory'
            }
        });

        for (var i=0;i<results.length;i++){
            store.add(store.model.create(results[i]));
        }

        cfg.items.push({
            xtype: 'ldk-tabledataview',
            title: 'Results',
            store: store
        });

        return cfg;
    },

    getPropertiesCfg: function(domain, properties){
        if (properties){
            var cfg = {
                xtype: 'panel',
                items: [],
                border: false
            }
            for (var prop in properties){
                var meta = this.getField(domain, prop);
                if (!meta){
                    console.error('not found: ' + domain + '/' + prop);
                    console.error(this.metadata[domain]);
                    continue;
                }
                if(!meta.hidden){
                    cfg.items.push({
                        xtype: 'displayfield',
                        fieldLabel: meta.caption,
                        value: properties[prop]
                    });
                }
            }
            return cfg.items.length ? cfg : null;
        }
    }
});