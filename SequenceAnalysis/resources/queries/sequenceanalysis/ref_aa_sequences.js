/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var Ext = require("Ext").Ext;

console.log("** evaluating: " + this['javax.script.filename']);


function beforeUpsert(row, errors) {
    if(row.sequence){
        //remove whitespace
        row.sequence = row.sequence.replace(/\s/g, '');
        row.sequence = row.sequence.toUpperCase();

        //maybe enforce ATGCN?  allow IUPAC?
        if(!row.sequence.match(/^[*ARNDCQEGHILKMFPSTWYVX:]+$/)){
            addError(errors, 'sequence', 'Sequence can only contain valid amino acid characters: ARNDCQEGHILKMFPSTWYV*');
        }
    }

    if(row.name){
        //trim name
        row.name = row.name.replace(/^\s+|\s+$/g, '')

        //enforce no pipe character in name
        if(row.name.match(/\|/)){
            addError(errors, 'name', 'Sequence name cannot contain the pipe ("|") character');
        }

        //enforce no slashes in name
        if(row.name.match(/[\/|\\]/)){
            addError(errors, 'name', 'Sequence name cannot contain slashes ("/" or "\\")');
        }
    }

    if(row.exons && row.sequence){
        var length = 0;
        var exonArray = row.exons.split(';');
        Ext.each(exonArray, function(exon){
            exon = exon.split('-');
            length += (Number(exon[1]) - Number(exon[0]) + 1);
        }, this);
        length = length / 3;
        if(length != row.sequence.length){
            addError(errors, 'sequence', 'The length of the sequence (' + row.sequence.length + ') does not match the exon boundaries (' + length + ')');
        }
    }

    if(row.exons && row.exons.length){
        var exonArray = row.exons.split(';');
        var coordinates = exonArray[0].split('-');
        if(coordinates.length == 2)
            row.start_location = coordinates[0];
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

