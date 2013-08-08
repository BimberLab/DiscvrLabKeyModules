Ext4.define('SequenceAnalysis.panel.AssemblyPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.assemblypanel',
    initComponent: function(){
        Ext4.apply(this, {
            itemId: 'assemblyPanel',
            border: false,
            width: 'auto',
            listeners: {
                scope: this,
                change: function(field){
                    if(field.getValue())
                        this.expand();
                }
            },
            items: [{
                fieldLabel: 'Assemble Unaligned Reads',
                renderData: {
                    helpPopup: 'If checked, unaligned sequences will be assembled into contigs using CAP3.  This was designed to help identify novel alleles in sequence based genotyping.  A read will be considered to be unaligned if it does not match a reference with fewer than the specified number of high-confidence mismatches'
                },
                name: 'assembleUnaligned',
                itemId: 'assembleUnaligned',
                xtype: 'checkbox',
                disabled: false,
                scope: this,
                handler: function(c, val){
                    this.up('form').down('#assemblyOptions').setVisible(c.checked);
                }
            },{
                xtype: 'fieldset',
                style: 'padding:5px;',
                width: 'auto',
                hidden: true,
                hideMode: 'offsets',
                itemId: 'assemblyOptions',
                items: [{
                    fieldLabel: 'Assembly Percent Identity',
                    renderData: {
                        helpPopup: 'Unaligned sequences will be assembled using this percent identity'
                    },
                    name: 'assembleUnalignedPct',
                    xtype: 'numberfield',
                    value: 99.5
                },{
                    fieldLabel: 'Min Sequences Per Contig',
                    renderData: {
                        helpPopup: 'Only contigs with at least this many sequences will be reported'
                    },
                    name: 'minContigsForNovel',
                    xtype: 'numberfield',
                    value: 3
                }]
            }]
        });

        this.callParent(arguments);
    }
});
