Ext4.define('SequenceAnalysis.panel.VariantScatterGatherPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.sequenceanalysis-variantscattergatherpanel',

    defaultFieldWidth: null,

    initComponent: function (){
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            },
            items: [{
                fieldLabel: 'Scatter/Gather Scheme',
                xtype: 'combo',
                plugins: ['combo-autowidth'],
                labelWidth: this.labelWidth,
                width: this.defaultFieldWidth,
                expandToFitContent: true,
                value: 'none',
                displayField: 'label',
                valueField: 'value',
                store: {
                    type: 'array',
                    fields: ['label', 'value'],
                    data: [
                        ['None', 'none'],
                        ['Chromosome/Contig', 'contig'],
                        ['Chunked', 'chunked'],
                        ['Fixed # Jobs', 'fixedJobs']
                    ]
                },
                helpPopup: 'If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file',
                name: 'scatterGatherMethod',
                forceSelection: true,
                allowBlank: false,
                listeners: {
                    scope: this,
                    change: function(field, val) {
                        var panel = this.down('#scatterGatherMethodOptions');
                        panel.removeAll();

                        var toAdd = [];
                        if (val === 'chunked') {
                            toAdd.push({
                                xtype: 'ldk-integerfield',
                                labelWidth: this.labelWidth,
                                name: 'scatterGather.basesPerJob',
                                fieldLabel: 'Bases/Job (Mbp)',
                                minValue: 0,
                                helpPopup: 'The genome will be divided into jobs, each targeting approximately the specified number of megabases per job',
                                value: 200
                            });

                            toAdd.push({
                                xtype: 'checkbox',
                                labelWidth: this.labelWidth,
                                name: 'scatterGather.allowSplitChromosomes',
                                fieldLabel: 'Allow Split Contigs',
                                value: true,
                                inputValue: true,
                                helpPopup: 'If true, a given chromosome/contig can be split between jobs.  Otherwise chromosomes are always intact across jobs.'
                            });
                        }
                        else if (val === 'fixedJobs') {
                            toAdd.push({
                                xtype: 'ldk-integerfield',
                                labelWidth: this.labelWidth,
                                name: 'scatterGather.totalJobs',
                                fieldLabel: 'Total # of Jobs',
                                minValue: 0,
                                helpPopup: 'The genome will be divided into jobs, with each taking an equal number of base-pairs',
                                value: 10
                            });
                        }

                        if (toAdd.length) {
                            panel.add(toAdd);
                        }
                    }
                }
            },{
                xtype: 'panel',
                border: false,
                width: this.defaultFieldWidth ? this.defaultFieldWidth + 20 : null,
                bodyStyle: '',
                defaults: {
                    border: false,
                    width: this.defaultFieldWidth
                },
                itemId: 'scatterGatherMethodOptions'
            }]
        });

        this.callParent(arguments);
    }
});