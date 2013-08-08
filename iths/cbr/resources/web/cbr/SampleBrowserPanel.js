/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.ns('CBR.SampleBrowser');

/**
 * This is the primary panel used for the CBR sample browser.  It is responsible for overall layout and it generates
 * the store used to load/save data.
 * @class CBR.SampleBrowser.Panel
 */
Ext4.define('CBR.SampleBrowser.Panel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.cbr-samplebrowserpanel',

    initComponent: function(){
        this.initStore();

        Ext4.apply(this, {
            itemId: 'sampleBrowsePanel',
            width: 1400,
            bodyStyle: {
                padding: '5px'
            },
            defaults: {
                border: false,
                bodyStyle: {
                    padding: '5px'
                }
            },
            height: 1000,
            layout: {
                type: 'border'
            },
            items: this.getItemConfig()
        });

        this.callParent(arguments);
    },

    getItemConfig: function(){
        return [{
            region: 'center',
            style: {},
            bodyStyle: {},
            minSize: 400,
            xtype: 'cbr-detailspanel',
            autoScroll: true,
            itemId: 'detailsPanel',
            store: this.patientStore
        },{
            title: 'Patients',
            xtype: 'gridpanel',
            schemaName: 'cbr',
            queryName: 'eligibility',
            store: this.patientStore,
            columns: [{
                dataIndex: 'lastName',
                text: 'Last Name',
                width: 120
            },{
                dataIndex: 'firstName',
                text: 'First Name',
                width: 120
            },{
                dataIndex: 'schedDate',
                text: 'Sched Date',
                xtype:'datecolumn',
                format: 'm/d/y h:m A',
                width: 190
            },{
                dataIndex: 'repoName',
                text: 'Repository',
                width: 120
            },{
                dataIndex: 'eligible',
                text: 'Eligible',
                width: 80,
                renderer: function(data, meta, rec){
                    var total = 0, elig = 0;
                    var recs = rec.get('records');
                    var r;
                    for(var i=0;i<recs.length;i++){
                        r = recs[i];
                        total++;
                        if(r.get('rcIsPatElig') == 'y'){
                            elig++;
                        }
                    };

                    return elig + '/' + total;
                }
            }],
            region: 'west',
            width: 650,
            collapsible: true,
            split: true,
            listeners: {
                scope: this,
                selectionchange: this.onGridSelectionChange
            },
            viewConfig: {
                //conditional formatting:
                getRowClass: function(rec, idx, rowPrms, ds) {
                    return 'row-needsattention';
                }
            }
        },{
            title: 'Filter',
            xtype: 'cbr-filterpanel',
            region: 'north',
            collapsible: true,
            collapsed: false,
            split: true
        }];

    },

    onGridSelectionChange: function(selModel, recs, idx){
        var detailPanel = this.down('#detailsPanel');
        if(recs.length){
            detailPanel.loadRecord(recs[0]);
        }
        else {
            detailPanel.unloadRecord('No record selected');
        }
    },

    initStore: function(){
        this.eligibilityStore = Ext4.create('LABKEY.ext4.Store', {
            autoLoad: true,
            storeId: 'cbr-eligibility',
            schemaName: 'cbr',
            queryName: 'eligibility',
            maxRows: 2000,
            columns: 'eligId,' +
                'scheduleId/participantId,scheduleId/participantId/lastName,scheduleId/participantId/firstName,scheduleId/participantId/isIneligible,' +
                'scheduleId,scheduleId/schedDate,' +
                'studyId,studyId/cbrStudyId,studyId/studyTitle,' +
                'scheduleId/participantId/dob,scheduleId/participantId/amalgaID,scheduleId/participantId/gender,' +
                'studyId/repository,studyId/repository/repoName,' +
                'rcComments,rcIsPatElig,consentForm',
            remoteFilter: false,
            remoteSort: false,
            metadata: {
                eligId: {
                    editable: false,
                    shownInGrid: false
                },
                scheduleId: {
                    editable: false,
                    shownInGrid: false
                },
                'scheduleId/schedDate': {
                    editable: false,
                    shownInGrid: true,
                    fixedWidthCol: true,
                    columnConfig: {
                        width: 120
                    }
                },
                'scheduleId/participantId': {
                    editable: false,
                    shownInGrid: false
                },
                'scheduleId/participantId/lastName': {
                    editable: false,
                    fixedWidthCol: true,
                    columnConfig: {
                        width: 120
                    }
                },
                'scheduleId/participantId/firstName': {
                    editable: false,
                    fixedWidthCol: true,
                    columnConfig: {
                        width: 120
                    }
                },
                'scheduleId/participantid/mrn': {
                    editable: false,
                    shownInGrid: false
                },
                studyId: {
                    editable: false,
                    shownInGrid: false
                },
                'studyId/cbrStudyId': {
                    editable: false,
                    shownInGrid: false
                },
                'studyId/studyTitle': {
                    editable: false,
                    fixedWidthCol: true,
                    shownInGrid: false
                },
                'scheduleId/participantId/dob': {
                    editable: false,
                    shownInGrid: false
                },
                'scheduleId/participantId/amalgaID': {
                    editable: false,
                    shownInGrid: false
                },
                'scheduleId/participantId/gender': {
                    editable: false,
                    shownInGrid: false
                },
                'studyId/repository': {
                    editable: false,
                    shownInGrid: false
                },
                'studyId/repository/repoName': {
                    fixedWidthCol: true,
                    columnConfig: {
                        width: 150
                    }
                },
                rcComments: {
                    shownInGrid: false
                },
                rcIsPatElig: {
                    shownInGrid: true
                },
                consentForm: {
                    shownInGrid: true
                }
            },


            // TODO: figure out the proper set of filters.  date is probably good enough (ie. past records probably no longer matter).
            // might also be able to infer which records have no longer relevant, perhaps if they've already been reviewed
//            filterArray: [
//                LABKEY.Filter.create('', new Date().format('Y-m-d'), LABKEY.Filter.Type.DATE_GREATER_THAN_OR_EQUAL)
//            ]
            listeners: {
                load: this.onStoreLoad,
                scope: this
            }
        });

        this.patientStore = Ext4.create('Ext.data.Store', {
            proxy: {
                type: 'memory'
            },
            fields: ['key', 'lastName', 'firstName', 'gender', 'schedDate', 'repoName', 'repoId', 'eligible', 'participantId', 'scheduleId', 'records']

        });
    },

    onStoreLoad: function(store){
        var toAdd = [];
        var map = {};
        var records = store.getRange();
        for (var i=0;i<records.length;i++){
            var rec = records[i];
            var key = [rec.get('scheduleId/participantId'), rec.get('scheduleId')].join(';');
            if(!map[key]){
                var newRec = this.patientStore.create({
                    key: key,
                    firstName: rec.get('scheduleId/participantId/firstName'),
                    lastName: rec.get('scheduleId/participantId/lastName'),
                    gender: rec.get('scheduleId/participantId/gender'),
                    schedDate: rec.get('scheduleId/schedDate'),
                    repoId: rec.get('studyId/repository'),
                    repoName: rec.get('studyId/repository/repoName'),
                    eligible: rec.get('rcIsPatElig'),
                    participantId: rec.get('scheduleId/participantId'),
                    scheduleId: rec.get('scheduleId'),
                    records: [rec]

                });
                toAdd.push(newRec);
                map[key] = newRec;
            }
            else {
                map[key].get('records').push(rec);
            }
        }

        if(toAdd.length)
            this.patientStore.add(toAdd);
    }
});


