/**
 * Designed to let the user reorganize files for sequence import, inferring file order by name
 *
 * @cfg targetGrid
 * @cfg sequencePanel
 */
Ext4.define('SequenceAnalysis.window.SequenceFileOrderWindow', {
    extend: 'Ext.window.Window',

    initComponent: function() {
        Ext4.apply(this, {
            title: 'Reorder Sequence Files',
            bodyStyle: 'padding: 5px;',
            modal: true,
            width: 700,
            defaults: {
                border: false
            },
            items: [{
                html: 'This helper allows you to reorder the input files, which is primarily important if you have paired-end data or multiple files per readset (such as a large sequence run that was split into multipe FASTQs).  Choose one of the options below to group files, or drag/drop them to reorder them.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'checkbox',
                fieldLabel: 'Data Is Paired End',
                labelWidth: 140,
                style: 'padding-bottom: 10px',
                helpPopup: 'Check this if the input data is paired end.  At the moment, the pipeline expects 2 FASTQ input files per readset.',
                value: this.sequencePanel.down('#usePairedEnd').getValue()
            },{
                xtype: 'button',
                text: 'Fill by Row',
                style: 'margin-right: 5px;',
                border: true,
                handler: function(btn){
                    var win = btn.up('window');

                }
            },{
                xtype: 'button',
                text: 'Fill by Column',
                style: 'margin-right: 5px;',
                border: true,
                handler: function(btn){
                    var win = btn.up('window');

                }
            },{
                xtype: 'button',
                text: 'Infer Based on Illumina Filename',
                style: 'margin-right: 5px;',
                border: true,
                handler: function (btn) {
                    var win = btn.up('window');

                }
            },{
                html: '<hr>'
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(){

    }
});