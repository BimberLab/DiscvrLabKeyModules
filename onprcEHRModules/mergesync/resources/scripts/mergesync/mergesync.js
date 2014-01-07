/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

exports.init = function(EHR){
    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.COMPLETE, 'study', 'Clinpath Runs', function(event, errors, helper){
        console.log('merge handler called!');
        var rows = helper.getRows();
        console.log(rows.length);

    });
}