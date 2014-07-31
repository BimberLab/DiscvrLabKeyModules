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
                },
                columnConfig: {
                    //used to allow editing of unit cost
                    enforceUnitCost: false
                }
            },
            Id: {
                allowBlank: true,
                nullable: true
            },
            debitedaccount: {
                hidden: false,
                lookups: false
            },
            creditedaccount: {
                hidden: false,
                lookups: false
            },
            chargetype: {
                hidden: true,
                allowBlank: true
            },
            chargecategory: {
                hidden: false
            },
            project: {
                allowBlank: true
            },
            issueId: {
                hidden: false
            },
            sourceInvoicedItem: {
                hidden: false,
                userEditable: false,
                columnConfig: {
                    width: 180
                }
            },
            comment: {
                allowBlank: false
            }
        }
    }
});