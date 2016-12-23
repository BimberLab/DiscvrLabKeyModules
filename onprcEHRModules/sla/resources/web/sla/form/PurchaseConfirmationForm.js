/**
 * Ext component for display of the admin confirmation details form (see CreatePurchaseOrder.js for usage).
 */
Ext4.define('SLA.form.OrderConfirmationForm', {
    extend: 'Ext.form.Panel',

    title: 'Order Confirmation Details',
    bodyStyle: 'padding: 10px;',
    layout:'column',
    defaults: {
        border: false
    },

    initData: {}, // provided on initialization for updating a purchase order

    FIELD_LABEL_WIDTH: 125,

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function()
    {
        this.items = this.getPurchaseFormItems();

        this.callParent();
    },

    /**
     * Initialize the form components for the Admin confirmation details.
     * @returns {Array} - an array of component config objects for the Ext form panel
     */
    getPurchaseFormItems : function()
    {
        var confirmationNumField = Ext4.create('Ext.form.field.Text', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'confirmationnum',
            value: this.initData['confirmationnum'],
            fieldLabel: 'Confirmation #',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var vendorContactField = Ext4.create('Ext.form.field.Text', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'vendorcontact',
            value: this.initData['vendorcontact'],
            fieldLabel: 'Vendor Contact Person',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var housingAvailField = Ext4.create('Ext.form.RadioGroup', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            // radio group expects the value to be an object with a key that matches a radio item name
            value: {housingconfirmed: this.initData['housingconfirmed']},
            fieldLabel: 'Housing Availability',
            labelWidth: this.FIELD_LABEL_WIDTH,
            columns: 1,
            items: [
                {boxLabel: SLA.util.PurchaseOrder.HOUSING_AVAIL_MAP[1], name: 'housingconfirmed', inputValue: 1},
                {boxLabel: SLA.util.PurchaseOrder.HOUSING_AVAIL_MAP[2], name: 'housingconfirmed', inputValue: 2},
                {boxLabel: SLA.util.PurchaseOrder.HOUSING_AVAIL_MAP[3], name: 'housingconfirmed', inputValue: 3}
            ]
        });

        var orderedByField = Ext4.create('Ext.form.field.Text', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'orderedby',
            value: this.initData['orderedby'],
            fieldLabel: 'Ordered By Name',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var orderDateField = Ext4.create('Ext.form.field.Date', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'orderdate',
            value: this.initData['orderdate'] ? new Date(this.initData['orderdate']) : undefined,
            fieldLabel: 'Order Date',
            format: 'Y-m-d',
            maxValue: new Date(),  // limited to the current date or prior
            labelWidth: this.FIELD_LABEL_WIDTH,
            width: 250
        });

        var darCommentsField = Ext4.create('Ext.form.field.TextArea', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'darcomments',
            value: this.initData['darcomments'],
            fieldLabel: 'DCM Comments',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var adminCommentsField = Ext4.create('Ext.form.field.TextArea', {
            disabled: !LABKEY.user.canUpdate, // fields in this form should only be editable for Editors
            name: 'comments',
            value: this.initData['comments'],
            fieldLabel: 'Admin Comments',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var colDefaults = {
            width: 400
        };

        // display the input fields in 3 columns with equal width
        return [{
            columnWidth: 0.33,
            defaults: colDefaults,
            items: [
                confirmationNumField,
                vendorContactField,
                housingAvailField
            ]
        },{
            columnWidth: 0.33,
            defaults: colDefaults,
            items: [
                orderedByField,
                orderDateField
            ]
        },{
            columnWidth: 0.34,
            defaults: colDefaults,
            items: [
                darCommentsField,
                adminCommentsField
            ]
        }];
    }
});