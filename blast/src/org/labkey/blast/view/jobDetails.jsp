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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.blast.model.BlastJob" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add(ClientDependency.fromModuleName("laboratory"));
    }
%>
<%
    JspView<BlastJob> me = (JspView) HttpView.currentView();
    BlastJob job = me.getModelBean();
    String renderTarget = "blast-"; //TODO: make unique?

    String outputFmtName = getViewContext().getRequest().getParameter("outputFmt");
    if (StringUtils.trimToNull(outputFmtName) == null)
    {
        outputFmtName = "flatQueryAnchoredWithIdentities";
    }

    BlastJob.BLAST_OUTPUT_FORMAT outputFormat = null;
    try
    {
        outputFormat = BlastJob.BLAST_OUTPUT_FORMAT.valueOf(outputFmtName);
    }
    catch (IllegalArgumentException e)
    {
        //will handle below
    }

    boolean hasRun = job.isHasRun();
%>

<script type="text/javascript">

    Ext4.onReady(function(){
        Ext4.define('BLAST.panel.BlastDetailsPanel', {
            extend: 'Ext.panel.Panel',

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
                                LABKEY.Filter.create('objectid', <%=q(h(job.getObjectid()))%>, LABKEY.Filter.Types.EQUAL)
                            ]
                        }
                    },{
                        html: '<hr>'
                    },{
                        layout: 'hbox',
                        border: false,
                        hidden: !<%=h(hasRun)%>,
                        items: [{
                            xtype: 'combo',
                            fieldLabel: 'Choose Output Format',
                            name: 'outputFmt',
                            queryMode: 'local',
                            displayField: 'label',
                            valueField: 'id',
                            labelWidth: 150,
                            width: 600,
                            value: <%=q(h(outputFormat == null ? null : outputFormat.name()))%>,
                            store: {
                                type: 'array',
                                fields: ['label', 'id'],
                                reader: {
                                    type: 'array',
                                    idProperty: 'id'
                                },
                                idProperty: 'id',
                                data: [
                                    ['Pairwise', 'pairwise'],
                                    ['Query-anchored showing identities', 'queryAnchoredWithIdentities'],
                                    ['Query-anchored no identities', 'queryAnchoredNoIdentities'],
                                    ['Flat query-anchored, show identities', 'flatQueryAnchoredWithIdentities'],
                                    ['Flat query-anchored, no identities', 'flatQueryAnchoredNoIdentities'],
                                    ['XML Blast output', 'xml'],
                                    ['Tabular', 'tabular'],
                                    ['Summary of Perfect Matches', 'alignmentSummary']
                                ]
                            }
                        },{
                            xtype: 'button',
                            text: 'Reload',
                            style: 'margin-left: 10px;',
                            handler: function(btn){
                                var fmt = btn.up('panel').down('combo').getValue();
                                if (!fmt){
                                    Ext4.Msg.alert('Error', 'Must choose an output format');
                                    return;
                                }

                                window.location = LABKEY.ActionURL.buildURL('blast', 'jobDetails', null, {outputFmt: fmt, jobId: <%=q(h(job.getObjectid()))%>});
                            }
                        },{
                            xtype: 'button',
                            text: 'Download Results',
                            style: 'margin-left: 10px;',
                            handler: function(btn){
                                var fmt = btn.up('panel').down('combo').getValue();
                                if (!fmt){
                                    Ext4.Msg.alert('Error', 'Must choose an output format');
                                    return;
                                }

                                var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                                                '<form method="POST" action="' + LABKEY.ActionURL.buildURL("blast", "downloadBlastResults") + '">' +
                                                '<input type="hidden" name="fileName" value="' + Ext4.htmlEncode('blastResults.txt') + '" />' +
                                                '<input type="hidden" name="jobId" value="' + <%=q(h(job.getObjectid()))%> + '" />' +
                                                '<input type="hidden" name="outputFormat" value="' + fmt + '" />' +
                                                '<input type="hidden" name="X-LABKEY-CSRF" value="' + Ext4.htmlEncode(LABKEY.CSRF) + '" />' +
                                                '</form>');
                                newForm.submit();

                            }
                        }]
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
                if (!<%=h(hasRun)%>){
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

        }).render(<%=q(h(renderTarget))%>);
    });

</script>

<div id="<%=h(renderTarget)%>"></div>
<div id="<%=h(renderTarget + "_results")%>">
    <%
        if (job.isHasRun())
        {
            if (outputFormat != null)
            {
                if (!outputFormat.supportsHTML())
                {
                    out.print(unsafe("<pre>"));
                }
                job.getResults(outputFormat, out);

                if (!outputFormat.supportsHTML())
                {
                    out.print(unsafe("</pre>"));
                }
            }
            else
            {
                out.print(h("Either no output format specified, or an invalid option was provided."));
            }
        }
        else if (job.hasError(getUser()))
        {
            out.print("There was an error running this job.");
        }
    %>
</div>