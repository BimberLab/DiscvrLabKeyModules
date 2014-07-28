<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();

        resources.add(ClientDependency.fromFilePath("Ext4ClientAPI"));
        resources.add(ClientDependency.fromFilePath("clientapi/ext4/Util.js"));
        resources.add(ClientDependency.fromFilePath("clientapi/ext4/data/Reader.js"));
        resources.add(ClientDependency.fromFilePath("clientapi/ext4/data/Proxy.js"));
        resources.add(ClientDependency.fromFilePath("clientapi/ext4/data/Store.js"));
        resources.add(ClientDependency.fromFilePath("ux/CheckCombo/CheckCombo.js"));
        resources.add(ClientDependency.fromFilePath("ux/CheckCombo/CheckCombo.css"));

        return resources;
    }
%>


<div id="facetedSearchPanel"></div>

<script type="text/javascript">
    Ext4.QuickTips.init();

    var getComboBoxConfig = function(name, fieldLabel, tipTxt, value){

        var createCheckCombo = (fieldLabel == 'Specimen Category');
        if (tipTxt)
            fieldLabel = '<span data-qtip="' + tipTxt + '">' + fieldLabel + '</span>';

        // define data model for combo box store
        if (!Ext4.ModelManager.isRegistered('LABKEY.vbd.data.Samples')) {
            Ext4.define('LABKEY.vbd.data.Samples', {
                extend : 'Ext.data.Model',
                fields : [
                    'value'
                ]
            });
        }

        var extraParams = {
            schemaName  : 'lists',
            queryName   : 'Samples'
        };
        extraParams['query.columns'] = name;

        var id = Ext4.id();
        var storeConfig = {
            model       : 'LABKEY.data.Samples',
            autoLoad    : false,
            pageSize    : 10000,
            fields      : [{name : 'value', convert : distinctQueryConverter}],
            checkCombo  : createCheckCombo,
            queryColumn : name,
            parentCombo : id,
            listeners: {
                scope: this,
                beforeload : beforeLoadComboHandler,
                load : function(s, recs, success, operation, ops) {
                    if (!success)
                        console.log('combo load failed');
                    if (!createCheckCombo)
                        s.insert(0, {value : '&nbsp;'});
                }
            }
        };

        // check box combos don't exibit dynamic faceting behavior
        if (createCheckCombo) {
            storeConfig.proxy = {
                type    : 'ajax',
                url     : LABKEY.ActionURL.buildURL('query', 'selectDistinct.api'),
                extraParams : extraParams,
                reader : {
                    type : 'json',
                    root : 'values'
                }
            };
        }

        return {
            fieldLabel      : fieldLabel,
            name            : name,
            store           : storeConfig,
            id              : id,
            queryCaching    : false,
            editable        : false,
            labelWidth      : 160,
            matchFieldWidth : false,
            value           : value,
            flex            : 1,
            displayField    : 'value',
            valueField      : 'value',
            emptyText       : 'None',
            listeners: {
                scope: this,
                change: createCheckCombo ? checkComboHandler : filterComboHandler,
                beforequery : {fn : function(event){}}
            }
        };
    };

    var distinctQueryConverter = function(v, record) {

        if (record.raw && record.raw.value)
            return record.raw.value;
        else if (record.raw)
            return record.raw;
        return v;
    };

    var getPersonFilterCombos = function(personCategory, personPathDiagnosis){
        var personFilterCombos = {};

        personFilterCombos.personCategory = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personCategory',
                'Person Category', 'A computed value to categorize person types.', personCategory));
        personFilterCombos.personGender = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personGender',
                'Gender', 'Gender of patient, subject or donor at time of specimen collection'));
        personFilterCombos.personRace = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personRace',
                'Race', 'Race of patient, subject or donor at time of specimen collection'));
        personFilterCombos.personEthnicity = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personEthnicity',
                'Ethnicity', 'Ethnicity of patient, subject or donor at time of specimen collection'));
        personFilterCombos.personPathStage = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personPathStage',
                'Pathology TNM Stage', "Final pathologic stage of patient, subject or donor's primary cancer (indicated in primary cancer site field) at time of specimen collection."));
        personFilterCombos.personPathDiagnosis = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personPathDiagnosis',
                'Pathologic Diagnosis', 'Overall pathologic diagnosis for patient, subject or donor (cancer or not cancer only)'));
        personFilterCombos.personPrimaryHistDiagnosis = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personPrimaryHistDiagnosis',
                'Histologic Diagnosis', "Histologic diagnosis of patient, subject or donor's primary cancer. More specific than overall pathologic diagnosis of cancer vs. not cancer."));
        personFilterCombos.personPrimarySite = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'personPrimarySite',
                'Primary Cancer Site', "Anatomic site of person's primary cancer (if overall pathologic diagnosis is cancer)"));

        return personFilterCombos;
    };

    var getSpecimenFilterCombos = function(specimenCategories){
        var specimenFilterCombos = {};

        specimenFilterCombos.specimenCategory = Ext4.create('Ext.ux.CheckCombo', getComboBoxConfig.call(this, 'specimenCategory',
                'Specimen Category', 'A computed value to categorize specimen types.', specimenCategories));
        specimenFilterCombos.specimenType = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenType',
                'Specimen Type', 'High level type of specimen (e.g. tissue, blood, serum, plasma)'));
        specimenFilterCombos.specimenPreservationMethod = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenPreservationMethod',
                'Preservation Method', 'Preservation method for specimen (e.g. frozen, FFPE)'));
        specimenFilterCombos.specimenPathDiagnosis = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenPathDiagnosis',
                'Pathologic Diagnosis', 'Overall pathologic diagnosis for specimen itself (cancer or not cancer only). May be different from overall pathologic diagnosis of person.'));
        specimenFilterCombos.specimenHistDiagnosis = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenHistDiagnosis',
                'Histologic Diagnosis', 'Histologic diagnosis of specimen. More specific than overall specimen pathologic diagnosis of cancer vs. not cancer. May be different from primary histologic diagnosis of person.'));
        specimenFilterCombos.specimenPathGrade = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenPathGrade',
                'Cancer Grade', 'Pathologic grade of specimen.'));
        specimenFilterCombos.specimenTumorMarkers = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenTumorMarkers',
                'Tumor Markers', 'List of any specific mutation(s) or tumor markers present in specimen'));
        specimenFilterCombos.specimenPriorTx = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenPriorTx',
                'Prior Treatment', 'List of any prior treatment that could affect analysis of specimen (e.g. hormone therapy, chemotherapy, radiation)'));
        specimenFilterCombos.specimenSite = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'specimenSite',
                'Specimen Site', 'Anatomic site from which specimen was obtained'));
        specimenFilterCombos.repositoryShortName = Ext4.create('Ext.form.field.ComboBox', getComboBoxConfig.call(this, 'repositoryShortName',
                'Repository', 'Short name of repository where specimen is located.'));

        return specimenFilterCombos;
    };

    var prepData = function(values){
        // Have to use this instead of a convert function on the model because sometimes we get nulls, and you
        // cannot pass in null as a raw value to a store, or it results in an error.
        for(var i = 0; i < values.length; i++){
            var value = values[i];
            if(value == null){
                value = '[null]'
            }
            values[i] = {value: value, displayValue: value};
        }
    };

    var filterComboHandler = function(cb, newValue){
        if (cb && newValue == '&nbsp;')
        {
            // Have to do this or &nbsp; gets displayed.
            cb.clearValue();
        }

        searchHandler.call(this);
    };

    var beforeLoadComboHandler = function(store, op) {

        // don't want faceting behavior for the checkbox combos
        if (store.checkCombo)
            return;

        if (this.formPanel) {
            var formValues = this.formPanel.getForm().getValues();
            var filterArray = [];
            var queryColumn = store.queryColumn;

            for (var name in formValues){

                if (name == queryColumn)
                    continue;
                if (formValues.hasOwnProperty(name)){
                    var value = formValues[name];

                    if (value === '&nbsp;' || value === '')
                        continue;

                    if(value != null && value instanceof Array && value.length > 0){
                        var newValues = [];
                        for (var i = 0; i < value.length; i++){
                            newValues.push(value[i]);
                        }
                        value = newValues.join(";");
                        filterArray.push(LABKEY.Filter.create(name, value, LABKEY.Filter.Types.EQUALS_ONE_OF));
                    }
                    else {
                        filterArray.push(LABKEY.Filter.create(name, value));
                    }
                }
            }
            var extraParams = LABKEY.Query.buildQueryParams('lists', 'Samples', filterArray);
            extraParams['query.columns'] = queryColumn;

            var combo = Ext4.getCmp(store.parentCombo);
            if (combo && combo.getPicker)
                combo.getPicker().setLoading(true);

            Ext4.Ajax.request({
                method  : 'GET',
                url     : LABKEY.ActionURL.buildURL('query', 'selectDistinct.api'),
                params  : extraParams,
                success : function(response){
                    var json = Ext4.JSON.decode(response.responseText);

                    if (json.values && json.values.length)
                    {
                        var data = [];
                        for (var i=0; i < json.values.length; i++)
                        {
                            if (json.values[i])
                                data.push(json.values[i]);
                        }
                        store.loadRawData(data);
                        if (combo && combo.getPicker)
                            combo.getPicker().setLoading(false);
                    }
                },
                failure : function(response){
                    if (el) el.unmask();
                    console.error(response);
                }
            });
        }
    };

    var searchHandler = function(){
        var searchValues = {};
        var comboName;
        var value;
        // Need to get the grouping order, then pass that to the roll up function later.
//        var groupingOder = getGroupingOrder();
//        console.log(groupingOrder);

        for(comboName in this.personCombos){
            if(this.personCombos.hasOwnProperty(comboName)){
                value = this.personCombos[comboName].getValue();

                if(value != null){
                    searchValues[comboName] = value == '[null]' ? '' : value;
                }
            }
        }

        for(comboName in this.specimenCombos){
            if(this.specimenCombos.hasOwnProperty(comboName)){
                value = this.specimenCombos[comboName].getValue();

                if(comboName == "specimenCategory" && value !=null && value instanceof Array && value.length > 0){
                    var newValues = [];
                    for(var i = 0; i < value.length; i++){
                        newValues.push(value[i]);
                    }
                }

                if(value != null && (!(value instanceof Array) || (value instanceof Array && value.length > 0))){
                    searchValues[comboName] = value == '[null]' ? null : value;
                }
            }
        }

        this.resultsPanel.getEl().mask('Loading rollup...');
        Ext4.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('vbdsearch', 'getFacetedSearchResults.api'),
            jsonData: searchValues,
            success: function(response){
                var json = Ext4.JSON.decode(response.responseText);
                this.facetedResultsStore.loadData(json.rows);
                this.resultsPanel.getEl().unmask();
            },
            failure: function(response){
                console.error(response);
            }
        });

    };

    var delayedSearchHandler = new Ext4.util.DelayedTask(searchHandler, this);

    var checkComboHandler = function(cb, newValue){
        delayedSearchHandler.delay(500, undefined, undefined, [cb, newValue]);
    };

    var getPersonFilterPanel = function(combos){
        var leftSpecimenCol = combos.slice(0, 5);
        var rightSpecimenCol = combos.slice(5, 9);

        return {
            xtype : 'panel',
            title : 'Person Filters',
            flex  : 1,
            //margin: '0 5 0 0',
            bodyPadding: 5,
            //height: 200,
            layout : 'form',
            items : combos
/*
            layout: 'column',
            items: [
                {
                    columnWidth: .45,
                    border: false,
                    items: leftSpecimenCol
                },
                {
                    columnWidth: .45,
                    border: false,
                    items: rightSpecimenCol
                }
            ]
*/
        }
    };

    var getSpecimenFilterPanel = function(combos){
        var leftSpecimenCol = combos.slice(0, 5);
        var rightSpecimenCol = combos.slice(5, 11);

        return {
            xtype : 'panel',
            title : 'Specimen Filters',
            flex : 1,
            margin: '0 5 0 0',
            bodyPadding: 5,
            //height: 200,
            layout: 'form',
            items : combos
/*
            items: [
                {
                    columnWidth: .45,
                    border: false,
                    items: leftSpecimenCol
                },
                {
                    columnWidth: .45,
                    border: false,
                    items: rightSpecimenCol
                }
            ]
*/
        };
    };

    var getGroupingPanel = function(){
        return Ext4.create('Ext.panel.Panel', {
            bodyPadding: 5,
            items: [
                {xtype: 'combo', value: 'Preservation Method', fieldLabel: 'Group By'},
                {xtype: 'combo', value: 'Gender', fieldLabel: 'Then Group By'},
                {xtype: 'combo', value: 'Repository', fieldLabel: 'Then Group By'}
            ]
        });
    };

    var getFilterTab = function(personCombos, specimenCombos){
        return Ext4.create('Ext.form.Panel', {
            //title: 'Filters',
            border: false,
            minWidth: 600,
            maxWidth: 900,
            margin: '10 0 0 0',

            bodyStyle: 'background-color: transparent;',
            layout: 'hbox',
            items: [getPersonFilterPanel(personCombos), getSpecimenFilterPanel(specimenCombos)]
        });
    };

