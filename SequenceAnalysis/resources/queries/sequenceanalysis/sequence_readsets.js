var LABKEY = require("labkey");

var triggerHelper = new org.labkey.sequenceanalysis.query.SequenceTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeDelete(row, errors){
    if (!this.extraContext.deleteFromServer){
        errors._form = 'You cannot directly delete readsets.  To delete these records, use the delete button above the readset grid.';
    }
}

function afterInsert(row, errors) {
    if (row.sraAccessions) {
        triggerHelper.createReaddataForSra(row.rowid, row.sraAccessions);
    }
}