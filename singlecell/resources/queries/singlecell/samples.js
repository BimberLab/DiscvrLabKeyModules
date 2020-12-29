var console = require("console");
var LABKEY = require("labkey");
var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'singlecell', 'samples');

function beforeInsert(row, errors){
    beforeUpsert(row, null, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, oldRow, errors);
}

function beforeUpsert(row, oldRow, errors){
    if (['IE1', 'IE-1', 'IE1 Pool', 'IE-1 Pool', 'CMV IE-1 Pool', 'CMV IE1 Pool'].indexOf(row.stim) !== -1){
        row.stim = 'CMV IE-1';
    }
    else if (['IE2', 'IE-2', 'IE2 Pool', 'IE-2 Pool', 'CMV IE-2 Pool', 'CMV IE2 Pool'].indexOf(row.stim) !== -1){
        row.stim = 'CMV IE-2';
    }
    else if (['IE-1|IE-2', 'IE1/IE2', 'IE-1/IE-2', 'IE1IE2', 'CMV IE-2 Pool', 'CMV IE2 Pool'].indexOf(row.stim) !== -1){
        row.stim = 'IE-1/IE-2';
    }

    var lookupFields = ['stim'];
    for (var i=0;i<lookupFields.length;i++){
        var f = lookupFields[i];
        var val = row[f];
        if (!LABKEY.ExtAdapter.isEmpty(val)){
            var normalizedVal = helper.getLookupValue(val, f);

            if (!LABKEY.ExtAdapter.isEmpty(normalizedVal)){
                row[f] = normalizedVal;  //cache value for purpose of normalizing case
            }
        }
    }
}