/**
 * This class should render the details content for a given record.  The content may be context-dependent, depending on the type of record selected
 * @class CBR.SampleBrowser.DetailsPanel
 */
Ext4.define('CBR.SampleBrowser.DetailsPanel', {
    extend: 'Ext.tab.Panel',
    alias: 'widget.cbr-detailspanel',
    initComponent: function(){
        Ext4.apply(this, {
            xtype: 'tabpanel',
            autoScroll: true,
            defaults: {
                border: false
            },
            items: [{
                itemId: 'patientInfo',
                title: 'Patient Info',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'No patient selected'
                }]
            },{
                itemId: 'patientStudyList',
                title: 'Patient Study List',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'No patient selected'
                }]
            }]
        });

        this.callParent(arguments);

        if(this.activeRecord)
            this.loadRecord(this.activeRecord);
    },

    unloadRecord: function(msg){
        var item = {
            html: msg
        }

        var panel = this.down('#patientInfo');
        panel.removeAll();
        panel.add(item);

        panel = this.down('#patientStudyList');
        panel.removeAll();
        panel.add(item);

        this.activeRecord = null;
    },

    loadRecord: function(activeRecord){
        if(this.beforeLoad(activeRecord) === false){
            return;
        }

        this.unloadRecord('Loading...');

        this.pendingStores = 0;
        this.activeRecord = activeRecord;
        this.pendingStores++;
        this.scheduleStore = new LABKEY.ext4.Store({
            schemaName: 'cbr',
            queryName: 'schedule',
            columns: '*',
            filterArray: [LABKEY.Filter.create('scheduleid', activeRecord.get('scheduleId'), LABKEY.Filter.Types.EQUAL)],
            listeners: {
                scope: this,
                load: this.onStoreLoad
            },
            autoLoad: true
        });

        this.pendingStores++;
        this.participantStore = new LABKEY.ext4.Store({
            schemaName: 'cbr',
            queryName: 'participant',
            columns: '*',
            filterArray: [LABKEY.Filter.create('participantid', activeRecord.get('participantId'), LABKEY.Filter.Types.EQUAL)],
            listeners: {
                scope: this,
                load: this.onStoreLoad
            },
            autoLoad: true
        });
    },

    beforeLoad: function(activeRecord){
        if(!this.activeRecord)
            return true;

        var isDirty = this.down('#patientInfoForm').getForm().isDirty() || this.down('#patientStudyListForm').getForm().isDirty();
        if(isDirty){
            Ext4.Msg.confirm('Unsaved Changes', 'There are unsaved changes for this record.  If you would like to save them, click yes.  Otherwise they will be discarded.', function(btn){
                if(btn == 'yes'){
                    this.onUpdate(this.down('#patientInfoForm'));
                    this.onUpdate(this.down('#patientStudyListForm'));
                }
                else if (btn == 'no'){
                    this.activeRecord.reject();
                    this.activeRecord = null;
                }
                this.loadRecord(activeRecord);
            }, this);

            return false;
        }


        return true;
    },

    onStoreLoad: function(store){
        this.pendingStores--;

        if(!this.pendingStores){
            this.updatePatientDetails();
            this.updateStudyList();
        }
    },

    updatePatientDetails: function(){
        var participantRecord = this.participantStore.getAt(0);
        var scheduleRecord = this.scheduleStore.getAt(0);

        var items = [];
        items.push({
            html: '<h1>' + participantRecord.get('lastName') + ', ' + participantRecord.get('firstName') + '</h1>',
            style: 'padding-bottom: 10px;font-size:20;'
        });

        items.push({
            xtype: 'form',
            layout: 'hbox',
            defaults: {
                border: false,
                bodyStyle: 'padding: 5px;'
            },
            items: [{
                defaults: {
                    border: false,
                    xtype: 'displayfield',
                    labelStyle: 'text-decoration: underline;'
                },
                items: [{
                    fieldLabel: 'MRN',
                    value: participantRecord.get('mrn')
                },{
                    fieldLabel: 'Gender',
                    value: participantRecord.get('gender')
                },{
                    fieldLabel: 'Birth',
                    value: participantRecord.get('dob') ? participantRecord.get('dob').format('m/d/Y') : ''
                },{
                    fieldLabel: 'Age',
                    value: participantRecord.get('dob') ? this.getAge(participantRecord.get('dob')) : ''
                },{
                    fieldLabel: 'Language',
                    value: participantRecord.get('language')
                },{
                    fieldLabel: 'Patient Type',
                    value: scheduleRecord.get('patType')
                }]
            },{
                defaults: {
                    border: false,
                    xtype: 'displayfield',
                    labelStyle: 'text-decoration: underline;'
                },
                items: [{
                    //TODO: find value
                    fieldLabel: 'Cancer Type',
                    value: scheduleRecord.get('cancerType')
                },{
                    fieldLabel: 'Facility',
                    value: scheduleRecord.get('facility')
                },{
                    fieldLabel: 'Location',
                    value: scheduleRecord.get('location')
                },{
                    fieldLabel: 'Amalga Data Source',
                    value: scheduleRecord.get('amalgaDataSource')
                }]
            },{
                defaults: {
                    border: false,
                    xtype: 'displayfield',
                    labelStyle: 'text-decoration: underline;'
                },
                items: [{
                    fieldLabel: 'Sched Date',
                    value: scheduleRecord.get('schedDate') ? scheduleRecord.get('schedDate').format('m/d/Y h:m A') : ''
                },{
                    fieldLabel: 'Provider Name',
                    value: scheduleRecord.get('provider')
                },{
                    //TODO:
                    fieldLabel: 'Diagnosis',
                    value: scheduleRecord.get('diagnosis')
                },{
                    //TODO
                    fieldLabel: 'Procedure',
                    value: scheduleRecord.get('procedure')
                }]
            },{

            }]
        });

        items.push({
            xtype: 'form',
            itemId: 'patientInfoForm',
            style: 'padding: 5px;',
            listeners: {
                dirtychange: function(form, dirty){
                    form.owner.down('#updateBtn').setDisabled(!dirty);
                }
            },
            items: [{
                xtype: 'checkbox',
                style: 'padding-top: 10px;',
                boxLabel: 'Mark as INELIGIBLE for all studies',
                checked: participantRecord.get('isIneligible'),
                boundRecord: participantRecord,
                dataIndex: 'isIneligible'
            },{
                xtype: 'textarea',
                fieldLabel: 'RC Notes',
                labelAlign: 'top',
                width: 600,
                style: 'padding-top: 10px;',
                value: participantRecord.get('patNotes'),
                boundRecord: participantRecord,
                dataIndex: 'patNotes'
            },{
                xtype: 'displayfield',
                fieldLabel: 'Eligibility Summary',
                itemId: 'eligibilitySummary',
                labelAlign: 'top',
                value: this.getSummaryText()
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Update',
                itemId: 'updateBtn',
                disabled: true,
                scope: this,
                handler: function(btn){
                    var form = btn.up('form');
                    this.onUpdate(form);
                }
            }]
        })

        var panel = this.down('#patientInfo');
        panel.removeAll();
        panel.add(items);
    },

    getSummaryText: function(){
        var studyCount = this.activeRecord.get('records').length;
        var studies = {};
        var records = this.activeRecord.get('records');
        for (var i=0;i<records.length;i++){
            var r = records[i];
            if(!studies[r.get('studyId/repository/repoName')])
                studies[r.get('studyId/repository/repoName')] = 0;

            studies[r.get('studyId/repository/repoName')]++;
        }

        var summaryText = 'Considered for ' + studyCount + ' studies (';
        for (var repo in studies){
            summaryText += studies[repo] + ' ' + repo + ', ';
        }
        return summaryText.replace(/, $/g, ')');
    },

    getAge: function(dateString) {
        var today = new Date();
        var birthDate = new Date(dateString);
        var age = today.getFullYear() - birthDate.getFullYear();
        var m = today.getMonth() - birthDate.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
            age--;
        }
        return age;
    },

    onUpdate: function(form){
        var isDirty = false;
        var stores = [];
        form.cascade(function(item){
            if(item.boundRecord && item.isDirty()){
                stores.push(item.boundRecord.store);
                isDirty = true;
                //the store should auto-sync
                item.boundRecord.set(item.dataIndex, item.getValue());
                item.resetOriginalValue();
            }
        }, this);

        if(stores.length){
            stores = Ext4.unique(stores);
            Ext4.each(stores, function(store){
                store.sync();
            }, this);

            this.activeRecord.store.fireEvent('update', this.activeRecord.store, this.activeRecord, Ext4.data.Model.EDIT, []);
        }
    },

    updateStudyList: function(){
        var participantRecord = this.participantStore.getAt(0);
        var scheduleRecord = this.scheduleStore.getAt(0);

        var items = [];
        items.push({
            html: '<h1>' + participantRecord.get('lastName') + ', ' + participantRecord.get('firstName') + '</h1>',
            style: 'padding-bottom: 10px;font-size:20;'
        });

        items.push({
            xtype: 'form',
            layout: 'hbox',
            defaults: {
                border: false,
                bodyStyle: 'padding: 5px;'
            },
            items: [{
                defaults: {
                    border: false,
                    xtype: 'displayfield',
                    labelStyle: 'text-decoration: underline;'
                },
                items: [{
                    fieldLabel: 'MRN',
                    value: participantRecord.get('mrn')
                },{
                    fieldLabel: 'Gender',
                    value: participantRecord.get('gender')
                },{
                    fieldLabel: 'Age',
                    value: participantRecord.get('dob') ? this.getAge(participantRecord.get('dob')) : ''
                }]
            },{
                defaults: {
                    border: false,
                    xtype: 'displayfield',
                    labelStyle: 'text-decoration: underline;'
                },
                items: [{
                    //TODO: find value
                    fieldLabel: 'Cancer Type',
                    value: scheduleRecord.get('cancerType')
                }]
            }]
        });

        var eligibilityStore = this.up('#sampleBrowsePanel').eligibilityStore;
        var recordMap = {};
        var records = eligibilityStore.getRange();
        for (var i=0;i<records.length;i++){
            var r = records[i];
            if(r.get('scheduleId/participantId') == participantRecord.get('participantid')
                && r.get('scheduleId/schedDate').getTime() == scheduleRecord.get('schedDate').getTime()
            ){
                if(!recordMap[r.get('studyId/repository/repoName')]){
                    recordMap[r.get('studyId/repository/repoName')] = [];
                }
                recordMap[r.get('studyId/repository/repoName')].push(r);
            }
        }

        var comboStore = Ext4.create('Ext.data.Store', {
            fields: ['display', 'value'],
            proxy: {
                type: 'memory'
            }
        });
        comboStore.add({value: null, display: 'No Decision'});
        comboStore.add({value: 'n', display: 'No'});
        comboStore.add({value: 'y', display: 'Yes'});

        var studiesConfig = {
            xtype: 'form',
            itemId: 'patientStudyListForm',
            defaults: {
                border: false
            },
            listeners: {
                dirtychange: function(form, dirty){
                    form.owner.down('#updateBtn').setDisabled(!dirty);
                }
            },
            items: [],
            buttons: [{
                text: 'Update',
                itemId: 'updateBtn',
                disabled: true,
                scope: this,
                handler: function(btn){
                    var form = btn.up('form');
                    this.onUpdate(form);
                }
            }]
        };

        for (var repo in recordMap){
            var table = {
                title: '<b>' + repo + ':</b>',
                style: 'padding-top: 20px;',
                border: false,
                collapsible: true,
                layout: {
                    type: 'table',
                    columns: 3
                },
                items: [],
                defaults: {
                    border: false,
                    style: 'margin: 3px;'
                }
            };

            Ext4.each(recordMap[repo], function(r){
                table.items.push({
                    html: r.get('studyId/studyTitle'),
                    width: 300
                });
                table.items.push({
                    xtype: 'combo',
                    boundRecord: r,
                    displayField: 'display',
                    valueField: 'value',
                    queryMode: 'local',
                    store: comboStore,
                    emptyText: 'No Decision',
                    dataIndex: 'rcIsPatElig',
                    value: r.get('rcIsPatElig')
                });
                table.items.push({
                    xtype: 'textfield',
                    dataIndex: 'rcComments',
                    boundRecord: r,
                    value: r.get('rcComments')
                });
            }, this);
            studiesConfig.items.push(table);
        }
        items.push(studiesConfig);

        var panel = this.down('#patientStudyList');
        panel.removeAll();
        panel.add(items);
    }
});


