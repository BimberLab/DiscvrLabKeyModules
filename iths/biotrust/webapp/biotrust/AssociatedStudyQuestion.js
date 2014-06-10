/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.biotrust.AssociatedStudyQuestion', {
    extend: 'Ext.form.FieldContainer',
    alias: 'widget.associatedstudyquestion',
    isFormField: true,
    submitValue: true,

    constructor : function(config) {

        Ext4.applyIf(config, {
            border: false,
            width: 800,
            labelWidth: 350,
            margin: "10px 10px 15px"
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.originalValue = this.value;
        this.dirty = false;

        this.defaults = {
            width : this.width - this.labelWidth,
            hideLabel: true
        };

        var studyNameCombo = Ext4.create('Ext.form.field.ComboBox', {
            editable : false,
            allowBlank : false,
            forceSelection : true,
            isFormField : false,
            submitValue : false,
            store : Ext4.create('LABKEY.ext4.Store', {
                schemaName: "biotrust",
                sql : "SELECT RowId, SurveyLabel AS StudyName FROM StudyRegistrationDetails"
                    + " WHERE SurveyDesignId = " + this.getStudySurveyDesignId(),
                autoLoad: true,
                sort: "StudyName",
                listeners: {
                    load: function() {
                        studyNameCombo.enable();
                        studyNameCombo.addCls('study-names-loaded-marker');// for selenium testing
                    }
                }
            }),
            queryMode : 'local',
            displayField : 'StudyName',
            valueField : 'RowId',
            emptyText: 'Select...',
            disabled: true,
            listeners : {
                scope : this,
                change : function(combo, newValue, oldValue) {
                    var url = LABKEY.ActionURL.buildURL('biotrust', 'updateStudyRegistration', null, { rowId : newValue });
                    this.down('displayfield').setValue("<a href='" + url + "' target='_blank'>" + combo.getDisplayValue() + "</a>");
                }
            }
        });

        this.items = [
            studyNameCombo,
            {
                xtype: 'displayfield',
                hidden: true
            }
        ];

        this.callParent();
    },

    getStudySurveyDesignId : function() {
        var formPanel = Ext4.ComponentQuery.query('#SampleRequestFormPanel')
        return (formPanel.length > 0 && formPanel[0].studySurveyDesignId) ? formPanel[0].studySurveyDesignId : 0;
    },

    getName : function() {
        return this.name;
    },

    resetOriginalValue : function() {
        this.setValue(this.originalValue);
    },

    clearValue : function() {
        this.down('combo').clearValue();
    },

    setValue : function(value) {
        if (value)
        {
            this.down('combo').setValue(value);

            this.setDirty(false);
        }
    },

    getValue : function() {
        return this.down('combo').getValue();
    },

    getSubmitData : function() {
        return this.getValue();
    },

    getSubmitValue : function() {
        return this.getValue();
    },

    setDirty : function(isDirty) {
        if (this.dirty != isDirty)
            this.fireEvent('dirtychange', this, isDirty);

        this.dirty = isDirty;
    },

    isDirty : function() {
        return this.dirty;
    },

    isValid : function() {
        return this.getValue() != null;
    },

    clearInvalid : function() {
        // not implemented
    },

    setReadOnly : function(readOnly) {
        this.down('combo').setVisible(!readOnly);
        this.down('displayfield').setVisible(readOnly);
    }
});