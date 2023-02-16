Ext4.define('SequenceAnalysis.field.IntervalField', {
    extend: 'Ext.form.field.TextArea',
    alias: 'widget.sequenceanalysis-intervalfield',

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
            val = val.replace(/ /g, '');
            val = val.replace(/;+/g, ';');
            val = val.replace(/(^;|;$)/g, '');
            val = val.split(';');

            Ext4.Array.forEach(val, function(v, idx){
                val[idx] = Ext4.String.trim(v);
            }, this);
        }

        return val;
    },

    validator: function(val) {
        val = this.processText(val);
        if (val) {
            var msgs = [];

            Ext4.Array.forEach(val, function(v, idx){
                var toTest = val[idx].split(':');
                if (toTest.length > 2){
                    msgs.push('Invalid interval: ' + v);

                }
                //NOTE: an interval with just a contig name is valid
                else if (toTest.length === 2) {
                    var coords = toTest[1].split('-');
                    if (coords.length !== 2) {
                        msgs.push('Invalid interval: ' + v);

                    }
                }
            }, this);

            msgs = Ext4.unique(msgs).join('\n');
            if (msgs){
                return msgs;
            }
        }

        return true;
    }
});