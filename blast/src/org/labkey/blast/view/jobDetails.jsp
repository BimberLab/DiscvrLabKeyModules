<%
    /*
     * Copyright (c) 2011-2014 LabKey Corporation
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
<%@ page import="org.labkey.blast.model.BlastJob" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.apache.commons.io.IOUtils" %>
<%@ page import="java.io.BufferedReader" %>
<%@ page import="java.io.FileReader" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromModuleName("laboratory"));

        return resources;
    }
%>
<%
    JspView<BlastJob> me = (JspView) HttpView.currentView();
    BlastJob job = me.getModelBean();
    String renderTarget = "blast-" + ""; //TODO

%>

<div id=<%=q(renderTarget)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){
        Ext4.define('BLAST.panel.BlastDetailsPanel', {
            extend: 'Ext.panel.Panel',

            statics: {
                downloadRawData: function(jobId){
                    console.log(jobId);
                    var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                                    '<form method="POST" action="' + LABKEY.ActionURL.buildURL("blast", "downloadBlastResults") + '">' +
                                    '<input type="hidden" name="fileName" value="' + Ext4.htmlEncode('blastResults.txt') + '" />' +
                                    '<input type="hidden" name="jobId" value="' + jobId + '" />' +
                                    '</form>');
                    newForm.submit();
                }
            },

            initComponent: function(){
                Ext4.apply(this, {
                    border: false,
                    bodyStyle: 'padding: 5px;',
                    defaults: {
                        border: false
                    },
                    items: [{
                        xtype: 'labkey-detailspanel',
                        showTitle: false,
                        showBackBtn: false,
                        store: {
                            schemaName: 'blast',
                            queryName: 'blast_jobs',
                            filterArray: [
                                LABKEY.Filter.create('objectid', <%=q(job.getObjectid())%>, LABKEY.Filter.Types.EQUAL)
                            ]
                        }
                    },{
                        html: '<hr>'
                    },{
                        itemId: 'htmlArea',
                        defaults: {
                            border: false
                        },
                        items: this.getResultItems()
                    }]
                });

                this.callParent(arguments);
            },

            doLoad: function(){
                window.location.reload();
            },

            getResultItems: function(){
                var ret = [];
                if (!<%=job.isHasRun()%>){
                    ret.push({
                        xtype: 'panel',
                        minHeight: 200,
                        listeners: {
                            scope: this,
                            delay: 100,
                            afterrender: function(){
                                var target = this.down('#htmlArea');
                                this.loadMask = new Ext4.LoadMask(target, {msg: 'The job has not yet completed.  This page will load every 5 seconds until the job is finished.'});
                                this.loadMask.show();
                                Ext4.Function.defer(this.doLoad, 5000, this);
                            }
                        }
                    });

                    Ext4.Function.defer(function(){
                        window.location.reload();
                    }, 5000, this);
                }

                return ret;
            }
        });

        Ext4.create('BLAST.panel.BlastDetailsPanel', {

        }).render(<%=q(renderTarget)%>);
    });

</script>


<div id=<%=q(renderTarget + "_results")%>>
    <%
        if (job.isHasRun())
        {
            out.write("<a href='javascript:void(0);' onclick='BLAST.panel.BlastDetailsPanel.downloadRawData(\"" + job.getObjectid() + "\")'>[Download Raw Output]</a><p>");
            try (BufferedReader reader = new BufferedReader(new FileReader(job.getExpectedOutputFile())))
            {
                IOUtils.copy(reader, out);
            }
        }
    %>
</div>