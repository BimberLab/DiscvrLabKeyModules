/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var LABKEY = require("labkey");

var triggerHelper = new org.labkey.sequenceanalysis.query.SequenceTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function afterDelete(row, errors){
    if (row.dataid){
        triggerHelper.deleteData(row.dataid);
    }
}