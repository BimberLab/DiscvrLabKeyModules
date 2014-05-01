/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 *
 * @class SequenceAnalysis.panel.IlluminaSampleExportPanel
 */
Ext4.define('SequenceAnalysis.panel.IlluminaSampleExportPanel', {
    extend: 'Ext.panel.Panel',

    sectionNames: ['Header', 'Reads', 'Settings', 'Data'],

    ignoredSectionNames: ['Data'],

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            //title: 'Create Illumina Sample Sheet',
            border: false,
            itemId: 'illuminaPanel',
            width: '100%',
            defaults: {
                border: true
            },
            items: [{
                html: 'You have chosen to export ' + this.rows.length + ' samples.<br><br>' +
                    'This page is designed to facilitate the creation of an Illumina Sample Sheet, which can be imported into the instrument.  This sample sheet will contain information that allows the system automatically connect the resulting FASTQ files with these readsets.  This is important in order to automatically connect the metadata such as subjectId, date, etc. with the sequence data.  There are two ways to approach this:<br><br>' +
                    '1. The panel below is designed to create a sample sheet that can be fed directly into the instrument\'s software, without needing to use Illumina Experiment Manager (IEM) to generate the template.  This process does require some level of understanding of the file format expected by the instrument, and the configuration settings required by your application.  If you perform the same application repeatedly, it is possible to configure the template once, and then save it.  Also, many common Illumina applications and kits can be selected below.  If this is your first time using this page, I recommend exporting the sheet through this system and comparing the resulting file against one produced through IEM.  The advantage of this route is that you save the step of creating a sheet through IEM.  To proceed with this option, use the panel below to select your application, sample kit and other parameters.  Using the second tab you can preview (or edit) the header section that will be produced.  Once satisfied, use the \'Download\' button to export the sample sheet that can be fed directly into the instrument.<br><br>' +
                    '2. The second approach is a hybrid.  You can begin by creating the sample sheet in IEM.  After you have selected your application, IEM will allow you to import a sample sheet from a text file (this file will look more or less like the third tab, titled \'Sample Selection\').  You can create this file by exporting a table of the samples using the \'Export Table Only\' button below.  This will produce a file with one row per sample, which can be used to create a PLT file and imported into IEM.  You can then complete the wizard in IEM to create the sample sheet that will be fed to the instrument.  The advantage of this route is that most of the Illumina-specific settings are handled in IEM.  The disadvatage is that it creates a second step not otherwise required.<br><br>' +
                    'Please note that with both routes the samples are identified in the resulting files using the Readset IDs.  These IDs are automatically created in this system when you first create that readset.  It is essential that these numbers match for the system to associate the readsets and FASTQ files correctly.',
                border: false,
                bodyStyle: 'padding: 5px;',
                style: 'padding-bottom: 5px;'
            },{
                xtype: 'tabpanel',
                defaults: {
                    border: false
                },
                listeners: {
                    scope: this,
                    beforetabchange: this.onBeforeTabChange,
                    tabchange: this.onTabChange
                },
                items: [{
                    xtype: 'form',
                    title: 'General Info',
                    itemId: 'defaultTab',
                    bodyStyle: 'padding: 5px;',
                    defaults: {
                        width: 400,
                        labelWidth: 180,
                        maskRe: /[^,]/
                    },
                    items: [{
                        xtype: 'textfield',
                        allowBlank: false,
                        fieldLabel: 'Reagent Cassette Id',
                        helpPopup: 'This should match the ID of the Illumina flow cell.  It will be used as the filename of the export.  If you do not have this value, you can always rename the file later',
                        itemId: 'fileName',
                        value: 'Illumina',
                        maskRe: /[0-9a-z_-]/i,
                        maxLength: 100
                    },{
                        xtype: 'textfield',
                        itemId: 'investigator',
                        fieldLabel: 'Investigator Name',
                        value: LABKEY.Security.currentUser.displayName,
                        section: 'Header'
                    },{
                        xtype: 'textfield',
                        itemId: 'experimentName',
                        fieldLabel: 'Experiment Name',
                        section: 'Header'
                    },{
                        xtype: 'textfield',
                        itemId: 'projectName',
                        fieldLabel: 'Project Name',
                        section: 'Header'
                    },{
                        xtype: 'datefield',
                        itemId: 'dateField',
                        fieldLabel: 'Date',
                        value: new Date(),
                        section: 'Header'
                    },{
                        xtype: 'textfield',
                        itemId: 'description',
                        fieldLabel: 'Description',
                        section: 'Header'
                    },{
                        xtype: 'combo',
                        itemId: 'application',
                        fieldLabel: 'Application',
                        editable: false,
                        queryMode: 'local',
                        allowBlank: false,
                        displayField: 'name',
                        valueField: 'name',
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            schemaName: 'sequenceanalysis',
                            queryName: 'illumina_applications',
                            columns: '*',
                            autoLoad: true,
                            listeners: {
                                load: function(store){
                                    var rec = LDK.StoreUtils.createModelInstance(store, {
                                        name: 'Custom'
                                    });
                                    store.add(rec);
                                }
                            }
                        }),
                        listeners: {
                            scope: this,
                            change: function(field, val){
                                var kitField = field.up('panel').down('#sampleKit');
                                if (!val || val == 'Custom'){
                                    kitField.setDisabled(true);
                                    return;
                                }

                                kitField.setDisabled(false);
                                var recIdx = field.store.find(field.valueField, val);
                                var rec = field.store.getAt(recIdx);
                                var kits = rec.get('compatiblekits');
                                kits = kits ? kits.replace(/,/g, ';') : kits;

                                kitField.reset();
                                kitField.store.filterBy(function(rec){
                                    return kits.indexOf(rec.get('name')) > -1;
                                });
                            }
                        }
                    },{
                        xtype: 'combo',
                        itemId: 'sampleKit',
                        fieldLabel: 'Sample Kit',
                        queryMode: 'local',
                        allowBlank: false,
                        editable: false,
                        displayField: 'name',
                        valueField: 'name',
                        disabled: true,
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            schemaName: 'sequenceanalysis',
                            queryName: 'illumina_sample_kits',
                            columns: '*',
                            autoLoad: true
                        })
                    },{
                        xtype: 'textfield',
                        fieldLabel: 'Chemistry',
                        itemId: 'chemistry',
                        value: 'Amplicon',
                        section: 'Header'
                    },{
                        xtype: 'numberfield',
                        section: 'Reads',
                        minValue: 0,
                        value: 251,
                        itemId: 'cycles1',
                        fieldLabel: 'Cycles Read 1'
                    },{
                        xtype: 'numberfield',
                        section: 'Reads',
                        minValue: 0,
                        value: 251,
                        helpPopup: 'Leave blank for single-end reads',
                        itemId: 'cycles2',
                        fieldLabel: 'Cycles Read 2'
                    },{
                        xtype: 'combo',
                        itemId: 'genomeFolder',
                        fieldLabel: 'Genome Folder (optional)',
                        queryMode: 'local',
                        allowBlank: true,
                        editable: false,
                        displayField: 'label',
                        valueField: 'label',
                        store: Ext4.create('LABKEY.ext4.data.Store', {
                            schemaName: 'sequenceanalysis',
                            queryName: 'illumina_genome_folders',
                            columns: '*',
                            autoLoad: true
                        })
                    },{
                        xtype: 'numberfield',
                        itemId: 'IEMFileVersion',
                        fieldLabel: 'IEMFileVersion',
                        section: 'Header',
                        hidden: true,
                        value: 4
                    }]
                },{
                    title: 'Preview Header',
                    itemId: 'previewTab',
                    bodyStyle: 'padding: 5px;'
                },{
                    title: 'Preview Samples',
                    itemId: 'previewSamplesTab',
                    bodyStyle: 'padding: 5px;'
                }]
            }],
            buttons: [{
                text: 'Download For Instrument',
                handler: this.onDownload,
                scope: this
            },{
                text: 'Download Table Only',
                hidden: true,
                handler: this.onSimpleTableDownload,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    var url = LABKEY.ActionURL.getParameter('srcURL');
                    if(url)
                        window.location = decodeURIComponent(url);
                    else
                        window.location = LABKEY.ActionURL.buildURL('project', 'start');

                }
            }]
        });

        this.callParent();

        //button should require selection, so this should never happen...
        if(!this.rows || !this.rows.length){
            this.hide();
            alert('No Samples Selected');
        }
    },

    onBeforeTabChange: function(){
        var application = this.down('#defaultTab').down('#application').getValue();
        if(!application){
            Ext4.Msg.alert('Error', 'You must choose an application');
            return false;
        }
    },

    onTabChange: function(panel, newTab, oldTab){
        if (newTab.itemId == 'defaultTab'){

        }
        else if (newTab.itemId == 'previewTab'){
            this.populatePreviewTab();
        }
        else if (newTab.itemId == 'previewSamplesTab'){
            this.populatePreviewSamplesTab();
        }

    },

    populatePreviewSamplesTab: function(){
        var previewTab = this.down('#previewSamplesTab');

        var table = this.generateSamplesPreview();

        previewTab.removeAll();
        previewTab.add({
            border: false,
            xtype: 'container',
            defaults: {
                border: false
            },
            items: [table],
            buttonAlign: 'left',
            buttons: [{
                text: 'Edit Samples',
                hidden: true,
                scope: this,
                handler: this.onEditSamples
            }]
        });
    },

    populatePreviewTab: function(){
        var previewTab = this.down('#previewTab');

        var items = this.generateTemplatePreview();
        previewTab.removeAll();
        previewTab.add({
            border: false,
            xtype: 'form',
            defaults: {
                labelSeparator: '',
                labelWidth: 200,
                width: 500
            },
            items: items,
            buttonAlign: 'left',
            buttons: [{
                text: 'Edit Sheet',
                scope: this,
                handler: this.onEditTemplate
            }]
        });
    },

    parseText: function(text){
        text = text.split(/[\r\n|\r|\n]+/g);

        var vals = {};
        var activeSection = '';
        var errors = [];
        Ext4.each(text, function(line, idx){
            if(!line)
                return;

            line = line.split(/[\,|\t]+/g);

            if(line.length > 2)
                errors.push('Error reading line ' + (idx+1) + '. Line contains too many elements: "' + line.join(',') + '"');

            var prop = line.shift();
            if(prop.match(/^\[/)){
                prop = prop.replace(/\]|\[/g, '');
                if(this.sectionNames.indexOf(prop) == -1)
                    errors.push('Unknown section name: ' + prop);
                if(this.ignoredSectionNames.indexOf(prop) != -1)
                    errors.push('Cannot edit section: [' + prop + '] from this page');

                activeSection = prop;
                return;
            }

            if(!vals[activeSection])
                vals[activeSection] = [];

            var val = line.join('');
            vals[activeSection].push([prop, val]);
        }, this);

        if(errors.length){
            Ext4.Msg.alert("Error", errors.join('<br>'));
            return false;
        }

        return vals;
    },

    buildValuesObj: function(){
        var errors = [];
        this.down('form').items.each(function(field){
            if(field.isFormField && !field.isValid()){
                Ext4.each(field.getErrors(), function(e){
                    errors.push(field.fieldLabel + ': ' + e);
                }, this);
            }
        });

        if(errors.length){
            errors = Ext4.unique(errors);
            errors = errors.join('<br>');
            Ext4.Msg.alert("Error", "There are errors in the form:<br>" + errors);
            return;
        }

        var application = this.down('#application').getValue();

        var valuesObj = {};
        this.down('form').items.each(function(item){
            if(item.section){
                if(!valuesObj[item.section])
                    valuesObj[item.section] = [];

                if (item.section == 'Reads')
                    valuesObj[item.section].push([item.getValue()]);
                else
                    valuesObj[item.section].push([item.fieldLabel, item.getValue()]);
            }
            else if (item.itemId == 'application' || item.itemId == 'sampleKit'){
                var recIdx = item.store.find(item.valueField, item.getValue());
                if (recIdx == -1)
                    return;

                var rec = item.store.getAt(recIdx);

                //if we have a custom application, we only support the JSON field
                if (application != 'Custom'){
                    if (rec.get('workflowname')){
                        if(!valuesObj['Header'])
                            valuesObj['Header'] = [];

                        valuesObj['Header'].push(['Workflow', rec.get('workflowname')]);
                    }

                    if (rec.get('settings')){
                        if(!valuesObj['Settings'])
                            valuesObj['Settings'] = [];

                        var settings = Ext4.decode(rec.get('settings'));
                        for (var i in settings){
                            valuesObj['Settings'].push([i, settings[i]]);
                        }
                    }

                    if (item.itemId == 'application'){
                        valuesObj['Header'].push(['Application', rec.get('name')]);
                    }

                    if (item.itemId == 'sampleKit'){
                        valuesObj['Header'].push(['Assay', rec.get('name')]);
                    }
                }

                if (rec.get('json')){
                    if (item.itemId == 'sampleKit' && application == 'Custom'){
                        return;
                    }

                    var json = Ext4.decode(rec.get('json'));
                    for (var section in json){
                        var array = json[section];
                        if(!valuesObj[section])
                            valuesObj[section] = [];

                        valuesObj[section] = valuesObj[section].concat(json[section]);
                    }
                }
            }
        }, this);

        return valuesObj;
    },

    generateTemplatePreview: function(){
        var obj = this.buildValuesObj();
        if (!obj)
            return;

        var rows = [];
        Ext4.each(this.sectionNames, function(section){
            if(section != 'Data'){
                rows.push({
                    xtype: 'displayfield',
                    fieldLabel: '<b>[' + section + ']</b>'
                });

                Ext4.each(obj[section], function(row){
                    var value = Ext4.isDate(row[1]) ? row[1].format('m/d/Y') : row[1];

                    rows.push({
                        xtype: 'displayfield',
                        fieldLabel: row[0] ? row[0].toString() : '',
                        value: value
                    });
                }, this);
            }
        }, this);

        return rows;
    },

    generateSamplesPreview: function(){
        var rows = this.getDataSectionRows();
        var table = {
            layout: {
                type: 'table',
                columns: rows[0].length,
                defaults: {
                    border: false
                }
            },
            items: []
        };

        this.redGreenText(rows);

        Ext4.each(rows, function(row, idx){
            Ext4.each(row, function(cell){
                table.items.push({
                    tag: 'div',
                    autoEl: {
                        style: 'padding: 5px;'
                    },
                    border: false,
                    style: idx ? null : 'border-bottom: black medium solid;',
                    html: Ext4.isEmpty(cell) ? '&nbsp;' : cell.toString()
                });

            }, this);
        }, this);

        return table;
    },

    generateHeaderArray: function(){
        var obj = this.buildValuesObj();
        if (!obj)
            return;

        var rows = [];
        Ext4.each(this.sectionNames, function(section){
            if(section != 'Data'){
                rows.push(['[' + section + ']']);
                Ext4.each(obj[section], function(row){
                    var value = Ext4.isDate(row[1]) ? row[1].format('m/d/Y') : row[1];
                    var thisRow = [row[0]];
                    if(!Ext4.isEmpty(value))
                        thisRow.push(value);

                    rows.push(thisRow);
                }, this);
            }
        }, this);

        return rows;
    },

    generateHeaderText: function(){
        var rowArray = [];
        Ext4.each(this.generateHeaderArray(), function(row){
            rowArray.push(row.join(','))
        }, this);

        return rowArray.join('\n');
    },

    COL_IDX7: 6,
    COL_IDX5: 8,

    redGreenText : function(rows){
        var coloredString;
        var idx7 = [];
        var idx5 = [];

        for(var i = 1; i < rows.length; i++){ //skip header row
            coloredString = '';
            if (rows[i][this.COL_IDX7]){
                for(var q = 0; q < rows[i][this.COL_IDX7].length; q++){
                    var color;
                    if(rows[i][this.COL_IDX7].charAt(q) == 'A' || rows[i][this.COL_IDX7].charAt(q) == 'C'){
                        color = 'red';
                    }
                    else if(rows[i][this.COL_IDX7].charAt(q) == 'G' || rows[i][this.COL_IDX7].charAt(q) == 'T'){
                        color = 'green';
                    }
                    coloredString += '<span style="color:' + color + '">' + rows[i][this.COL_IDX7].charAt(q) + '</span>';
                    if (q >= idx7.length)
                        idx7.push([]);
                    idx7[q].push(color);
                }
                rows[i][this.COL_IDX7] = '<span style="font-family:monospace; font-size:12pt">' + coloredString + '</span>';
            }

            coloredString = '';
            if (rows[i][this.COL_IDX5]){
                for(var q = 0; q < rows[i][this.COL_IDX5].length; q++){
                    var color;
                    if(rows[i][this.COL_IDX5].charAt(q) == 'A' || rows[i][this.COL_IDX5].charAt(q) == 'C'){
                        color = 'red';
                    }
                    else if(rows[i][this.COL_IDX5].charAt(q) == 'G' || rows[i][this.COL_IDX5].charAt(q) == 'T'){
                        color = 'green';
                    }
                    coloredString += '<span style="color:' + color + '">' + rows[i][this.COL_IDX5].charAt(q) + '</span>';
                    if (q >= idx5.length)
                        idx5.push([]);

                    idx5[q].push(color);
                }
                rows[i][this.COL_IDX5] = '<span style="font-family:monospace; font-size:12pt">' + coloredString + '</span>';
            }
        }

        var string7 = '';
        for (var j=0;j<idx7.length;j++){
            string7 += Ext4.unique(idx7[j]).length == 1 ? 'X' : '&nbsp;';
        }

        var string5 = '';
        for (var j=0;j<idx5.length;j++){
            string5 += Ext4.unique(idx5[j]).length == 1 ? 'X' : '&nbsp;';
        }

        //TODO: error messages, also validate logic
        var newRow = new Array();
        newRow[this.COL_IDX7] = '<span style="font-family:monospace; font-size:12pt">' + string7 + '</span>';
        newRow[this.COL_IDX5] = '<span style="font-family:monospace; font-size:12pt">' + string5 + '</span>';
        //rows.push(newRow);
    },

    getDataSectionRows: function(){
        var exportRows = [];

        var genomeFolder = '';
        var genomeField = this.down('#genomeFolder');
        if (genomeField.getValue()){
            var recIdx = genomeField.store.find(genomeField.valueField, genomeField.getValue());
            var rec = genomeField.store.getAt(recIdx);
            genomeFolder = rec.get('folder');
        }

        var sampleColumns = [
            ['Sample_ID', 'rowid'],
            ['Sample_Name', 'name'],
            ['Sample_Plate', ''],
            ['Sample_Well', ''],
            ['Sample_Project', ''],
            ['I7_Index_ID', 'barcode5'],
            ['index', 'barcode5/sequence'],
            ['I5_Index_ID', 'barcode3'],
            ['index2', 'barcode3/sequence'],
            ['Description', ''],
            ['GenomeFolder', '', genomeFolder]
        ];

        var headerRow = [];
        Ext4.each(sampleColumns, function(col){
            headerRow.push(col[0]);
        }, this);
        exportRows.push(headerRow);

        Ext4.each(this.rows, function(row){
            var toAdd = [];
            Ext4.each(sampleColumns, function(col){
                if (col.length > 2){
                    toAdd.push(col[2]);
                }
                else {
                    toAdd.push(row[col[1]]);
                }
            }, this);
            exportRows.push(toAdd);
        }, this);

        return exportRows;
    },

    generateSampleText: function(){
        var text = '';
        var rows = this.getDataSectionRows();
        Ext4.each(rows, function(row){
            text += row.join(',') + '\n';
        }, this);

        return text;
    },

    getFluorArray: function(sequence){
        var FLUOR_MAP = {
            A: 'RED',
            C: 'RED',
            T: 'GREEN',
            G: 'GREEN'
        }

    //    var RC_FLUOR_MAP = {
    //        T: 'RED',
    //        G: 'RED',
    //        A: 'GREEN',
    //        C: 'GREEN'
    //    }

        var fluors = [];
        for(var pos = 0, char; pos < sequence.length; pos++){
            char = sequence[pos].toUpperCase();
            fluors.push(FLUOR_MAP[char]);
        }
        return fluors;
    },

    validateTemplate: function(){
        //TODO
    },

    onEditTemplate: function(btn){
        var tab = this.down('#previewTab');
        tab.removeAll();
        tab.add({
            xtype: 'form',
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'This view allows you to edit the raw text in the sample sheet.  The sample sheet is divided into sections, with each section beginning with a term in brackets (ie. \'[Header]\').  The supported section names are: ' + this.sectionNames.join(', ') + '; however, Data cannot be edited through this page.  Within each section, you can enter rows as name/value pairs, which are separated by a comma. When you are finished editing, hit \'Done Editing\' to view the result.<br><br>NOTE: None of the fields on the General Info tab will be included if you save this as an application.',
                width: 800,
                style: 'padding-bottom: 10px;',
                border: false
            },{
                xtype: 'textarea',
                itemId: 'sourceField',
                width: 800,
                height: 400,
                value: this.generateHeaderText()
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Done Editing',
                scope: this,
                handler: this.onDoneEditing
            },{
                text: 'Cancel',
                scope: this,
                handler: this.populatePreviewTab
            }]
        })
    },

    onEditSamples: function(btn){
        //TODO
    },

    onDoneEditing: function(){
        if(this.down('#previewTab').down('#sourceField')){
            var field = this.down('#sourceField');
            if(field.isDirty()){
                var val = this.parseText(field.getValue());
                if(val === false)
                    return false;

                this.setValuesFromText(val);
            }
        }
        this.populatePreviewTab();
    },

    setValuesFromText: function(values){
        var json = {};
        var form = this.down('#defaultTab');
        for (var section in values){
            if (section == 'Reads'){
                if (values[section].length > 0)
                    this.down('#cycles1').setValue(values[section][0]);

                if (values[section].length > 1)
                    this.down('#cycles2').setValue(values[section][1]);
            }
            else {
                Ext4.each(values[section], function(pair){
                    var field = form.items.findBy(function(item){
                        return item.section == section && item.fieldLabel == pair[0];
                    }, this);

                    if(field){
                        field.setValue(pair[1])
                    }
                    else {
                        if(!json[section])
                            json[section] = [];

                        json[section].push(pair)
                    }
                }, this);
            }
        }

        var applicationField = this.down('#defaultTab').down('#application');
        var recIdx = applicationField.store.find('name', 'Custom');
        if(recIdx == -1){
            var rec = LDK.StoreUtils.createModelInstance(applicationField.store, {name: 'Custom'});
            applicationField.store.add(rec);
        }
        else {
            applicationField.store.getAt(recIdx).set('json', Ext4.JSON.encode(json));
        }

        applicationField.setValue('Custom');
    },

    onSimpleTableDownload: function(btn){
        if(this.onDoneEditing() === false)
            return;

        if(!this.down('form').getForm().isValid())
            return;

        var fileNamePrefix = this.down('#defaultTab').down('#fileName').getValue();
        if(!fileNamePrefix){
            alert('Must provide the flow cell Id, which will be used as the filename.  If you do not know this, fill out another value and rename the file later.');
            return;
        }

        var text = this.getSimpleTableOutput();
        LABKEY.Utils.convertToTable({
            fileNamePrefix: fileNamePrefix,
            delim: 'COMMA',
            newlineChar: '\r\n',
            exportAsWebPage: LABKEY.ActionURL.getParameter('exportAsWebPage'),
            rows: text
        });
    },

    onDownload: function(btn){
        if(this.onDoneEditing() === false)
            return;

        if(!this.down('form').getForm().isValid())
            return;

        var fileNamePrefix = this.down('#defaultTab').down('#fileName').getValue();
        if(!fileNamePrefix){
            alert('Must provide the flow cell Id, which will be used as the filename.  If you do not know this, fill out another value and rename the file later.');
            return;
        }

        var text = this.getTableOutput();
        LABKEY.Utils.convertToTable({
            fileNamePrefix: fileNamePrefix,
            delim: 'COMMA',
            newlineChar: '\r\n',
            exportAsWebPage: LABKEY.ActionURL.getParameter('exportAsWebPage'),
            rows: text
        });
    },

    getTableOutput: function(){
        var text = this.generateHeaderArray();
        text.push(['[Data]']);
        return text.concat(this.getDataSectionRows());
    },

    getSimpleTableOutput: function(){
//        var text = [
//            ['Version 2'],
//            ['ID', this.down('#defaultTab').down('#fileName').getValue()],
//            ['Assay', this.down('#defaultTab').down('#sampleKit').getValue()],
//            ['IndexReads', 2],
//            ['IndexCycles', 8]
//        ];
//        return text.concat(this.getDataSectionRows());
        return this.getDataSectionRows();
    },

    onSaveApplication: function(btn){
        //if we're editing the source, need to save first
        if(this.onDoneEditing() === false)
            return;

        var field = this.down('#defaultTab').down('#application');
        var rec = field.store.getAt(field.store.find('name', field.getValue()));

        if(!rec.dirty && !rec.phantom){
            alert('This application is already saved');
        }
        else {
            if(rec.phantom || !rec.get('editable')){
                var msg = 'Choose a name for this application';
                if(!rec.get('editable'))
                    msg = 'This application cannot be edited.  Please choose a different name:';

                Ext4.Msg.prompt('Choose Name', msg, function(btn, msg){
                    if(btn == 'ok'){
                        if(Ext4.isEmpty(msg)){
                            alert('Must enter a name');
                            this.onSaveApplication();
                            return;
                        }

                        var idx = field.store.find('name', msg);
                        if(idx != -1){
                            alert('Error: name is already is use');
                            this.onSaveApplication();
                            return;
                        }
                        rec.set('name', msg);
                        this.down('#defaultTab').down('#application').setValue(msg);
                        this.saveApplication(rec);
                    }
                }, this);
            }
            else {
                this.saveApplication(rec);
            }
        }
    },

    saveApplication: function(rec){
        rec.set('editable', true);
        var config = {
            schemaName: 'sequenceanalysis',
            queryName: 'illumina_applications',
            rows: [rec.data],
            scope: this,
            success: function(){
                var field = this.down('#defaultTab').down('#application');
                field.store.load();
            },
            failure: LDK.Utils.getErrorCallback({
                logToMothership: true
            })
        }

        if(rec.phantom){
            LABKEY.Query.insertRows(config)
        }
        else {
            LABKEY.Query.updateRows(config)
        }
    }
});