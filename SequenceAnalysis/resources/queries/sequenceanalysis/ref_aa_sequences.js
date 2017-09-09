/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");
var LABKEY = require("labkey");
var Ext = require("Ext").Ext;

console.log("** evaluating: " + this['javax.script.filename']);

var triggerHelper = new org.labkey.sequenceanalysis.query.SequenceTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeUpsert(row, errors) {
    //TODO: translate name -> rowId if string used:

    if (row.sequence){
        //remove whitespace
        row.sequence = row.sequence.replace(/\s/g, '');
        row.sequence = row.sequence.toUpperCase();

        //maybe enforce ATGCN?  allow IUPAC?
        if (!row.sequence.match(/^[*ARNDCQEGHILKMFPSTWYVX:]+$/)){
            addError(errors, 'sequence', 'Sequence can only contain valid amino acid characters: ARNDCQEGHILKMFPSTWYV*');
        }
    }

    if (row.name){
        //trim name
        row.name = row.name.replace(/^\s+|\s+$/g, '')

        //enforce no pipe character in name
        if (row.name.match(/\|/)){
            addError(errors, 'name', 'Sequence name cannot contain the pipe ("|") character');
        }

        //enforce no slashes in name
        if (row.name.match(/[\\]/)){
            addError(errors, 'name', 'Sequence name cannot contain backslashes');
        }
    }

    var exonArray = [];
    var lengthFromExons = 0;
    if (row.exons) {
        exonArray = row.exons.split(';');
        for (var i = 0;i<exonArray.length; i++) {
            var exon = exonArray[i].split('-');
            if (exon.length != 2 || isNaN(exon[0]) || isNaN(exon[1])){
                addError(errors, 'exons', 'Improper exons: ' + row.exons);
                return;
            }

            exonArray[i] = [parseInt(exon[0]), parseInt(exon[1])];
            lengthFromExons += (exonArray[i][1] - exonArray[i][0] + 1);
        }
        lengthFromExons = lengthFromExons / 3;
    }

    //infer from coordinates:
    if (row.ref_nt_id && exonArray.length){
        row.isComplement = !!row.isComplement;
        var sequence = triggerHelper.extractAASequence(row.ref_nt_id, exonArray, row.isComplement);
        if (sequence && lengthFromExons != sequence.length){
            addError(errors, 'sequence', 'The length of the sequence (' + sequence.length + ') does not match the exon boundaries (' + lengthFromExons + ')');
            return;
        }

        row.sequence = sequence;
    }

    if (row.exons && row.sequence && lengthFromExons != row.sequence.length){
        addError(errors, 'sequence', 'The length of the sequence (' + row.sequence.length + ') does not match the exon boundaries (' + lengthFromExons + ')');
    }

    if (row.exons && row.exons.length){
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

