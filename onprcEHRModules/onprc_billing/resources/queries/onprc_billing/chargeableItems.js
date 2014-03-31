/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var LABKEY = require("labkey");

var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'onprc_billing', 'chargeableItems');
var ehrHelper = org.labkey.ehr.utils.TriggerScriptHelper.create(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeDelete(row, errors){
    if (helper.verifyNotUsed('onprc_billing', 'invoicedItems', 'chargeid', row['rowid'])){
        addError(errors, 'name', 'Cannot delete row with ID: ' + row['rowid'] + ' because it is referenced by the table invoicedItems.  You should inactivate this item instead.');
    }

    if (helper.verifyNotUsed('onprc_billing', 'miscCharges', 'chargeid', row['rowid'], ehrHelper.getEHRStudyContainerPath())){
        addError(errors, 'name', 'Cannot delete row with ID: ' + row['rowid'] + ' because it is referenced by the table miscCharges.  You should inactivate this item instead.');
    }
}

function addError(errors, fieldName, msg){
    if (!errors[fieldName])
        errors[fieldName] = [];

    errors[fieldName].push(msg);
}
