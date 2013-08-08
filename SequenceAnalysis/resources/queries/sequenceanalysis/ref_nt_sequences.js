/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");

console.log("** evaluating: " + this['javax.script.filename']);


function beforeUpsert(row, errors) {
    if(row.sequence){
        //remove whitespace
        row.sequence = row.sequence.replace(/\s/g, '');
        row.sequence = row.sequence.toUpperCase();

        //maybe allow IUPAC?
        if(!row.sequence.match(/^[ATGCN]+$/)){
            addError(errors, 'sequence', 'Sequence can only contain valid bases: ATGCN');
            console.log(row.name);
        }
    }

    if(row.name){
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

function addError(errors, field, msg) {
    if(!errors[field])
        errors[field] = [];

    errors[field].push(msg);
}

function beforeDelete(row, errors){
    errors._form = 'You cannot directly delete reference sequences.  To delete these records, use the delete button above the sequences grid.';
}
