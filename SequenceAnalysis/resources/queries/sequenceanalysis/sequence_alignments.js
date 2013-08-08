/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

console.log("** evaluating: " + this['javax.script.filename']);

function beforeDelete(row, errors){
    var sql = 'nt_snp_id IN (select rowid from sequenceanalysis.nt_snps where alignment_id = ?)';
    org.labkey.sequenceanalysis.SequenceAnalysisManager.cascadeDelete(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id, 'sequenceanalysis', 'aa_snps', 'nt_snp_id', row.rowid, sql);

    org.labkey.sequenceanalysis.SequenceAnalysisManager.cascadeDelete(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id, 'sequenceanalysis', 'nt_snps', 'alignment_id', row.rowid);
}