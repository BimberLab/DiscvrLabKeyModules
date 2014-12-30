/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

console.log("** evaluating: " + this['javax.script.filename']);

function afterDelete(row, errors){
    if (row.objectid) {
        console.log('cascade deleting children of jbrowse database: ' + row.objectid);
        org.labkey.jbrowse.model.Database.onDatabaseDelete(LABKEY.Security.currentContainer.id, row.objectid, true);
    }
}