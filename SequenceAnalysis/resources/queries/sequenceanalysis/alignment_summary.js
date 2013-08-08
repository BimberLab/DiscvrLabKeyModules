/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

console.log("** evaluating: " + this['javax.script.filename']);

function beforeDelete(row, errors){
    org.labkey.sequenceanalysis.SequenceAnalysisManager.cascadeDelete(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id, 'sequenceanalysis', 'alignment_summary_junction', 'alignment_id', row.rowid);
}