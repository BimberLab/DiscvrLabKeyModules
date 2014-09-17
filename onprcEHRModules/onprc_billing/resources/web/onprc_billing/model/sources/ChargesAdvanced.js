/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
EHR.model.DataModelManager.registerMetadata('ChargesAdvanced', {
    allQueries: {

    },
    byQuery: {
        'onprc_billing.miscCharges': {
            unitcost: {
                hidden: false,
                editorConfig: {
                    decimalPrecision: 2
                }
            },
            debitedaccount: {
                hidden: false,
                lookups: false
            },
            chargetype: {
                allowBlank: true,
                hidden: false
            }
        }
    }
});