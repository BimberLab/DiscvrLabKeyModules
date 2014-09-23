/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This is the default metadata applied to records in SLA forms
 */
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

            }
        }
    }
});
