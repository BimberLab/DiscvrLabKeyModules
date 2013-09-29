Ext4.define('SequenceAnalysis.panel.AlignerPanel', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.alignerpanel',

    initComponent: function(){
        Ext4.apply(this, {
            width: '100%'
            ,bodyBorder: false
            ,style: 'padding: 5px;'
            ,itemId: 'alignerpanel'
            ,border: true
            ,defaults: {
                width: 350
            }
            ,items: [{
                xtype: 'labkey-combo',
                fieldLabel: 'Aligner',
                displayField: 'displayname',
                valueField: 'name',
                itemId: 'alignerField',
                name: 'aligner',
                allowBlank: false,
                value: this.value || null,
                store: Ext4.create('LABKEY.ext4.data.Store', {
                    schemaName: 'sequenceanalysis',
                    queryName: 'aligners',
                    columns: '*',
                    autoLoad: true,
                    listeners: {
                        scope: this,
                        load: function(store){
                            this.down('#alignerField').fireEvent('select', this.down('#alignerField'));
                        }
                    }
                }),
                forceSelection:true,
                typeAhead: true,
                lazyInit: false,
                queryMode: 'local',
                triggerAction: 'all',
                listeners: {
                    scope: this,
                    select: this.setAligner,
                    afterrender: this.setAligner
                }
            }]
        });

        this.callParent(arguments);
    },

    setAligner: function(field){
        var aligner = field.getValue();
        var recIdx = field.store.find('name', field.getValue(), null, null, null, true);

        if(recIdx == -1){
            return;
        }
        var rec = field.store.getAt(recIdx);

        this.items.each(function(c){
            if(c.fieldType=='alignParam')
                this.remove(c);
        }, this);

        var items = rec.get('jsonconfig') ? Ext4.JSON.decode(rec.get('jsonconfig')) : new Array();

        Ext4.each(items, function(i, idx){
            Ext4.applyIf(i, {
                xtype: 'numberfield',
                border: false,
                bodyStyle:'padding:5px 5px 0',
                minValue:0,
                msgTarget: 'qtip',
                fieldType: 'alignParam'
            });
            this.insert(1+idx, i);
        }, this);

        if(rec.get('description')){
            this.insert(1, {
                xtype: 'displayfield',
                fieldLabel: 'Description',
                width: 800,
                value: rec.get('description'),
                border: false,
                fieldType: 'alignParam',
                style: 'padding-bottom: 5px;'
            });
        }

        var pairedEndField = this.up('#sequenceAnalysisPanel').down('#usePairedEnd');
        if(pairedEndField && pairedEndField.getValue() && items[0].name != 'pairedEnd'){
            alert("This aligner does not support paired end reads")
        }
    },

    isValid: function(){
        return true;
    }
});
