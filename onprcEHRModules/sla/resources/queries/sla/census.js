/*
 * Copyright (c) 2010-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0

 */
//Created: 1-27-2017  R.Blasa
require("ehr/triggers").initScript(this);
var triggerHelper = new org.labkey.onprc_ehr.query.ONPRC_EHRTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function onInit(event, helper){
    helper.setScriptOptions({
        allowAnyId: true,
        requiresStatusRecalc: true


    });
}
//Validate to ensure users enter current date into input screen

EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.BEFORE_UPSERT, 'sla', 'census', function(helper, scriptErrors, row, oldRow){

    var OrgDate = (row.date.getYear() + 1900) + '-' + (row.date.getMonth() + 1) + '-' + row.date.getDate();
    var CurDate = (new Date().getYear() + 1900) + '-' + (new Date().getMonth() + 1) + '-' + new Date().getDate();

    //Prevent users from entering date entries that are not current date
    if (row.date && OrgDate != CurDate) {
    //if (row.date) {
        EHR.Server.Utils.addError(scriptErrors, 'date', 'It is imperative that you enter todays date.', 'WARN');
    }
});
