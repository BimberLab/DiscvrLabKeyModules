/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
EHR.model.DataModelManager.registerMetadata('Reversals', {
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
            creditedaccount: {
                hidden: false,
                lookups: false
            }
        }
    }
});