Ext4.define('SequenceAnalysis.window.ImportSequencesFromFastaWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName, genomeChecked){
            Ext4.create('SequenceAnalysis.window.ImportSequencesFromFastaWindow', {
                dataRegionName: dataRegionName,
                genomeChecked: genomeChecked
            }).show();
        }
    },

    initComponent: function(){

        Ext4.apply(this, {
            modal: true,
            title: 'Import Sequences From FASTA',
            items: [{
                xtype: 'sequenceanalysis-importsequencesfromfastapanel',
                hideButtons: true,
                suppressTitle: true,
                actionName: 'importFastaSequences',
                dataRegionName: this.dataRegionName
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    this.down('panel').onSubmit(btn);
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    var win = btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    }
});