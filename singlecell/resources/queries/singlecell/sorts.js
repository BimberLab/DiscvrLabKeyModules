var console = require("console");
var LABKEY = require("labkey");
var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'singlecell', 'sorts');
var wellHelper = org.labkey.singlecell.ImportHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'sorts');

var wellMap = wellHelper.getInitialWells();

function beforeInsert(row, errors){
    beforeUpsert(row, null, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, oldRow, errors);
}

var rowIdx = -1;

function beforeUpsert(row, oldRow, errors){
    if (row.well){
        row.well = row.well.toUpperCase();
    }

    if (['TNF+', 'TNF Pos', 'CD69/TNF', 'CD69+/TNF+', 'CD69-Pos/TNF-Pos', 'CD69/TNFa', 'TNF+/CD69+'].indexOf(row.population) !== -1){
        row.population = 'TNF-Pos';
    }
    else if (['TNF-', 'CD69-/TNF-', 'TNF Neg', 'CD69-Neg/TNF-Neg'].indexOf(row.population) !== -1){
        row.population = 'TNF-Neg';
    }
    else if (['Bulk CD8', 'Bulk CD8 T-cells', 'Bulk-CD8', 'CD8+', 'CD8', 'CD8s'].indexOf(row.population) !== -1){
        row.population = 'Bulk CD8s';
    }
    else if (['CD8-CD69-Pos', 'CD69-Pos/TNF-Neg', 'TNF-/CD69+', 'CD69+', 'CD69+/TNF-'].indexOf(row.population) !== -1){
        row.population = 'CD69-Pos';
    }

    //Naive cells
    if (row.population && row.population.match(/ï/)){
        row.population = row.population.replace(/ï/g, 'i');
    }

    //Tetramer/spaces:
    if (row.population && row.population.match(/ Tet$/)){
        row.population = row.population.replace(/ /g, '-');
    }

    //check for duplicate plate/well
    oldRow = oldRow || {};
    var rowId = row.rowId || oldRow.rowId || rowIdx;
    rowIdx--;

    var lookupFields = ['sampleId'];

    //for 10x-style pooled expts, support 'pool' as a special-case for well name
    var well = row.well || oldRow.well || '';
    if ('pool' !== well.toLowerCase()) {
        var wellArr = [(row.plateId || oldRow.plateId), well];
        var wellKey = wellArr.join('<>').toUpperCase();
        if (wellMap[wellKey] && wellMap[wellKey] !== rowId) {
            errors.well = 'Duplicate entry for plate/well: ' + wellArr.join('/');
        }
        else {
            wellMap[wellKey] = rowId;
        }

        lookupFields.push('well');
    }

    for (var i=0;i<lookupFields.length;i++){
        var f = lookupFields[i];
        var val = row[f];
        if (!LABKEY.ExtAdapter.isEmpty(val)){
            var normalizedVal = helper.getLookupValue(val, f);

            if (LABKEY.ExtAdapter.isEmpty(normalizedVal)){
                errors[f] = ['Unknown value for field: ' + f + '. Value was: ' + val];
            }
            else {
                row[f] = normalizedVal;  //cache value for purpose of normalizing case
            }
        }
    }
}