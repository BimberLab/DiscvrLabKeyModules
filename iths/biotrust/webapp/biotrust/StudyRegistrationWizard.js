/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * This is the NWBioTrust override for the survey panel that is specific to the study registration wizard.
 */
Ext4.define('LABKEY.ext4.biotrust.StudyRegistrationWizard', {

    extend : 'LABKEY.ext4.BaseSurveyPanel',

    constructor : function(config){

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
            itemId: 'StudyRegistrationFormPanel', // used by sidebar section click function
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
        }
    },

    initComponent : function() {

        this.getStudyRegistrationData();

        this.callParent();
    },

    getStudyRegistrationData : function() {
        this.rowMap = {};
        this.initialResponses = {};

        if (this.rowId)
        {
            // query the DB for the values for the given study registration RowId
            LABKEY.Query.selectRows({
                schemaName: 'biotrust',
                queryName: 'StudyRegistrationDetails',
                filterArray: [
                    LABKEY.Filter.create('RowId', this.rowId),
                    LABKEY.Filter.create('SurveyDesignId', this.surveyDesignId)
                ],
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

                        if (this.initialResponses['surveylabel'])
                            this.surveyLabel = this.initialResponses['surveylabel'];

                        this.getWizardSections();
                    }
                    else
                        this.onFailure({}, "Could not find a study registration record for the following rowId: " + this.rowId, true);
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
                        this.setLabelCaption(this.metadata);
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
            this.onFailure({}, "Could not find a survey design with label 'StudyRegistration'. Please contact an administrator to get this resolved.", false);
        }
    },

    generateSurveySections : function(surveyConfig) {

        this.addSurveyStartPanel();

        this.callParent([surveyConfig]);

        this.addSavePanel();

        this.configureSurveyLayout(this.metadata);

        this.updateRegisterInfo();

        this.configureFieldListeners();

        if (this.rowId && this.initialResponses != null)
        {
            // if we have an existing survey record, initialize the fields based on the initialResponses
            this.setValues(this.getForm(), this.initialResponses);
        }

        this.clearLoadingMask();
   },

   addSavePanel : function() {

       this.saveBtn = Ext4.create('Ext.button.Button', {
           text: 'Save',
           tooltip: 'Saves your work for later',
           hidden : !this.canEdit,
           disabled: !this.surveyLabel,
           width: 175,
           height: 30,
           handler: this.saveSurvey,
           scope: this
       });

       this.saveDisabledInfo = Ext4.create('Ext.form.DisplayField', {
           hideLabel: true,
           width: 250,
           style: "text-align: center;",
           hidden: this.surveyLabel != null,
           value: "<span style='font-style: italic; font-size: 90%'>Note: The '" + this.labelCaption + "' field at the"
               + " beginning of the form must be filled in before you can save the form*.</span>"
       });

       this.registerInfo = Ext4.create('Ext.container.Container', {
           hideLabel: true,
           width: 250,
           style: "text-align: center;"
       });

       this.registerBtn = Ext4.create('Ext.button.Button', {
           text: 'Register',
           tooltip: 'Submits your study details to NWBioTrust',
           hidden : !this.canEdit,
           disabled: !this.surveyLabel,
           width: 175,
           height: 30,
           handler: this.registerStudyBtnClick,
           scope: this
       });

       this.cancelBtn = Ext4.create('Ext.button.Button', {
           text: this.canEdit ? 'Cancel' : 'Done',
           tooltip: 'Discards your work',
           width: 175,
           height: 30,
           handler: function() { this.leavePage('nwbt.STUDY_REGISTRATIONS'); },
           scope: this
       });

       var items = [];
       if (this.canEdit)
       {
           items.push({
               xtype: 'panel',
               border: false,
               layout: {type: 'vbox', align: 'center'},
               items: [this.saveBtn, this.saveDisabledInfo]
           });
           items.push({xtype: 'label', width: 75}); // spacer

           if (!this.isRegistered)
           {
               items.push({
                   xtype: 'panel',
                   border: false,
                   layout: {type: 'vbox', align: 'center'},
                   items: [this.registerBtn, this.registerInfo]
               });
               items.push({xtype: 'label', width: 75}); // spacer
           }
       }
       items.push(this.cancelBtn);

       this.sections.push(Ext4.create('Ext.panel.Panel', {
           title: this.canEdit ? (this.isRegistered ? 'Save / Cancel' : 'Save / Register / Cancel') : 'Done',
           isDisabled: false,
           layout: {
               type: 'hbox',
               align: 'top',
               pack: 'center'
           },
           header: false,
           border: false,
           bodyStyle: 'padding-top: 25px;',
           anchor: '100%',
           items: items
       }));
   },

    toggleSaveBtn : function(enable, toggleMsg) {
        this.callParent([enable, toggleMsg]);
        this.registerBtn.setDisabled(!enable);
    },

    registerStudyBtnClick : function(btn, evt) {
        this.saveSurvey(btn, evt, true, null, null);
    },

    updateStep : function(step) {
        this.callParent([step]);
        this.updateRegisterInfo();
    },

    updateRegisterInfo : function() {
        var msg = this.surveyLabel == null ? "-" + this.labelCaption + " (Start)<br/>" : "";
        for (var name in this.validStatus)
        {
            if (!this.validStatus[name])
            {
                var cmp = this.down('[name=' + name + ']');
                // conditional validStatus for hidden fields
                if (cmp && !cmp.isHidden())
                    msg += "-" + (cmp.shortCaption ? cmp.shortCaption : name) + "<br/>";
            }
        }

        if (msg.length > 0)
        {
            msg = "<span style='font-style: italic; font-size: 80%'>"
                    + "Note: The following fields must be valid before you can register the study*:<br/>"
                    + msg + "</span>";
        }

        this.registerInfo.update(msg);
        this.registerBtn.setDisabled(msg.length != 0);
    },

    getFormDirtyValues : function() {
        var values = this.callParent();
        Ext4.each(Ext4.ComponentQuery.query('contactcombo'), function(contactCombo) {
            // if the updated values has a contact userId or rowId, we also need to store whether or not the contact is in system or out-of-system
            if (values[contactCombo.getName()])
                values[contactCombo.getName() + "insystem"] = contactCombo.isSelectedUserInSystem();
        });
        return values;
    },

    // override from BaseSurveyPanel
    saveSurvey : function(btn, evt, toRegister, successUrl, idParamName) {

        // check to make sure the survey label is not null, it is required
        if (!this.surveyLabel)
        {
            Ext4.MessageBox.alert('Error', 'The ' + this.labelCaption + ' is required.');
            return;
        }

        // check to see if there is anything to be saved
        if (!this.isSurveyDirty())
        {
            if (toRegister)
                this.registerStudy();
            else
                this.leavePage('nwbt.STUDY_REGISTRATIONS');

            return;
        }

        this.toggleSaveBtn(false, false);

        // get the dirty form values which are also valid and to be submitted
        this.submitValues = this.getFormDirtyValues();

        // send the survey rowId, surveyDesignId, and responsesPk as params to the API call
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponse.api'),
            method  : 'POST',
            jsonData: {
                surveyDesignId : this.surveyDesignId,
                rowId          : this.surveyRowId ? this.surveyRowId : undefined,
                responsesPk    : this.rowId ? this.rowId : undefined,
                label          : this.surveyLabel,
                responses      : this.submitValues,
                submit         : false
            },
            success: function(resp) {
                var o = Ext4.decode(resp.responseText);
                if (o.success)
                {
                    // store the survey rowId and responsesPk, for new entries
                    if (o.survey["rowId"])
                        this.surveyRowId = o.survey["rowId"];
                    if (o.survey["responsesPk"])
                        this.rowId = o.survey["responsesPk"];
                }

                // reset the values so that the form's dirty state is cleared, with one special case for the survey label field
                this.submitValues[this.down('.textfield[itemId=surveyLabel]').getName()] = this.down('.textfield[itemId=surveyLabel]').getValue();
                this.setValues(this.getForm(), this.submitValues);

                if (toRegister)
                {
                    this.registerStudy();
                }
                else
                {
                    // the idParamName is set when the saveSurvey event was fired, we don't need to navigate in that case
                    if (!idParamName)
                        this.leavePage('nwbt.STUDY_REGISTRATIONS');
                    else
                        this.toggleSaveBtn(true, false);
                }
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    registerStudy : function() {
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'registerStudy.api'),
            method  : 'POST',
            jsonData: {
                rowId: this.surveyRowId,
                status: 'Received'
            },
            success: function() {
                Ext4.Msg.show({
                    title: 'Study Registered Successfully',
                    msg: 'Would you like to request samples now?',
                    icon: Ext4.Msg.QUESTION,
                    buttons: Ext4.Msg.YESNO,
                    fn: function(btnId) {
                        if (btnId == 'yes')
                            this.leavePage('nwbt.SAMPLE_REQUESTS');
                        else
                            this.leavePage('nwbt.STUDY_REGISTRATIONS');
                    },
                    scope: this
                });

            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onFailure : function(resp, message, hidePanel) {
        this.callParent([resp, message, hidePanel]);

        if (this.isSurveyDirty() && this.down('.textfield[itemId=surveyLabel]').isValid())
            this.toggleSaveBtn(true, false);
    }
});