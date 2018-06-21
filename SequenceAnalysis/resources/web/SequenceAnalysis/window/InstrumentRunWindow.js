Ext4.define('SequenceAnalysis.window.InstrumentRunWindow', {
    extend: 'Ext.window.Window',

    initComponent: function(){
        if (!this.selectedRows.length){
            Ext4.Msg.alert('Error', 'No rows selected');
            return;
        }

        Ext4.apply(this, {
            title: 'Create/Add To Instrument Run',
            activeTab: 0,
            border: false,
            width: 510,
            bodyStyle: 'padding: 5px;',
            frame: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'radiogroup',
                style: 'padding:5px;',
                columns: 2,
                width: 500,
                itemId: 'inputType',
                items: [{
                    boxLabel: 'Create New Run',
                    inputValue: 'new',
                    width: 250,
                    name: 'inputType',
                    checked: true
                },{
                    boxLabel: 'Add To Existing Run',
                    inputValue: 'existing',
                    width: 250,
                    name: 'inputType'
                }],
                listeners: {
                    change: {fn: function(btn, val){
                            var panel = btn.up('window');
                            if (val.inputType === 'new')
                                panel.renderNewRunForm.call(panel);
                            else
                                panel.renderExistingRunForm.call(panel);
                        }, delay: 20}
                }
            },{
                xtype: 'ldk-linkbutton',
                text: 'View Existing Instrument Runs',
                linkTarget: '_blank',
                linkCls: 'labkey-text-link',
                style: 'margin-left: 5px;',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'instrument_runs'})
            },{
                itemId: 'renderArea',
                bodyStyle: 'padding: 5px;'
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.formSubmit
            },{
                text: 'Cancel',
                scope: this,
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent();
    },

    renderNewRunForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'form',
            itemId: 'newRunForm',
            border: false,
            defaults: {
                border: false,
                width: 480,
                labelAlign: 'top'
            },
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Run Name',
                allowBlank: false,
                name: 'name'
            },{
                xtype: 'ldk-simplelabkeycombo',
                fieldLabel: 'Instrument Type',
                schemaName: 'sequenceanalysis',
                queryName: 'instruments',
                sortField: 'displayname',
                valueField: 'displayname',
                displayField: 'displayname',
                plugins: ['ldk-usereditablecombo'],
                name: 'instrumenttype'
            },{
                xtype: 'textfield',
                fieldLabel: 'Lane(s)',
                name: 'lane'
            },{
                xtype: 'textfield',
                fieldLabel: 'Center/Facility',
                name: 'facility'
            },{
                xtype: 'datefield',
                fieldLabel: 'Run Date',
                name: 'rundate'
            },{
                xtype: 'textarea',
                fieldLabel: 'Comment',
                height: 80,
                name: 'comment'
            }]
        });
    },

    renderExistingRunForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'labkey-combo',
            labelAlign: 'top',
            displayField: 'name',
            valueField: 'rowid',
            itemId: 'existingRun',
            fieldLabel: 'Choose Run',
            typeAhead: true,
            minChars: 0,
            forceSelection: true,
            caseSensitive: false,
            anyMatch: true,
            width: 480,
            queryMode: 'local',
            store: Ext4.create('LABKEY.ext4.data.Store', {
                schemaName: 'sequenceanalysis',
                queryName: 'instrument_runs',
                columns: 'rowid,name',
                sort: '-rowid',
                autoLoad: true
            }),
            listeners: {
                render: function(field){
                    Ext4.defer(field.focus, 100, field);
                }
            }
        });
    },

    listeners: {
        beforerender: function(win){
            win.renderNewRunForm();
        }
    },

    formSubmit: function(){
        if (this.down('#inputType').getValue().inputType === 'new'){
            var form = this.down('#newRunForm');
            if (!form.isValid()){
                Ext4.Msg.alert('Error', 'Missing one or more required fields');
                return;
            }

            this.createRun(form.getValues());
        }
        else {
            var val = this.down('#existingRun').getValue();
            if (!val){
                Ext4.Msg.alert('Error', 'Must choose existing run');
                return;
            }

            Ext4.each(this.selectedRows, function(r){
                r.set('instrument_run_id', val);
            }, this);

            this.close();
        }
    },

    createRun: function(row) {
        Ext4.Msg.wait('Saving...');

        LABKEY.Query.insertRows({
            schemaName: 'sequenceanalysis',
            queryName: 'instrument_runs',
            rows: [row],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(response){
                Ext4.Msg.hide();

                var rowId = response.rows[0].rowId;
                LDK.Assert.assertNotEmpty('RowId not found in InstrumentRunWindow', rowId);

                Ext4.each(this.selectedRows, function(r){
                    r.set('instrument_run_id', rowId);
                }, this);

                this.close();
            }
        });
    }
});