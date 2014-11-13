/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('ONPRC_Billing.window.ReverseChargeWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }
            else if (checked.length > 1750){
                Ext4.Msg.alert('Error', 'You cannot adjust more than 1750 records at a time.');
                return;
            }

            Ext4.create('ONPRC_Billing.window.ReverseChargeWindow', {
                dataRegionName: dataRegionName,
                checked: checked
            }).show();
        }
    },

    initComponent: function(){
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];

        this.ehrCtx = EHR.Utils.getEHRContext();
        if (!this.ehrCtx){
            Ext4.Msg.alert('Error', 'Unable to find the EHR container, aborting.');
            return;
        }

        Ext4.apply(this, {
            modal: true,
            closeAction: 'destroy',
            title: 'Reverse/Adjust Charges',
            width: 1000,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                labelWidth: 150
            },
            items: [{
                html: 'This helper allows you to make adjustments and reversals.  Once you select the type of adjustment, look below to see an explanation of the items it will create.<br><br>' +
                'NOTE: You have selected ' + this.checked.length + ' items to be reversed or adjusted.  These are based on the rows you checked on the previous grid.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'radiogroup',
                itemId: 'reversalType',
                columns: 1,
                fieldLabel: 'Reversal Type',
                defaults: {
                    xtype: 'radio',
                    name: 'reversalType'
                },
                listeners: {
                    scope: this,
                    change: this.onChange
                },
                items: [{
                    boxLabel: 'Reverse Charges Only',
                    inputValue: 'reversal',
                    checked: true
                },{
                    boxLabel: 'Adjust Charges',
                    inputValue: 'adjustment'
                },{
                    boxLabel: 'Mark As Errored By IBS',
                    inputValue: 'markErrored'
                }]
            },{
                html :'<hr>'
            },{
                xtype: 'panel',
                itemId: 'itemArea',
                defaults: {
                    border: false
                },
                border: false,
                style: 'padding-top: 10px;'
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);

        this.on('render', function(window){
            var group = window.down('radiogroup');
            group.fireEvent('change', group, group.getValue());
        }, this, {single: true});

        this.on('show', function(){
            if (this.isLoading){
                Ext4.Msg.wait('Loading...');
            }
        }, this, {single: true});

        LDK.Assert.assertNotEmpty('No GUIDs found', this.checked.join(''));

        this.isLoading = true;
        LABKEY.Query.selectRows({
            method: 'POST',
            schemaName: 'onprc_billing',
            queryName: 'invoicedItems',
            requiredVersion: 9.1,
            filterArray: [
                LABKEY.Filter.create('objectid', this.checked.join(';'), LABKEY.Filter.Types.IN)
            ],
            columns: 'rowid,objectid,invoiceid,invoiceid/status,Id,date,project,creditedaccount,debitedaccount,rateId,exemptionId,quantity,investigatorId,chargeid,unitcost',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: this.onDataLoad
        });
    },

    onDataLoad: function(results){
        this.isLoading = false;
        this.selectRowsResults = results;

        var missingCharge = 0;
        if (results && results.rows && results.rows.length){
            Ext4.Array.forEach(results.rows, function(r){
                var sr = new LDK.SelectRowsRow(r);
                if (!sr.getValue('chargeId')){
                    missingCharge++;
                }
            }, this);
        }

        if (Ext4.Msg.isVisible())
            Ext4.Msg.hide();

        if (missingCharge){
            Ext4.Msg.alert('No Charge Id', 'Note: a total of ' + missingCharge + ' of these items are missing a charge ID, which indicates they are part of a subset of legacy items imported from IRIS.  You can perform the reversal, but they will not process correctly without additional changes.  Please contact your administrator after making this adjument.');
        }
    },

    onChange: function(field){
        var val = field.getValue().reversalType;

        var items = [];
        if (val == 'reversal'){
            items.push({
                html: 'This will create a new transaction to reverse the original charge.  Note: the aliases used will match those from the original transaction, and does not check whether they are still valid.  The new transactions will use the date selected below, not the original date.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'datefield',
                width: 400,
                labelWidth: 150,
                itemId: 'dateField',
                value: new Date(),
                fieldLabel: 'Transaction Date'
            },{
                xtype: 'numberfield',
                labelWidth: 150,
                hideTrigger: true,
                minValue: 0,
                itemId: 'issueId',
                width: 400,
                fieldLabel: 'Issue #'
            },{
                xtype: 'textarea',
                labelWidth: 150,
                itemId: 'comment',
                width: 400,
                fieldLabel: 'Comment'
            });
        }
        else if (val == 'adjustment'){
            var unitCosts = [];
            Ext4.Array.forEach(this.selectRowsResults.rows, function(row){
                var sr = new LDK.SelectRowsRow(row);
                unitCosts.push(sr.getValue('unitcost'));
            }, this);
            unitCosts = Ext4.unique(unitCosts);

            items.push({
                html: 'This will reverse the original charges and create adjustments based on your selections below.  ' +
                    '<ul>' +
                    '<li>All transactions will use the date selected below, as opposed to the original transaction date.</li>' +
                    '<li>The reversal will use the aliases used on the original transaction.  This does not check whether the aliases are still valid.</li>' +
                    '<li>If you choose to change the project, the project/alias selected will be used on the adjustment.  If you leave this blank, the original project/alias will be used</li>' +
                    '<li>If you select an alternate credit alias, this will be used on all adjustments.  Otherwise the original credit alias will be used.' +
                    '<li>Note: you can leave any or all of these fields blank and create the adjustment.  You will have the opportunity to view the adjustment form, which allows you to independently edit any of these values there as well.</li>' +
                    '</ul>',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'datefield',
                width: 400,
                labelWidth: 150,
                itemId: 'dateField',
                value: new Date(),
                fieldLabel: 'Transaction Date'
            },{
                xtype: 'numberfield',
                labelWidth: 150,
                hideTrigger: true,
                minValue: 0,
                itemId: 'issueId',
                width: 400,
                fieldLabel: 'Issue #'
            },{
                xtype: 'textarea',
                labelWidth: 150,
                itemId: 'comment',
                width: 400,
                fieldLabel: 'Comment'
            });

            //project / debit alias
            items.push({
                xtype: 'checkbox',
                boxLabel: 'Change Project/Debit Alias',
                itemId: 'doChangeProject',
                labelWidth: 350,
                listeners: {
                    change: function(field, val){
                        field.up('panel').down('#projectPanel').setVisible(val);
                    }
                }
            },{
                xtype: 'panel',
                itemId: 'projectPanel',
                hidden: true,
                defaults: {
                    border: false,
                    style: 'margin-left: 20px;'
                },
                items: [{
                    xtype: 'ehr-projectfield',
                    labelWidth: 150,
                    itemId: 'projectField',
                    showInactive: true,
                    showAccount: true,
                    width: 400,
                    fieldLabel: 'Project',
                    matchFieldWidth: false,
                    listeners: {
                        change: function(field){
                            var panel = field.up('panel');
                            var debitAliasField = panel.down('#debitAliasField');
                            var recIdx = field.store.find('project', field.getValue());
                            var rec;
                            if (recIdx != -1){
                                rec = field.store.getAt(recIdx);
                            }

                            if (field.getValue()){
                                debitAliasField.setDisabled(false);
                                debitAliasField.store.filterArray = [LABKEY.Filter.create('project', field.getValue())];
                                debitAliasField.store.load();
                            }
                            else {
                                debitAliasField.setDisabled(true);
                                debitAliasField.setValue(null);
                                debitAliasField.store.filterArray = [];
                            }

                            debitAliasField.setValue(null);
                        }
                    }
                },{
                    xtype: 'labkey-combo',
                    width: 400,
                    labelWidth: 150,
                    fieldLabel: 'Alias To Use',
                    plugins: ['ldk-usereditablecombo'],
                    disabled: true,
                    itemId: 'debitAliasField',
                    displayField: 'account',
                    valueField: 'account',
                    listConfig: {
                        innerTpl: '{[values["account"] + (values["account"] == "Other" ? "" : " (" + (values["startdate"] ? values["startdate"].format("Y-m-d") : "No Start") + " - " + (values["enddate"] ? values["enddate"].format("Y-m-d") : "No End") + ")")]}',
                        getInnerTpl: function(){
                            return this.innerTpl;
                        }
                    },
                    store: {
                        type: 'labkey-store',
                        schemaName: 'onprc_billing',
                        queryName: 'projectAccountHistory',
                        sort: '-startdate',
                        autoLoad: false,
                        columns: 'project,account,startdate,enddate'
                    }
                }]
            });

            //credit alias
            items.push({
                xtype: 'checkbox',
                boxLabel: 'Change Credit Alias',
                itemId: 'doChangeCreditAlias',
                listeners: {
                    change: function(field, val){
                        field.up('panel').down('#creditPanel').setVisible(val);
                    }
                }
            },{
                xtype: 'panel',
                itemId: 'creditPanel',
                hidden: true,
                defaults: {
                    border: false,
                    style: 'margin-left: 20px;'
                },
                items: [{
                    xtype: 'textfield',
                    itemId: 'creditAliasField',
                    width: 400,
                    labelWidth: 150,
                    fieldLabel: 'Credit Alias',
                    displayField: 'alias',
                    valueField: 'alias'
                }]
            });

            //unit cost
            items.push({
                xtype: 'checkbox',
                boxLabel: 'Change Unit Cost',
                itemId: 'doChangeUnitCost',
                listeners: {
                    change: function(field, val){
                        field.up('panel').down('#unitCostPanel').setVisible(val);
                    }
                }
            },{
                xtype: 'panel',
                itemId: 'unitCostPanel',
                hidden: true,
                defaults: {
                    border: false,
                    style: 'margin-left: 20px;'
                },
                items: [{
                    xtype: 'numberfield',
                    itemId: 'unitCostField',
                    labelWidth: 150,
                    decimalPrecision: 2,
                    hideTrigger: true,
                    width: 400,
                    fieldLabel: 'Unit Cost',
                    value: unitCosts.length == 1 ? unitCosts[0] : null
                }]
            });
        }
        else if (val == 'markErrored'){
            items.push({
                html: 'This will mark the selected charges as errored by IBS.  This is intended to ensure the PRIMe record accurately matches what was actually sent to IBS.  This will zero out the item in PRIMe as though no transaction occurred.  If the charge was initially errored and then corrected and submitted, this is not the proper thing to do.  This should only be used to flag items that were not importing into IBS and will never be imported into IBS.',
                style: 'padding-bottom: 10px;'
            });
        }

        var target = this.down('#itemArea');
        target.removeAll();
        target.add(items);
    },

    onSubmit: function(){
        var val = this.down('#reversalType').getValue().reversalType;
        var issueField = this.down('#issueId');

        //these are always required
        var commentField = this.down('#comment');
        if (commentField && !commentField.getValue()){
            Ext4.Msg.alert('Error', 'Must enter a comment.  It is recommended to also enter the issue associated with this adjustment.');
            return;
        }

        var dateField = this.down('#dateField');
        if (dateField && !dateField.getValue()){
            Ext4.Msg.alert('Error', 'You must select the transaction date.');
            return;
        }

        //specifc to adjustments
        if (val == 'adjustment'){
            //we deliberately do not require them to actually enter changes here.  they could choose to do all of this on the next form
            if (this.down('#doChangeProject').getValue() && !this.down('#projectField').getValue()){
                Ext4.Msg.alert('Error', 'You have checked that you want to alter project/debit alias, but did not supply the project.  Either enter a project or uncheck the field');
                return;

            }

            if (this.down('#doChangeCreditAlias').getValue() && !this.down('#creditAliasField').getValue()){
                Ext4.Msg.alert('Error', 'You have checked that you want to alter the credit alias, but did not supply the new alias.  Either enter an alias or uncheck the field');
                return;
            }

            if (this.down('#doChangeUnitCost').getValue() && Ext4.isEmpty(this.down('#unitCostField').getValue())){
                Ext4.Msg.alert('Error', 'You have checked that you want to alter the unit cost, but did not supply a unit cost.  Either enter a unit cost of uncheck the field');
                return;
            }

            //then check aliases, if needed
            if (val == 'changeCreditAlias' || (val == 'changeProject' && this.down('#creditAliasField').getValue())){
                var aliases = [];
                if (this.down('#doChangeProject').getValue() && this.down('#debitAliasField').getValue()){
                    aliases.push(Ext4.String.trim(this.down('#debitAliasField').getValue()));
                }

                if (this.down('#doChangeCreditAlias').getValue() && this.down('#creditAliasField').getValue()){
                    aliases.push(Ext4.String.trim(this.down('#creditAliasField').getValue()));
                }

                if (aliases.length){
                    this.validateAliases(aliases);
                    return;
                }
            }
        }

        this.doUpdate();
    },

    doUpdate: function(){
        var reversalType = this.down('#reversalType').getValue().reversalType;
        var issueField = this.down('#issueId');
        var commentField = this.down('#comment');
        var dateField = this.down('#dateField');

        var toApply = {
            chargecategory: 'Adjustment'
        };

        if (reversalType == 'adjustment'){
            var projectField = this.down('#projectField');
            var debitAliasField = this.down('#debitAliasField');
            var creditAliasField = this.down('#creditAliasField');
            var unitCostField = this.down('#unitCostField');

            if (this.down('#doChangeProject').getValue()){
                toApply.project = this.down('#projectField').getValue();
                toApply.debitedaccount = this.down('#debitAliasField').getValue();
                if (toApply.debitedaccount){
                    toApply.debitedaccount = Ext4.String.trim(toApply.debitedaccount);
                }
            }

            if (this.down('#doChangeCreditAlias').getValue()){
                toApply.creditedaccount = this.down('#creditAliasField').getValue();
                if (toApply.creditedaccount){
                    toApply.creditedaccount = Ext4.String.trim(toApply.creditedaccount);
                }
            }

            if (this.down('#doChangeUnitCost').getValue()){
                toApply.unitcost = this.down('#unitCostField').getValue();
            }
        }

        this.hide();

        var miscChargesInserts = [];
        var invoicedItemsUpdate = [];
        var objectIds = [];
        Ext4.Array.forEach(this.selectRowsResults.rows, function(row){
            var sr = new LDK.SelectRowsRow(row);
            var baseValues = {
                //same for all records
                date: dateField.getValue() || new Date(),
                comment: commentField ? commentField.getValue() : null,
                issueId: issueField ? issueField.getValue() : null,

                //copied from that record
                Id: sr.getValue('Id'),
                billingDate: new Date(),
                investigatorId: sr.getValue('investigatorId'),
                chargeId: sr.getValue('chargeId'),
                quantity: sr.getValue('quantity'),
                sourceInvoicedItem: sr.getValue('objectid'),

                //these may be overridden
                project: sr.getValue('project'),
                debitedaccount: sr.getValue('debitedaccount'),
                creditedaccount: sr.getValue('creditedaccount'),
                unitcost: sr.getValue('unitcost')

                //these are deliberately ignored
                //transactionNumber: sr.getValue('transactionNumber'),
                //item: sr.getValue('item'),
                //itemCode: sr.getValue('itemCode'),
                //category: sr.getValue('category'),
                //servicecenter: sr.getValue('servicecenter'),
                //faid: sr.getValue('faid'),
                //lastName: sr.getValue('lastName'),
                //firstName: sr.getValue('firstName'),
                //department: sr.getValue('department'),
                //totalcost: sr.getValue('totalcost'),
                //rateId: sr.getValue('rateId'),
                //exemptionId: sr.getValue('exemptionId'),
                //creditaccountid: sr.getValue('creditaccountid')
            };
            objectIds.push(sr.getValue('objectid'));

            if (reversalType == 'reversal'){
                var toInsert = Ext4.apply({}, baseValues);
                Ext4.apply(toInsert, {
                    chargecategory: 'Reversal',
                    unitcost: -1 * sr.getValue('unitcost')
                });

                miscChargesInserts.push(toInsert);
            }
            else if (reversalType == 'adjustment'){
                //first create a charge to reverse the original
                //we expect to use the same credit/debit alias as the original
                var reversalCharge = Ext4.apply({}, baseValues);
                Ext4.apply(reversalCharge, {
                    chargecategory: 'Reversal',
                    unitcost: -1 * sr.getValue('unitcost')
                });

                miscChargesInserts.push(reversalCharge);

                var newCharge = Ext4.apply({}, baseValues);
                Ext4.apply(newCharge, toApply);

                miscChargesInserts.push(newCharge);
            }
            else if (reversalType == 'markErrored'){
                invoicedItemsUpdate.push({
                    rowid: sr.getValue('rowid'),
                    objectid: sr.getValue('objectid'),
                    transactionType: 'ERROR',
                    totalcost: null
                });
            }
        }, this);

        this.close();
        if (miscChargesInserts.length){
            Ext4.Msg.wait('Saving...');
            var taskId = LABKEY.Utils.generateUUID().toUpperCase();
            Ext4.Array.forEach(miscChargesInserts, function(obj, idx){
                obj.objectid = LABKEY.Utils.generateUUID().toUpperCase();
                obj.taskid = taskId;
                obj.formSort = (idx + 1);
            }, this);

            LABKEY.Query.saveRows({
                method: 'POST',
                containerPath: this.ehrCtx['EHRStudyContainer'],
                commands: [{
                    command: 'insert',
                    containerPath: this.ehrCtx['EHRStudyContainer'],
                    schemaName: 'ehr',
                    queryName: 'tasks',
                    rows: [{
                        taskid: taskId,
                        title: 'Reversal/Adjustment',
                        formtype: 'Reversals',
                        assignedto: LABKEY.Security.currentUser.id,
                        category: 'task',
                        duedate: new Date()
                    }]
                },{
                    command: 'insert',
                    containerPath: this.ehrCtx['EHRStudyContainer'],
                    schemaName: 'onprc_billing',
                    queryName: 'miscCharges',
                    rows: miscChargesInserts
                }],
                timeout: 9999999,
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function(){
                    Ext4.Msg.hide();
                    var url = LABKEY.ActionURL.buildURL('ehr', 'dataEntryForm', this.ehrCtx['EHRStudyContainer'], {
                        formType: 'Reversals',
                        taskid: taskId
                    });

                    Ext4.Msg.alert('Success', 'A form has been created with the reversals/adjustments.  This form has been opened in a new tab where you are able to make changes.<br><br>Sometimes the browser will block this as a popup.  If this form does not appear, <a href="' + url + '" target="_blank">click here to open it.</a>');
                    window.open(url, '_blank');
                }
            });
        }
        else if (invoicedItemsUpdate){
            Ext4.Msg.wait('Saving...');

            LABKEY.Query.updateRows({
                schemaName: 'onprc_billing',
                queryName: 'invoicedItems',
                method: 'POST',
                timeout: 9999999,
                scope: this,
                rows: invoicedItemsUpdate,
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Success', 'The selected charges have been marked as errors.', function(){
                        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                        dataRegion.refresh();
                    }, this);
                }
            });
        }
        else {
            Ext4.Msg.alert('Error', 'There are no changes to make');
        }
    },

    validateAliases: function(aliases){
        LABKEY.Query.selectRows({
            schemaName: 'onprc_billing_public',
            queryName: 'aliases',
            columns: 'alias',
            filterArray: [LABKEY.Filter.create('alias', aliases.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                if (!results || !results.rows || results.rows.length != aliases.length){
                    //TODO: find missing aliases
                    Ext4.Msg.alert('Error', 'Unable to find alias: ' + aliases);
                }
                else {
                    this.doUpdate();
                }
            }
        });
    }
});