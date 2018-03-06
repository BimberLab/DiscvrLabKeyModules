/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This panel is designed to allow the user to prospectively create the template for an assay run.
 *
 */
Ext4.define('Laboratory.panel.AssayTemplatePanel', {
    extend: 'Laboratory.panel.AbstractAssayPanel',
    alias: 'widget.laboratory-assaytemplatepanel',

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            border: false,
            itemId: 'assayTemplatePanel',
            pollForChanges: true,
            defaults: {
                style: 'margin-bottom: 10px;',
                bodyStyle: 'padding: 5px;'
            },
            items: [{
                xtype: 'panel',
                title: 'General Information',
                border: true,
                html: this.HEADER_HTML + '<br><br>This page allows you to plan an experiment run before performing the experiment.  You can upload and save sample information, arrange a plate layout, etc.  ' +
                        'This is usually an optional step, but can sometimes streamline import.  For example, some assays allow you to export a template that will be read directly into the instrument.' +
                        '<br><br>' +
                        'Note: Usually results are imported using a spreadsheet, which can be found using the \'Add From Spreadsheet\' button; however, you can also copy the samples from a previous run using the \'Copy Previous Run\' button. ' +
                        'There are several ways to organize the plate/well organization.  Once your samples have been added, you can use the \'Plate Layout\' button to organize samples among wells.  '
                        ,
                style: 'margin-bottom: 10px;'
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Save',
                scope: this,
                handler: function(btn){
                    var panel = btn.up('panel');
                    panel.doSave(function(results){
                        Ext4.Msg.alert('Success', 'Save Complete');
                        this.setTemplate(results.template);
                    });
                }
            },{
                text: 'Save and Close',
                scope: this,
                handler: function(btn){
                    var panel = btn.up('panel');
                    panel.doSave(function(results){
                        Ext4.Msg.alert('Success', 'Save Complete', function(){
                            window.location = LABKEY.ActionURL.buildURL('project', 'start', Laboratory.Utils.getQueryContainerPath())
                        });
                    });
                }
            },{
                text: 'Download',
                itemId: 'downloadBtn',
                handler: this.downloadTemplate,
                scope: this,
                hidden: true
            }]
        });

        this.callParent(arguments);

        Ext4.Msg.wait('Loading...');
        Laboratory.Utils.getAssayDetails({
            assayId: this.assayId,
            success: this.onMetaLoad,
            scope: this
        });

        window.onbeforeunload = LABKEY.beforeunload(function (){
            return this.isDirty();
        }, this);
    },

    appendDownloadMenuItems: function(){
        var btn = this.down('#downloadBtn');
        if(!this.templateMetadata.hideDownloadBtn)
            btn.setVisible(true);
    },

    validateForm: function(){
        var title = this.down('#templateTitle').getValue();
        if (!title){
            Ext4.Msg.alert('Error', 'Must choose a name for this run');
            return false;
        }

        var importMethod = this.down('#templateImportMethod').getValue();
        if (!importMethod){
            Ext4.Msg.alert('Error', 'Must choose an import method run');
            return false;
        }

        var grid = this.down('#resultGrid');
        if (grid){
            var fields = [];
            var categoryCol;
            Ext4.each(grid.columns, function(col){
                if (col.field){
                    if (col.field.allowBlank === false || col.field.nullable === false)
                        fields.push(col.field.name || col.field.dataIndex);
                }
                else if (col.editor){
                    if (col.editor.allowBlank === false || col.editor.nullable === false)
                        fields.push(col.editor.dataIndex || col.editor.name);
                }

                if (col.dataIndex == 'category'){
                    categoryCol = col;
                }
            }, this);

            var missingFields = LDK.StoreUtils.getMissingRequiredFields(grid.store, fields);
            if (missingFields.length){
                Ext4.Msg.alert('Error', 'One or more required fields are missing from the sample records: ' + missingFields.join(', '));
                return false;
            }

            if (categoryCol){
                var msg = LDK.StoreUtils.validateLookupValue(grid.store, 'category', (categoryCol.editor || categoryCol.field));
                if (msg){
                    Ext4.Msg.alert('Error', msg);
                    return false;
                }
            }
        }

        return true;
    },

    downloadTemplate: function(){
        this.down('#errorArea').removeAll();

        if (!this.validateForm())
            return;

        Ext4.Msg.wait('Saving run...');
        this.doSave(function(){
            var json = this.getJson();
            Ext4.apply(json, {
                templateName: this.down('#templateTitle').getValue(),
                assayId: this.assayDesign.id
            });

            //NOTE: we will validate/download this data using 2 requests.  the first is just to validate it, and the second
            // will post and perform the download.
            var params = {
                labkeyAssayId: this.assayDesign.id,
                importMethod: this.down('#templateImportMethod').getValue(),
                jsonData: Ext4.encode(json)
            };

            this.downloadTemplatePost(params);
        })
    },

    downloadTemplatePost: function(params){
        var form = Ext4.create('Ext.form.Panel', {
            url: LABKEY.ActionURL.buildURL('laboratory', 'createTemplate'),
            method: 'POST',
            standardSubmit: true
        });

        form.submit({
            params: params,
            scope: this,
            failure: function(response){
                console.error('error');
                console.error(arguments);
            }
        });
    },

    doSave: function(onSuccess){
        this.down('#errorArea').removeAll();

        if (!this.validateForm())
            return;

        Ext4.Msg.wait('Saving...');
        var title = this.down('#templateTitle').getValue();
        var importMethod = this.down('#templateImportMethod').getValue();
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('laboratory', 'saveTemplate'),
            method: 'POST',
            jsonData: {
                templateId: this.templateId,
                title: title,
                comments: this.down('#templateComments').getValue(),
                protocolId: this.assayDesign.id,
                importMethod: importMethod,
                json: Ext4.encode(this.getJson())
            },
            failure: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();

                if (results){
                    if (results.errors){
                        this.appendErrors(results.errors);
                    }

                    Ext4.Msg.alert('Error', 'There was an error with the template' + (results.exception ? ': ' + results.exception : ''));
                }
                else {
                    Ext4.Msg.alert('Error', 'There was an error with the template');
                    console.error(arguments);
                }
            }, this),
            success: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();

                if (results.success){
                    this.resetDirtyState();

                    if (onSuccess)
                        onSuccess.call(this, results);
                }
                else {
                    Ext4.Msg.alert('Error', 'There was an error with the template');
                }
            }, this)
        });
    },

    appendErrors: function(errors){
        var msgs = [];
        Ext4.each(errors, function(e){
            if (e && e.exception)
                msgs.push(e.exception);
        }, this);

        this.down('#errorArea').add({
            border: false,
            html: '<span style="color: red;">' + msgs.join('<br>') + '</span>'
        });
    },

    setTemplate: function(map, loadRecords){
        this.templateId = map.rowid;

        this.down('#templateTitle').setValue(map.title);
        this.down('#templateComments').setValue(map.comments);
        this.down('#templateId').setValue(map.rowid);

        if (map.json && loadRecords){
            var grid = this.down('#resultGrid');
            var json = Ext4.decode(map.json);
            this.loadTemplateToGrid(grid, json);
        }
    },

    loadTemplate: function(templateId){
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
                if (results.rows.length)
                    this.setTemplate(results.rows[0], true);
            }
        })
    },

    onMetaLoad: function(result){
        Ext4.Msg.hide();

        if (!result.domains || LABKEY.Utils.isEmptyObj(result.domains)){
            Ext4.Msg.alert('Error', 'Error: assay not found: ' + this.assayId);
            console.error(result);
            return;
        }

        this.domains = result.domains;
        this.assayDesign = result.assayDesign;
        this.importMethods = result.protocols[0].importMethods;

        //this should be the first that actually supports templates:
        var supportedMethods = [];
        Ext4.each(this.importMethods, function(m){
            if (m.supportsTemplates)
                supportedMethods.push(m.name);
        }, this);
        supportedMethods = Ext4.unique(supportedMethods);

        this.defaultImportMethod = result.protocols[0].defaultImportMethod || 'Default Excel';
        if (supportedMethods.indexOf(this.defaultImportMethod) == -1){
            this.defaultImportMethod = supportedMethods[0];
        }

        this.templateMetadata = result.templateMetadata;

        var templateId = LABKEY.ActionURL.getParameter('templateId');
        if (templateId){
            this.loadTemplate(templateId);
        }

        this.handleImportMethods();
        this.selectedMethod = this.getImportMethodByName(this.defaultImportMethod);

        this.add(this.getInitialItems());
        this.toggleImportMethod();

    },

    getInitialItems: function(){
        var data = [];
        Ext4.each(this.importMethods, function(m){
            if (m.supportsTemplates)
                data.push([m.label, m.name])
        }, this);

        var importMethodStore = Ext4.create('Ext.data.ArrayStore', {
            fields: ['name', 'label'],
            data: data
        });

        var defaultSupportsTemplates = importMethodStore.find('name', this.defaultImportMethod) > -1;

        var toAdd = [{
            xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
        }];

        toAdd.push({
            xtype: 'form',
            title: 'Run Information',
            itemId: 'templatePanel',
            defaults: {
                labelWidth: 150,
                width: 450
            },
            items: [{
                xtype: 'displayfield',
                fieldLabel: 'Template Id',
                hidden: true,
                itemId: 'templateId'
            },{
                xtype: 'textfield',
                fieldLabel: 'Run Name',
                itemId: 'templateTitle',
                allowBlank: false,
                value: this.getMetadata(true).Run.map.Name.value || this.getMetadata(true).Run.map.Name.defaultValue
            },{
                xtype: 'combo',
                fieldLabel: 'Import Method',
                itemId: 'templateImportMethod',
                allowBlank: false,
                displayField: 'label',
                valueField: 'name',
                forceSelection: true,
                store: importMethodStore,
                editable: false,
                value: defaultSupportsTemplates ? this.defaultImportMethod : data[0][1],
                listeners: {
                    scope: this,
                    buffer: 10,
                    select: function(field, records){
                        if(records.length){
                            this.selectedMethod = this.getImportMethodByName(records[0].get('name'));
                            this.toggleImportMethod();
                        }
                    }
                }
            },{
                xtype: 'textarea',
                fieldLabel: 'Comments',
                itemId: 'templateComments',
                height: 100
            },{
                //this will be used on form submit
                xtype: 'hidden',
                itemId: 'jsonData',
                name: 'jsonData',
                value: null
            },{
                xtype: 'hidden',
                name: 'labkeyAssayId',
                value: this.assayDesign.id
            }]
        });

        toAdd.push({
            xtype: 'container',
            itemId: 'dataArea',
            defaults: {
                style: 'margin-bottom: 10px;',
                bodyStyle: 'padding: 5px;'
            }
        });

        return toAdd;
    },

    toggleImportMethod: function(){
        var target = this.down('#dataArea');
        target.removeAll();

        var toAdd = [];
        toAdd.push({
            xtype: 'form',
            title: 'Result Fields',
            itemId: 'resultFields',
            hidden: true
        });

        target.add(toAdd);

        var grid = this.getResultGridConfig(true);
        Ext4.apply(grid, {
            title: 'Sample Information',
            columns: this.getTemplateGridColumns(),
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorder'
                }
            }
        });

        if(this.templateMetadata.showPlateLayout){
            target.add({
                xtype: 'welllayoutpanel',
                title: 'Plate Layout',
                targetStore: grid.store
            });
        }

        target.add(grid);

        target.add({
            itemId: 'errorArea',
            border: false
        });

        this.appendDownloadMenuItems();

        var cfg = this.getDomainConfig('Results', true);
        var resultFields = this.down('#resultFields');
        if (cfg && cfg.length){
            resultFields.setVisible(true);
            resultFields.removeAll();
            resultFields.add(cfg);
        }
        else {
            resultFields.setVisible(false);
        }
    },
});