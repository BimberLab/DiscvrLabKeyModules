Ext4.define('SequenceAnalysis.field.TrimmingTextArea', {
    extend: 'Ext.form.field.TextArea',
    alias: 'widget.sequenceanalysis-trimmingtextarea',

    initComponent: function (){
        Ext4.apply(this, {
            height: 150,
            listeners: {
                afterrender: function () {
                    this.el.swallowEvent(['keypress', 'keydown']);
                }
            }
        });


        this.callParent();
    },

    getSubmitValue: function(){
        var val = this.callParent(arguments);
        val = this.processText(val);

        return val ? val.join(';') : null;
    },

    getToolParameterValue : function(){
        return this.getSubmitValue();
    },

    processText: function(val){
        if (val){
            val = Ext4.String.trim(val);
            val = val.replace(/(\r\n|\n|\r)/gm,";");
            val = val.replace(/(^;|;$)/g, '');
            val = val.replace(/(;+)/g, ';');
            val = val.split(';');

            Ext4.Array.forEach(val, function(v, idx){
                val[idx] = Ext4.String.trim(v);
            }, this);
        }

        return val;
    }
});