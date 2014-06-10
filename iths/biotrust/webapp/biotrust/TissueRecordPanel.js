/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('LABKEY.ext4.TissueRecordPanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.biotrust-tissuerecordpanel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            store: null,
            border: true,
            forceFit: true,
            columns: []
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.tissueTitleMap = {
            TissueSample: 'Tissue Samples',
            BloodSample: 'Blood Samples'
        };

        this.selModel = Ext4.create('Ext.selection.RowModel', {
            allowDeselect: true,
            listeners: {
                scope: this,
                selectionchange: function() {
                    this.down('#edit-selected-btn').setDisabled(!this.getSelectionModel().hasSelection());
                    this.down('#delete-selected-btn').setDisabled(!this.getSelectionModel().hasSelection());
                }
            }
        });

        this.dockedItems = [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            items: [{
                text:'Add Record',
                hidden: this.readOnly,
                handler: function(){
                    if (!this.sampleRequestStudyId)
                    {
                        Ext4.Msg.alert('Error', 'You must first select an associated study for this sample request.');
                        return;
                    }

                    if (this.sampleRequestRowId)
                    {
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('biotrust', 'createTissueRecord.api'),
                            method  : 'POST',
                            jsonData: {
                                sampleId : this.sampleRequestRowId,
                                studyId : this.sampleRequestStudyId,
                                requestType : this.recordType
                            },
                            success : function(resp){
                                var o = Ext4.decode(resp.responseText);
                                this.showUpdateRecordWindow(o.tissueId, 'Add ' + this.tissueTitleMap[this.recordType], null, true);
                            },
                            failure : this.onFailure,
                            scope   : this
                        });
                    }
                },
                scope: this
            },{
                itemId: 'edit-selected-btn',
                text: this.readOnly ? 'View Selected' : 'Edit Selected',
                disabled: true,
                handler: function() {
                    var selectedArr = this.getSelectionModel().getSelection();
                    if (selectedArr)
                    {
                        var tissueId = selectedArr[0].get("RowId") || selectedArr[0].get("rowid");
                        this.showUpdateRecordWindow(tissueId, 'Edit ' + this.tissueTitleMap[this.recordType], null, false);
                    }
                },
                scope: this
            },{
                itemId: 'delete-selected-btn',
                text:'Delete Selected',
                hidden: this.readOnly,
                disabled: true,
                handler: function() {
                    var selectedArr = this.getSelectionModel().getSelection();
                    if (selectedArr)
                    {
                        // confirm that they really want to delete the tissue record
                        Ext4.Msg.confirm('Confirm Pending Request Deletion', 'Are you sure you want to delete this tissue/blood record and all of its data?', function(btn){
                            if (btn == 'yes')
                            {
                                this.getEl().mask("Deleting tissue/blood record...");
                                Ext4.Ajax.request({
                                    url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteTissueRecord.api'),
                                    method  : 'POST',
                                    jsonData: { tissueId : selectedArr[0].get("RowId") },
                                    success : function(resp){
                                        this.getStore().load();
                                    },
                                    failure : function(resp) {
                                        this.onFailure(resp);
                                        this.getEl().unmask();
                                    },
                                    scope   : this
                                });
                            }
                        }, this);
                    }
                },
                scope: this
            }]
        }];

        this.callParent();
    },

    getColumnsCfg : function(storeColumns)
    {
        var storeColMap = {};
        Ext4.each(storeColumns, function(storeCol){
            storeColMap[storeCol.header] = storeCol.dataIndex;
        });

        var columns = [{"text": "ID", "dataIndex": storeColMap["Study Record Id"], "width": 20, "align": "center" }];

        if (this.recordType == "TissueSample")
        {
            columns.push({"text": "Tissue Type", "dataIndex": storeColMap["Tissue Type"] });
            columns.push({"text": "Anatomical site", "dataIndex": storeColMap["Anatomical Site"], "width": 40 });
            columns.push({"text": "Preservation", "dataIndex": storeColMap["Preservation"], "width": 70 });
        }
        else if (this.recordType == "BloodSample")
        {
            columns.push({"text": "Sample Type", "dataIndex": storeColMap["Blood Sample Type"], "width": 45, renderer: function(value) {
                return value == "ClinicalAppointment" ? "Clinical Appointment" : value;
            }});
            columns.push({"text": "Tube Type", "dataIndex": storeColMap["Tube Type"] });
            columns.push({"text": "Hold at", "dataIndex": storeColMap["Hold At Location"], "width": 75 });
        }

        return columns;
    },

    actionColumnHandler : function(record, sectionTitle) {
        var tissueId = record.get("RowId") || record.get("rowid");
        this.showUpdateRecordWindow(tissueId, 'Edit ' + (record.get("RequestTypeDisplay") || record.get("RequestType")), sectionTitle, false);
    },

    initGridStore : function(settings) {
        Ext4.apply(this, settings);

        // empty grid for display, won't be "activated" until we have a selected studyId, etc.
        this.getGridStore([LABKEY.Filter.create('RowId', null, LABKEY.Filter.Types.ISBLANK)]);
    },

    updateGridStore : function(studyId, sampleRequestRowId, settings) {
        if (sampleRequestRowId && this.recordType)
        {
            this.sampleRequestRowId = sampleRequestRowId;
            this.sampleRequestStudyId = studyId;

            Ext4.apply(this, settings);

            this.getGridStore([
                LABKEY.Filter.create('SampleId', this.sampleRequestRowId),
                LABKEY.Filter.create('RequestType', this.recordType)
            ]);
        }
    },

    getGridStore : function(filterArray) {
        Ext4.create('LABKEY.ext4.Store', {
            schemaName: "biotrust",
            queryName: "SampleRequestTissueRecords",
            filterArray: filterArray,
            sort: "RowId",
            autoLoad: true,
            listeners: {
                scope: this,
                beforeload: function(){ this.getEl().mask("Loading..."); },
                load: function(store, records) {
                    this.reconfigure(store, this.getColumnsCfg(store.getColumns()));
                    this.getEl().unmask();
                }
            }
        });
    },

    showUpdateRecordWindow : function(tissueId, title, goToSection, deleteOnCancel) {
        if (tissueId)
        {
            var win = Ext4.create('Ext.window.Window', {
                cls: 'data-window',
                modal: true,
                y: 100,
                title: title,
                closable: !deleteOnCancel,
                items: [Ext4.create('LABKEY.ext4.biotrust.TissueRecordWizard', {
                    cls             : 'lk-survey-panel themed-panel',
                    sampleId        : this.sampleRequestRowId,
                    recordType      : this.recordType || null,
                    tissueId        : tissueId,
                    initSectionTitle : goToSection,
                    isSubmitted     : this.isSubmitted,
                    canEdit         : this.canEdit,
                    canEditSubmitted : this.canEditSubmitted,
                    canEditRequests : this.canEditRequests,
                    alwaysEditablePropertyNames : this.alwaysEditablePropertyNames,
                    deleteOnCancel  : deleteOnCancel,
                    autosaveInterval: 60000
                })],
                listeners: {
                    scope: this,
                    closewindow : function() {
                        this.getStore().load();
                        win.close();
                    }
                }
            });
            win.show();
        }
    },

    // returns the number of tissue/sample records in this panel's grid
    getValue : function() {
        return this.getStore().getCount() > 0 ? this.getStore().getCount() : null;
    },

    onFailure : function(resp, message) {
        var error = {};
        if (resp && resp.responseText)
            error = Ext4.decode(resp.responseText);
        else if (resp)
            error = resp;

        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');
    }
});


