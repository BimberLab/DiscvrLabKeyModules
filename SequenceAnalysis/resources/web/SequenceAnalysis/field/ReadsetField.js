Ext4.define('SequenceAnalysis.field.ReadsetField', {
    extend: 'LDK.form.field.SimpleLabKeyCombo',
    fieldLabel: 'Readset',
    alias: 'widget.sequenceanalysis-readsetfield',

    initComponent: function(){
        Ext4.apply(this, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_readsets',
            displayField: 'name',
            valueField: 'rowid'
        });

        this.listConfig = this.listConfig || {};
        Ext4.apply(this.listConfig, {
            innerTpl: [
                '{[values["rowid"] + ") " + values["name"]]}' +
                //space added so empty strings render with full height
                '&nbsp;'
            ]
        });

        this.callParent(arguments);
    }
});