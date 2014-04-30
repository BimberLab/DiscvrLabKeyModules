/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);

var LABKEY = require("labkey");
var billingHelper = new org.labkey.onprc_billing.query.BillingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.ON_BECOME_PUBLIC, 'ehr', 'projectAccountHistory', function(helper, errors, row, oldRow){
    if (row.project && !row.enddate && !helper.isValidateOnly()){
        //TODO: deactivate other active rows?
    }
});