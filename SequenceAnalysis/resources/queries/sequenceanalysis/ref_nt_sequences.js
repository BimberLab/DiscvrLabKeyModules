/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var LABKEY = require("labkey");

console.log("** evaluating: " + this['javax.script.filename']);

var triggerHelper = new org.labkey.sequenceanalysis.query.SequenceTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeUpsert(row, errors) {
    if (row.name){
        row.name = row.name.replace(/\s+/g, '_');

        //enforce no pipe character in name
        if(row.name.match(/\|/)){
            addError(errors, 'name', 'Sequence name cannot contain the pipe ("|") character: ' + row.name);
        }

        //enforce no slashes in name
        if(row.name.match(/[\/|\\]/)){
            addError(errors, 'name', 'Sequence name cannot contain slashes ("/" or "\\"): ' + row.name);
        }
    }
}

function beforeInsert(row, errors) {
    beforeUpsert(row, errors);
}

function beforeUpdate(row, oldRow, errors) {
    beforeUpsert(row, errors);
}

//NOTE: sequence is no longer stored in the DB, but provide a path for API to add it anyway
function afterInsert(row, errors) {
    afterUpsert(row, errors);
}

function afterUpdate(row, oldRow, errors) {
    afterUpsert(row, errors);
}

function afterUpsert(row, oldRow, errors) {
    if (row.sequence) {
        triggerHelper.processSequence(row.rowid, row.sequence || null);
    }
}

function addError(errors, field, msg) {
    if(!errors[field])
        errors[field] = [];

    errors[field].push(msg);
}

function beforeDelete(row, errors){
    errors._form = 'You cannot directly delete reference sequences.  To delete these records, use the delete button above the sequences grid.';
}
