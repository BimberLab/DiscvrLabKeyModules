Ext4.define('SequenceAnalaysis.panel.SnpCallPanel', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.snpcallpanel',
    initComponent: function(){
        Ext4.apply(this, {
            xtype: 'fieldset',
            style: 'padding:5px;',
            bodyStyle: 'padding:5px;',
            defaults: {
                width: 350,
                minValue: 0
            },
            defaultType: 'numberfield',
            itemId: 'snpcallpanel',
            width: '100%',
            items: [{
//                fieldLabel: 'Min Alignment Length',
//                renderData: {
//                    helpPopup: 'Aligned sequences below this length will be ignored'
//                },
//                name: 'snp.minLength',
//                minValue: 0,
//                value: 75
//            },{
                //pval = 10^(qual/-10)
                fieldLabel: 'Min SNP Quality',
                renderData: {
                    helpPopup: 'SNPs with a quality score below this value will be converted to N'
                },
                name: 'snp.minQual',
                minValue: 0,
                value: 17
            },{
                fieldLabel: 'Min Avg SNP Quality',
                renderData: {
                    helpPopup: 'At each position, the average score will be calculated per base.  If the avg. score for a given base if below this threshold, all SNPs with this mutation will be converted to N'
                },
                name: 'snp.minAvgSnpQual',
                minValue: 0,
                value: 17
            },{
                fieldLabel: 'Min DIP Quality',
                renderData: {
                    helpPopup: 'DIPs (deletion/indel polymorphisms) with a quality score below this value will be converted to N.'
                },
                name: 'snp.minIndelQual',
                minValue: 0,
                value: 20
            },{
                fieldLabel: 'Use Neighborhood Quality',
                renderData: {
                    helpPopup: 'If selected, in addition to the quality of the SNP, the avg quality of the preceeding 4 bases and following 4 bases will be considered.  SNPs in regions of low quality will be converted to N'
                },
                name: 'snp.neighborhoodQual',
                xtype: 'checkbox',
                checked: false,
                hidden: true
            },{
                fieldLabel: 'Min Avg DIP Quality',
                renderData: {
                    helpPopup: 'At each DIP position, the average score will be calculated per base.  If the avg. score for a given base if below this threshold, all DIPs with this mutation will be converted to N'
                },
                name: 'snp.minAvgDipQual',
                minValue: 0,
                value: 25
//            },{
//                fieldLabel: 'Indel Percent Filter',
//                renderData: {
//                    helpPopup: 'If selected, at every position where an indel exists, all reads with indels are examined.  For that indel to pass, at least X% of total indels must have passed the above quality filters.  If not, all indels at this position are converted to N'
//                },
//                name: 'snp.minIndelPct',
//                minValue: 0,
//                value: 60
            }]
        });

        this.callParent(arguments)
    }
});
