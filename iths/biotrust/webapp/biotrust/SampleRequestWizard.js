/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * This is the NWBioTrust override for the survey panel that is specific to the sample request wizard.
 */
Ext4.define('LABKEY.ext4.biotrust.SampleRequestWizard', {

    extend : 'LABKEY.ext4.BaseSurveyPanel',

    constructor : function(config){

        Ext4.QuickTips.init();
        Ext4.apply(Ext4.QuickTips.getQuickTip(), {
            dismissDelay: 30000,
            autoHide: false
        });

        Ext4.apply(config, {
            border: true,
            width: 870,
            minHeight: 25,
            layout: {
                type: 'hbox',
                pack: 'start',
                align: 'stretchmax'
            },
            trackResetOnLoad: true,
            items: [],
            itemId: 'SampleRequestFormPanel', // used by sidebar section click function and AssociatedStudyQuestion
            surveyLayout: 'card',
            showProgressBar: true,
            progressBarWidth: 500,
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

        if (this.canEdit)
        {
            // check dirty state on page navigation
            window.onbeforeunload = LABKEY.beforeunload(this.isSurveyDirty, this);

            // hook onunload to clean up auto-created samples if a user navigates away, note: chrome won't work with
            // this method and needs to use onbeforeunload
            if (!Ext4.isChrome) {

                var me = this;
                window.onunload = function(){

                    if (me.deleteOnCancel) {

                        // this has to be a synchronous ajax request because the window is unloading
                        var url = LABKEY.ActionURL.buildURL('biotrust', 'deleteSampleRequest.api', null, {sampleId : me.rowId});
                        var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
                        request.open("POST", url, false);
                        request.send();

                        me.deleteOnCancel = false;
                    }
               };
            }
        }
    },

    initComponent : function() {

        this.getSampleRequestData();

        this.callParent();
    },

    isSurveyDirty : function() {
        if (Ext4.isChrome && this.deleteOnCancel) {

            // this has to be a synchronous ajax request because the window is unloading
            var url = LABKEY.ActionURL.buildURL('biotrust', 'deleteSampleRequest.api', null, {sampleId : this.rowId});
            var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
            request.open("POST", url, false);
            request.send();

            this.deleteOnCancel = false;
            return false;
        }
        return this.getForm().isDirty();
    },

    getSampleRequestData : function() {
        this.rowMap = {};
        this.initialResponses = {};

        if (this.rowId)
        {
            // query the DB for the values for the given sample request RowId
            LABKEY.Query.selectRows({
                schemaName: 'biotrust',
                queryName: 'SampleRequestDetails',
                filterArray: [LABKEY.Filter.create('RowId', this.rowId)],
                success: function(data) {
                    if (data.rows.length == 1)
                    {
                        // save the raw row and process the entries so we can initialize the form
                        this.rowMap = data.rows[0];
                        Ext4.Object.each(this.rowMap, function(key, value){
                            // make sure to parse dates accordingly, as they come in as strings but datefield.setValue doesn't like that
                            if (Ext4.Date.parse(value, "Y/m/d H:i:s", true))
                                value = Ext4.Date.parse(value, "Y/m/d H:i:s", true);

                            this.initialResponses[key.toLowerCase()] = value;
                        }, this);

                        if (this.initialResponses['studyid'])
                            this.studyId = this.initialResponses['studyid'];

                        this.getWizardSections();
                        this.setSubmittedByInfo({submitted: this.initialResponses['submitted'], submittedBy: this.initialResponses['submittedby']});
                    }
                    else
                        this.onFailure({}, "Could not find a sample request record for the following rowId: " + this.rowId, true);
                },
                failure : this.onFailure,
                scope   : this
            });
        }
        else
            this.getWizardSections();
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
        {
            this.onFailure({}, "Could not find a survey design with label 'SampleRequest'. Please contact an administrator to get this resolved.", false);
        }
    },

    generateSurveySections : function(surveyConfig) {

        this.callParent([surveyConfig]);

        this.addSurveyEndPanel();

        this.configureSurveyLayout(this.metadata);

        this.configureFieldListeners();

        // if we know the studyId from the URL, set the initial value for that field
        var studyField = this.getForm().findField('studyid');
        if (studyField)
        {
            if (this.studyId)
                studyField.setValue(this.studyId);

            // don't let the user change the studyId for an already saved sample request
            if (this.initialResponses['studyid'])
                studyField.setReadOnly(true);
        }

        if (this.rowId && this.initialResponses != null)
        {
            // if we have an existing sample request record, initialize the fields based on the initialResponses
            this.setValues(this.getForm(), this.initialResponses);

            this.updateTissueRecordGrids();
        }
        else
        {
            this.initTissueRecordGrids();
        }

        this.updateSubmitInfo();

        this.clearLoadingMask();
   },

    customizeQuestionConfig : function(question, config, hidden) {
        config = this.callParent([question, config, hidden]);

        if (this.canEditRequests && this.alwaysEditablePropertyNames.indexOf(config.name) > -1)
            config.readOnly = false;

        return config;
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

    updateSubmitInfo : function() {
        // don't use formBind for this wizard since we have some custom "question" types
        this.submitBtn.formBind = false;

        // add to message for the selection of sample types
        var msg = "";
        if (!this.getSampleTypesValidState())
            msg += "-Types of Samples Needed (Sample Information)<br/>";

        // add to message for requirement that at least one tissue/blood record exists
        var count = 0;
        Ext4.each(this.getTissueRecordGrids(), function(grid){
            count += grid.getStore().getTotalCount();
        }, this);
        if (count == 0)
        {
            msg += "-Tissue Sample Records (Tissue Samples)<br/>"
                + "or Blood Sample Records (Blood Samples)<br/>";
        }

        // add to message for each required field name that is visible (i.e. section not disabled and not hidden)
        Ext4.each(this.requiredFieldNames, function(name){
            var cmp = this.down('[name=' + name + ']');

            // check if the cmp exists and has a sectionTitle
            if (cmp && cmp.sectionTitle)
            {
                // add to the required list info if the section is enabled
                var section = this.down('panel[title=' + cmp.sectionTitle + ']');

                // get the field value to determine if it is not null (special case for radiogroups
                var value = cmp.getValue();
                if (cmp.getXType() == "radiogroup")
                    value = cmp.getChecked().length > 0 ? cmp.getValue() : null;

                if (section && !section.isDisabled && !cmp.isHidden() && !value)
                    msg += "-" + (cmp.shortCaption ? cmp.shortCaption : name) + "<br/>";
            }
        }, this);

        if (msg.length > 0)
        {
            msg = "<span style='font-style: italic; font-size: 80%'>"
                + "Note: The following fields must be valid before you can save the request form:<br/>"
                + msg + "</span>";
        }

        this.submitInfo.update(msg);
        this.submitBtn.setDisabled(msg.length > 0);
    },

    addSurveyEndPanel : function() {
        this.callParent();

        var lastSection = this.sections[this.sections.length - 1];

        // add the submit button for RCs if the sample request is not locked
        if (this.canEditSubmitted && this.isSubmitted)
        {
            lastSection.add({xtype: 'label', width: 75, value: null});
            lastSection.add({
                xtype: 'panel',
                layout: {type: 'vbox', align: 'center'},
                items: [this.submitBtn, this.submitInfo]
            });
        }
        // CBR Issue 191: allow saving changes to some special fields even if sample request is locked
        else if (this.canEditRequests && this.isSubmitted)
        {
            lastSection.setTitle('Save');
            this.doneBtn.hide();
            lastSection.down('.panel').add({
                xtype: 'displayfield',
                hideLabel: true,
                width: 250,
                style: "text-align: center;",
                value: "<span style='font-style: italic; font-size: 90%'>This sample request has been locked, but you are allowed to "
                        + "edit certain properties (i.e. protocol documents, sample pickup info, etc.) because you are a "
                        + "project/site administrator.</span><br/><br/>"
            });
            lastSection.down('.panel').add(this.saveBtn);
        }

        // add a cancel button if this is a new sample request
        if (!this.rowId)
        {
            lastSection.add({xtype: 'label', width: 75, value: null});
            lastSection.add(this.getCancelButton());
        }

        // add tooltips for save and submit
        this.saveBtn.setTooltip('Saves your work for later');
        this.submitBtn.setTooltip('Submits your sample request details to NWBioTrust');

        // change the save buttons disabled info for this wizard
        this.saveDisabledInfo.setValue("<span style='font-style: italic; font-size: 90%'>Note: The 'Associated Study' field at the"
                + " beginning of the form must be filled in and there must be at least one tissue/blood sample record added before you"
                + " can save the form.</span>");
    },

    getCancelButton : function() {
        return Ext4.create('Ext.button.Button', {
            text: 'Cancel',
            tooltip: 'Discards your work',
            width: 125,
            height: 30,
            handler: function() {
                if (this.deleteOnCancel)
                {
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('biotrust', 'deleteSampleRequest.api'),
                        method  : 'POST',
                        jsonData: { sampleId : this.rowId },
                        success : function(resp){
                            this.deleteOnCancel = false;
                            this.leavePage('nwbt.SAMPLE_REQUESTS');
                        },
                        failure : this.onFailure,
                        scope   : this
                    });
                }
                else
                    this.leavePage('nwbt.SAMPLE_REQUESTS');
            },
            scope: this
        });
    },

    getSampleTypesValidState : function() {
        var valid = false;

        // tracks the state of the Sample Types checkbox group as a whole
        var checkboxes = this.getSampleTypeCheckboxes();
        if (checkboxes.length > 0)
        {
            Ext4.each(checkboxes, function(cb) {
                if (cb.getValue())
                    valid = true;
            });
        }
        // no sample type checkes, so skip this validation step
        else
            valid = true;

        return valid;
    },

    getSampleTypeCheckboxes : function(cbName) {
        var fields = [
            {name: 'requiresurgicaltissue', sectionTitle: 'Tissue Samples'},
            {name: 'requirenonsurgicaltissue', sectionTitle: 'Tissue Samples'},
            {name: 'requireappointmentsblood', sectionTitle: 'Blood Samples'},
            {name: 'requirediscardedblood', sectionTitle: 'Blood Samples'},
            {name: 'requireffpetissue', sectionTitle: 'Tissue Samples'},
            {name: 'requirearchivedsamples', sectionTitle: ''}
        ];

        var checkboxes = [];
        Ext4.each(fields, function(field){
            var cb = this.down('checkboxfield[name=' + field.name + ']');
            if (cb)
            {
                if (!cbName || field.name == cbName)
                {
                    cb.sectionTitle = field.sectionTitle;
                    checkboxes.push(cb);
                }
            }
        }, this);

        return checkboxes;
    },

    getTissueRecordGrids : function() {
        return Ext4.ComponentQuery.query('biotrust-tissuerecordpanel');
    },

    updateStep : function(step) {
        this.checkForAssociatedStudySelection();

        var origStep = this.currentStep;

        this.callParent([step]);

        // for this wizard, we'll insert the initial sample request record when the associated study is selected
        if (origStep != this.currentStep && !this.rowId && this.studyId)
        {
            this.createSampleRequestRecord();
        }

        this.updateSubmitInfo();
        this.toggleSaveBtn();
    },

    checkForAssociatedStudySelection : function() {
        // check for change to associated study selection
        var studyField = this.getForm().findField("studyid");
        if (studyField && this.studyId != studyField.getValue())
        {
            // each time the studyId changes, we have to update the tissue record grids accordingly
            this.studyId = studyField.getValue();
            this.updateTissueRecordGrids();
        }
    },

    createSampleRequestRecord : function() {
        if (!this.studyId)
        {
            Ext4.MessageBox.show({
                title: 'Error',
                msg: 'You must first select an associated study.',
                buttons: Ext4.Msg.OK,
                icon: Ext4.Msg.ERROR
            });
            return;
        }

        LABKEY.Query.insertRows({
            schemaName: 'biotrust',
            queryName: 'SampleRequests',
            rows: [{ StudyId: this.studyId }],
            success: function(data) {
                if (data.rows.length == 1)
                {
                    this.deleteOnCancel = true;
                    this.rowId = data.rows[0].rowId || data.rows[0].rowid;

                    // set the study field to readOnly so that it can't be changed after the save occurs
                    var studyField = this.getForm().findField("studyid");
                    if (studyField)
                        studyField.setReadOnly(true);

                    // now that we have a sample request rowid, we can initialize the tissue record grids
                    this.updateTissueRecordGrids();
                }
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    initTissueRecordGrids : function() {
        Ext4.each(this.getTissueRecordGrids(), function(grid){
            grid.initGridStore({
                isSubmitted : this.isSubmitted,
                canEdit : this.canEdit,
                canEditSubmitted : this.canEditSubmitted,
                canEditRequests : this.canEditRequests,
                alwaysEditablePropertyNames : this.alwaysEditablePropertyNames
            });
        }, this);
    },

    updateTissueRecordGrids : function() {
        if (this.studyId && this.rowId)
        {
            Ext4.each(this.getTissueRecordGrids(), function(grid){
                grid.updateGridStore(this.studyId, this.rowId, {
                    isSubmitted : this.isSubmitted,
                    canEdit : this.canEdit,
                    canEditSubmitted : this.canEditSubmitted,
                    canEditRequests : this.canEditRequests,
                    alwaysEditablePropertyNames : this.alwaysEditablePropertyNames
                });
            }, this);

            this.toggleSaveBtn();
        }
    },

    toggleSaveBtn : function() {
        // there must be an associated study and at least one tissue/blood sample request to allow saving
        var count = 0;
        Ext4.each(this.getTissueRecordGrids(), function(grid){
            count += grid.getStore().getTotalCount();
        }, this);
        var enable = this.studyId && count > 0;

        this.saveBtn.setDisabled(!enable);
        if (enable)
            this.saveDisabledInfo.hide();
        else
            this.saveDisabledInfo.show();
    },

    getFormDirtyValues : function() {
        var values = this.callParent();
        Ext4.each(Ext4.ComponentQuery.query('contactcombo'), function(contactCombo) {
            // if the updated values has a contact userId or rowId, we also need to store whether or not the contact is in system or out-of-system
            if (values[contactCombo.name])
                values[contactCombo.name + "insystem"] = contactCombo.isSelectedUserInSystem();
        });
        return values;
    },

    // override of BaseSurveyPanel
    saveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {

        // from fire saveSurvey event on "Attach a file" click, create the sample request if necessary
        if (!this.rowId && idParamName)
        {
            this.checkForAssociatedStudySelection();
            this.createSampleRequestRecord();
            return;
        }

        // get the dirty form values which are also valid and to be submitted
        this.submitValues = this.getFormDirtyValues();

        // the sample request record should have already been inserted
        var errorMessage = "";
        if (!this.rowId)
            errorMessage += (errorMessage.length > 0 ? "<br/>" : "") + "This sample request has not yet been inserted.";
        else
            this.submitValues['RowId'] = this.rowId;

        if (errorMessage.length > 0)
        {
            Ext4.MessageBox.show({
                title: 'Error',
                msg: errorMessage,
                buttons: Ext4.Msg.OK,
                icon: Ext4.Msg.ERROR
            });
            return;
        }

        // if either the submit or save button was pressed, clear the deleteOnCancel flag so we can
        // unload cleanly
        if (btn != null)
            this.deleteOnCancel = false;

        // check to see if there is anything to be saved
        if (!this.isSurveyDirty() && !toSubmit)
        {
            this.leavePage('nwbt.SAMPLE_REQUESTS');
            return;
        }

        this.getEl().mask(toSubmit ? "Submitting sample request..." : "Saving sample request...");
        LABKEY.Query.updateRows({
            schemaName: 'biotrust',
            queryName: 'SampleRequests',
            rows: [this.submitValues],
            success: function(data) {
                // reset the values so that the form's dirty state is cleared
                this.setValues(this.getForm(), this.submitValues);

                if (toSubmit)
                    this.submitTissueRecords();
                else
                {
                    this.getEl().unmask();

                    // the idParamName is set when the saveSurvey event was fired, we don't need to navigate in that case
                    if (!idParamName)
                        this.leavePage('nwbt.SAMPLE_REQUESTS');
                }
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    submitTissueRecords : function() {
        this.tissueRecordCount = 0;
        Ext4.each(this.getTissueRecordGrids(), function(grid){
            grid.getStore().each(function(record) {
                this.tissueRecordCount++;
                this.submitIndividualRecord(record);
            }, this);
        }, this);
    },

    submitIndividualRecord : function(record) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'findSurveyDesign.api'),
            method  : 'POST',
            params  : {label : record.get("RequestType")},
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                if (o.success)
                {
                    var surveyLabel = record.get("RecordId") + ": " + record.get("RequestTypeDisplay") + " - " + (record.get("TissueType") || record.get("TubeType"));

                    // send the surveyDesignId and responsesPk as params to the API call, this will create and submit a survey instance for this tissue record
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponse.api'),
                        method  : 'POST',
                        jsonData: {
                            surveyDesignId : o.surveyDesignId,
                            rowId : record.get("SurveyRowId") ? record.get("SurveyRowId") : undefined,
                            responsesPk : record.get("RowId"),
                            label : surveyLabel,
                            responses : {},
                            submit : true
                        },
                        success: function() {
                            this.tissueRecordCount--;
                            if (this.tissueRecordCount == 0)
                            {
                                this.getEl().unmask();
                                this.leavePage('nwbt.SAMPLE_REQUESTS');
                            }
                         },
                         failure : this.onFailure,
                         scope   : this
                    });
                }
                else
                    this.onFailure(resp);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onFailure : function(resp, message, hidePanel) {
        if (this.getEl() && this.getEl().isMasked())
            this.getEl().unmask();

        this.callParent([resp, message, hidePanel]);
    }
});