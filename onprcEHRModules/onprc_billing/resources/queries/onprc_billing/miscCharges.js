/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);


function onInit(event, helper){
    helper.setScriptOptions({
        allowAnyId: true,
        allowDeadIds: true,
        allowDatesInDistantPast: true
    });
}

EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.ON_BECOME_PUBLIC, 'onprc_billing', 'miscCharges', function(scriptErrors, helper, row, oldRow){
    if (!helper.isETL() && row){
        //force this date to match the current date
        row.billingDate = new Date();

        if (!row.project && !row.debitedaccount){
            EHR.Server.Utils.addError(scriptErrors, 'project', 'Must provide either project or alias', 'ERROR');
            EHR.Server.Utils.addError(scriptErrors, 'debitedaccount', 'Must provide either project or alias', 'ERROR');
        }
    }
});
