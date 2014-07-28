<%
/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %><%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
        return resources;
    }
%>
<div id="returnURL"></div>
<div id="specimenResults"></div>

<script type="text/javascript">
    var requestHandler = function(dataRegion){

/*
        var filters = dataRegion.getUserFilter();
        var urlParams = {};

        for(var i = 0; i < filters.length; i ++){
            var filter = filters[i];
            if(filter.fieldKey === 'repositoryShortName'){
                urlParams.repository = filter.value;
            }
        }
        urlParams.params = filters;

        dataRegion.getSelected({
            success: function(data){
                urlParams.rows = data.selected.join(';');
                window.location = LABKEY.ActionURL.buildURL('vbdsearch', 'specimenRequest', null, urlParams);
            },
            failure: function(errorInfo){
                console.error(errorInfo);
            }
        });
*/
        var url = LABKEY.ActionURL.buildURL('vbdsearch', 'specimenRequest');
        window.location = url + '?' + dataRegion.getSearchString();
    };

    Ext.onReady(function(){
        var params = LABKEY.ActionURL.getParameters();
        var filters = [];
        var returnURLDiv = Ext.query('#returnURL')[0];
        var returnURL = params.returnURL ? params.returnURL : LABKEY.ActionURL.buildURL('vbdsearch', 'facetedSpecimenSearch');

        for(var columnName in params){
            if(columnName != 'returnURL' && params.hasOwnProperty(columnName)){
                filters.push(LABKEY.Filter.create(columnName, params[columnName]));
            }
        }


        returnURLDiv.innerHTML = '<a href="' + returnURL + '"><h3>&larr; Return to Faceted Search</h3></a>';

        var qwp = new LABKEY.QueryWebPart({
            renderTo: 'specimenResults',
            schemaName: 'lists',
            queryName: 'Samples',
            dataRegionName : 'vbd',
            buttonBar: {
                position: 'top',
                includeStandardButtons: false,
                items: [
                    {text: 'Contact Repository', handler: requestHandler},
                    LABKEY.QueryWebPart.standardButtons.exportRows
                ]
            },
            showRecordSelectors: false,
            showDetailsColumn : false,
            showUpdateColumn : false,
            showPagination: true,
            showSurroundingBorder   : false,
            removeableFilters: filters
        });
    });
</script>