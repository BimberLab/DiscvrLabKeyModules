Ext4.define('SequenceAnalysis.form.field.VariantFieldSelector', {
    extend: 'Ext.form.field.TextArea',
    alias: 'widget.sequenceanalysis-variantfieldselector',
    mode: 'site',
    height: 400,

    siteDefaults: [
        'CHROM',
        'POS',
        'REF',
        'ALT',
        'QUAL',
        'FILTER',
        'NSAMPLES',
        'NCALLED',
        'AC',
        'AF',
        'DP',
        'VAR',
        'NO-CALL',
        'TYPE',
        'INFO'
    ],

    genotypeDefaults: [
        'GT',
        'GQ',
        'DP',
        'AD'
        //'FILTER'
    ],

    initComponent: function() {
        Ext4.apply(this, {
            width: 800
        });

        this.callParent();
    },

    onRender : function(ct, position){
        this.callParent(arguments);

        this.wrap = this.inputEl.wrap({
            tag: 'div',
            cls: 'x4-form-field-wrap'
        });

        this.linkDiv = this.wrap.createChild({
            tag: 'div',
            style: 'vertical-align:top;'
        }, this.inputEl);

        this.linkEl = this.linkDiv.createChild({
            tag: 'a',
            cls: 'labkey-text-link',
            html: 'Add Common Fields'
        });
        this.linkEl.on('click', this.addFields, this);
    },

    addFields: function(){
        if (this.mode == 'site'){
            this.setValue(this.siteDefaults.join('\n'));
        }
        else if (this.mode == 'genotype'){
            this.setValue(this.genotypeDefaults.join('\n'));
        }
    },

    onDestroy : function(){
        if (this.linkEl){
            this.linkEl.removeAllListeners();
            this.linkEl.remove();
        }

        if (this.linkDiv){
            this.linkDiv.removeAllListeners();
            this.linkDiv.remove();
        }

        if (this.instructionDiv){
            this.instructionDiv.removeAllListeners();
            this.instructionDiv.remove();
        }

        if (this.wrap){
            this.wrap.remove();
        }

        this.callParent(this);
    },

    setValue: function(){
        if (arguments[0]){
            arguments[0] = arguments[0].replace(/\r/g, '');
        }

        this.callParent(arguments);
    },

    getValue: function(){
        var ret = this.callParent(arguments);
        ret = ret.replace(/\r/g, '');

        return ret;
    }
});