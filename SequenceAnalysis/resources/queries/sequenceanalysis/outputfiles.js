/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var LABKEY = require("labkey");

var triggerHelper = new org.labkey.sequenceanalysis.query.SequenceTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeDelete(row, errors){
    if (!this.extraContext.deleteFromServer) {
        errors._form = 'You cannot directly delete analyses.  To delete these records, use the delete button above the analysis grid.';
    }
}

function beforeInsert(row, errors){
    beforeUpsert(row, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, errors);
}

function beforeUpsert(row, errors) {
    if (row.dataid && typeof row.dataid == 'string') {
        row.dataid = triggerHelper.createExpData(row.dataid);
    }
}
