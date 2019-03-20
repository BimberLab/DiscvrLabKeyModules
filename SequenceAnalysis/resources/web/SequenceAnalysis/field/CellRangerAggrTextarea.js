Ext4.define('SequenceAnalysis.field.CellRangerAggrTextarea', {
    extend: 'Ext.form.field.TextArea',
    alias: 'widget.sequenceanalysis-aggr-textarea',

    initComponent: function () {
        Ext4.apply(this, {
            height: 150,
            listeners: {
                scope: this,
                afterrender: function () {
                    this.el.swallowEvent(['keypress', 'keydown']);

                    var lines = ['RowId\tName'];
                    var outputFileMap = this.up('window').outputFileMap;
                    Ext4.Array.forEach(this.up('window').outputFileIds, function(outputFileId){
                        var so = outputFileMap[outputFileId];
                        lines.push([outputFileId, so ? so.name : ''].join('\t'));
                    }, this);

                    this.setValue(lines.join('\n'));
                }
            }
        });

        this.callParent();
    },

    getToolParameterValue : function(){
        return this.getSubmitValue();
    },

    getSubmitValue: function(){
        var val = this.callParent(arguments);
        val = this.processText(val);

        if (val){
            var ret = [];
            Ext4.Array.forEach(val, function(line){
                ret.push(line.join(','));
            }, this);

            return ret.join('<>');
        }

        return null;
    },

    processText: function(val){
        if (val){
            val = LDK.Utils.CSVToArray(val, '\t');
        }

        return val;
    }
});