/**
 * This class should render the filter UI that will filter the nodes in the project tree.
 * @class CBR.SampleBrowser.FilterPanel
 */
Ext4.define('CBR.SampleBrowser.FilterPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.cbr-filterpanel',
    initComponent: function(){
        Ext4.apply(this, {
            itemId: 'filterPanel',
            layout: 'hbox',
            defaults: {
                border: false
            },
            items: [{
                layout: 'column',
                width: 275,
                defaults: {
                    labelWidth: 100
                },
                items: [
                    this.getFieldConfig({
                        xtype: 'labkey-combo',
                        fieldLabel: 'Repository',
                        storeField: 'repoName',
                        getFilterFn: this.getStartsWithFilter('repoName'),
                        displayField: 'repoName',
                        valueField: 'repoName',
                        nullCaption: 'Show All',
                        emptyText: 'Show All',
                        store: {
                            type: 'labkey-store',
                            schemaName: 'cbr',
                            queryName: 'repository',
                            sort: 'repoName',
                            autoLoad: true,
                            listeners: {
                                load: function(store){
                                    //insert dummy record
                                    var rec = store.createModel({});
                                    store.insert(0, rec);
                                }
                            }
                        }
                    })
                ,
                    this.getFieldConfig({
                        xtype: 'datefield',
                        fieldLabel: 'Dates from',
                        storeField: 'schedDate',
                        getFilterFn: function(val){
                            return Ext4.create('Ext.util.Filter', {
                                filterFn: function(item) {
                                    return item.get('schedDate') >= val;
                                }
                            });
                        }
                    })
                ]
            },{
                layout: 'column',
                width: 475,
                items: [
                    this.getFieldConfig({
                        xtype: 'checkbox',
                        boxLabel: 'Show only patients with no decision made',
                        labelWidth: 40,
                        storeField: 'schedDate',
                        getFilterFn: function(val){
                            return Ext4.create('Ext.util.Filter', {
                                filterFn: function(item) {
                                    console.log('NYI');
                                    return true;
                                }
                            });
                        }
                    })
                ,
                    this.getFieldConfig({
                        xtype: 'datefield',
                        fieldLabel: 'to',
                        labelWidth: 40,
                        storeField: 'schedDate',
                        getFilterFn: function(val){
                            //add a day in order to account for date vs datetime
                            val.setDate(val.getDate()+1);
                            val.setHours(0);
                            val.setMinutes(0);

                            return Ext4.create('Ext.util.Filter', {
                                filterFn: function(item) {
                                    return item.get('schedDate') < val;
                                }
                            });
                        }
                    })
                ]
            },{
                layout: 'column',
                width: 175,
                items: [{
                    xtype: 'button',
                    text: 'Generate Report',
                    scope: this,
                    handler: function(btn){
                        alert('When you click this a report will be generated');
                    }
                }]
            }],
            buttonAlign: 'left',
            buttons: [{
                xtype: 'button',
                text: 'Update',
                scope: this,
                handler: this.updateFilters
            }]
        });

        this.callParent(arguments);
    },

    getFieldConfig: function(cfg){
        return Ext4.apply({
            style: 'margin-right: 5px'
            //TODO: consider automatically applying filters
//            ,listeners: {
//                scope: this,
//                delay: 400,
//                buffer: 200,
//                change: this.updateFilters
//            }
        }, cfg);
    },

    getStartsWithFilter: function(fieldName){
        return function(val){
            return Ext4.create('Ext.util.Filter', {
                filterFn: function(item) {
                    if(Ext4.isEmpty(val))
                        return true;
                    var re = new RegExp('^' + val, 'i');
                    return Ext4.isEmpty(item.get(fieldName)) ? false
                        : item.get(fieldName).toString().match(re);
                }
            })
        }
    },

    updateFilters: function(btn){
        var panel = btn.up('#filterPanel');
        var store = panel.up('#sampleBrowsePanel').patientStore;

        var filters = [];
        panel.cascade(function(item){
            if(item.storeField){
                var val = item.getValue();
                if(val){
                    filters.push(item.getFilterFn(val));
                }
            }
        }, this);

        store.clearFilter();
        store.filter(filters);
    }
});
