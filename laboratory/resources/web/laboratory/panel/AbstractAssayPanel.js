/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Laboratory.panel.AbstractAssayPanel', {
    extend: 'Ext.form.Panel',
    LABEL_WIDTH: 150,
    FIELD_WIDTH: 450,
    HEADER_HTML: 'This assay is part of the <a href="https://github.com/bbimber/discvr/wiki">DISCVR software package</a>, an open-source collection of extensions to LabKey Server.  Please use the previous link for more detail.',

    getDomainConfig: function(domain, forTemplate){
        var meta = this.getMetadata(forTemplate)[domain].map;
        var domainFields = new Array();
        switch (domain){
            case 'Results':
                for (var prop in meta){
                    if (meta[prop].setGlobally)
                        domainFields.push(meta[prop]);
                }
                break;
            default:
                for (var prop in meta){
                    domainFields.push(meta[prop]);
                }
        }

        var toAdd = [];
        for (var i=0; i < domainFields.length; i++){
            if (domain == 'Other' && !domainFields[i].domain){
                alert('Error: Must supply the domain for field: '+domainFields[i].name);
                return
            }

            domainFields[i].domain = domainFields[i].domain || domain;

            if (LABKEY.ext4.Util.shouldShowInInsertView(domainFields[i])){
                var fieldObj = this.getFieldConfig(domainFields[i]);
                if (!fieldObj)
                    continue;

                var editor = LABKEY.ext4.Util.getFormEditorConfig(fieldObj);

                if (fieldObj.getInitialValue){
                    try
                    {
                        if (!Ext4.isFunction(fieldObj.getInitialValue))
                            fieldObj.getInitialValue = eval('(' + fieldObj.getInitialValue + ')');

                        editor.value = fieldObj.getInitialValue(this);
                    }
                    catch (error)
                    {
                        LDK.Utils.logToServer({message: "unable to parse getInitialValue() for field: " + fieldObj.name});
                        console.error(error);
                    }
                }
            }

            toAdd.push(editor);
        }

        return toAdd;
    },

    getMetadata: function(forTemplate){
        var meta = {
            Batch: {},
            Run: {},
            Results: {}
        };
        var colOrder = {
            Batch: [],
            Run: [],
            Results: []
        };

        Ext4.each(['Batch', 'Run', 'Results'], function(domain){
            meta[domain] = {};

            Ext4.each(this.domains[domain], function(col){
                meta[domain][col.name] = {};
                Ext4.Object.merge(meta[domain][col.name], col);

                colOrder[domain].push(col.name);
            }, this);
        }, this);

        Ext4.Object.merge(meta, this.metadataDefaults);
        Ext4.Object.merge(meta, this.metadata);

        if (this.selectedMethod){
            Ext4.Object.merge(meta, this.selectedMethod.metadataDefaults);
            Ext4.Object.merge(meta, this.selectedMethod.metadata);
        }

        if (forTemplate && this.templateMetadata && this.templateMetadata.domains){
            Ext4.Object.merge(meta, this.templateMetadata.domains);
        }

        return {
            Batch: {
                map: meta.Batch,
                columns: colOrder.Batch
            },
            Run: {
                map: meta.Run,
                columns: colOrder.Run
            },
            Results: {
                map: meta.Results,
                columns: colOrder.Results
            }
        };
    },

    getFieldConfig: function(fieldObj){
        if(!fieldObj.jsonType)
            fieldObj.jsonType = LABKEY.ext4.Util.findJsonType(fieldObj);

        //TODO: this needs to be somewhere more central
        fieldObj.fieldLabel = Ext4.util.Format.htmlEncode(fieldObj.label || fieldObj.caption || fieldObj.header || fieldObj.name);
        fieldObj.dataIndex = fieldObj.dataIndex || fieldObj.name;
        fieldObj.editable = (fieldObj.userEditable!==false && !fieldObj.readOnly && !fieldObj.autoIncrement);
        fieldObj.allowBlank = fieldObj.nullable;

        if (fieldObj.lookup && !fieldObj.lookup.containerPath){
            fieldObj.lookup.containerPath = Laboratory.Utils.getQueryContainerPath();
        }
        if (!fieldObj.name && !fieldObj.dataIndex){
            console.log('improper field, probably the result of bad metadata merging');
            console.log(fieldObj);
            return;
        }

        //NOTE: the default view of the run domain includes batch properties.  we assume these should be ignored
        if (fieldObj.fieldKeyArray && fieldObj.fieldKeyArray.length > 1){
            console.log('looked up field: ' + fieldObj.name);
            return;
        }

        if(!fieldObj.id){
            fieldObj.id = fieldObj.name
        }

        fieldObj = Ext4.Object.merge({
            editorConfig: {
                //editable: true,
                itemId: fieldObj.name,
                width: this.FIELD_WIDTH,
                lazyInit: false,
                domain: fieldObj.domain,
                labelWidth: this.LABEL_WIDTH
            }
        }, fieldObj);

        this.metadataDefaults = this.metadataDefaults || {};
        if (this.metadataDefaults[fieldObj.domain]){
            Ext4.Object.merge(fieldObj, this.metadataDefaults[fieldObj.domain]);
        }

        this.metadata = this.metadata || {};
        if (this.metadata[fieldObj.domain] && this.metadata[fieldObj.domain][fieldObj.name]){
            Ext4.Object.merge(fieldObj, this.metadata[fieldObj.domain][fieldObj.name]);
        }

        if (this.selectedMethod){
            if (this.selectedMethod.metadataDefaults && this.selectedMethod.metadataDefaults[fieldObj.domain]){
                Ext4.Object.merge(fieldObj, this.selectedMethod.metadataDefaults[fieldObj.domain]);
            }

            if (this.selectedMethod.metadata && this.selectedMethod.metadata[fieldObj.domain] && this.selectedMethod.metadata[fieldObj.domain][fieldObj.name]){
                Ext4.Object.merge(fieldObj, this.selectedMethod.metadata[fieldObj.domain][fieldObj.name]);
            }
        }

        return fieldObj;
    },

