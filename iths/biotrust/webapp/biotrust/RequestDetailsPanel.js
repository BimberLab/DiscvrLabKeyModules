/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('LABKEY.ext4.biotrust.BaseDetailPanel', {
    extend: 'Ext.form.Panel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            frame: false,
            border: false,
            width: 620,
            padding: 5,
            bodyStyle: 'background-color: transparent;'
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();
    },

    getTextLink : function(text, url) {
        return {
            xtype: 'container',
            width: 140,
            hidden: LABKEY.ActionURL.getParameter("_print") != null,
            html: LABKEY.Utils.textLink({text: text}),
            listeners: {
                scope: this,
                render : function(cmp) {
                    cmp.getEl().on('click', function(){
                        window.open(url, this.linkTarget ? this.linkTarget : '_blank');
                    }, this);
                }
            }
        };
    },

    getRowValue : function(row, varName) {
        // need to try both camel case and lowercase
        var value = row[varName];
        if (value == null)
            value = row[varName.toLowerCase()];

        return value;
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

Ext4.define('LABKEY.ext4.biotrust.RecordDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        // add header if in print mode
        if (LABKEY.ActionURL.getParameter("_print") != null)
        {
            this.add({
                xtype: 'label',
                html: '<span style="font-size: 20px;">' + this.status + ' Assessment Details</span>'
            })
        }

        this.add({
            xtype: 'fieldcontainer',
            layout: 'hbox',
            items: [{
                xtype: 'displayfield',
                labelStyle: 'font-style: italic;',
                fieldLabel: this.idFieldLabel || 'Request ID',
                labelWidth: this.idFieldLabel ? 25 : 75,
                value: this.recordId,
                width: this.width / 3 * 2
            },{
                xtype: 'displayfield',
                labelStyle: 'font-style: italic;',
                fieldLabel: 'Date of Request',
                labelWidth: 115,
                value: Ext4.util.Format.date(this.submitted, 'm/d/Y'),
                width: this.width / 3
            }]
        });
    }
});

Ext4.define('LABKEY.ext4.biotrust.InvestigatorDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        LABKEY.Query.executeSql({
            schemaName: 'biotrust',
            sql: 'SELECT StudyRegistrationDetails.RowId, '
                + 'COALESCE(Contacts.FirstName, Users.FirstName) AS FirstName, '
                + 'COALESCE(Users.LastName, Contacts.LastName) AS LastName, '
                + 'COALESCE(IFDEFINED(Users.Institution), Contacts.Institution) AS Institution, '
                + 'COALESCE(Users.Email, Contacts.Email) AS Email, '
                + 'COALESCE(IFDEFINED(Users.PhoneNumber), Contacts.PhoneNumber) AS PhoneNumber, '
                + 'FROM StudyRegistrationDetails '
                + 'LEFT JOIN core.Users ON Users.UserId = StudyRegistrationDetails.PrincipalInvestigator AND StudyRegistrationDetails.PrincipalInvestigatorInSystem = true '
                + 'LEFT JOIN biotrust.Contacts ON Contacts.RowId = StudyRegistrationDetails.PrincipalInvestigator AND StudyRegistrationDetails.PrincipalInvestigatorInSystem = false '
                + 'WHERE StudyRegistrationDetails.RowId = ' + this.studyId + ' AND StudyRegistrationDetails.SurveyDesignId = ' + this.studySurveyDesignId,
            containerPath: this.containerId,
            success: function(data) {
                if (data.rows.length == 1)
                {
                    var row = data.rows[0];
                    var name = null;
                    if (this.getRowValue(row, "FirstName"))
                        name = this.getRowValue(row, "FirstName");
                    if (this.getRowValue(row, "LastName"))
                        name += " " + this.getRowValue(row, "LastName");

                    this.add({
                        xtype: 'fieldset',
                        title: 'Investigator Information',
                        collapsible: false,
                        anchor: '99%',
                        items: [{
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;'
                            },
                            items: [{
                                width: 250,
                                labelWidth: 50,
                                fieldLabel: 'Name',
                                value: Ext4.util.Format.htmlEncode(name)
                            },{
                                labelWidth: 75,
                                fieldLabel: 'Institution',
                                value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "Institution"))
                            }]
                        },{
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;',
                                labelWidth: 75,
                                width: this.width / 2
                            },
                            items: [{
                                width: 250,
                                labelWidth: 50,
                                fieldLabel: 'Email',
                                value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "Email"))
                            },{
                                labelWidth: 75,
                                fieldLabel: 'Phone #',
                                value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "PhoneNumber"))
                            }]
                        }]
                    });
                }
            },
            failure: this.onFailure,
            scope: this
        });
    }
});

