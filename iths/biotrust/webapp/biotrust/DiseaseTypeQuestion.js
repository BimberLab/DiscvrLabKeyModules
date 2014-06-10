/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.biotrust.DiseaseTypeQuestion', {
    extend: 'Ext.form.FieldContainer',
    alias: 'widget.diseasetypequestion',
    isFormField: true,
    submitValue: true,

    constructor : function(config) {

        Ext4.applyIf(config, {
            border: false,
            width: 800,
            margin: "10px 10px 15px"
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.originalValue = this.value;
        this.dirty = false;

        this.items = [{
            xtype: 'container',
            height: 150,
            padding: 5,
            border: 1,
            style: {
                borderColor: '#C0C0C0',
                borderStyle: 'solid'
            },
            autoScroll: true,
            defaults: {
                xtype: "checkboxfield",
                margin: "0",
                isFormField: false, // we'll submit the disease type as a whole instead of individual checkboxes
                submitValue: false
            },
            items: [
                { boxLabel: "<b>Non-Cancer</b>", margin: "0 0 8 0", inputValue: "Non-Cancer" },
                { xtype: 'label', html: "<b>Cancer</b>", margin: 0  },
                { boxLabel: "Head & neck (general)", inputValue: "Head & neck (general)" },
                { boxLabel: "Lungs, trachea and bronchi", inputValue: "Lungs, trachea and bronchi" },
                { boxLabel: "Entire GI tract", inputValue: "Entire GI tract" },
                { boxLabel: "Esophagus", inputValue: "Esophagus" },
                { boxLabel: "Stomach", inputValue: "Stomach" },
                { boxLabel: "Small intestine", inputValue: "Small intestine" },
                { boxLabel: "Colon and rectum", inputValue: "Colon and rectum" },
                { boxLabel: "Anus", inputValue: "Anus" },
                { boxLabel: "Liver", inputValue: "Liver" },
                { boxLabel: "Pancreas", inputValue: "Pancreas" },
                { boxLabel: "Entire urinary system", inputValue: "Entire urinary system" },
                { boxLabel: "Kidney", inputValue: "Kidney" },
                { boxLabel: "Bladder", inputValue: "Bladder" },
                { boxLabel: "All male genital", inputValue: "All male genital" },
                { boxLabel: "Testicular", inputValue: "Testicular" },
                { boxLabel: "Penile", inputValue: "Penile" },
                { boxLabel: "Breast", inputValue: "Breast" },
                { boxLabel: "All female genital", inputValue: "All female genital" },
                { boxLabel: "Ovarian and fallopian tube", inputValue: "Ovarian and fallopian tube" },
                { boxLabel: "Uterus/endometrium", inputValue: "Uterus/endometrium" },
                { boxLabel: "Cervix", inputValue: "Cervix" },
                { boxLabel: "Vulva", inputValue: "Vulva" },
                { boxLabel: "Primary brain tumors", inputValue: "Primary brain tumors" },
                { boxLabel: "Thyroid", inputValue: "Thyroid" },
                { boxLabel: "Pituitary (benign and malignant)", inputValue: "Pituitary (benign and malignant)" },
                { boxLabel: "Parathyroid (benign and malignant)", inputValue: "Parathyroid (benign and malignant)" },
                { boxLabel: "Adrenal", inputValue: "Adrenal" },
                { boxLabel: "All hematolymphoid", inputValue: "All hematolymphoid" },
                { boxLabel: "Leukemia", inputValue: "Leukemia" },
                { boxLabel: "Lymphoma", inputValue: "Lymphoma" },
                { boxLabel: "Myeloma", inputValue: "Myeloma" },
                { boxLabel: "Melanoma", inputValue: "Melanoma" },
                { boxLabel: "Non-melanoma skin cancer", inputValue: "Non-melanoma skin cancer" },
                { boxLabel: "All sarcoma (bone and soft tissue)", inputValue: "All sarcoma (bone and soft tissue)" },
                { boxLabel: "Malignant neoplasms (all or specify in the field below)", inputValue: "Malignant neoplasms (all or specify in the field below)" },
                { boxLabel: "Benign neoplasms (specify in the field below)", inputValue: "Benign neoplasms (specify in the field below)" },
                { boxLabel: "Other", inputValue: "Other" }
            ]
        }];

        this.callParent();

        Ext4.each(this.query('checkboxfield'), function(cb){
            cb.on('change', function(){
                this.setDirty(true);
            }, this);
        }, this);
    },

    getName : function() {
        return this.name;
    },

    resetOriginalValue : function() {
        this.setValue(this.originalValue);
    },

    clearValue : function() {
        Ext4.each(this.query('checkboxfield'), function(cb){
            cb.setValue(false);
        }, this);
    },

    setValue : function(value) {
        if (value)
        {
            var valArr = value.split("|");
            Ext4.each(this.query('checkboxfield'), function(cb){
                if (valArr.indexOf(cb.inputValue) > -1)
                    cb.setValue(true);
            }, this);

            this.setDirty(false);
        }
    },

    getValue : function() {
        var value = "";
        Ext4.each(this.query('checkboxfield'), function(cb){
            if (cb.getValue())
                value += (value.length > 0 ? "|" : "") + cb.inputValue;
        }, this);

        return value.length == 0 ? null : value;
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
        return true;
    },

    clearInvalid : function() {
        // not implemented since there isn't really an invalid state for this question type
    },

    setReadOnly : function(readOnly) {
        Ext4.each(this.query('checkboxfield'), function(cb){
            cb.setReadOnly(readOnly);
        }, this);
    }
});



