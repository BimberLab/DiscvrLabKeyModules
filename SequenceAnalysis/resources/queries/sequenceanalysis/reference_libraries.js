/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

console.log("** evaluating: " + this['javax.script.filename']);

function afterDelete(row, errors){
    console.log('cascade deleting children of reference library: ' + row.rowid);
    org.labkey.sequenceanalysis.SequenceAnalysisManager.deleteReferenceLibrary(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id, row.rowid);
}