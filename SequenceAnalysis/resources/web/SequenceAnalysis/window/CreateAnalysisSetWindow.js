Ext4.define('SequenceAnalysis.window.CreateAnalysisSetWindow', {
    extend: 'Ext.window.Window',

    statics: {

    },


    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Create/Add To Analysis Set',
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'You are able to group files into sets, which allows you to create reusable units of data.  For example, you might have read data or alignments from 12 subjects: 6 controls and 6 experimental.  '
            }]
        })

        this.callParent(arguments);
    }
});