Ext4.define('LABKEY.ext4.biotrust.StudyRegistrationDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.getStudyInformation();
    },

    getStudyInformation : function() {

        LABKEY.Query.selectRows({
            schemaName: 'biotrust',
            queryName: 'StudyRegistrationDetails',
            filterArray: [
                LABKEY.Filter.create('RowId', this.studyId),
                LABKEY.Filter.create('SurveyDesignId', this.studySurveyDesignId)
            ],
            containerPath: this.containerId,
            success: function(data) {
                if (data.rows.length == 1)
                {
                    var row = data.rows[0];

                    this.add({
                        xtype: 'fieldset',
                        title: 'Study Information',
                        collapsible: false,
                        anchor: '99%',
                        defaults: {
                            labelStyle: 'font-style: italic;',
                            labelWidth: 150
                        },
                        items: [{
                            xtype: 'displayfield',
                            fieldLabel: 'Study Name',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "SurveyLabel"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'Study Description',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "StudyDescription"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'IRB Approval Status',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "IrbApprovalStatus"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'IRB File Number',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "IrbFileNumber"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'Protocol Number',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "ProtocolNumber"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'IRB Expiration Date',
                            value: Ext4.util.Format.date(this.getRowValue(row, "IrbExpirationDate"), 'm/d/Y')
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'Reviewing IRB',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "ReviewingIrb") == 'Other'
                                        ? this.getRowValue(row, "ReviewingIrb") + " - " + this.getRowValue(row, "ReviewingIrbOther")
                                        : this.getRowValue(row, "ReviewingIrb"))
                        },{
                            xtype: 'displayfield',
                            fieldLabel: 'Have Funding',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "HaveFunding"))
                        },{
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            items: [
                                this.getTextLink('view details', LABKEY.ActionURL.buildURL('biotrust', 'updateStudyRegistration', this.containerId, {
                                    rowId : this.studyId, srcURL : window.location
                                })),
                                this.getTextLink('view document set', LABKEY.ActionURL.buildURL('biotrust', 'manageDocumentSet', this.containerId, {
                                    rowId : this.getRowValue(row, "SurveyRowId"), srcURL : window.location
                                }))
                            ]
                        }]
                    });
                }
            },
            failure: this.onFailure,
            scope: this
        });
    }
});

