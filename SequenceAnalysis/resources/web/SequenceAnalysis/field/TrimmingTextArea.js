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

        if (this.stripCharsRe && Ext4.isString(this.stripCharsRe)) {
            this.stripCharsRe = this.stripCharsRe.replaceAll('^/', '')
            this.stripCharsRe = this.stripCharsRe.split(/(?=\/)\//)
            if (this.stripCharsRe.length && this.stripCharsRe[0] === '') {
                this.stripCharsRe.shift()
            }
            this.stripCharsRe = new RegExp(this.stripCharsRe[0], this.stripCharsRe.length > 1 ? this.stripCharsRe[1] : null)
        }

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

            if (val && this.stripCharsRe) {
                val = val.replace(this.stripCharsRe, '');
            }

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