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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.security.CreateContactsPermission" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("Ext4ClientApi"));
      resources.add(ClientDependency.fromPath("biotrust/NWBioTrust.css"));
      return resources;
  }
%>
<%
    boolean canManageContacts = getContainer().hasPermission(getUser(), CreateContactsPermission.class);
    ActionURL contactsTabURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
    contactsTabURL.addParameter("pageId", "nwbt.CONTACTS");

    String renderId = "biotrust-contacts-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script type="text/javascript">

    Ext4.onReady(function(){
        var items = [];

        Ext4.define('LABKEY.biotrust.data.Contacts', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'userid',       type : 'int' },
                {name : 'displayname',  type : 'string' },
                {name : 'role',         type : 'string'},
                {name : 'systemuser', type : 'string'}
            ]
        });

        var contactStore = Ext4.create('Ext.data.Store',{
            model : 'LABKEY.biotrust.data.Contacts',
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('biotrust', 'getContacts.api'),
                reader : {
                    type : 'json',
                    root : 'contacts'
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    // remove contacts that don't have a role
                    Ext4.each(recs, function(record){
                        if (record.get("role") == "N/A")
                            s.remove(record);
                    });

                    s.sort('displayname', 'ASC');
                }
            }
        });

        var tpl = new Ext4.XTemplate(
            '<table style="border-collapse: collapse; cellpadding=5">',
                '<tpl for=".">',
                    '<tr class="{[xindex % 2 === 1 ? "labkey-alternate-row" : "labkey-row"]}">',
                        '<tpl switch="systemuser">',
                            '<tpl case="true">',
                                '<td><span style="font-weight: bold">{displayname}</span></td>',
                            '<tpl default>',
                                '<td><span style="font-weight: bold">{displayname}</span><span style="font-style: italic"> (out-of-system)</span></td>',
                        '</tpl>',
                    '</tr>',
                    '<tr class="{[xindex % 2 === 1 ? "labkey-alternate-row" : "labkey-row"]}">',
                        '<td style="font-style: italic;">&nbsp;&nbsp;{role}</td>',
                    '</tr>',
                '</tpl>',
            '</table>'
        );

        items.push({
            xtype : 'dataview',
            store : contactStore,
            tpl : tpl
        });

        Ext4.create('Ext.panel.Panel', {
            renderTo: <%=q(renderId)%>,
            frame   : false,
            border  : false,
            items : items
        });
    });

</script>

<div id=<%=q(renderId)%>></div>
<br/>
<%= textLink(canManageContacts ? "Manage Contacts" : "View Contacts", contactsTabURL) %>
<p>
    Please contact NWBioTrust Research Coordinator if you have questions at 206.221.7246 or <a href="mailto:NWBT@uw.edu">NWBT@uw.edu</a>.
</p>