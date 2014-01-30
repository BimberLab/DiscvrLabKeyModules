Ext4.define('ONPRC_Billing.panel.ChargesInstructionPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.onprc-chargesinstructionpanel',

    initComponent: function(){
        Ext4.apply(this, {
            defaults: {
                border: false
            },
            items: [{
                html: 'This form allows authorized users to enter new charges.  Unlike IRIS, wherever possible charges are calculated based on data from the animal record.  Therefore rather than editing or entering charges here, editing the animal record may be the more appropriate route.  This page should be used to enter new charges or manual ad-hoc adjustments.  ' +
                    '<br><br>' +
                '<b>Important:</b> the vast majority of the time, you should leave unit cost blank.  It will be calculated based on standard rates (at the time of the charge), and based on known exemptions.  This should only be used if you are crediting an' +
                'This is not the appropriate place to make adjustments or corrections for previously invoiced items.',
                style: 'padding: 5px;'
            }]
        })

        this.callParent(arguments);
    }
});