/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/* TODO: THIS IS NO LONGER IS USER FOR THE SAMPLE REQUEST WIZARD AND CAN LIKELY BE REMOVED */
Ext4.define('LABKEY.ext4.biotrust.LinkToDiscarded', {
    extend: 'Ext.form.field.Display',
    alias: 'widget.biotrust-linktodiscarded',

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        var params = { requestType: 'DiscardedBlood' };
        if (LABKEY.ActionURL.getParameter("studyId"))
            params['studyId'] = LABKEY.ActionURL.getParameter("studyId");

        var href = LABKEY.ActionURL.buildURL("biotrust", "updateSampleRequest", null, params);

        this.value = "<span style='font-style: italic;'>Are you looking to enter a sample request for discarded blood samples?</span> "
            + LABKEY.Utils.textLink({text: 'click here', href: href});

        this.callParent();
    }
});