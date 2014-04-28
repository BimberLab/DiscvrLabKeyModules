/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('Laboratory.Utils');

Laboratory.Utils = new function(){
    return {
        toTitleCase: function(str){
            return str.replace(/\w\S*/g, function(txt){return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();});
        },

        /**
         * The purpose of this helper is to provide a listing of all items to display in the laboratory module navigation pages.  It draws from
         * assays, sample sets, and other other NavItems that have been registered with LaboratoryService.
         * @param [config.types] Optional.
         * @param [config.includeHidden] Optional. If true, non-visible items will be included.
         * @param config.success Success callback.  Will be passed a single object as an argument with the following properties
         * <li>data</li>
         * <li>samples</li>
         * <li>settings</li>
         * <li>misc</li>
         * <li>reports</li>
         * @param config.failure Error callback
         * @param config.scope Scope of callbacks
         * @private
         */
        getDataItems: function(config){
            config = config || {};

            var params = {};
            if (config.types)
                params.types = config.types;
            if (config.includeHidden)
                params.includeAll = config.includeHidden;

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('laboratory', 'getDataItems', config.containerPath, params),
                method : 'GET',
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure
                }),
                success: function(response){
                    var json = LABKEY.ExtAdapter.decode(response.responseText);
                    if (config.success)
                        config.success.call((config.scope || this), json);
                }
            }

            return Ext4.Ajax.request(requestConfig);
        },

        /**
         * A helper to return the row count(s) from an array of queries.  It is designed to assist with the creation of
         * overview or summary UI. The primary advantage is that it provides a single callback when all operations are complete.
         * @constructor
         * @param {object} config The config object.
         * @param {array} config.queries An array of queries to count.  Each item should have the following:
         * <li>schemaName: The name of the schema
         * <li>queryName: The name of the query.  If you supply only schemaName and queryName, a basic SQL statement along the lines of 'select * from schemaName.queryName' will be generated and used.
         * <li>sql: If you need a more complex SQL statement to be used for this summary (for example, if you need filters applied), you can
         * @param {function} config.success The success callback.  It will be passed the same object passed to config.queries, except a 'total' property will  be appended to each query.
         * @param {function} config.failure The failure callback.  Note: this will be called for a failure on each individual query, as opposed to one failure callback for the entire set, so it could potentially be called more than once.
         * @param {object} config.scope The scope of the callbacks.
         */
        getQueryCounts: function(config){
            var multi = new LABKEY.MultiRequest();
            config.scope = config.scope || this;

            Ext4.each(config.queries, function(query){
                var sql = query.sql || 'select count(*) as total from ' + query.schemaName + '.' + '"' + query.queryName + '"';
                multi.add(LABKEY.Query.executeSql, {
                    schemaName: query.schemaName,
                    sql: sql,
                    scope: this,
                    success: function(data){
                        if(!data || !data.rows)
                            return;

                        query.total = data.rows[0].total;
                    },
                    failure: config.failure
                });
            }, this);

            multi.send(callback, this);

            function callback(){
                if(Ext4.isFunction(config.success))
                    config.success.call(config.scope, config.queries)
            }
        },

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
        },

        /**
         * Returns the set of import methods for a given assay.  Each import method determines how the data will be parsed and displayed, such as
         * supporting multiple instruments for a single assay.
         * @param config A config object
         * @param {string} config.assayId The Id of the assay
         * @param {function} config.success The success callback.  It will be passed the same object passed to config.queries, except a 'total' property will  be appended to each query.
         * @param {function} config.failure The failure callback.  Note: this will be called for a failure on each individual query, as opposed to one failure callback for the entire set, so it could potentially be called more than once.
         * @param {object} config.scope The scope of the callbacks.
         */
        getImportMethods: function(config){
            var params = {};
            if (config.assayId)
                params.assayId = config.assayId;

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('laboratory', 'getImportMethods', config.containerPath, params),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LDK.Utils.getErrorCallback()
            }

            return Ext4.Ajax.request(requestConfig);
        },

        /**
         * Returns a list of navItems summarizing data saved in this container
         * @param {Array} [providers] Optional.  An array of dataprovider names to include.  If null, all providers will be returned.
         * @param {function} success The success callback
         * @param {function} failure The failure callback
         * @param {Object} scope The scope of the callbacks
         * @return {Mixed}
         */
        getDataSummary: function(config){
            config = config || {};
            var params = {};
            if (config.providers)
                params.providers = config.providers

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('laboratory', 'getDataSummary', config.containerPath, params),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure
                })
            }

            return Ext4.Ajax.request(requestConfig);
        },

        /**
         * Returns a list of navItems summarizing data saved in this container for the provided subjectId
         * @param {Array} subjectIds An array of subjectId to query
         * @param {Array} [providers] Optional.  An array of dataprovider names to include.  If null, all providers will be returned.
         * @param {function} success The success callback
         * @param {function} failure The failure callback
         * @param {Object} scope The scope of the callbacks
         */
        getSubjectIdSummary: function(config){
            config = config || {};

            if (!config.subjectIds){
                Ext4.Msg.alert('Error', 'Must provide an array of subjectIds');
                return;
            }

            var params = {};
            if (config.providers)
                params.providers = config.providers;

            params.subjectIds = config.subjectIds;

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('laboratory', 'getSubjectIdSummary', config.containerPath, params),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure
                })
            }

            return Ext4.Ajax.request(requestConfig);
        },

        /**
         * An API to send a message to the maintainers of this module, without publically exposing the contact email.
         * @param {String} config.email The reply email for this message
         * @param {String} config.message The message to appear in the body of the email
         */
        sendSupportMessage: function(config){
            if (!config || !config.message || !config.email){
                alert('Must provide a reply email and message');
            }

            Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('laboratory', 'supportMessage'),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : {
                    email: config.email,
                    message: config.message
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * A helper to retrieve the default set of columns used in the assay import
         * excel template.  Will return an empty list if templates are not supported
         * @param config.assayId The Id of the assay
         * @param config.importMethod The import method to be used
         * @param config.success Success callback
         * @param config.scope Scope for the callback
         */
        getAssayImportHeaders: function(config){
            if (!config.assayId || !config.importMethod){
                alert('Must provide the assayId and name of importMethod');
                return;
            }

            return Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('laboratory', 'getAssayImportHeaders', config.containerPath),
                params: {
                    protocol: config.assayId,
                    importMethod: config.importMethod
                },
                method : 'POST',
                scope: config.scope,
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure,
                    scope: config.scope
                }),
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope)
            });
        },

        /**
         * A helper to retrieve metadata about an assay using a single asyc call.  It will call
         * LABKEY.Assay.getById() to return the definition, and call Laboratory.Utils.getImportMethods()
         * @param config.assayId The Id of the assay
         * @param config.success Success callback
         * @param config.scope Scope for the callback
         */
        getAssayDetails: function(config){
            var ret = {};
            ret.domains = {};

            var multi = new LABKEY.MultiRequest();
            multi.add(LABKEY.Assay.getById, {
                id: config.assayId,
                success: function(results){
                    if(!results.length)
                        return;

                    ret.assayDesign = results[0];
                    ret.domains.Batch = results[0].domains[results[0].name + ' Batch Fields'];
                    ret.domains.Run = results[0].domains[results[0].name + ' Run Fields'];
                    ret.domains.Results = results[0].domains[results[0].name + ' Result Fields'];

                    //unfortunately the assay def doesnt include Run or Comments in the field list.  presumably this is b/c they're not part of the domain?
                    //we add them back here:
                    ret.domains.Run.unshift({
                        name: 'comments',
                        caption: 'Run Description',
                        jsonType: 'string'
                    });
                    ret.domains.Run.unshift({
                        name: 'Name',
                        caption: 'Run Name',
                        jsonType: 'string'
                    });
                },
                failure: LDK.Utils.getErrorCallback(),
                scope: this
            });
            multi.add(Laboratory.Utils.getImportMethods, {
                assayId: config.assayId,
                success: function(results){
                    Ext4.apply(ret, results.providers[0]);
                },
                failure: LDK.Utils.getErrorCallback(),
                scope: this
            });
            multi.send(function(){
                var multi2 = new LABKEY.MultiRequest();
                multi2.add(LABKEY.Query.getQueryDetails, {
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: ret.assayDesign.protocolSchemaName,
                    queryName: 'Batches',
                    success: function(results){
                        ret.domains.Batch = results.columns;
                    },
                    failure: LDK.Utils.getErrorCallback(),
                    scope: this
                });
                multi2.add(LABKEY.Query.getQueryDetails, {
                    schemaName: ret.assayDesign.protocolSchemaName,
                    queryName: 'Runs',
                    success: function(results){
                        ret.domains.Run = results.columns;
                    },
                    failure: LDK.Utils.getErrorCallback(),
                    scope: this
                });
                multi2.add(LABKEY.Query.getQueryDetails, {
                    schemaName: ret.assayDesign.protocolSchemaName,
                    queryName: 'Data',
                    success: function(results){
                        ret.domains.Results = results.columns;
                    },
                    failure: LDK.Utils.getErrorCallback(),
                    scope: this
                });
                multi2.send(function(){
                    if(config.success)
                        config.success.call((config.scope || this), ret);
                }, this);
            }, this);
        },

        /**
         * This will generate and run SQL to group a query by the passed fields and return the count of each group
         * @param config
         * @param config.schemaName
         * @param config.queryName
         * @param config.queryName
         * @param config.fieldNames
         * @param config.success
         * @param config.failure
         */
        getQuerySummaryCounts: function(config){
            var sql = 'SELECT ';
            var delim = '';
            Ext4.each(config.fieldNames, function(fn, idx){
                sql += delim + 't."' + fn + '"';
                delim = ', '
            }, this);
            sql += ', count(*) as _total';
            sql += ' FROM "' + config.schemaName + '"."' + config.queryName + '" t';
            sql += ' GROUP BY ';
            delim = '';
            Ext4.each(config.fieldNames, function(fn, idx){
                sql += delim + 't."' + fn + '"';
                delim = ', '
            }, this);
            console.log(sql);

            LABKEY.Query.executeSql({
                requiredVersion: 9.1,
                schemaName: config.schemaName,
                sql: sql,
                success: config.success,
                failure: config.failure
            });
        },

        /**
         * API to save the default visibility of NavItems in the current folder
         * @private
         * @param config
         * @param config.items A map of the property names and boolean indicating default visibility of that item.  The property name should be provided in the response from getDataItems()
         * @param [config.success]
         * @param [config.failure]
         * @param [config.scope]
         */
        saveItemVisibility: function(config){
            if (!config.items){
                alert('Must provide an array of items to save');
                return;
            }

            return Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('laboratory', 'setItemVisibility', config.containerPath),
                params: {
                    jsonData: Ext4.encode(config.items)
                },
                method : 'POST',
                scope: config.scope,
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure,
                    scope: config.scope
                }),
                success: config.success
            });
        },

        /**
         * Retrieve the list of demographics sources registered for this container
         * @param {boolean} config.includeTotals
         * @param config.success Success callback
         * @param config.scope Scope for the callback
         */
        getDemographicsSources: function(config){
            config = config || {};
            var params = {};

            if (config.includeTotals)
                params.includeTotals = config.includeTotals;

            if (config.includeSiteSummary)
                params.includeSiteSummary = config.includeSiteSummary;

            return Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('laboratory', 'getDemographicsSources', config.containerPath),
                method : 'POST',
                params: params,
                scope: config.scope,
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure,
                    scope: config.scope
                }),
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope)
            });
        },

        /**
         * Retrieve the list of additonal data sources registered for this container
         * @param {boolean} config.includeTotals
         * @param config.success Success callback
         * @param config.scope Scope for the callback
         * @param config.includeSiteSummary Includes a site-wide summary.  Only available for site admins
         */
        getAdditionalDataSources: function(config){
            config = config || {};
            var params = {};

            if (config.includeTotals)
                params.includeTotals = config.includeTotals;

            if (config.includeSiteSummary)
                params.includeSiteSummary = config.includeSiteSummary;

            return Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('laboratory', 'getAdditionalDataSources', config.containerPath),
                method : 'POST',
                params: params,
                scope: config.scope,
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure,
                    scope: config.scope
                }),
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope)
            });
        },

        getDefaultWorkbookFolderType: function(){
            var ctx = LABKEY.getModuleContext('laboratory');
            LDK.Assert.assertNotEmpty('laboratory module context not found', ctx);
            return ctx ? ctx['DefaultWorkbookFolderType'] : null;
        },

        isLaboratoryAdmin: function(){
            var ctx = LABKEY.getModuleContext('laboratory');
            LDK.Assert.assertNotEmpty('Laboratory context not loaded', ctx);

            return ctx.isLaboratoryAdmin;
        }
    }
}