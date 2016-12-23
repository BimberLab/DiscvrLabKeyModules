/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * @class
 * This is the panel that appears when hitting the 'Add Bulk' button on EHR grids.  It provides a popup to find the set of
 * distinct animal IDs based on room, case, etc.
 *
 * @cfg targetStore
 * @cfg formConfig
 */
Ext4.define('SLA.window.AddCensusWindow', {
    extend: 'Ext.window.Window',

    MAX_RECORDS: 350,

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Choose Location(s)',
            modal: true,
            closeAction: 'destroy',
            border: true,
            bodyStyle: 'padding:5px',
            width: 450,
            defaults: {
                width: 400,
                labelWidth: 140,
                border: false,
                bodyBorder: false
            },
            items: [{
                html: 'This helper is designed to quickly populate this form based on the most recent census data for the selected location(s).  You can leave any of the fields blank.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'form',
                defaults: {
                    border: false,
                    width: 400
                },
               /* items: [{
                    xtype: 'ehr-areafield',
                    multiSelect: false,
                    emptyText: '',
                    fieldLabel: 'Area',
                    itemId: 'areaField',
                    pairedWithRoomField: true,
                    getRoomField: function(){
                        return this.up('form').down('#roomField')
                    }
                },{  */
                    items: [{
                    xtype: 'ehr-roomfield',
                    multiSelect: true,
                    addAllSelector: true,
                    typeAhead: true,
                    emptyText: '',
                   // showOccupiedOnly: true,
                    fieldLabel: 'Room(s)',
                    itemId: 'roomField',
                    getStoreFilterArray: function(){
                         var ret = [
                              LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK),
                              LABKEY.Filter.create('housingType', 589, LABKEY.Filter.Types.EQUAL)
                              ];
                        return ret;
                     },
                    listeners: {
                        change: function (field) {
                            var areaField = field.up('panel').down('#areaField');
                            areaField.reset();
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    this.doQuery();
                }
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').hide();
                }
            }]
        });

        this.callParent(arguments);
    },

    addRecords: function(records){
        if (records.length && this.targetStore){
            records = Ext4.Array.unique(records);
            if (records.length > this.MAX_RECORDS){
                Ext4.Msg.alert('Error', 'Too many records were returned: ' + records.length);
                return;
            }

            var toAdd = [];
            Ext4.Array.forEach(records, function(s){
                toAdd.push(this.targetStore.createModel(s));
            }, this);

            if (toAdd.length) {
                this.targetStore.add(toAdd);
            }
        }

        if (Ext4.Msg.isVisible())
            Ext4.Msg.hide();

        this.close();
    },




    doQuery: function(){
        var room = this.down('#roomField').getValue();
        room = !room || Ext4.isArray(room) ? room : [room];

        //var filterArray = [LABKEY.Filter.create('room', room.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)];

        if (!Ext4.isEmpty(room))  {
            var filterArray = [LABKEY.Filter.create('room', room.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)];


        }
        if (filterArray.length == 0){
            Ext4.Msg.alert('Error', 'Must choose a location');
            return;
        }

        this.hide();
        Ext4.Msg.wait("Loading...");

        LABKEY.Query.selectRows({
            schemaName: 'sla',
            queryName:  'SLAMostRecentCensus',                     //'mostRecentCensus',
             sort:     'room,project/displayName,date desc',
            filterArray: filterArray,
            scope: this,
            success: this.onSuccess,
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onSuccess: function(results){
        if (!results.rows || !results.rows.length){
            Ext4.Msg.hide();
            Ext4.Msg.alert('', 'No matching records were found.');
            return;
        }

        var records = [];
        Ext4.Array.forEach(results.rows, function(row){
            if(row.project){
                //TODO: set correct rows

                var obj = {
                    date: row.Date,
                    project: row.project,
                    room: row.room,
                    species: row.Species,
                    cagetype: row.Cage_Type,
                    cageSize: row.Cage_Size,
                    countType: row.countType,
                    animalCount: row.Animal_Count,
                    cageCount: row.Cage_Count


                };

                records.push(obj);
            }
        }, this);

        this.addRecords(records);
    }
});

EHR.DataEntryUtils.registerGridButton('CENSUS_ADD', function(config){
    return Ext4.Object.merge({
        text: 'Add Previous Census',
        tooltip: 'Click to add results for a location, based on the previous census',
        handler: function(btn){
            var grid = btn.up('gridpanel');

            Ext4.create('SLA.window.AddCensusWindow', {
                targetStore: grid.store,
                formConfig: grid.formConfig
            }).show();
        }
    }, config);
});
