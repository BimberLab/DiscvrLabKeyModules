Ext4.define('SequenceAnalysis.panel.IntervalPanel', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.sequenceanalysis-intervalpanel',

    initComponent: function (){
        Ext4.apply(this, {
            //width: '100%',
            minWidth: 1150,
            style: 'margin-top: 20px;padding-top: 10px;padding-bottom: 10px',
            title: 'Select Intervals',
            border: true,
            defaults: {
                border: false
            },
            items: [{
                html: 'This allows you to provide either a list of intervals, or a file (such as a BED file) specifying specific intervals that will included.',
                border: false,
                maxWidth: 1000,
                style: 'padding-bottom: 20px;'
            },{
                xtype: 'panel',
                bodyStyle: 'padding: 10px;',
                border: false,
                width: 1060,
                items: [{
                    xtype: 'radiogroup',
                    fieldLabel: 'Interval Source',
                    itemId: 'source',
                    columns: 1,
                    isFormField: false,
                    value: 'none',
                    items: [{
                        boxLabel: 'None',
                        formId: this.getId(),
                        inputValue: 'none',
                        name: 'source',
                        checked: true
                    },{
                        boxLabel: 'Manual Entry',
                        formId: this.getId(),
                        inputValue: 'manual',
                        name: 'source'
                    },{
                        boxLabel: 'From Genome',
                        formId: this.getId(),
                        inputValue: 'fromGenome',
                        name: 'source'
                    },{
                        boxLabel: 'By File Id',
                        formId: this.getId(),
                        inputValue: 'byFileId',
                        name: 'source'
                    }],
                    listeners: {
                        scope: this,
                        change: function (field, val){
                            var target = this.down('#fields');
                            target.removeAll();
                            if (val.source == 'fromGenome'){
                                target.add(this.getGenomeItems());
                            }
                            else if (val.source == 'byFileId'){
                                target.add(this.getFileIdItems());
                            }
                            else if (val.source == 'manual'){
                                target.add(this.getManualItems());
                            }
                        },
                        afterrender: function (field){
                            field.fireEvent('change', field, field.getValue());
                        }
                    }
                },{
                    itemId: 'fields',
                    style: 'padding-top: 20px;padding-bottom: 20px;',
                    border: false,
                    defaults: {
                        width: 600
                    }
                }]
            }]
        });

        this.callParent();
    },

    getGenomeItems: function(){
        return [{
            xtype: 'sequenceanalysis-genomefileselectorfield',
            fieldLabel: 'Track',
            allowBlank: false,
            itemId: 'fileId',
            genomeId: '',
            extensions: ['vcf', 'vcf.gz', 'bed', 'gff', 'gtf']
        }];
    },

    getFileIdItems: function(){
        return [{
            xtype: 'ldk-expdatafield',
            fieldLabel: 'File Id',
            allowBlank: false,
            itemId: 'fileId'
        }];
    },

    getManualItems: function(){
        return [{
            html: 'Enter intervals, one per line, in the format: chr01:10-200 (no spaces).  Please be sure your chromosome names match exactly or the job may fail.',
            minWidth: 200,
            maxWidth: 500,
            border: false
        },{
            xtype: 'textarea',
            fieldLabel: 'Intervals',
            allowBlank: false,
            itemId: 'intervals'
        }];
    },

    getValue: function(){
        var source = this.down('radiogroup').getValue().source;
        if (source && source != 'none'){
            var intervals = null;
            if (this.down('#intervals') && this.down('#intervals').getValue()){
                intervals = this.down('#intervals').getValue();
                intervals = Ext4.String.trim(intervals);
                intervals = intervals.replace(/[\s,;]+/g, ';');
            }

            return {
                source: source,
                intervals: intervals,
                fileId: this.down('#fileId') ? this.down('#fileId').getValue() : null
            }
        }
    },

    setValue: function(val){
        if (val){
            if (val.source){
                this.down('#source').setValue({source: val.source});
            }

            if (val.intervals && this.down('#intervals')){
                this.down('#intervals').setValue(val.intervals.split(';').join('\n'));
            }

            if (val.fileId && this.down('#fileId')){
                this.down('#fileId').setValue(val.fileId);
            }
        }
        else {
            this.down('radiogroup').setValue('none');
        }
    },

    getErrors: function(){
        var msgs = [];
        var source = this.down('radiogroup').getValue().source;
        if (source && source != 'none'){
            if (this.down('#fileId') && !this.down('#fileId').getValue()){
                msgs.push('Missing one or more required fields');
            }

            if (this.down('#intervals') && !this.down('#intervals').getValue()){
                msgs.push('Missing one or more required fields');
            }
        }

        msgs = Ext4.Array.unique(msgs);

        return msgs;
    }
});