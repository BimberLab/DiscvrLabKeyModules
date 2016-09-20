Ext4.define('SequenceAnalysis.panel.VariantMaskPanel', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.sequenceanalysis-variantmaskpanel',

    initComponent: function (){
        Ext4.apply(this, {
            //width: '100%',
            minWidth: 1150,
            style: 'margin-top: 20px;padding-top: 10px;padding-bottom: 10px',
            title: 'Variant Masking',
            border: true,
            defaults: {
                border: false
            },
            items: [{
                html: 'This allows you to provide a file, such as a BED or VCF file, that can be used to mask/filter overlapping sites.  A common example is masking repetitive regions.  You can either select from tracks associated with this genome, or enter a specific file using the LabKey file Id.  This is shown most commonly found from the sequence outputs table, where this column is typically shown.',
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
                    fieldLabel: 'Selection Type',
                    itemId: 'selectionType',
                    columns: 1,
                    isFormField: false,
                    items: [{
                        boxLabel: 'None',
                        inputValue: 'none',
                        name: 'selectionType',
                        checked: true
                    },{
                        boxLabel: 'From Genome',
                        inputValue: 'fromGenome',
                        name: 'selectionType'
                    },{
                        boxLabel: 'By File Id',
                        inputValue: 'byFileId',
                        name: 'selectionType'

                    }],
                    listeners: {
                        scope: this,
                        change: function (field, val){
                            var target = this.down('#fields');
                            target.removeAll();
                            if (val.selectionType == 'fromGenome'){
                                target.add(this.getGenomeItems());
                            }
                            else if (val.selectionType == 'byFileId'){
                                target.add(this.getFileIdItems());
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
            xtype: 'textfield',
            fieldLabel: 'Mask Name',
            allowBlank: false
        },{
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
            xtype: 'textfield',
            fieldLabel: 'Mask Name',
            allowBlank: false
        },{
            xtype: 'ldk-expdatafield',
            fieldLabel: 'File Id',
            allowBlank: false
        }];
    },

    getValue: function(){
        console.log('not implemented');
    },

    setValue: function(){
        console.log('not implemented');
    },

    getErrors: function(){
        console.log('not implemented');
    }
});