Ext4.define('SequenceAnalysis.field.GenomeField', {
    extend: 'LABKEY.ext4.ComboBox',
    alias: 'widget.sequenceanalysis-genomefield',

    initComponent: function () {
        Ext4.apply(this, {
            forceSelection: true,
            displayField: 'name',
            valueField: 'rowid',
            queryMode: 'local',
            triggerAction: 'all',
            store: {
                type: 'labkey-store',
                schemaName: 'sequenceanalysis',
                queryName: 'reference_libraries',
                filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                columns: 'name,rowid',
                sort: 'name',
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                autoLoad: true
            }
        });

        this.callParent(arguments);
    },

    getSubmitValue: function(){
        var val = this.callParent(arguments);
        if (Ext4.isArray(val)) {
            val = val.join(';');
        }

        return val;
    },

    getToolParameterValue : function(){
        return this.getSubmitValue();
    },

    setValue: function(val) {
        if (this.multiSelect && val && Ext4.isString(val)) {
            val = val.split(this.delimiter);
            this.callParent([val]);
        }
        else {
            this.callParent(arguments);
        }
    }
});