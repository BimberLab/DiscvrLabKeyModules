/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("biotrust/NWBioTrust.css");

Ext4.define('LABKEY.ext4.biotrust.CreateNewRequestPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : { type: 'hbox', align: 'stretch' },
            frame  : false,
            border : false,
            requestFormIds : [], // survey design IDs to create a panel for
            requestConfigs : [] // predefined request configurations to create a panel for
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.callParent();

        // create request form info panels for each configuration
        Ext4.each(this.requestConfigs, function(cfg){
            this.createRequestPanelFromConfig(cfg);
        }, this);

        // create request form info panels for each survey design id
        Ext4.each(this.requestFormIds, function(id){
            this.createRequestPanelFromId(id);
        }, this);
    },

    initPanelConfig : function() {
        return this.add({
            xtype : 'panel',
            frame: true,
            maxWidth : 450,
            style : 'margin: 7px;',
            bodyStyle : 'padding: 3px;',
            html : 'Loading...'
        });
    },

    createRequestPanelFromId : function(id) {

        var panel = this.initPanelConfig();

        // get the sample request form info (label, description, etc.) for the given id
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyTemplate.api'),
            method  : 'POST',
            jsonData: {rowId : id},
            success : function(resp){
                var o = Ext4.decode(resp.responseText);

                if (o.success && o.survey)
                {
                    // add the sample request form label and descriptoin to the html
                    var html = "<div class='request-title'>" + Ext4.util.Format.htmlEncode(o.survey.label) + "</div>";
                    if (o.survey.description != null)
                        html += "<div class='request-description'>" + Ext4.util.Format.htmlEncode(o.survey.description) + "</div>";

                    // add a Click Here link for creating a new sample request of this type
                    var href = LABKEY.ActionURL.buildURL('survey', 'updateSurvey', LABKEY.container.path, {
                        surveyDesignId : id, srcURL : window.location
                    });
                    html += "<a href='" + href + "' class='request-title'>Click Here</a>";

                    panel.update(html);
                }
                else
                    this.onFailure(panel, resp);
            },
            failure : function(resp) {
                this.onFailure(panel, resp);
            },
            scope   : this
        });
    },

    createRequestPanelFromConfig : function(cfg) {

        var panel = this.initPanelConfig();

        var html = "<div class='request-title'>" + cfg.title + "</div>";
        if (cfg.description != null)
            html += "<div class='request-description'>" + cfg.description + "</div>";
        if (cfg.href != null)
            html += "<a href='" + cfg.href + "' class='request-title'>Click Here</a>";

        panel.update(html);
    },

    onFailure : function(panel, resp) {
        var message = 'An unknown error has ocurred.';

        if (resp)
        {
            var error = Ext4.decode(resp.responseText);
            if (error.exception)
                message = error.exception;
            else if (error.errorInfo)
                message = error.errorInfo;
        }

        panel.update("<span class='labkey-error'>" + message + "</span>");
    }
});