/*
    var getGroupingTab = function(){
        return Ext4.create('Ext.panel.Panel', {
            title: 'Grouping',
            border  : false,
            hidden  : true,
            layout  : 'hbox',
            items: [getGroupingPanel()]
        });
    };
*/

    var getSearchPanel = function(personCombos, specimenCombos){
        this.formPanel = getFilterTab(personCombos, specimenCombos);
        return {
            xtype : 'panel',
            border: false,
            bodyStyle: 'background-color: transparent;',
            minWidth: 600,
            maxWidth: 900,

            //margin: '10 0 0 0',
            items: [
                {
                    xtype : 'box',
                    html  : '<p><h3>Specimen and Person Filters</h3><p>Modify the filter selections below to update the search results ' +
                            'grid shown above. Clicking on the grid cell values will navigate you to a view showing the individual ' +
                            'sample records filtered by the current selections.'
                },
                this.formPanel
            ]
        };
    };

    var getFacetedSearchResults = function() {

        if (!this.resultsPanel)
        {
            Ext4.define('VBD.model.FacetedResults', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'repositoryShortName'},
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

            var config = {
                model   : 'VBD.model.FacetedResults',
                autoLoad: false

/*
                proxy   : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('vbdsearch', 'getFacetedSearchResults.api'),
                    reader : {
                        type : 'json',
                        root : 'rows'
                    }
                }
*/
            };

            this.facetedResultsStore = Ext4.create('Ext.data.Store', config);

            var tpl = new Ext4.XTemplate(
                '<div class="faceted-search-results">',
                    '<table width="100%" class="labkey-data-region labkey-show-borders">',
                    '<tr>',
                        '<th>Repository</th>',
                        '<th>Met Tissue</th>',
                        '<th>Blood</th>',
                        '<th>Tissue (cancer)</th>',
                        '<th>Tissue (non-cancer)</th>',
                        '<th>Other</th>',
                        '<th>Total</th></tr>',
                    '<tpl for="."><tr class="{[xindex % 2 === 0 ? "labkey-row" : "labkey-alternate-row"]}">',
                        '<td>{[this.getRepositoryHtml(values.repositoryShortName)]}</td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.metastaticUrl)]}">{metastatic}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.bloodUrl)]}">{blood}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.tissuecancerUrl)]}">{tissuecancer}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.tissuenoncancerUrl)]}">{tissuenoncancer}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.otherUrl)]}">{other}</a></div></td>',
                        '<td><div style="padding: 3px 0 3px 0;"><a href="{[this.formatUrl(values.totalUrl)]}">{total}</a></div></td>',
                    '</tr></tpl></table>',
                '</div>',
                {
                    me : this,
                    getRepositoryHtml : function(name)
                    {
                        var rec = this.me.repositoryStore.findRecord('repositoryShortName', name);
                        var tip = null;
                        if (rec)
                            tip = this.me.repositoryTpl.apply(rec.data);

                        if (tip)
                            return '<span data-qtip="' + tip + '"><a href="javascript:void(0)">' + name + '</a></span>';
                        else
                            return '<span>' + name + '</span>';
                    },
                    formatUrl : function(url)
                    {
                        var params = LABKEY.ActionURL.getParameters(url);
                        params.returnURL = LABKEY.ActionURL.buildURL(LABKEY.ActionURL.getController(), LABKEY.ActionURL.getAction(), null, LABKEY.ActionURL.getParameters());

                        return LABKEY.ActionURL.buildURL('vbdsearch', 'specimenSearchResults', null, params);
                    }
                }
            );
            var dataView = Ext4.create('Ext.view.View', {
                store   : this.facetedResultsStore,
                loadMask: false,
                tpl     : tpl,
                ui      : 'custom',
                itemSelector : 'div.faceted-search-results',
                scope   : this
            });

            this.resultsPanel = Ext4.create('Ext.Panel', {
                minHeight   : 200,
                cls         : 'iScroll',
                ui          : 'custom',
                items       : dataView
            });
        }
        return this.resultsPanel;
    };

    Ext4.onReady(function(){
        Ext4.define('FacetModel', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'value'},
                {name: 'displayValue'}
            ]
        });

        this.repositoryTpl = new Ext4.XTemplate(
            'Repository : {repositoryName}<br>' +
            'Contact : {contact}<br>',
            'Contact email : {contactEmail}<br>',
            'Last updated : {[values.lastUpdated ? Ext.Date.format(values.lastUpdated, "Y-m-d") : "NA"]}<br>'
        );

        this.repositoryStore = new LABKEY.ext4.data.Store({
            schemaName  : 'lists',
            queryName   : 'Repositories',
            autoLoad    : true
        });

        this.repositoryStore.on('load', function(){

            var params = LABKEY.ActionURL.getParameters(),
                personFiltersItems = [],
                specimenFiltersItems = [];

            this.specimenCombos = getSpecimenFilterCombos.call(this, params.specimenCategory == undefined ? null : params.specimenCategory.split(';'));
            this.personCombos = getPersonFilterCombos.call(this, params.personCategory, params.personPathDiagnosis);
            this.matchedSpecimenSearch = params.matchedSpecimenSearch;

            for(var personComboName in this.personCombos){
                if(this.personCombos.hasOwnProperty(personComboName)){
                    personFiltersItems.push(this.personCombos[personComboName]);
                }
            }

            for(var specimenComboName in this.specimenCombos){
                if(this.specimenCombos.hasOwnProperty(specimenComboName)){
                    specimenFiltersItems.push(this.specimenCombos[specimenComboName]);
                }
            }

            var searchPanel = getSearchPanel(personFiltersItems, specimenFiltersItems);

            this.resultsGrid = Ext4.create('Ext.container.Container', {
                html: '<div id="rollUpTable"></div>',
                minHeight: 400
            });

            var items = [];
            items.push(getFacetedSearchResults());
            if (!this.matchedSpecimenSearch) {

                items.push(searchPanel);
            }
            //items.push(this.resultsGrid);

            var outerPanel = Ext4.create('Ext.panel.Panel', {
                border: false,
//                width: 1450,
//                minWidth: 1450,
                bodyStyle: 'background-color: transparent;',
                renderTo: 'facetedSearchPanel',
                items: items
            });

            var resize = function(w,h) {
                LABKEY.ext4.Util.resizeToViewport(outerPanel, w, h);
            };

            Ext4.EventManager.onWindowResize(resize);

            searchHandler.call(this);
        }, this);
    });
</script>