Ext4.define('LABKEY.ext4.biotrust.SampleRequestDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.getSampleRequestInformation();
    },

    getSampleRequestInformation : function() {

        LABKEY.Query.selectRows({
            schemaName: 'biotrust',
            queryName: 'SampleRequestTissueRecords',
            filterArray: [LABKEY.Filter.create('RowId', this.tissueId)],
            containerPath: this.containerId,
            success: function(data) {
                if (data.rows.length == 1)
                {
                    var row = data.rows[0];

                    var items = [];

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Type of request',
                        value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "RequestTypeDisplay") || this.getRowValue(row, "RequestType"))
                    });

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Collection Start Date',
                        value: this.getRowValue(row, 'CollectionStartASAP') ? "ASAP" : Ext4.util.Format.date(this.getRowValue(row, "CollectionStartDate"), 'm/d/Y')
                    });
                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Collection End Date',
                        value: this.getRowValue(row, 'CollectionEndOngoing') ? "Ongoing collection or N/A" : Ext4.util.Format.date(this.getRowValue(row, "CollectionEndDate"), 'm/d/Y')
                    });

                    if (this.getRowValue(row, "RequestType") == 'TissueSample')
                    {
                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Tissue Type',
                            value: this.getRowValue(row, 'TissueTypeOther')
                                    ? Ext4.util.Format.htmlEncode(this.getRowValue(row, 'TissueTypeOther'))
                                    : Ext4.util.Format.htmlEncode(this.getRowValue(row, 'TissueType'))
                        });
                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Anatomical Site',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'AnatomicalSite'))
                        });
                    }
                    if (this.getRowValue(row, "RequestType") == 'BloodSample')
                    {
                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Tube Type',
                            value: this.getRowValue(row, 'TubeTypeOther')
                                ? Ext4.util.Format.htmlEncode(this.getRowValue(row, 'TubeTypeOther'))
                                : Ext4.util.Format.htmlEncode(this.getRowValue(row, 'TubeType'))
                        });
                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Type of Blood Samples',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'BloodSampleType'))
                        });
                        items.push({
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;',
                                width: this.width / 2
                            },
                            items: [
                                {
                                    fieldLabel: 'Minimum Volume/donor',
                                    labelWidth: 150,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'MinimumSize'))
                                },
                                {
                                    fieldLabel: 'Units',
                                    labelWidth: 50,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'MinimumSizeUnits'))
                                }
                            ]
                        });
                        items.push({
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;',
                                width: this.width / 2
                            },
                            items: [
                                {
                                    fieldLabel: 'Preferred Volume/donor',
                                    labelWidth: 150,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'PreferredSize'))
                                },
                                {
                                    fieldLabel: 'Units',
                                    labelWidth: 50,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'PreferredSizeUnits'))
                                }
                            ]
                        });
                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Hold At',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'HoldAtLocation') == 'Other'
                                        ? this.getRowValue(row, 'HoldAtLocation') + " - " + this.getRowValue(row, 'HoldAtLocationOther')
                                        : this.getRowValue(row, 'HoldAtLocation'))
                        });
                    }
                    if (this.getRowValue(row, "RequestType") == 'TissueSample')
                    {
                        items.push({
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;',
                                width: this.width / 2
                            },
                            items: [
                                {
                                    fieldLabel: 'Minimum Size',
                                    labelWidth: 150,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'MinimumSize'))
                                },
                                {
                                    fieldLabel: 'Units',
                                    labelWidth: 50,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'MinimumSizeUnits'))
                                }
                            ]
                        });
                        items.push({
                            xtype: 'fieldcontainer',
                            layout: 'hbox',
                            fieldLabel: '',
                            defaults: {
                                xtype: 'displayfield',
                                labelStyle: 'font-style: italic;',
                                width: this.width / 2
                            },
                            items: [
                                {
                                    fieldLabel: 'Preferred Size',
                                    labelWidth: 150,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'PreferredSize'))
                                },
                                {
                                    fieldLabel: 'Units',
                                    labelWidth: 50,
                                    value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'PreferredSizeUnits'))
                                }
                            ]
                        });

                        items.push({
                            xtype: 'displayfield',
                            fieldLabel: 'Preservation',
                            value: Ext4.util.Format.htmlEncode(this.getRowValue(row, 'Preservation'))
                        });
                    }

                    this.getProtocolDocuments();

                    items.push(this.getTextLink('view details', LABKEY.ActionURL.buildURL('biotrust', 'updateSampleRequest', this.containerId, {
                        rowId: this.sampleId, sectionTitle: this.requestType == 'BloodSample' ? 'Blood Samples' : 'Tissue Samples'
                    })));

                    this.add({
                        xtype: 'fieldset',
                        title: 'Sample Request Information',
                        collapsible: false,
                        anchor: '99%',
                        defaults: {
                            labelStyle: 'font-style: italic;',
                            labelWidth: 150
                        },
                        items: items
                    });
                }
            },
            failure: this.onFailure,
            scope: this
        });
    },

    getProtocolDocuments : function() {
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'getDocumentSet.api'),
            method  : 'POST',
            jsonData: {
                rowId : this.sampleId,
                ownerType: 'samplerequest',
                documentTypeName: 'Specimen Processing Protocol' + (this.requestType == 'BloodSample' ? " (Blood)" : " (Tissue)")
            },
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                var links = [];
                Ext4.each(o.documentSet, function(doc){
                    links.push("<a href='" + doc.downloadURL + "'>" + doc.name + "</a>");
                });

                if (links.length > 0)
                {
                    var fieldSet = this.down('fieldset[title=Sample Request Information]');
                    fieldSet.insert(
                        (LABKEY.ActionURL.getParameter("_print") ? fieldSet.items.length : fieldSet.items.length-1),
                        {
                            xtype: 'displayfield',
                            fieldLabel: 'Preservation Protocol',
                            value: links.join(",<br/>")
                        }
                    );
                }
            },
            scope   : this
        });
    }
});

