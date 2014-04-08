Ext4.define('ONPRC_Billing.panel.ChargesAdvancedInstructionPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.onprc-chargesadvancedinstructionpanel',

    initComponent: function(){
        Ext4.apply(this, {
            defaults: {
                border: false
            },
            items: [{
                html: 'This form allows authorized users to enter new charges.  Unlike IRIS, wherever possible charges are calculated based on data from the animal record.  Therefore rather than editing or entering charges here, editing the animal record may be the more appropriate route.  This page should be used to enter new charges or manual ad-hoc adjustments.  ' +
                    '<br><br>' +
                '<b>Important:</b>' +
                '<li>The vast majority of the time, you should leave unit cost blank.  It will be calculated based on standard rates (at the time of the charge), and based on known exemptions.</li>' +
                '<li>This is probably not the appropriate place to make adjustments or corrections for previously invoiced items.</li>' +
                '<li>You should also only fill out the alias if you are deviating from the project\'s standard alias.  In most cases, PRIMe will infer the alias to use based on project.</li>',
                style: 'padding: 5px;'
            }]
        })

        this.callParent(arguments);
    }
});