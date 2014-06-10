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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainKind" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.email.BioTrustNotificationManager" %>
<%@ page import="org.labkey.biotrust.query.BioTrustQuerySchema" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4"));
      resources.add(ClientDependency.fromFilePath("dataview/DataViewsPanel.css"));
      return resources;
  }
%>
<%
    Container c = getContainer();
    User user = getUser();
    ViewContext context = getViewContext();

    Map<String, Domain> domainMap = BioTrustQuerySchema.getDomainMap(c);

    // URLs for biotrust hard tables
    ActionURL requestCategoryURL = QueryService.get().urlFor(user, c, QueryAction.executeQuery, BioTrustQuerySchema.NAME, BioTrustQuerySchema.REQUEST_CATEGORY_TABLE_NAME);
    ActionURL requestStatusURL = QueryService.get().urlFor(user, c, QueryAction.executeQuery, BioTrustQuerySchema.NAME, BioTrustQuerySchema.REQUEST_STATUS_TABLE_NAME);
    ActionURL documentTypesURL = QueryService.get().urlFor(user, c, QueryAction.executeQuery, BioTrustQuerySchema.NAME, BioTrustQuerySchema.DOCUMENT_TYPES_TABLE_NAME);

    Map<String, String> notifications = PropertyManager.getProperties(c.getProject(), BioTrustNotificationManager.NWBT_EMAIL_NOTIFICATIONS);
%>

<script type="text/javascript">

    Ext4.QuickTips.init();
    var manageUtil = {

        newSchema : function() {

            var formPanel = {
                xtype   : 'form',
                items   : [
                    {xtype : 'textfield', name : 'queryName', allowBlank : false, width : 350, emptyText : 'Enter the table name to store request responses'}
                ],
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            editWindow.getEl().mask("Saving...");
                            form.submit({
                                url : LABKEY.ActionURL.buildURL('biotrust', 'editSpecimenRequestDefinition.api'),
                                method : 'POST',
                                success : function(form, action){
                                    editWindow.getEl().unmask();
                                    editWindow.close();

                                    var o = action.result;

                                    if (o.success && o.url)
                                    {
                                        window.location = o.url;
                                    }
                                },
                                failure : function(response){
                                    editWindow.getEl().unmask();
                                    Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                                },
                                scope : this
                            });
                        }
                    },
                    scope   : this
                }]
            };

            var editWindow = Ext4.create('Ext.window.Window', {
                width  : 460,
                maxHeight : 750,
                layout : 'fit',
                draggable : false,
                modal  : true,
                title  : 'New Request Response Schema',
                defaults: {
                    border: false, frame: false
                },
                bodyPadding : 10,
                items : formPanel,
                scope : this
            });

            editWindow.show();
        },

        deleteSchema : function(domainURI) {

            Ext4.Msg.show({
                title : 'Delete Schema',
                msg   : 'Are you sure you wish to delete the specified schema, all specimen requests saved to this schema will be deleted as well.',
                buttons : Ext4.MessageBox.OKCANCEL,
                icon    : Ext4.MessageBox.WARNING,
                fn      : function(btn){
                    if (btn == 'ok') {
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('biotrust', 'deleteSpecimenRequestDefinition.api', null, {
                                domainURI  : domainURI,
                                queryName   : 'settings'
                            }),
                            method : 'POST',
                            success: function(response) {
                                var o = Ext4.decode(response.responseText);

                                if (o.success)
                                    window.location = window.location;
                            },
                            failure : function(response) {
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope  : this
            });
        },

        manageNotifications : function() {

            var formPanel = {
                xtype   : 'form',
                items   : [
                    {xtype : 'displayfield', value : 'Select the email notification types that should be enabled. ' +
                            'No email will be sent for any item that is unchecked.'},
                    {xtype : 'checkbox', name : 'studyRegistered', checked : <%=h(notifications.get("studyRegistered") != null ? false : true)%>,
                            boxLabel : 'Study Registered<br/>'
                                + '<div style="font-size: 11px; font-style: italic; margin-left: 20px;">Notifications are sent to the project specified RC email '
                                + 'address when an investigator clicks the "Register" button on the study registration wizard. Each PI in the study folder is '
                                + 'also CC\'d on the email notification.</div>'},
                    {xtype : 'checkbox', name : 'sampleRequestSubmitted', checked : <%=h(notifications.get("sampleRequestSubmitted") != null ? false : true)%>,
                            boxLabel : 'Sample Request Submitted<br/>'
                                + '<div style="font-size: 11px; font-style: italic; margin-left: 20px;">Notifications are sent to the project specified RC email '
                                + 'address when an investigator clicks the "Submit completed form" button on the sample request wizard. Each PI in the study folder is '
                                + 'also CC\'d on the email notification.</div>'},
                    {xtype : 'checkbox', name : 'approverReviewRequested', checked : <%=h(notifications.get("approverReviewRequested") != null ? false : true)%>,
                            boxLabel : 'Approver Review Requested<br/>'
                                + '<div style="font-size: 11px; font-style: italic; margin-left: 20px;">Notifications are sent to the selected reviewers when a sample '
                                + 'request tissue/blood record is changed to an approval state from the RC dashboard. The selected reviewers are set via the same RC '
                                + 'status change dialog.</div>'},
                    {xtype : 'checkbox', name : 'approverReviewSubmitted', checked : <%=h(notifications.get("approverReviewSubmitted") != null ? false : true)%>,
                            boxLabel : 'Sample Review Submitted<br/>'
                                + '<div style="font-size: 11px; font-style: italic; margin-left: 20px;">Notifications are sent to the project specified RC email '
                                + 'when a faculty reviewer submits a response to a review request on the Approver Review dashboard. </div>'},
                    {xtype : 'checkbox', name : 'investigatorFolderCreated', checked : <%=h(notifications.get("investigatorFolderCreated") != null ? false : true)%>,
                            boxLabel : 'Investigator Folder Created<br/>'
                                + '<div style="font-size: 11px; font-style: italic; margin-left: 20px;">Notifications are sent to the project specified RC email '
                                + 'when an investigator creates a new folder for themselves via the "Setup Account" button on the home page.</div>'}
                ],
                buttons     : [{
                    text : 'Update',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            editWindow.getEl().mask("Updating...");
                            form.submit({
                                url : LABKEY.ActionURL.buildURL('biotrust', 'updateEmailNotifications.api'),
                                method : 'POST',
                                success : function(form, action){
                                    editWindow.getEl().unmask();
                                    editWindow.close();
                                    window.location = window.location;
                                },
                                failure : function(response){
                                    editWindow.getEl().unmask();
                                    Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                                },
                                scope : this
                            });
                        }
                    },
                    scope   : this
                }]
            };

            var editWindow = Ext4.create('Ext.window.Window', {
                width  : 500,
                maxHeight : 750,
                layout : 'fit',
                draggable : false,
                modal  : true,
                title  : 'Manage NWBioTrust Email Notifications',
                defaults: {
                    border: false, frame: false
                },
                bodyPadding : 10,
                items : formPanel,
                scope : this
            });

            editWindow.show();
        }
    };
