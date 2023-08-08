/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'jbrowseBrowser',
        title: 'JBrowse Genome Browser',
        permission: 'read',
        path: './src/client/JBrowse/Browser'
    },{
        name: 'jbrowse2SearchWebpart',
        title: 'JBrowse Search',
        permission: 'read',
        template: 'app',
        generateLib: true,
        path: './src/client/JBrowse/Search/webpart'
    },{
        name: 'variantSearch',
        title: 'Variant Search',
        permission: 'read',
        path: './src/client/JBrowse/VariantSearch'
    },{
        name: 'variantTable',
        title: 'Variant Table',
        permission: 'read',
        path: './src/client/JBrowse/VariantTable'
    }]
};