Ext4.define('LABKEY.ext4.biotrust.GeneralPopulationRequirementsDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.getEligibilityInformation();
    },

    getEligibilityInformation : function() {
        LABKEY.Query.selectRows({
            schemaName: 'biotrust',
            queryName: 'SampleRequestDetails',
            filterArray: [LABKEY.Filter.create('RowId', this.sampleId)],
            containerPath: this.containerId,
            success: function(data) {
                if (data.rows.length == 1)
                {
                    var row = data.rows[0];

                    var items = [];

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: '# of Specimen Donors',
                        value: this.getRowValue(row, "TotalSpecimenDonorsNA") ? "N/A" : Ext4.util.Format.htmlEncode(this.getRowValue(row, "TotalSpecimenDonors"))
                    });

                    items.push(this.getVariableFieldContainer(row, 'GenderRequirements', 'Gender Req.'));
                    items.push(this.getVariableFieldContainer(row, 'AgeRequirements', 'Age Req.'));
                    items.push(this.getVariableFieldContainer(row, 'RaceRequirements', 'Race/Ethnicity  Req.'));
                    items.push(this.getVariableFieldContainer(row, 'HistologicalDiagnosisRequirements', 'Primary Hist. Diagnosis'));
                    items.push(this.getVariableFieldContainer(row, 'BioMarkerRequirements', 'BioMarker Req.'));
                    items.push(this.getVariableFieldContainer(row, 'OtherRequirements', 'Other Req.'));

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Other Exclusion Criteria',
                        value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "ExclusionOtherCriteria"))
                    });

                    items.push(this.getTextLink('view details', LABKEY.ActionURL.buildURL('biotrust', 'updateSampleRequest', this.containerId, {
                        rowId: this.sampleId, sectionTitle: 'General Population Requirements'
                    })));

                    this.add({
                        xtype: 'fieldset',
                        title: 'General Population Requirements',
                        collapsible: false,
                        anchor: '99%',
                        defaults: {
                            labelStyle: 'font-style: italic;',
                            labelWidth: 150
                        },
                        items: items
                    });
              }
            },
            failure: this.onFailure,
            scope: this
        });
    },

    getVariableFieldContainer : function(row, varName, label) {
       if (this.getRowValue(row, varName) != null)
       {
           return {
               xtype: 'displayfield',
               fieldLabel: label,
               value: Ext4.util.Format.htmlEncode(this.getRowValue(row, varName))
           };
       }
       else
           return null;
    }
});

Ext4.define('LABKEY.ext4.biotrust.SamplePickupDetailPanel', {
    extend: 'LABKEY.ext4.biotrust.BaseDetailPanel',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.getSamplePickupInformation();
    },

    getSamplePickupInformation : function() {
        LABKEY.Query.selectRows({
            schemaName: 'biotrust',
            queryName: 'SampleRequestDetails',
            filterArray: [LABKEY.Filter.create('RowId', this.sampleId)],
            columns: 'SamplePickupNotes,SamplePickupContactEmail,SamplePickupContactDisplayName,SamplePickupContactPhoneNumber,'
                + 'SamplePickupSecondaryContactEmail,SamplePickupSecondaryContactDisplayName,SamplePickupSecondaryContactPhoneNumber',
            maxRows: 1,
            containerPath: this.containerId,
            success: function(data) {
                if (data.rows.length == 1)
                {
                    var row = data.rows[0];

                    var items = [];

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Primary Contact',
                        value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupContactDisplayName"))
                    });
                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: ' ',
                        labelSeparator: '',
                        hidden: this.getRowValue(row, "SamplePickupContactEmail") == null,
                        value: 'email: ' + Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupContactEmail"))
                            + ', phone: ' + Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupContactPhoneNumber") || "NA")
                    });

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Secondary Contact',
                        value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupSecondaryContactDisplayName"))
                    });
                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: ' ',
                        labelSeparator: '',
                        hidden: this.getRowValue(row, "SamplePickupSecondaryContactEmail") == null,
                        value: 'email: ' + Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupSecondaryContactEmail"))
                                + ', phone: ' + Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupSecondaryContactPhoneNumber") || "NA")
                    });

                    items.push({
                        xtype: 'displayfield',
                        fieldLabel: 'Notes',
                        value: Ext4.util.Format.htmlEncode(this.getRowValue(row, "SamplePickupNotes"))
                    });

                    items.push(this.getTextLink('view details', LABKEY.ActionURL.buildURL('biotrust', 'updateSampleRequest', this.containerId, {
                        rowId: this.sampleId, sectionTitle: 'Sample Pickup Information'
                    })));

                    this.add({
                        xtype: 'fieldset',
                        title: 'Sample Pickup Information',
                        collapsible: false,
                        defaults: {
                            labelStyle: 'font-style: italic;',
                            labelWidth: 150
                        },
                        items: items
                    });
                }
            },
            failure: this.onFailure,
            scope: this
        });
    },

    getVariableFieldContainer : function(row, varName, label) {
        if (this.getRowValue(row, varName) != null)
        {
            return {
                xtype: 'displayfield',
                fieldLabel: label,
                value: Ext4.util.Format.htmlEncode(this.getRowValue(row, varName))
            };
        }
        else
            return null;
    }
});