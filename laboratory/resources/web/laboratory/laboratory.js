/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('Laboratory');

Laboratory.ITEM_CATEGORY = new function(){
    return {
        data: {
            name: 'data',
            label: 'Data'
        },
        samples: {
            name: 'samples',
            label: 'Samples'
        },
        misc: {
            name: 'misc',
            label: 'Miscellaneous'
        },
        reports: {
            name: 'reports',
            label: 'Reports'
        },
        tabbedReports: {
            name: 'tabbedReports',
            label: 'Tabbed Reports'
        },
        settings: {
            name: 'settings',
            label: 'Settings'
        }
    }
};
