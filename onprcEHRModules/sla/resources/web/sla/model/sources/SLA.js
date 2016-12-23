/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This is the default metadata applied to records in SLA forms
 */

//Modified 3-5-2016  R. Blasa
EHR.model.DataModelManager.registerMetadata('SLA', {
    allQueries: {

    },
    byQuery: {
        'sla.census': {
            project: {
                allowBlank: false,
                xtype: 'onprc-projectfield' ,
                columnConfig: {
                    width: 250
                }

            },
             species: {
                allowBlank: false,
                xtype: 'onprc_Species' ,
                columnConfig: {
                    width: 100
                }
            },
            investigator: {
                allowBlank: false,
                columnConfig: {
                    width: 100
                }
            },

            cageSize: {
                allowBlank: false,
                xtype: 'onprc_CageSize' ,
                columnConfig: {
                    width: 150
                }
            },
            cagetype: {
                allowBlank: false,
                xtype: 'onprc_CageType' ,
                columnConfig: {
                    width: 150
                }
            },
            date: {
                xtype: 'xdatetime',
                editorConfig: {
                    dateFormat: 'Y-m-d',
                    timeFormat: 'H:i'
                },

                columnConfig: {
                    width: 200
                }
        },


            /* Added 3-12-2015 Blasa */
            room: {
                xtype: 'onprc_Roomfield',
                columnConfig: {
                    width: 200
                }
            },

         }
    }
});

