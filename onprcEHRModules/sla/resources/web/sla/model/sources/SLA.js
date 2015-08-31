/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This is the default metadata applied to records in SLA forms
 */

Ext4.define('SLA.form.field.RodentRoomField', {
    extend: 'EHR.form.field.RoomField',
    alias: 'widget.sla-rodentroomfield',

    getStoreFilterArray : function() {
        var ret = this.callParent();
        ret.push(LABKEY.Filter.create('housingType/value', 'Rodent Location'));
        return ret;
    }
});
EHR.model.DataModelManager.registerMetadata('SLA', {
    allQueries: {

    },
    byQuery: {
        'sla.census': {
            project: {
                allowBlank: false,
                xtype: 'ehr-projectfield'
            },
            date: {

            },
                 /* Added 3-12-2015 Blasa */
            room: {
                xtype: 'sla-rodentroomfield',
                columnConfig: {
                    width: 200
                }
            },
            cageSize: {
                columnConfig: {
                    width: 200
                }
            },
            cagetype: {
                columnConfig: {
                    width: 200
                }
            }

        }
    }
});

