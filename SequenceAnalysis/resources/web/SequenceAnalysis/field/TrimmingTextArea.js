Ext4.define('SequenceAnalysis.field.TrimmingTextArea', {
    extend: 'Ext.form.field.TextArea',
    alias: 'widget.sequenceanalysis-trimmingtextarea',

    delimiter: ';',
    replaceAllWhitespace: true,

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

    getErrors: function(value){
        var errors = this.callParent(arguments);

        if (!this.allowBlank && Ext4.isEmpty(this.getSubmitValue())) {
            errors = errors.concat('Must enter a value');
        }

        return errors;
    },

    setValue: function(val){
        if (Ext4.isString(val)) {
            val = val.split(this.delimiter);
            val = val.join('\n');
        }

        this.callParent([val]);
    },

    getSubmitValue: function(){
        var val = this.callParent(arguments);
        val = this.processText(val);

        return val ? val.join(this.delimiter) : null;
    },

    getToolParameterValue : function(){
        return this.getSubmitValue();
    },

    processText: function(val){
        if (val){
            val = Ext4.String.trim(val);
            val = val.replace(/(\r\n|\n|\r)/gm,this.delimiter);
            if (this.replaceAllWhitespace) {
                val = val.replace(/ /g, '');
            }

            val = val.replace(new RegExp(this.delimiter + '+', 'g'), this.delimiter);
            val = val.replace(new RegExp('^' + this.delimiter + '|' + this.delimiter + '$', 'g'), '');
            val = val.split(this.delimiter);

            Ext4.Array.forEach(val, function(v, idx){
                val[idx] = Ext4.String.trim(v);
            }, this);
        }

        return val;
    }
});