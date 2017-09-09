/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('Laboratory.Assay');

Laboratory.Assay = new function(){
    return {
        parseAssayName: function(schemaName, queryName){
            //different parsing for old/new style assay names
            var match = queryName.match(new RegExp('^(.*)'+' (Runs|Data|Batches)', 'i'));
            var providerName = '';
            var protocolName = '';
            if (match){
                protocolName = match[1];
            }
            else {
                match = schemaName.split(/\./g);
                providerName = match[1];
                protocolName = match[2];
            }

            return {
                schemaName: schemaName,
                queryName: queryName,
                protocolName: protocolName,
                providerName: providerName
            }
        },

        viewBatchesBtn: function(dataRegion, sourceDomain){
            var assay = Laboratory.Assay.parseAssayName(dataRegion.schemaName, dataRegion.queryName);
            if(!assay.protocolName){
                alert('Improper Assay Name');
                return;
            }

            var params = {
                schemaName: assay.schemaName,
                'query.queryName': 'Batches'
            };

            window.location = LABKEY.ActionURL.buildURL("query", "executeQuery", null, params);

        },

        viewRunsBtn: function(dataRegion, sourceDomain){
            var assay = Laboratory.Assay.parseAssayName(dataRegion.schemaName, dataRegion.queryName);
            if(!assay.protocolName){
                alert('Improper Assay Name');
                return;
            }

            var params = {
                schemaName: assay.schemaName,
                'query.queryName': 'Runs'
            };

            var checked = dataRegion.getChecked();
            if(sourceDomain=='Batch' && checked.length)
                params['query.Batch/RowId~in'] = checked.join(';');

            window.location = LABKEY.ActionURL.buildURL("query", "executeQuery", null, params);

        },

        viewResultsBtn: function(dataRegion, sourceDomain){
            var assay = Laboratory.Assay.parseAssayName(dataRegion.schemaName, dataRegion.queryName);
            if(!assay.protocolName){
                alert('Improper Assay Name');
                return;
            }

            var params = {
                schemaName: assay.schemaName,
                'query.queryName': 'Data'
            };

            var checked = dataRegion.getChecked();
            if(sourceDomain=='Runs' && checked.length)
                params['query.Run/RowId~in'] = checked.join(';');

            window.location = LABKEY.ActionURL.buildURL("query", "executeQuery", null, params);

        },

        manageAssayBtn: function(dataRegion, sourceDomain){
            var assay = Laboratory.Assay.parseAssayName(dataRegion.schemaName, dataRegion.queryName);
            if(!assay.protocolName){
                alert('Improper Assay Name');
                return;
            }

            LABKEY.Assay.getByName({
                name: assay.protocolName,
                scope: this,
                success: function(assay){
                    var assay = assay[0];
                    window.location = LABKEY.ActionURL.buildURL("assay", "designer", null, {rowId: assay.id, providerName: assay.name});
                }
            });
        }
    }
}