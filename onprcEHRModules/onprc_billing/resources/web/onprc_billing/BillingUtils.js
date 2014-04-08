/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.ns('ONPRC.BillingUtils');

ONPRC.BillingUtils = new function(){
    var BILLING_PERIOD_LENGTH = 15;

    return {
        /**
         * Returns the estimated billing period start, based on the passed date.
         * We either use the 1st of the month or the 16th, depending on the date
         */
        getBillingPeriodStart: function(date){
            var dayOfMonth = date.getDate();
            if (dayOfMonth <= BILLING_PERIOD_LENGTH)
            {
                return Ext4.Date.getFirstDateOfMonth(date);
            }
            else {
                //return the 16th
                var ret = Ext4.Date.getFirstDateOfMonth(date);
                return Ext4.Date.add(ret, Ext4.Date.DAY, BILLING_PERIOD_LENGTH)
            }
        },

        /**
         * Returns the estimated billing period end, based on the passed date
         * We either use the 15th of the month or the last day of the month
         */
        getBillingPeriodEnd: function(date){
            var dayOfMonth = date.getDate();
            if (dayOfMonth > BILLING_PERIOD_LENGTH)
            {
                return Ext4.Date.getLastDateOfMonth(date);
            }
            else {
                //return the 15th
                var ret = Ext4.Date.getFirstDateOfMonth(date);
                return Ext4.Date.add(ret, Ext4.Date.DAY, BILLING_PERIOD_LENGTH - 1)
            }
        },

        deleteInvoiceRuns: function(dataRegionName){
            if (!LABKEY.Security.currentUser.canDelete){
                alert('You do not have permission to delete data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('onprc_billing', 'deleteBillingPeriod', null, {
                dataRegionSelectionKey: dataRegion.name,
                '.select': checked,
                returnURL: window.location.pathname + window.location.search
            });
        },

        isBillingAdmin: function(){
            var ctx = LABKEY.getModuleContext('onprc_billing');
            if (!ctx || !ctx.BillingContainerInfo)
                return false;

            return ctx.BillingContainerInfo.effectivePermissions.indexOf('org.labkey.onprc_billing.security.ONPRCBillingAdminPermission') > -1;
        }
    }
};