</script>

<div>
    <div style="font-weight: bold;">Configuration Instructions</div><br/>
    <div><table>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/documents/NWBT Project Configuration.docx">NWBT Project Configuration</a></td>
        </tr>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/documents/NWBT Investigator Folder Configuration.docx">NWBT Investigator Folder Configuration</a></td>
        </tr>
    </table></div><br/>

    <div style="font-weight: bold;">Manage Sample Request Lookup Tables</div><br/>
    <div><table>
        <tr>
            <td><a href="<%=h(documentTypesURL.getLocalURIString())%>">Document Types</a></td>
            <td><span style='font-style: italic;'>(i.e. IRB Approval Packet, MTDUA Agreement, Confidentiality Pledge, Blank Consent Form)</span></td>
        </tr>
        <tr>
            <td><a href="<%=h(requestCategoryURL.getLocalURIString())%>">Request Category</a></td>
            <td><span style='font-style: italic;'>(i.e. CBR RC 1, CBR RC 2, Repository 1, Repository 2)</span></td>
        </tr>
        <tr>
            <td><a href="<%=h(requestStatusURL.getLocalURIString())%>">Request Status</a></td>
            <td><span style='font-style: italic;'>(i.e. Submitted, Request Review, Feasibility Review, Oversight Review, Approved, Closed)</span></td>
        </tr>
    </table></div><br/>

    <div style="font-weight: bold;">Manage Email Notifications</div>
    <div><table>
        <tr><td>
            <p>Click the "Manage Notifications" link below to see what email notification types exist in the NWBioTrust system, when
            <br/>they are sent, and who they are sent to. You can also enable or disable NWBioTrust email notifications for this project.</p>
            <p>To view or edit the templates that are used for the various email notifications, go to the
            <span style='font-style:italic;'>Admin > Site > Admin Console</span>
            <br/> page and click on the "Email Customization" link. From there you can select the "Email Type" and edit the template
            <br/>subject or message body.</p>
        </td></tr>
        <tr><td><a href="javascript:void(0)" onclick="manageUtil.manageNotifications();">Manage Notifications</a></td></tr>
    </table></div><br/>

    <div style="font-weight: bold;">Metadata for Contacts Fields</div><br/>
    <div><table>
        <tr><td>This set of contact fields should be configured as the user properties for this server.
            <br/>To configure user properties go to the Admin > Site > Site Users page and click the "Change User Properties" button.
            <br/>For a new server, you can use the "Import Fields" button to paste in the contents of this TSV linked below.
        </td></tr>
        <tr><td><a href="<%=getContextPath()%>/biotrust/metadata/contacts-fields.tsv">Contacts fields</a></td></tr>
    </table></div><br/>

    <div style="font-weight: bold;">List Archive for Anatomical Site SNOMED-CT Codes</div><br/>
    <div><table>
        <tr><td>To configure the Anatomical Site SNOMED-CT Codes, download the list archive below and import it into the
            <br/>Admin > Manage Lists page in the RC project container.</td></tr>
        <tr><td><a href="<%=getContextPath()%>/biotrust/metadata/anatomical-site-list.zip">Anatomical Site</a></td></tr>
    </table></div><br/>

    <div style="font-weight: bold;">NWBT Branding CSS</div><br/>
    <div><table>
        <tr><td>To configure the NWBT server for button branding and font style, download the CSS file below and install it as the custom
            <br/>stylesheet on the "Resources" tab of the Admin > Site > Admin Console > Look and Feel Settings page. </td></tr>
        <tr><td><a href="<%=getContextPath()%>/biotrust/NWBioTrustBranding.css">CSS file</a></td></tr>
    </table></div><br/>

    <div style="font-weight: bold;">Metadata for Study Registration and Sample Requests</div><br/>
    <div><table>
        <tr><td>To configure the Study Registration and Sample Request wizard metadata, download the 4 JSON files below and do
            <br/>the following steps for each: 1) Click the [edit] link for the corresponding record in the Survey Designs grid above
            <br/>2) Paste the contents of the JSON file into the survey design metadata textarea 3) Click the "Save Survey" button.
            <p>After a server upgrade, you will also want to toggle the RC project folder type so that any newly added wizard fields
            <br/>are created in the biotrust schema tables. To do this, go to the Admin > Folder > Management page, select the
            <br/>"Folder Type" tab, change the folder type to Collaboration, click the "Update Folder" button, and then go back to the
            <br/>folder type page and reset the Folder Type back to "NW BioTrust Research Coordinator" and save.</p>
        </td></tr>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/metadata/study-registration.json">Study Registration</a></td>
        </tr>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/metadata/sample-request.json">Sample Request</a></td>
        </tr>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/metadata/tissue-sample.json">Tissue Sample Record</a></td>
        </tr>
        <tr>
            <td><a href="<%=getContextPath()%>/biotrust/metadata/blood-sample.json">Blood Sample Record</a></td>
        </tr>
    </table></div><br/>

    <div style="font-weight: bold;">Project Groups</div>
    <div><table>
        <tr><td>
            <p>The following project permissions groups are created automatically when you create a NWBioTrust project:</p>
            <table>
                <tr>
                    <td valign="top">Research Coordinators:</td>
                    <td>has access to the project root (i.e. RC dashboard) folder and all investigator subfolders,
                        <br/>with the <span style='font-style: italic;'>NWBT Research Coordinator</span> role.</td>
                </tr>
                <tr>
                    <td valign="top">Approval Reviewers:</td>
                    <td>has access to the project root folder and all investigator subfolders, with the <span style='font-style: italic;'>NWBT Faculty
                        <br/>Reviewer</span> role. This will be the list of users displayed for selection on the RC dialog to change
                        <br/>the status of a sample request to an approval state.</td>
                </tr>
                <tr>
                    <td valign="top">LabKey API:</td>
                    <td>has access to the project root folder and all investigator subfolders, with the <span style='font-style: italic;'>Reader</span> role.
                        <br/>This is intended to only include a single user (i.e. labkeyapi_dev@uw.edu) which will be used
                        <br/>by the external system to make API calls to get data.</td>
                </tr>
            </table>
        </td></tr>
    </table></div>

    <div style="font-weight: bold; display:none;">Existing Request Response Schemas</div><br>

    <div style="display:none;"><table>
    <%
        for (Map.Entry<String, Domain> entry : domainMap.entrySet())
        {
            Domain domain = entry.getValue();
            DomainKind domainKind = domain.getDomainKind();

            ActionURL viewDataURL = domainKind.urlShowData(domain, context);
            ActionURL editURL = domainKind.urlEditDefinition(domain, context);
    %>
        <tr>
            <td><a href="<%=h(viewDataURL.getLocalURIString())%>"><%=h(domain.getName())%></a></td>
            <td><%=textLink("edit", editURL, "editDomain")%></td>
            <td><%=textLink("delete", new ActionURL(), "manageUtil.deleteSchema(" + q(domain.getTypeURI()) + ");return false;", "deleteDomain")%></td>
        </tr>
    <%
        }
    %>
    </table><br>
    <a href="javascript:void(0)" class="labkey-text-link" onclick="manageUtil.newSchema();">Create new Request Response Schema</a>
    </div>
</div>
