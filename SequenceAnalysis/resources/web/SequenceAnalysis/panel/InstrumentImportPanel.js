/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('SequenceAnalysis.panel.InstrumentImportPanel', {
    extend: 'SequenceAnalysis.panel.BaseSequencePanel',
    jobType: 'illuminaImport',

    initComponent: function(){
        Ext4.apply(this, {
            width: '100%',
            defaults: {
                width: 400
            },
            buttons: [{
                text: 'Import Data',
                itemId: 'startAnalysis',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.add({
            title: 'Instrument Import',
            xtype: 'form',
            width: '100%',
            defaults: {
                width: 450,
                labelWidth: 220
            },
            items: [{
                xtype: 'displayfield',
                fieldLabel: 'Files to Process',
                value: this.fileNames.join('<br>'),
                border: false
            },{
                fieldLabel: 'Job Name',
                xtype: 'textfield',
                helpPopup: 'This is the name assigned to this job, which must be unique.  Results will be moved into a folder with this name.',
                name: 'jobName',
                itemId: 'jobName',
                allowBlank:false,
                maskRe: new RegExp('[A-Za-z0-9_]'),
                validator: function(val){
                    return (this.isValidProtocol === false ? 'Job Name Already In Use' : true);
                }
//            },{
//                xtype: 'textfield',
//                itemId: 'runName',
//                name: 'runName',
//                fieldLabel: 'Run Name',
//                allowBlank: false,
//                helpPopup: 'This will be used to identify this instrument run.  Often the instrument automatically create this.',
//                value: LABKEY.ActionURL.getParameter("runName")
            },{
                itemId: 'platform',
                name: 'platform',
                fieldLabel: 'Sequence Platform',
                helpPopup: 'The sequence platform used to generate this data.',
                value: LABKEY.ActionURL.getParameter("platform") ? LABKEY.ActionURL.getParameter("platform").toUpperCase() : null,
                xtype: 'labkey-combo',
                displayField: 'platform',
                valueField: 'platform',
                allowBlank: false,
                store: Ext4.create('LABKEY.ext4.data.Store', {
                    schemaName: 'sequenceanalysis',
                    queryName: 'sequence_platforms',
                    columns: 'platform',
                    autoLoad: true
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                triggerAction: 'all'
            },{
                xtype: 'labkey-combo',
                itemId: 'instrumentId',
                name: 'instrumentId',
                fieldLabel: 'Instrument Id',
                helpPopup: 'The Id of the instrument.  This should match the RowId of the instrument in the system\'s Instrument table.',
                value: LABKEY.ActionURL.getParameter("instrumentId"),
                displayField: 'displayname',
                valueField: 'rowid',
                store: Ext4.create('LABKEY.ext4.data.Store', {
                    schemaName: 'sequenceanalysis',
                    queryName: 'instruments',
                    columns: 'rowid,displayname',
                    autoLoad: true,
                    listeners: {
                        scope: this,
                        load: function(store){
                            this.down('#instrumentId').fireEvent('select', this.down('#instrumentId'));
                        }
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                triggerAction: 'all'
            },{
                xtype: 'datefield',
                itemId: 'runDate',
                name: 'runDate',
                fieldLabel: 'Run Date',
                helpPopup: 'The date the instrument run occurred.',
                value: LABKEY.ActionURL.getParameter("runDate")
            },{
                xtype: 'checkbox',
                itemId: 'autoCreateReadsets',
                name: 'autoCreateReadsets',
                helpPopup: 'Normally, the system expects the readset to have been created ahead of this step.  At the time, you would have uploaded the sample name, barcodes, subjectId, etc.  The CSV sample sheet you are importing here should contain the RowId of these readsets, which is used to connect the incoming sequence data with those previously imported readsets.  If the sample id from the sample sheet you are importing does not match an existing readset, you can choose to have one automatically created.',
                fieldLabel: 'Autocreate readsets',
                value: false
            },{
                xtype: 'textfield',
                itemId: 'fastqPrefix',
                name: 'fastqPrefix',
                helpPopup: 'To import Illumina data, you will have supplied the CSV file with sample information.  By default, the system will attempt to load all FASTQ files in the same directory as that file.  If you provide a prefix, only FASTQ files beginning with this prefix will be imported.',
                fieldLabel: 'FASTQ Prefix (Illumina Only)',
                value: LABKEY.ActionURL.getParameter("fastqPrefix")
            }]
        });
    },

    initFiles: function(){

    },

    getJsonParams: function(config){
        var fields = this.callParent(arguments);

        if (!fields)
            return;

        fields.runName = fields.jobName;

        return fields;
    },

    onSubmit: function(){
        var json = this.getJsonParams();
        if(!json)
            return false;

        json.inputFiles = [];
        Ext4.Array.forEach(LABKEY.ActionURL.getParameterArray('file'), function(f){
            var path = LABKEY.ActionURL.getParameter('path') || '';
            json.inputFiles.push({fileName: path + f});
        }, this);

        if (!json.inputFiles.length){
            Ext4.Msg.alert('Error', 'No files provided');
            return;
        }

        this.startAnalysis(json);
    }
});


