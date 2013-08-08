
Ext4.define('LABKEY.ext.VBDSearchFilterPanel', {
    extend: 'Ext.form.Panel',
    SCHEMA_NAME: 'lists',
    LIST_NAME: 'Samples',
    initComponent: function() {
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            border: true,
            collapsible: true,
            titleCollapse: true,
            minHeight: 225,
            layout: 'anchor',
            defaults: {
                anchor: '100%',
                padding: 5,
                labelWidth: 200,
                labelAlign: 'right',
                border: false
            },
            items: []
        });
        Ext4.applyIf(this,{
            title: 'Filters',
            fields: [],
            whereSqlFragment: null
        });

        this.callParent(arguments);

        this.addEvents('filterValuesChanged');

        this.configureFields();
    },

    configureFields: function() {
        Ext4.each(this.fields, function(field){
            this.add({
                xtype: 'fieldcontainer',
                fieldLabel: field.label,
                layout: 'hbox',
                height: 22,
                items: [
                    this.getDisplayField(field),
                    this.getInputField(field)
                ]
            });
        }, this);
    },

    getDisplayField: function(field) {
        return {
            xtype: 'fieldcontainer',
            hideLabel: true,
            layout: 'hbox',
            items: [
                {
                    xtype: 'image',
                    itemId: field.name + '-icon',
                    fieldName: field.name,
                    src: LABKEY.contextPath + '/_images/arrow_down.png',
                    width: 20,
                    listeners: {
                        scope: this,
                        el: {
                            scope: this,
                            click: function(){
                                this.toggleInput(field.name);
                            }
                        }
                    }
                },
                {
                    xtype: 'displayfield',
                    itemId: field.name + '-display',
                    value: 'All'
                }
            ]
        }
    },

    getInputField: function(field) {
        if (field.range)
        {
            return {
                xtype: 'fieldcontainer',
                hideLabel: true,
                itemId: field.name,
                name: field.name,
                hidden: true,
                layout: 'hbox',
                items: [
                    {
                        xtype: 'numberfield',
                        anchor: '50%',
                        itemId: field.name + '-gt',
                        name: field.name + '-gt',
                        fieldLabel: 'At least',
                        labelWidth: 60,
                        width: 110,
                        allowDecimals: false,
                        value: null,
                        listeners: {
                            scope: this,
                            buffer: 1000,
                            change: function(cmp) {
                                this.updateRangeFieldDisplay(cmp);
                                this.fireEvent('filterValuesChanged', this.getFieldValues());
                            },
                            blur: function(cmp){
                                var ltCmp = this.down('#' + field.name + '-lt');
                                if (cmp.getValue()!= null && ltCmp.getValue() != null)
                                    this.toggleInput(field.name);
                            }
                        }
                    },
                    {
                        xtype: 'label',
                        width: 10
                    },
                    {
                        xtype: 'numberfield',
                        anchor: '50%',
                        itemId: field.name + '-lt',
                        name: field.name + '-lt',
                        fieldLabel: 'and under',
                        labelWidth: 70,
                        width: 120,
                        allowDecimals: false,
                        value: null,
                        listeners: {
                            scope: this,
                            buffer: 1000,
                            change: function(cmp) {
                                this.updateRangeFieldDisplay(cmp);
                                this.fireEvent('filterValuesChanged', this.getFieldValues());
                            },
                            blur: function(cmp){
                                var gtCmp = this.down('#' + field.name + '-gt');
                                if (cmp.getValue()!= null && gtCmp.getValue() != null)
                                    this.toggleInput(field.name);
                            }
                        }
                    }
                ]
            }
        }
        else
        {
            return {
                xtype: 'checkcombo',
                width: 315,
                itemId: field.name,
                name: field.name,
                hidden: true,
                hideLabel: true,
                editable: false,
                allowBlank: true,
                displayField: 'value',
                valueField: 'value',
                store: this.getFieldStore(field),
                addAllSelector: true,
                expandToFitContent: true,
                listeners: {
                    scope: this,
                    buffer: 1000,
                    change: function(cmp) {
                        this.updateFieldDisplay(cmp);
                        this.fireEvent('filterValuesChanged', this.getFieldValues());
                    },
                    collapse: function(){
                        this.toggleInput(field.name);
                    }
                }
            }
        }
    },

    toggleInput: function(fieldName) {
        var cmp = this.down('#' + fieldName);
        var icon = this.down('#' + fieldName + '-icon');
        var display = this.down('#' + fieldName + '-display');
        if (cmp && icon && display)
        {
            if (cmp.isHidden())
            {
                icon.setSrc(LABKEY.contextPath + '/_images/arrow_up.png');
                icon.hide();
                display.hide();
                cmp.show();
                if (cmp.getXType() == 'checkcombo')
                {
                    cmp.expand();
                    if (cmp.getStore().getCount() == 0)
                    {
                        this.down('#' + fieldName).setLoading(true);
                        cmp.getStore().load({
                            scope: this,
                            callback: function(){
                                this.down('#' + fieldName).setLoading(false);
                            }
                        });
                    }
                }
            }
            else
            {
                icon.setSrc(LABKEY.contextPath + '/_images/arrow_down.png');
                cmp.hide();
                icon.show();
                display.show();
            }
        }
    },

    updateFieldDisplay: function(cmp) {
        var displayName = cmp.name + "-display";
        var displayCmp = this.down('#' + displayName);
        if (displayCmp)
        {
            var values = cmp.getValue();
            if (values.length == 1 && values[0] == "")
            {
                displayCmp.setValue("All");
                cmp.setValue(null);
            }
            else if (values.length == 0 || values.length == cmp.getStore().getCount())
            {
                displayCmp.setValue("All");
            }
            else
            {
                displayCmp.setValue(cmp.getValue().join(", "));
                Ext4.create('Ext.tip.ToolTip', {
                    target: displayCmp.el,
                    html: cmp.getValue().join(", ")
                });
            }
        }
    },

    updateRangeFieldDisplay: function(cmp) {
        var baseName = cmp.name.replace("-gt", "").replace("-lt", "");
        var displayCmp = this.down('#' + baseName + "-display");
        var gtCmp = this.down('#' + baseName + "-gt");
        var ltCmp = this.down('#' + baseName + "-lt");
        if (displayCmp && gtCmp && ltCmp)
        {
            var str = (gtCmp.getValue() != null ? "At least " + gtCmp.getValue() : "");
            str += (ltCmp.getValue() != null && str.length > 0 ? " and " : "");
            str += (ltCmp.getValue() != null ? "under " + ltCmp.getValue() : "");
            displayCmp.setValue(str);
        }
    },

    getFieldStore: function(field) {
        return {
            type: 'labkey-store',
            storeId: field.name + '-store',
            schemaName: this.SCHEMA_NAME,
            sql: this.getSelectDistinctSql(field),
            columns: 'tumorType_organSite',
            sort: 'tumorType_organSite',
            autoLoad: false
//            listeners: {
//                scope: this,
//                load: function(){
//                    this.down('#' + field.name).setLoading(false);
//                }
//            }
        };
    },

    getSelectDistinctSql: function(field) {
        return 'SELECT DISTINCT(' + field.name + ') as value '
            + ' FROM ' + this.SCHEMA_NAME + '.' + this.LIST_NAME
            + ' WHERE ' + field.name + ' IS NOT NULL AND ' + field.name + ' != \'\''
            + (this.whereSqlFragment ? ' AND ' + this.whereSqlFragment : '');
    },

    getFieldValues: function() {
        return this.getValues();
    }
});