//    generateExcelTemplate: function(forTemplate){
//        var header = [];
//
//        var meta = this.getMetadata(forTemplate).Results;
//        var fields = [];
//        Ext4.each(meta.columns, function(fieldName){
//            if (!meta.map[fieldName].setGlobally)
//                fields.push(meta.map[fieldName])
//        }, this);
//
//        LDK.StoreUtils.createExcelTemplate({
//            fields: fields,
//            fileName: this.assayDesign.name + '_' + (new Date().format('Y-m-d H_i_s')) + '.xls'
//        });
//    },

    getResultGridConfig: function(forTemplate){
        return {
            xtype: 'ldk-gridpanel',
            itemId: 'resultGrid',
            title: 'Assay Results',
            autoScroll: true,
            bodyStyle: '', //to override the default of 5px padding
            clicksToEdit: 2,
            //enableColumnHide: false,
            minHeight: 400,
            tbar: [{
                text: 'Add From Spreadsheet',
                tooltip: 'Click to upload data using an excel template, or download this template',
                fileNamePrefix: this.assayDesign.name + '_',
                handler: function(btn){
                    var grid = btn.up('gridpanel');
                    Ext4.create('Laboratory.ext.AssaySpreadsheetImportWindow', {
                        targetGrid: grid,
                        includeVisibleColumnsOnly: true,
                        fileNamePrefix: btn.fileNamePrefix
                    }).show(btn);
                }
            },
                LABKEY.ext4.GRIDBUTTONS.getButton('ADDRECORD'),
                LABKEY.ext4.GRIDBUTTONS.getButton('DELETERECORD')
            ,{
                text: 'Plate Layout',
                handler: function(btn){
                    var grid = btn.up('gridpanel');
                    Ext4.create('Laboratory.window.WellAssignmentWindow', {
                        targetStore: grid.store,
                        targetGrid: grid,
                        wellField: 'well',
                        displayWellField: 'well_96'
                    }).show(btn);
                }
//            },{
//                text: 'Add Empty Well',
//                handler: function(btn){
//                    var grid = btn.up('grid');
//                    if (!grid.getSelectionModel().hasSelection()){
//                        alert('Must select a row where the record will be inserted');
//                        return;
//                    }
//
//                    var selections = grid.getSelectionModel().getSelection();
//                    var idx = grid.store.indexOf(selections[0]);
//                    grid.store.insert(idx, LDK.StoreUtils.createModelInstance(grid.store, {subjectId: 'empty', category: 'Empty Well'}));
//                }
            },{
                text: 'More Actions',
                menu: [
                    LABKEY.ext4.GRIDBUTTONS.getButton('DUPLICATE'),
                    LABKEY.ext4.GRIDBUTTONS.getButton('SORT'),
                    LABKEY.ext4.GRIDBUTTONS.getButton('BULKEDIT'),
                {
                    text: 'Copy Previous Run',
                    handler: function(btn){
                        var panel = btn.up('#assayTemplatePanel');
                        Ext4.create('Laboratory.ext.CopyRunWindow', {
                            assayName: panel.assayDesign.name,
                            targetPanel: panel
                        }).show(btn);
                    }
                },{
                    text: 'Append Standards',
                    handler: function(btn){
                        var grid = btn.up('gridpanel');
                        Ext4.create('Laboratory.ext.AppendStandardsWindow', {
                            targetStore: grid.store
                        }).show(btn);
                    }
                },{
                    text: 'Save Standards',
                    handler: function(btn){
                        var grid = btn.up('gridpanel');
                        Ext4.create('Laboratory.ext.SaveStandardsWindow', {
                            targetStore: grid.store
                        }).show(btn);
                    }
                }]
            }],
            forceFit: true,
            editable: true,
            store: Ext4.create('LABKEY.ext4.data.Store', {
                schemaName: LDK.AssayUtils.SCHEMA_NAME,
                queryName: LDK.AssayUtils.getResultQueryName(this.assayDesign.name),
                viewName: '~~INSERT~~',
                autoLoad: true,
                maxRows: 0,
                metadata: this.getMetadata(forTemplate).Results.map
            }),
            listeners: {
                columnmodelcustomize: function(grid, columns){
                    //provide a mechanism to promote specific fields to be set globally, rather than per result
                    for (var i=0;i<columns.length;i++){
                        var col = columns[i];
                        var meta = LABKEY.ext4.Util.findFieldMetadata(grid.store, col.dataIndex);
                        if(meta.setGlobally){
                            col.hidden = true;
                        }
                    }
                }
            }
        }
    },

    getImportMethodRadioConfig: function(forTemplate){
        //create the panel:
        var radios = [];
        for (var i=0;i<this.importMethods.length;i++){
            if (!forTemplate || this.importMethods[i].supportsTemplates){
                radios.push({
                    xtype: 'radio',
                    name:'importMethodToggle',
                    id:'importMethod'+i,
                    boxLabel: this.importMethods[i].label,
                    tooltip: this.importMethods[i].tooltip,
                    inputValue: this.importMethods[i].name,
                    checked: (this.defaultImportMethod == this.importMethods[i].name),
                    scope: this,
                    listeners: {
                        scope: this,
                        afterrender: function(field){
                            if (field.tooltip){
                                Ext4.QuickTips.register({
                                    target: field.boxLabelEl.id,
                                    text: field.tooltip
                                });
                                field.on('destroy', function(field){
                                    Ext4.QuickTips.unregister(field.boxLabelEl.id);
                                });
                            }
                        }
                    }
                });
            }
        }

        return {
            xtype: 'radiogroup',
            itemId: 'importMethodToggle',
            fieldLabel: 'Import Method',
            labelWidth: this.LABEL_WIDTH,
            columns: 1,
            isFormField: false,
            scope: this,
            defaults: {
                width: this.FIELD_WIDTH
            },
            items: radios,
            listeners: {
                scope: this,
                buffer: 10,
                change: function(group, val, oldVal){
                    if(val){
                        this.selectedMethod = this.getImportMethodByName(val.importMethodToggle);
                        this.toggleMethod();
                    }
                }
            }
        }
    },

    handleImportMethods: function(){
        this.importMethods = this.importMethods || new Array();

        for (var i=0;i<this.importMethods.length;i++){
            if (this.importMethods[i].init){
                this.importMethods[i].init(this);
            }
        }

        if (this.defaultImportMethod){
            Ext4.each(this.importMethods, function(m){
                if (this.defaultImportMethod == m.name){
                    this.selectedMethod = m;
                }
            }, this);
        }

        this.selectedMethod = this.selectedMethod || this.importMethods[0];
    },

    getImportMethodByName: function(name){
        var method;
        Ext4.each(this.importMethods, function(m){
            if (m.name == name){
                method = m;
                return false;
            }
        }, this);
        return method;
    },

    getInitialItems: function(){
        var toAdd = [];
        toAdd.push({
            xtype: 'form',
            title: 'General Information: ' + this.assayDesign.name,
            itemId: 'assayProperties',
            defaults: {
                width: this.FIELD_WIDTH,
                labelWidth: this.LABEL_WIDTH
            },
            items: [{
                width: 800,
                border: false,
                html: this.HEADER_HTML,
                style: 'padding-bottom: 20px'
            }]
        },{
            xtype: 'form',
            title: 'Import Properties',
            itemId: 'runProperties',
            //pollForChanges: true,
            items: [
                this.getImportMethodRadioConfig()
                ,{
                    //this will be used on form submit
                    xtype: 'hidden',
                    itemId: 'jsonData',
                    name: 'jsonData',
                    value: 'json'
                },{
                    xtype: 'hidden',
                    name: 'labkeyAssayId',
                    value: this.assayDesign.id
                }]
        });

        toAdd.push({
            xtype: 'form',
            hidden: true,
            title: 'Saved Sample Information',
            itemId: 'templatePanel',
            defaults: {
                border: false
            }
        });

        toAdd.push({
            xtype: 'form',
            title: 'Run Fields',
            itemId: 'runFields'
        });

        toAdd.push({
            xtype: 'form',
            title: 'Result Fields',
            itemId: 'resultFields'
        });

        return toAdd;
    },

    loadPreviousRun: function(config){
        var grid = this.down('#resultGrid');
        if (grid){
            var columns = [];
            Ext4.each(grid.columns, function(col){
                if (!col.hidden){
                    columns.push(col.dataIndex);
                }
            }, this);

            LABKEY.Query.selectRows({
                requiredVersion: 9.1,
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                columns: columns.join(','),
                schemaName: LDK.AssayUtils.SCHEMA_NAME,
                queryName: LDK.AssayUtils.getResultQueryName(this.assayDesign.name),
                filterArray: [
                    LABKEY.Filter.create('run/rowId', config.rowId, LABKEY.Filter.Types.EQUALS)
                ],
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    grid.store.add(LDK.QueryHelper.getRecordsFromSelectRows(results));
                }
            });
        }
    },

    getJson: function(){
        var json = {
            importMethod: this.selectedMethod ? this.selectedMethod.name : null,
            Results: {},
            Run: {},
            Batch: {}
        };

        this.form.getFields().each(function(field){
            if(field.domain){
                json[field.domain][field.name] = field.getValue()
            }
        }, this);

        var grid = this.down('#resultGrid');
        if (grid){
            json.ResultRows = [];
            grid.store.each(function(r){
                json.ResultRows.push(r.data);
            }, this);
        }

        grid = this.down('#templateGrid');
        if (grid){
            json.TemplateId = this.down('#templateIdField').getValue();
        }

        return json;
    },

    getTemplatePanelItems: function(){
        return [{
            html: 'This import method is designed to work in 2 stages.  First, you can upload and save the sample information and run design.  When you upload the results, this previously saved information will be merged with the results you upload, based on well location or sample order.  This process is designed to help capture sample attributes without necessarily needing to type them into the software of the instrument that generated the data.  ' +
                   'If you have not created a sample template, we suggest either picking a different import method above, or <a href="' + LABKEY.ActionURL.buildURL('laboratory', 'prepareExptRun', null, {assayId: this.assayId}) + '" target="_blank">click here</a> to create one now.  ' +
                    'If you do create and save a template, you will need to refresh this page for it to appear in the menu above',
            style: 'padding-bottom: 10px;'
        },{
            xtype: 'labkey-combo',
            fieldLabel: 'Sample Information',
            itemId: 'templateIdField',
            allowBlank: false,
            displayField: 'title',
            valueField: 'rowid',
            labelWidth: this.LABEL_WIDTH,
            width: this.FIELD_WIDTH,
            store: {
                type: 'labkey-store',
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'laboratory',
                queryName: 'assay_run_templates',
                columns: 'rowid,title,assayId,json',
                filterArray: [
                    LABKEY.Filter.create('assayId', this.assayDesign.id, LABKEY.Filter.Types.EQUALS),
                    LABKEY.Filter.create('runid', null, LABKEY.Filter.Types.ISBLANK)
                ],
                autoLoad: true
            },
            listeners: {
                scope: this,
                change: function(field, value){
                    if (value){
                        var recIdx = field.store.findExact('rowid', value);
                        var rec = field.store.getAt(recIdx);

                        if (rec.get('title')){
                            var nameField = this.down('#runFields').down('#Name');
                            nameField.setValue(rec.get('title'));
                        }

                        var target = this.down('#templatePreviewPanel');
                        target.removeAll();
                        var cfg = this.getResultGridConfig(true);
                        Ext4.apply(cfg, {
                            itemId: 'templateGrid',
                            title: 'Sample Records',
                            editable: false,
                            maxHeight: 400,
                            columns: this.getTemplateGridColumns(),
                            tbar: null
                        });

                        target.add(cfg);
                        var grid = this.down('#templateGrid');
                        grid.store.removeAll();

                        this.loadTemplateToGrid(grid, rec.get('json'));
                    }
                }
            }
        },{
            xtype: 'container',
            itemId: 'templatePreviewPanel'
        }];
    },

    loadTemplateToGrid: function(grid, jsonString){
        var json = Ext4.decode(jsonString);
        if (grid.store.isLoading()){
            grid.store.on('load', function(){
                this.loadTemplateToGrid(grid, jsonString);
            }, this);
            return;
        }

        if (json.ResultRows && json.ResultRows.length){
            var toAdd = [];
            Ext4.each(json.ResultRows, function(map){
                toAdd.push(LDK.StoreUtils.createModelInstance(grid.store, map, false));
            }, this);
            grid.store.add(toAdd);
        }
        else
        {
            Ext4.Msg.alert('Error', 'This saved template has no samples');
        }
    },

    getTemplateGridColumns: function(){
        var columns = [];
        var columnNames = [];
        var meta = this.getMetadata(true).Results;

        if (this.templateMetadata && this.templateMetadata.colOrder){
            columnNames = this.templateMetadata.colOrder.concat(meta.columns);
        }
        else
            columnNames = meta.columns;

        columnNames = Ext4.unique(columnNames);
        Ext4.each(columnNames, function(fn){
            var field = meta.map[fn];
            if (LABKEY.ext4.Util.shouldShowInInsertView(field) && !field.setGlobally && !field.calculated){
                var col = LDK.StoreUtils.getColumnConfigForField(field);
                columns.push(col);
            }
        }, this);

        return columns;
    },

    //NOTE: this is used to avoid beforeunload messages when downloading excel templates
    setDownloadPending: function(value){
        this.downloadPending = value;
    },

    isDirty: function(){
        if (this.downloadPending)
            return false;

        var dirty = false;
        this.cascade(function(item){
            if(item.isFormField && item.isDirty()){
                dirty = true;
                return false;
            }
        }, this);

        if (dirty)
            return true;

        var grid = this.down('#resultGrid');
        if (grid){
            if (LDK.StoreUtils.isDirty(grid.store)){
                return true;
            }
        }

        return false;
    },

    resetDirtyState: function(){
        LABKEY.setDirty(false);

        this.cascade(function(item){
            if(item.isFormField && item.isDirty()){
                item.resetOriginalValue();
            }
        }, this);

        var grid = this.down('#resultGrid');
        if (grid){
            grid.store.each(function(rec){
                rec.commit(true);
            }, this);
        }
    }
});


Ext4.ns('Laboratory.AssayButtons');

Ext4.apply(Laboratory.AssayButtons, {

});