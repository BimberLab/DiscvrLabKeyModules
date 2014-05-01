Ext4.define('ONPRC_Billing.window.ChangeBillDateWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dr = LABKEY.DataRegions[dataRegionName];
            var checked = dr.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            if (checked.length > 1999){
                Ext4.Msg.alert('Error', 'You cannot edit more than 2000 rows at a time');
                return;
            }

            Ext4.create('ONPRC_Billing.window.ChangeBillDateWindow', {
                dataRegionName: dataRegionName,
                checked: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            width: 600,
            bodyStyle: 'padding: 5px;',
            title: 'Change Billing Date',
            items: [{
                html: 'This will change the billing date of the selected items.  When misc charges are entered, the system records the value the user entered as a transaction date (which the user controls), and in the background will store the date this item was created.  The latter is automatic, and is used to determine which billing period into which these items fall.  Changing this should be extremely rare; however, this helper allows you to do so.  This is only appropriate for situations like last-minute adjustments, entered immediately after the billing period, but prior to running the billing process.',
                border: false,
                style: 'padding-bottom: 20px;'
            },{
                xtype: 'datefield',
                itemId: 'dateField',
                fieldLabel: 'Date',
                value: Ext4.Date.clearTime(new Date())
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);

        this.on('show', function(){
            var ctx = LABKEY.getModuleContext('onprc_billing');
            if (!ctx || !ctx.BillingContainer){
                Ext4.Msg.alert('Error', 'ONPRC Billing Container is not defined');
                this.close();
                return;
            }

            this.billingContainer = ctx.BillingContainer;
            this.queryItems();
        }, this, {single: true});
    },

    queryItems: function(){
        Ext4.Msg.wait('Loading...');

        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.selectRows, {
            method: 'POST',
            schemaName: 'onprc_billing',
            queryName: 'miscCharges',
            columns: 'objectid,invoiceId,qcstate,qcstate/PublicData',
            filterArray: [LABKEY.Filter.create('objectid', this.checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            scope: this,
            success: function(results){
                this.toUpdate = [];
                this.skippedRows = [];
                if (results && results.rows && results.rows.length){
                    Ext4.Array.forEach(results.rows, function(r){
                        if (r.invoiceId || r['qcstate/PublicData'] === false){
                            this.skippedRows.push(r);
                        }
                        else {
                            this.toUpdate.push(r.objectid);
                        }
                    }, this);
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        multi.add(LABKEY.Query.selectRows, {
            containerPath: this.billingContainer,
            schemaName: 'onprc_billing',
            queryName: 'invoiceRuns',
            columns: 'billingPeriodEnd',
            sort: '-billingPeriodEnd',
            maxRows: 1,
            scope: this,
            success: function(results){
                if (results && results.rows && results.rows.length){
                    this.lastInvoiceDate = LDK.ConvertUtils.parseDate(results.rows[0].billingPeriodEnd);
                    this.lastInvoiceDate = Ext4.Date.add(this.lastInvoiceDate, Ext4.Date.DAY, 1);
                    this.lastInvoiceDate = Ext4.Date.clearTime(this.lastInvoiceDate);
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        multi.send(this.onLoad, this);
    },

    onLoad: function(){
        if (this.lastInvoiceDate){
            this.lastInvoiceDate =
            this.down('#dateField').setMinValue(this.lastInvoiceDate);
        }

        Ext4.Msg.hide();

        if (this.skippedRows.length){
            Ext4.Msg.alert('Skipped rows', 'Note: a total of ' + this.skippedRows.length + ' records have already been billed or have not been finalized.  These will be skipped');
        }
    },

    onSubmit: function(){
        var date = this.down('#dateField').getValue();
        if (!date){
            Ext4.Msg.alert('Error', 'Must supply a date');
            return;
        }

        if (this.lastInvoiceDate && date.getTime() < this.lastInvoiceDate.getTime()){
            Ext4.Msg.alert('Error', 'Must supply a date that is after the last invoice date of: ' + this.lastInvoiceDate.format('Y-m-d'));
            return;
        }

        if (this.toUpdate.length){
            Ext4.Msg.wait('Saving...');
            var rows = [];
            Ext4.Array.forEach(this.toUpdate, function(objectid){
                rows.push({
                    objectid: objectid,
                    billingDate: date
                });
            }, this);

            LABKEY.Query.updateRows({
                method: 'POST',
                schemaName: 'onprc_billing',
                queryName: 'miscCharges',
                scope: this,
                rows: rows,
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Success', 'Records successfully updated.');
                    this.close();
                    LABKEY.DataRegions[this.dataRegionName].refresh();
                }
            })
        }
        else {
            Ext4.Msg.alert('Error', 'There are no records to update');
        }

        this.close();
    }
});