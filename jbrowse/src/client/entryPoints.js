/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'jbrowse',
        title: 'JBrowse Genome Browser',
        permission: 'read',
        template: 'app',
        path: './src/client/JBrowse/Browser'
    },
    {
        name: 'search',
        title: 'JBrowse Search',
        permission: 'read',
        template: 'app',
        path: './src/client/JBrowse/Browser/Search'
    }]
};
