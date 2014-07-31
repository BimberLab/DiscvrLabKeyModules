/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);

var onprcTriggerHelper = new org.labkey.onprc_ehr.query.ONPRC_EHRTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.BEFORE_UPSERT, 'onprc_billing', 'procedureFeeDefinition', function(helper, scriptErrors, row, oldRow){
    if (row.chargetype == 'Research Staff' && !row.assistingstaff && row.procedureid && onprcTriggerHelper.requiresAssistingStaff(row.procedureid)){
        EHR.Server.Utils.addError(scriptErrors, 'chargetype', 'If choosing Research Staff, you must enter the assisting staff.', 'ERROR');
    }

    if (row.assistingstaff && row.procedureid && !onprcTriggerHelper.requiresAssistingStaff(row.procedureid)){
        EHR.Server.Utils.addError(scriptErrors, 'assistingstaff', 'Only surgeries support assisting staff.', 'ERROR');
    }

    if (row.chargetype != 'Research Staff' && row.assistingstaff){
        EHR.Server.Utils.addError(scriptErrors, 'assistingstaff', 'This field will be ignored unless Research Staff is selected, and should be blank.', 'ERROR');
    }
});
