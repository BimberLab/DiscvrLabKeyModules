/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");
//var Ext = require("Ext").Ext;

var Laboratory = {};
Laboratory.Server = {};

exports.Laboratory = Laboratory;

/**
 * A server-side class of helpers, similar to the client-side Laboratory.Utils.
 * @class
 */
Laboratory.Server.Utils = new function(){
    return {
        /**
         * Returns either the current container if not a workbook, or the parent container if the current container is a workbook.
         * The purpose of this method is for code to be able to reliably target the parent of the workbook/child no matter what the current container is.
         * This is useful because querying the parent provides a larger scope, which is often what the user expects.
         * @returns {String} The path of the container
         */
        getQueryContainerPath: function(){
            return LABKEY.Security.currentContainer.type == 'workbook' ?
                LABKEY.Security.currentContainer.parentPath : LABKEY.Security.currentContainer.path;
        },

        /**
         * Similar to getQueryContainerPath, except it returns the entityId
         * @returns {String} The entityId of the container
         */
        getQueryContainerId: function(){
            return LABKEY.Security.currentContainer.type == 'workbook' ?
                LABKEY.Security.currentContainer.parentId : LABKEY.Security.currentContainer.id;
        }
    }
}
