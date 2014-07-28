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
<%@ page import="java.util.LinkedHashSet" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>

<%
    // Get repository info from form here.
%>


<div id="requestContainer">
    <style type="text/css" scoped="true" >
        .specimenRequestForm {
        }

        .specimenRequestForm ul {
            list-style-type: none;
            margin: 0;
            padding: 0;
        }

        .specimenRequestForm textarea {
            width: 600px;
            height: 100px;
            margin-top: 5px;
        }

        .requestLabel {
            font-weight: bold;
        }

        #requestContainer {
            margin-bottom: 10px;
        }
    </style>
    <div class="specimenRequestForm">
        <ul>
            <li><span class="requestLabel">Respository: </span><span id='repositoryName'></span></li>
            <li><span class="requestLabel">Email: </span><span id="repositoryEmail"></span></li>
            <li><span class="requestLabel">From: </span><span id="userEmail"></span></li>
        </ul>
        <div id="filter-description-div"></div>
        <div class="requestLabel">Comments, additional instructions:</div>
        <textarea name="emailComments" id="emailComments"></textarea>
        <div>
            <a id="sendRequestButton" class="labkey-button">Send Request</a>
            <a id="cancelRequestButton" class="labkey-button">Cancel</a>
        </div>
    </div>
</div>

<div id="requestedSpecimens"></div>

<script type="text/javascript">
    Ext4.onReady(function ()
    {
        var params = LABKEY.ActionURL.getParameters();
        var selectedRows = params.rows;

        // for now, not allowing record selection
/*
        if (selectedRows == null)
        {
            // Some sort of error message.
        } else {
            var qwp = new LABKEY.QueryWebPart({
                renderTo: 'requestedSpecimens',
                title: 'Requested Specimens',
                schemaName: 'lists',
                queryName: 'Samples',
                buttonBar: {
                    position: 'none',
                    includeStandardButtons: false,
                    showDeleteButton: false
                },
                frame: 'none',
                showBorders: false,
                showSurroundingBorder: false,
                showDetailsColumn: false,
                showUpdateColumn: false,
                showRecordSelectors: false,
                quickChartDisabled: true,
                filters: [LABKEY.Filter.create('key', [selectedRows], LABKEY.Filter.Types.IN)]
            });
        }
*/

        var repositoryEmail;

        Ext4.get('userEmail').setHTML(LABKEY.user.email);
        Ext4.get('repositoryName').setHTML(params.repositoryShortName);
        LABKEY.Query.selectRows({
            schemaName: 'lists',
            queryName: 'Repositories',
            filterArray: [LABKEY.Filter.create('repositoryShortName', params.repositoryShortName, LABKEY.Filter.Types.EQUAL)],
            success: function(data){
                if(data.rows.length > 0){
                    repositoryEmail = data.rows[0].contactEmail;
                    if (repositoryEmail)
                        Ext4.get('repositoryEmail').setHTML(repositoryEmail);
                    else {

                        var btn = Ext4.get('sendRequestButton');
                        btn.removeCls('labkey-button');
                        btn.addCls('labkey-disabled-button');
                    }
                }
            }
        })

        // filter description div
        var tpl = new Ext4.XTemplate(
            '<span style="font-weight: bold;">Filters Applied</span>' +
            '<table style="cellpadding=5">',
                '<tpl for=".">',
                    '<tr>',
                        '<td style="padding-left: 10px;">{name}</td>',
                        '<td style="padding-left: 15px;">{value}</td>',
                    '</tr>',
                '</tpl>',
            '</table>'
        );

        var filters = [];
        var filtersData = [];
        for(var columnName in params){
            if(columnName != 'returnURL' && params.hasOwnProperty(columnName)){
                var filter = LABKEY.Filter.create(columnName, params[columnName]);
                filters.push(filter);

                if (filter.getValue())
                    filtersData.push({name : filter.getColumnName(), value : filter.getValue()});
            }
        }

        Ext4.create('Ext.Component', {
            renderTo: 'filter-description-div',
            frame   : false,
            padding : '10 0',
            border  : false,
            tpl     : tpl,
            data    : filtersData
        });

        var samples = new LABKEY.QueryWebPart({
            renderTo    : 'requestedSpecimens',
            schemaName  : 'lists',
            queryName   : 'Samples',
            buttonBar: {
                position: 'top',
                includeStandardButtons: false,
                items: [
                    LABKEY.QueryWebPart.standardButtons.exportRows
                ]
            },
            showRecordSelectors : false,
            showDetailsColumn   : false,
            showUpdateColumn    : false,
            showPagination      : true,
            showSurroundingBorder : false,
            filters : filters
        });

        var sendRequestBtn = Ext4.get('sendRequestButton');
        if(sendRequestBtn){
            sendRequestBtn.on('click', function(){
                if (repositoryEmail)
                {
                    var dataRegion = samples.getDataRegion();

                    //var dr = qwp.getDataRegion();
                    var requestData = {
                        //selectedRows: selectedRows,
                        //schemaName: dr.schemaName,
                        //queryName: dr.queryName,
                        repository      : params.repositoryShortName,
                        repositoryEmail : Ext4.get('repositoryEmail').getHTML(),
                        comments        : Ext4.get('emailComments').getValue(),
                        searchString    : dataRegion ? dataRegion.getSearchString() : ''
                    };

                    Ext4.Ajax.request({
                        method: 'POST',
                        url: LABKEY.ActionURL.buildURL('vbdsearch', 'requestSpecimens'),
                        params: requestData,
                        success: function(response){
                            var json = Ext4.JSON.decode(response.responseText);
                            console.log(json);

                            if (json.returnURL)
                            {
                                var msg = Ext4.create('Ext.window.Window', {
                                    title    : 'Success',
                                    html     : '<span class="labkey-message">The email message has been successfully sent.</span>',
                                    modal    : true,
                                    closable : false,
                                    bodyStyle: 'padding: 20px;'
                                });

                                msg.show();
                                this.redirect = new Ext4.util.DelayedTask(function(){
                                    msg.hide();
                                    window.location = json.returnURL;
                                }, this);
                                this.redirect.delay(2500);
                            }
                        }
                    });
                }
            }, this);
        }

        var cancelRequestBtn = Ext4.get('cancelRequestButton');
        if(cancelRequestBtn){
            cancelRequestBtn.on('click', function(){window.history.back();});
        }
    });
</script>
