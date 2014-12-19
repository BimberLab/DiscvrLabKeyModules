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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>

<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();

    int index = part.getIndex();
    String panelId = "searchPanel" + index;
%>

<%--
<div class='panel-description'>
    <p>
        <strong>The Virtual Biospecimen Discovery</strong> (VBD) tool contains a searchable database of biospecimens
        that are potentially available for research from existing repositories across the FHCRC/UW Cancer Consortium.
    </p><p>
        The tool provides
        <ul>
            <li>an overview table of types and overall counts of biospecimens, by cancer type and specimen type</li>
            <li>the ability to find sets of matched specimens (e.g. blood and tissue from the same person)</li>
            <li>repository contact information for further exploration and requests</li>
        </ul>
    </p>
</div>
--%>
<div id="<%=text(panelId)%>"></div>

<script type="text/javascript">
    var getSearchResults = function() {

        if (!this.resultsPanel)
        {
            Ext4.define('VBD.model.SummaryResults', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'personCategory'},
                    {name : 'blood'},
                    {name : 'bloodUrl'},
                    {name : 'metastatic'},
                    {name : 'metastaticUrl'},
                    {name : 'other'},
                    {name : 'otherUrl'},
                    {name : 'tissuecancer'},
                    {name : 'tissuecancerUrl'},
                    {name : 'tissuenoncancer'},
                    {name : 'tissuenoncancerUrl'},
                    {name : 'total'},
                    {name : 'totalUrl'}
                ]
            });

            var storeCfg = {
                model   : 'VBD.model.SummaryResults',
                autoLoad: true,
                proxy   : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('vbdsearch', 'getSummaryResults.api'),
                    reader : {
                        type : 'json',
                        root : 'rows'
                    }
                },
                listeners: {
                    scope: this,
                    load: function(){
                        this.resultsPanel.getEl().unmask();
                    }
                }

            };

            var tpl = new Ext4.XTemplate(
                '<div class="faceted-search-results">',
                    '<table width="100%" class="labkey-data-region labkey-show-borders">',
                    '<tr>',
                        '<th>Person Type</th>',
                        '<th>Met Tissue</th>',
                        '<th>Blood</th>',
                        '<th>Tissue (cancer)</th>',
                        '<th>Tissue (non-cancer)</th>',
                        '<th>Other</th>',
                        '<th>Total</th></tr>',
                    '<tpl for="."><tr class="{[xindex % 2 === 0 ? "labkey-row" : "labkey-alternate-row"]}">',
                        '<td><div style="padding: 3px 0 3px 0;">{personCategory}</div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{metastaticUrl}">{metastatic}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{bloodUrl}">{blood}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{tissuecancerUrl}">{tissuecancer}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{tissuenoncancerUrl}">{tissuenoncancer}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{otherUrl}">{other}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{totalUrl}">{total}</a></div></td>',
                    '</tr></tpl></table>',
                '</div>'
            );
            var dataView = Ext4.create('Ext.view.View', {
                store   : storeCfg,
                tpl     : tpl,
                ui      : 'custom',
                itemSelector : 'div.faceted-search-results',
                scope   : this
            });

            this.resultsPanel = Ext4.create('Ext.Panel', {
                minHeight   : 200,
                //bodyPadding : 5,
                cls         : 'iScroll',
                ui          : 'custom',
                items       : dataView
            });
        }
        return this.resultsPanel;
    };

    Ext4.onReady(function(){
        var summaryPanel = Ext4.create('Ext.panel.Panel', {
            title: 'Summary',
            items: [this.getSearchResults()]
        });

        // matched specimen tab panel
        var tissueNotCancerCB = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Tissue (non-cancer)',
            name: 'specimenCategories',
            inputValue: 'tissue non cancer'
        });

        var tissueCancerCB = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Tissue (cancer)',
            name: 'specimenCategories',
            inputValue: 'tissue cancer',
            disabled: true
        });

        var bloodCB = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Blood',
            name: 'specimenCategories',
            inputValue: 'blood'
        });

        var metCB = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Met Tissue',
            name: 'specimenCategories',
            inputValue: 'metastatic',
            disabled: true
        });

        var otherCB = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Other',
            name: 'specimenCategories',
            inputValue: 'other'
        });

        var radioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Person Type',
            vertical: true,
            columns: 1,
            border: 1,
            flex: 1,
            items: [
                {boxLabel: 'Non-cancer female', name: 'personCategory', inputValue: 'non-cancer female', checked: true},
                {boxLabel: 'Non-cancer male', name: 'personCategory', inputValue: 'non-cancer male'},
                {boxLabel: 'Breast Cancer', name: 'personCategory', inputValue: 'breast cancer'},
                {boxLabel: 'Ovarian Cancer', name: 'personCategory', inputValue: 'ovarian cancer'},
                {boxLabel: 'Prostate Cancer', name: 'personCategory', inputValue: 'prostate cancer'},
                {boxLabel: 'Lung Cancer', name: 'personCategory', inputValue: 'lung cancer'},
                {boxLabel: 'Sarcoma Cancer', name: 'personCategory', inputValue: 'sarcoma cancer'},
                {boxLabel: 'Brain Cancer', name: 'personCategory', inputValue: 'cns cancer'}
            ],
            listeners: {
                scope: this,
                change: function(group, newVal){
                    var checkedBoxes = checkboxGroup.getChecked();

                    if(newVal.personCategory == 'non-cancer female' || newVal.personCategory == 'non-cancer male'){
                        tissueNotCancerCB.setDisabled(false);
                        tissueCancerCB.setDisabled(true);
                        metCB.setDisabled(true);

                        for(var i = 0; i < checkedBoxes.length; i++){
                            if(checkedBoxes[i].inputValue == 'tissue cancer' || checkedBoxes[i].inputValue == 'metastatic'){
                                checkedBoxes[i].setValue(false);
                            }
                        }
                    } else {
                        tissueNotCancerCB.setDisabled(false);
                        tissueCancerCB.setDisabled(false);
                        metCB.setDisabled(false);

                        for(var i = 0; i < checkedBoxes.length; i++){
                            if(checkedBoxes[i].inputValue == 'tissue non cancer'){
                                checkedBoxes[i].setValue(false);
                            }
                        }
                    }
                }
            }
        });

        var checkboxGroup = Ext4.create('Ext.form.CheckboxGroup', {
            fieldLabel: 'Specimens',
            vertical: true,
            columns: 1,
            border: 1,
            flex: 1,
            items: [
                tissueNotCancerCB,
                tissueCancerCB,
                bloodCB,
                metCB,
                otherCB
            ],
            listeners: {
                scope: this,
                change: function(group, newVal, oldVal){
                    if(newVal.specimenCategories){
                        showResultsBtn.setDisabled(false);
                    } else {
                        showResultsBtn.setDisabled(true);
                    }
                }
            }
        });

        var onShowResults = function(){
            var specimenCategories = checkboxGroup.getValue().specimenCategories;
            var personCategory = radioGroup.getValue().personCategory;

            if(specimenCategories instanceof Array){
                specimenCategories = specimenCategories.join(';');
            }
            
            var url = LABKEY.ActionURL.buildURL('vbdsearch', 'FacetedSpecimenSearch', null, {
                specimenCategory: specimenCategories,
                personCategory: personCategory,
                matchedSpecimenSearch : true
            });

            window.location = url;
        };

        var showResultsBtn = Ext4.create('Ext.button.Button', {
            text: 'show results',
            disabled: true,
            handler: onShowResults,
            scope: this
        });

        var matchedSpecimenPanel = Ext4.create('Ext.panel.Panel', {
            title: 'Matched Specimen',
            layout: 'hbox',
            bodyPadding: 10,
            bbar: ['->', showResultsBtn],
            items: [radioGroup, checkboxGroup]
        });

        var searchPanel = Ext4.create('Ext.tab.Panel', {
            width   : "100%",
            //border  : false,
            renderTo    : '<%=text(panelId)%>',
            defaults    : {
                //padding : 10
            },
            items: [summaryPanel, matchedSpecimenPanel]
        });

        this.resultsPanel.getEl().mask('Loading summary...');
    });
</script>
