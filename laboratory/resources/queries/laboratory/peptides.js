/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");

console.log("** evaluating: " + this['javax.script.filename']);

function beforeInsert(row, errors) {
    beforeBoth(row, errors);

}

function beforeUpdate(row, oldRow, errors) {
    beforeBoth(row, errors);

}


function beforeBoth(row, errors) {
    if(row.sequence){
        //remove whitespace
        row.sequence = row.sequence.replace(/\s/g, '');
        row.sequence = row.sequence.toUpperCase();

        //populate short name
        row.short_name = row.sequence[0] + row.sequence[row.sequence.length -1 ] + row.sequence.length;

        //maybe enforce ATGCN?  allow IUPAC?
        if(!row.sequence.match(/^[*ARNDCQEGHILKMFPSTWYVX]+$/)){
            errors.sequence = ('Sequence can only contain valid amino acid characters: ARNDCQEGHILKMFPSTWYV*: ' + '[' + row.sequence + ']')
        }

        function findChar(c){
            var match = row.sequence.match(new RegExp(c, 'g'));
            return match ? match.length : 0;
        }

        //round to 1 decimal
        row.mw = Math.round(10*(
            71.1 * findChar('A') +
            156.2 * findChar('R') +
            114.1 * findChar('N') +
            115.1 * findChar('D') +
            103.1 * findChar('C') +
            128.1 * findChar('Q') +
            129.1 * findChar('E') +
            57.1 * findChar('G') +
            137.1 * findChar('H') +
            113.1 * findChar('I') +
            113.1 * findChar('L') +
            128.2 * findChar('K') +
            131.2 * findChar('M') +
            147.2 * findChar('F') +
            97.1 * findChar('P') +
            87.1 * findChar('S') +
            101.1 * findChar('T') +
            186.2 * findChar('W') +
            163.2 * findChar('Y') +
            99.1 * findChar('V') +
            132.61 * findChar('B') +
            146.64 * findChar('Z') +
            18
        ))/10 || 0;
    }

    if(row.name){
        //row.name = row.name.replace(/\s+/g, '_');

        //enforce no pipe character in name
        if(row.name.match(/\|/)){
            errors.name = [('Sequence name cannot contain the pipe ("|") character')];
        }

        //enforce no slashes in name
        if(row.name.match(/[\/|\\]/)){
            errors.name = [('Sequence name cannot contain slashes ("/")')];
        }
    }
    else {
        if(row.sequence){
            row.name = row.sequence.charAt(0) + row.sequence.charAt(row.sequence.length - 1) + row.sequence.length;
        }
    }
}