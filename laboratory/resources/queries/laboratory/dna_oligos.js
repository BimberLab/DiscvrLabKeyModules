/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// ================================================

var console = require("console");

console.log("** evaluating: " + this['javax.script.filename']);



function beforeBoth(row, errors) {
    if(row.sequence){
        //remove whitespace
        row.sequence = row.sequence.replace(/\s/g, '');
        row.sequence = row.sequence.toUpperCase();

        if(!row.sequence.match(/^[ATGCNRYSWKMBDHVN]+$/)){
            errors.sequence = ('Sequence can only contain valid bases: ATGCN or IUPAC bases: RYSWKMBDHV');
        }
    }
}


function beforeInsert(row, errors) {
    beforeBoth(row, errors);

}

function beforeUpdate(row, oldRow, errors) {
    beforeBoth(row, errors);

}


