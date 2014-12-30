Ext4.define('ONPRC_Billing.panel.ChargesInstructionPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.onprc-chargesinstructionpanel',
    supportsTemplates: false,

    initComponent: function(){
        Ext4.apply(this, {
            defaults: {
                border: false
            },
            items: [{
                html: 'This form allows authorized users to enter new charges.  This should only be used for changes that are not automatically calculated from the animal record.  ' +
                    '<br><br>' +
                    '<b>Important:</b> The vast majority of the time, you should leave unit cost blank.  It will be calculated based on standard rates (at the time of the charge), and based on known exemptions.  This field will only be editable if the item you selected supports variable unit cost.',
                style: 'padding: 5px;'
            }]
        })

        this.callParent(arguments);
    }
});