/*
 * This is the NWBioTrust override for the survey panel that is specific to the add/edit tissue record wizard.
 */
Ext4.define('LABKEY.ext4.biotrust.TissueRecordWizard', {

    extend : 'LABKEY.ext4.BaseSurveyPanel',

    constructor : function(config){

        Ext4.apply(config, {
            border: false,
            width: 610,
            layout: {
                type: 'hbox',
                pack: 'start',
                align: 'stretchmax'
            },
            trackResetOnLoad: true,
            items: [],
            itemId: 'TissueRecordFormPanel', // used by sidebar section click function
            bubbleEvents: ['closewindow'],
            surveyLayout: 'auto',
            metadata: null,
            sections: [],
            validStatus: {},
            changeHandlers: {},
            listeners: {
                scope: this,
                afterrender: function() {
                    if (this.sections && this.sections.length == 0)
                        this.setLoading(true);
                }
            }
        });

        this.callParent([config]);

        this.addEvents('closewindow');
    },

    initComponent : function() {

        this.getTissueRecordData();

        this.callParent();
    },

    getTissueRecordData : function() {
        this.rowMap = {};
        this.initialTissueRecordValues = {};

        if (this.tissueId)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'getTissueRecords.api'),
                method  : 'POST',
                jsonData: {tissueId : this.tissueId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);
                    if (o.rows && o.rows.length == 1)
                    {
                        // save the raw row and process the entries so we can initialize the form
                        this.rowMap = o.rows[0];
                        Ext4.Object.each(this.rowMap, function(key, value){
                            // make sure to parse dates accordingly, as they come in as strings but datefield.setValue doesn't like that
                            var dateValue = this.getValueIfDate(value);
                            if (dateValue)
                                value = dateValue;

                            this.initialTissueRecordValues[key.toLowerCase()] = value;
                        }, this);

                        this.getSurveyDesign();
                    }
                    else
                        this.onFailure({}, "Could not find a tissue record for the following rowId: " + this.tissueId, true);
                },
                failure : this.onFailure,
                scope   : this
            });
        }
        else
            this.getSurveyDesign();
    },

    getSurveyDesign : function() {

        if (this.recordType)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'findSurveyDesign.api'),
                method  : 'POST',
                params  : {label : this.recordType},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.success)
                    {
                        this.surveyDesignId = o.surveyDesignId;
                        this.getWizardSections();
                    }
                    else
                        this.onFailure(resp);
                },
                failure : this.onFailure,
                scope   : this
            });
        }
    },

    getWizardSections : function() {
        // using the survey.SurveyDesigns table to store the question metadata
        if (this.surveyDesignId)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyTemplate.api'),
                method  : 'POST',
                jsonData: {rowId : this.surveyDesignId, stringifyMetadata : true},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.success)
                    {
                        this.metadata = Ext4.JSON.decode(o.survey.metadata);
                        this.generateSurveySections(this.metadata);
                    }
                    else
                        this.onFailure(resp);
                },
                failure : this.onFailure,
                scope   : this
            });
        }
        else
            this.onFailure({}, "Could not find a survey design with label '" + this.recordType + "'. Please contact an administrator to get this resolved.", false);
    },

    generateSurveySections : function(surveyConfig) {

        this.callParent([surveyConfig, 'Ext.form.Panel']);

        this.addSavePanel();

        this.configureSurveyLayout(this.metadata, this.initSectionTitle);

        this.configureFieldListeners();

        // hold onto the studyId
        this.studyId = this.initialTissueRecordValues['studyid'];

        // initialize the fields based on the initial values
        if (this.initialTissueRecordValues != null)
            this.setValues(this.getForm(), this.initialTissueRecordValues);

        // CBR issue 90: default units value of "mLs" for blood sample records
        if (this.recordType == "BloodSample")
        {
            this.getForm().findField('minimumsizeunits').setValue('mLs');
            this.getForm().findField('preferredsizeunits').setValue('mLs');
        }

        this.addCopyFromPreviousButtons();

        this.updateSaveInfo();

        this.clearLoadingMask();
    },

    configureFieldListeners : function() {

        Ext4.each(this.getForm().getFields().items, function(field){

            // add a global change listener for all questions so we can map them to any change handlers specified in the survey
            field.addListener('change', this.questionChangeHandler, this);

            // if the user can not edit this survey (i.e. submitted and non-admin), make the field readOnly
            // CBR issue 191: allow editing of special properties for locked requests (i.e. protocol docs, sample pickup info, etc.)
            if (!this.canEdit && (!this.canEditRequests || this.alwaysEditablePropertyNames.indexOf(field.getName()) == -1))
            {
                field.setReadOnly(true);
            }
            else
            {
                field.setReadOnly(false);

                // add a validity listener to each question
                if (field.submitValue)
                {
                    this.validStatus[field.getName()] = field.isValid();
                    field.clearInvalid();
                    field.addListener('validitychange', this.fieldValidityChanged, this);
                }
            }

        }, this);
    },

    addCopyFromPreviousButtons : function() {
        Ext4.each(this.sections, function(sectionPanel){
            if (sectionPanel.copyFromPrevious && this.sampleId && this.canEdit)
            {
                // query to see if there are any previous records to copy from
                var sql = "SELECT x.*, t.StudyId, t.RecordId, "
                    + "(t.RecordId || ': ' || t.RequestTypeDisplay || ': ' || (CASE WHEN t.TissueType IS NOT NULL THEN t.TissueType ELSE t.TubeType END)) AS RecordDisplayInfo "
                    + "FROM " + sectionPanel.copyFromPrevious + " AS x LEFT JOIN SampleRequestTissueRecords AS t ON x.TissueId = t.RowId WHERE t.RowId <> " + this.tissueId;
                // filter by studyId if we have one
                if (this.studyId)
                    sql += " AND t.StudyId = " + this.studyId;
                else
                    sql += " AND t.SampleId = " + this.sampleId;

                LABKEY.Query.executeSql({
                    schemaName: "biotrust",
                    sql: sql,
                    sort: 'RecordId',
                    success: function(data) {
                        if (data.rows.length > 0)
                        {
                            var copyFromPreviousRecords = [];
                            Ext4.each(data.rows, function(row){
                                var data = {};
                                Ext4.Object.each(row, function(key, value){
                                    // make sure to parse dates accordingly, as they come in as strings but datefield.setValue doesn't like that
                                    var dateValue = this.getValueIfDate(value);
                                    if (dateValue)
                                        value = dateValue;

                                    data[key.toLowerCase()] = value;
                                }, this);

                                if (data["recorddisplayinfo"])
                                {
                                    copyFromPreviousRecords.push({
                                        recordId: (row.RecordId || row.recordid),
                                        recordDisplayInfo: data["recorddisplayinfo"],
                                        data: data
                                    });
                                }
                            });

                            sectionPanel.insert(1, {
                                xtype: 'button',
                                text: 'Copy from Previous',
                                records: copyFromPreviousRecords,
                                panel : sectionPanel,
                                padding: 1,
                                height: 23,
                                handler: this.copyFromPreviousClick,
                                scope: this
                            });
                        }
                    },
                    failure: this.onFailure,
                    scope: this
                });
            }
        }, this);
    },

    copyFromPreviousClick : function(btn) {
        var win = Ext4.create('Ext.window.Window', {
            cls: 'data-window',
            modal: true,
            title: "Copy from Previous",
            bodyStyle: 'padding: 10px;',
            border: false, frame: false,
            height: 125,
            items: [{
                xtype: 'label',
                width: 600,
                html: 'Note: copying values from a previous record will replace any existing values in this form.',
                style: 'font-style: italic;'
            },{
                xtype: 'combo',
                itemId: 'copyFromRecordId',
                hideLabel: true,
                width: 600,
                queryMode: 'local',
                displayField: 'recordDisplayInfo',
                valueField: 'data',
                allowBlank: false,
                editable: false,
                emptyText: 'Select...',
                store: Ext4.create('Ext.data.Store', {
                    fields: ['recordDisplayInfo', 'data'],
                    data : btn.records
                })
            }],
            buttons: [{
                text: 'Copy',
                itemId: 'copyButton',
                scope: this,
                handler: function() {
                    var combo = win.down('#copyFromRecordId');
                    if (combo)
                        this.setValues(btn.panel.getForm(), combo.getValue());

                    win.close();
                }
            },{
                text: 'Cancel',
                handler: function(){ win.close(); }
            }]
        });
        win.show();
    },

    addSavePanel : function() {

        this.saveBtn = Ext4.create('Ext.button.Button', {
            text: 'Save',
            disabled: true,
            width: 175,
            height: 30,
            handler: this.saveTissueRecord,
            tooltip: (this.canEditRequests && this.isSubmitted)
                ? "This sample request has been locked, but you are allowed to edit certain properties (i.e. notes) because you are a project/site administrator."
                : null,
            scope: this
        });

        this.saveInfo = Ext4.create('Ext.container.Container', {
            hideLabel: true,
            width: 250,
            style: "text-align: center;"
        });

        this.cancelBtn = Ext4.create('Ext.button.Button', {
            text: (this.canEditRequests && this.isSubmitted) || this.canEdit ? 'Cancel' : 'Done',
            width: 175,
            height: 30,
            handler: function() {
                if (this.deleteOnCancel)
                {
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteTissueRecord.api'),
                        method  : 'POST',
                        jsonData: { tissueId : this.tissueId },
                        success : function(resp){
                            this.fireEvent('closewindow');
                        },
                        failure : this.onFailure,
                        scope   : this
                    });
                }
                else
                    this.fireEvent('closewindow');
            },
            scope: this
        });

        var items = [];
        if ((this.canEditRequests && this.isSubmitted) || this.canEdit)
        {
            items.push({
                xtype: 'panel',
                border: false,
                layout: {type: 'vbox', align: 'center'},
                items: [this.saveBtn, this.saveInfo]
            });
            items.push({xtype: 'label', width: 20}); // spacer
        }
        items.push(this.cancelBtn);

        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: !this.canEdit ? 'Done' : 'Save / Cancel',
            isDisabled: false,
            layout: {
                type: 'hbox',
                align: 'top',
                pack: 'center'
            },
            header: false,
            border: false,
            bodyStyle: 'padding-top: 15px;',
            anchor: '100%',
            items: items
        }));
    },

    questionChangeHandler : function(cmp, newValue, oldValue) {
        this.callParent([cmp, newValue, oldValue]);
        this.updateSaveInfo();
    },

    updateSaveInfo : function() {
        var isInvalid = false;
        Ext4.each(this.requiredFieldNames, function(name){
            var cmp = this.down('[name=' + name + ']');
            if (cmp)
            {
                // get the field value to determine if it is not null (special case for radiogroups)
                var value = cmp.getValue();
                if (cmp.getXType() == "radiogroup")
                    value = cmp.getChecked().length > 0 ? cmp.getValue() : null;

                if (!cmp.isHidden() && !value)
                    isInvalid = true;
            }
        }, this);

        this.saveBtn.setDisabled(isInvalid);
    },

    isTissueRecordDirty : function() {
        return this.getForm().isDirty();
    },

    getFormDirtyValues : function() {
        var updateCommands = [];

        Ext4.each(this.sections, function(sectionPanel){
            if (sectionPanel.getXType() == "form")
            {
                if (sectionPanel.getForm().isDirty())
                {
                    var values = this.getDirtyValues(sectionPanel.getForm());

                    // add a new update command for each of the sections that have dirty values
                    if (sectionPanel.title == "Blood Sample Information" || sectionPanel.title == "Tissue Type Information")
                    {
                        updateCommands.push({
                            schemaName: 'biotrust',
                            queryName: 'TissueRecords',
                            rows: [Ext4.apply({RowId: this.tissueId}, values)],
                            command: 'update'
                        });
                    }
                }
            }
        }, this);

        return updateCommands;
    },

    saveTissueRecord : function() {
        // check to see if there is anything to be saved
        if (!this.isTissueRecordDirty())
        {
            this.fireEvent('closewindow');
            return;
        }

        // get the dirty form values which are also valid and to be submitted
        var commands = this.getFormDirtyValues();

        // the tissue record should have already been inserted
        var errorMessage = "";
        if (!this.tissueId)
            errorMessage = "This tissue record has not yet been inserted.";

        if (commands.length == 0)
            errorMessage = "Unable to get form values to be updated.";

        if (errorMessage.length > 0)
        {
            Ext4.MessageBox.alert('Error', errorMessage);
            return;
        }

        LABKEY.Query.saveRows({
            commands: commands,
            success: function(data) {
                this.fireEvent('closewindow');
            },
            failure : this.onFailure,
            scope   : this
        });
    }
});



