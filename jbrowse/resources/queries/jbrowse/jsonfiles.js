/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");

function complete(){
    // A little heavy-handed, but ensures we get this right:
    console.log('Clearing JBrowseFieldUtils cache after update');
    org.labkey.jbrowse.JBrowseFieldUtils.clearCache();
}