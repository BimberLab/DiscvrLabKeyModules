/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// ================================================

var console = require("console");
var LABKEY = require("labkey");
var Utils = require("laboratory/Utils").Laboratory.Server.Utils;

var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'laboratory', 'samples');
var freezerHelper = org.labkey.laboratory.query.FreezerTriggerHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id);

console.log("** evaluating: " + this['javax.script.filename']);

function beforeInsert(row, errors){
    beforeUpsert(row, errors);
}

function beforeUpdate(row, oldRow, errors){
    //NOTE: this is designed to merge the old row into the new one.
    for (var prop in oldRow){
        if(!row.hasOwnProperty(prop) && LABKEY.ExtAdapter.isDefined(oldRow[prop])){
            row[prop] = oldRow[prop];
        }
    }

    beforeUpsert(row, errors);
}

function beforeUpsert(row, errors){
    var fields = ['quantity', 'row', 'cane', 'box', 'box_column', 'box_row', 'passage_number', 'parentsample'];
    var field;
    for (var i=0;i<fields.length;i++){
        field = fields[i];
        if(row[field] && Number(row[field]) < 0)
            errors[field] = 'Cannot have a negative value for ' + field;
    }

    if (!row.location && !row.freezer){
        errors.freezer = 'Must enter either a location or freezer';
        errors.location = 'Must enter either a location or freezer';
    }

    if (!row.samplename && !row.subjectid){
        errors.samplename = 'Must enter either a Sample Name or Subject Id';
        errors.subjectid = 'Must enter either a Sample Name or Subject Id';
    }

    if (row.sampledate && row.processdate){
        if (row.sampledate.getTime && row.processdate.getTime){
            if (row.sampledate.getTime() > row.processdate.getTime())
                errors.processdate = 'Processing date cannot be before the sample date.  Values are: [' + row.sampledate + '] and [' + row.processdate + ']';
        }
    }

//    var lookupFields = ['freezer', 'sampletype', 'additive', 'molecule_type'];
//    for (var i=0;i<lookupFields.length;i++){
//        var f = lookupFields[i];
//        var val = row[f];
//        if (!LABKEY.ExtAdapter.isEmpty(val)){
//            var normalizedVal = helper.getLookupValue(val, f);
//
//            if (LABKEY.ExtAdapter.isEmpty(normalizedVal))
//                errors[f] = ['Unknown value for field: ' + f + '. Value was: ' + val];
//            else
//                row[f] = normalizedVal;  //cache value for purpose of normalizing case
//        }
//    }

    //verify uniqueness within container
    if (!row.dateremoved && !LABKEY.ExtAdapter.isEmpty(row.freezer) && !LABKEY.ExtAdapter.isEmpty(row.box_row) && !LABKEY.ExtAdapter.isEmpty(row.box_column)){
        if (freezerHelper.isSamplePresent(n(row.location), n(row.freezer), n(row.cane), n(row.box), n(row.box_row), n(row.box_column), n(row.rowid))){
            var key = freezerHelper.getKey(n(row.location), n(row.freezer), n(row.cane), n(row.box), n(row.box_row), n(row.box_column));
            errors.location = 'There is already an active sample in this location: [' + key + ']';
        }
    }
}

//when passing undefined to java code, it is converted to a string, so we ensure we have NULL instead
function n(val){
    return LABKEY.ExtAdapter.isEmpty(val) ? null : val;
}