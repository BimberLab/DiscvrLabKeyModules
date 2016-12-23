/**
 * Ext component for display of the top level purchase order information form (see CreatePurchaseOrder.js for usage).
 */
Ext4.define('SLA.form.PurchaseForm', {
    extend: 'Ext.form.Panel',

    title: 'Purchase Order Information',
    bodyStyle: 'padding: 10px;',
    layout:'column',
    defaults: { border: false },

    isUpdate: false,
    initData: {}, // provided on initialization for updating a purchase order

    FIELD_LABEL_WIDTH: 200,

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function()
    {
        this.items = this.getPurchaseFormItems();

        this.callParent();

        this.addEvents('noRequestorRecord');
    },

    /**
     * Set the form field values based on the passed in data.
     * @param data - object with values that should be set based on the keys -> field name map
     */
    setFormValues : function(data)
    {
        this.getForm().setValues(data);
    },

    /**
     * Initialize the form components for the top level purchase order information.
     * @returns {Array} - an array of component config objects for the Ext form panel
     */
    getPurchaseFormItems : function()
    {
        // hidden objectId field for primary key of sla.purchase table
        var objectIdField = {
            xtype: 'hidden',
            name: 'objectid',
            value: this.initData['objectid'] || LABKEY.Utils.generateUUID()
        };
        // hidden project field for projectId from the selected IACUC protocol
        var projectField = {
            xtype: 'hidden',
            name: 'project',
            value: this.initData['project']
        };

        // the Requestor Name field defined as an Ext combobox that loads options from the sla.requestors table
        var requestorField = Ext4.create('Ext.form.ComboBox', {
            name: 'requestorid',
            value: this.initData['requestorid'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            width: 400,
            fieldLabel: 'Requestor Name*',
            labelWidth: this.FIELD_LABEL_WIDTH,
            store: Ext4.create('Ext.data.Store', {
                fields: ['objectid', 'firstname', 'lastname', 'email', 'phone'],
                proxy: SLA.util.PurchaseOrder.getComboStoreProxyConfig('requestors', 'sla_public'),
                pageSize: -1, // show all rows
                sorters: [{ property: 'lastname', direction: 'ASC' }],
                autoLoad: true
            }),
            editable: false,
            emptyText: 'Select...',
            allowBlank: false,
            // override the display of the combobox so that is shows lastname,firstname instead of just the objectid
            tpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<div class="x4-boundlist-item">',
                        '<tpl if="lastname != undefined && lastname.length &gt; 0">{lastname}, {firstname}',
                        '<tpl else>&nbsp;',
                        '</tpl>',
                    '</div>',
                '</tpl>'
            ),
            displayTpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<tpl if="lastname != undefined && lastname.length &gt; 0">{lastname}, {firstname}</tpl>',
                '</tpl>'
            ),
            valueField: 'objectid'
        });

        requestorField.getStore().on('load', this.addBlankItemToComboStore, requestorField);
        requestorField.getStore().on('load', function(store, records)
        {
            this.checkForCurrentUser(store, records, requestorField);
        }, this);

        // don't let non-admins changne the requestor field value or see the dropdown list
        if (!LABKEY.user.canUpdate)
            requestorField.setReadOnly(true);

        // when a selection is made in the Requestor Name combobox, this template will display additional
        // information about that requestor to the right of the dropdown component
        var requestorDetailsTpl = new Ext4.XTemplate(
            '<tpl if="objectid != undefined && objectid.length &gt; 0">',
                '<div style="padding-top: 4px;">Email: {email}, Phone #: {phone}</div>',
            '</tpl>'
        );

        // the Vender field defined as an Ext combobox that loads options from the sla.vendors table
        var vendorField = Ext4.create('Ext.form.ComboBox', {
            name: 'vendorid',
            value: this.initData['vendorid'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            width: 400,
            fieldLabel: 'Vendor*',
            labelWidth: this.FIELD_LABEL_WIDTH,
            store: Ext4.create('Ext.data.Store', {
                fields: ['objectid', 'name', 'phone1', 'phone2'],
                proxy: SLA.util.PurchaseOrder.getComboStoreProxyConfig('vendors', 'sla_public'),
                pageSize: -1, // show all rows
                sorters: [{ property: 'name', direction: 'ASC' }],
                autoLoad: true
            }),
            editable: false,
            emptyText: 'Select...',
            allowBlank: false,
            tpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<div class="x4-boundlist-item">',
                        '<tpl if="name != undefined && name.length &gt; 0">{name}',
                        '<tpl else>&nbsp;',
                        '</tpl>',
                    '</div>',
                '</tpl>'
            ),
            displayField: 'name',
            valueField: 'objectid'
        });

        vendorField.getStore().on('load', this.addBlankItemToComboStore, vendorField);

        // when a selection is made in the Vendor combobox, this template will display additional
        // information about that vendor to the right of the dropdown component
        var vendorDetailsTpl = new Ext4.XTemplate(
            '<tpl if="objectid != undefined && objectid.length &gt; 0">',
                '<div style="padding-top: 4px;">1st Phone #: {phone1}, 2nd Phone #: {phone2}</div>',
            '</tpl>'
        );

        var accountField = Ext4.create('Ext.form.field.Text', {
            name: 'account',
            value: this.initData['account'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            fieldLabel: 'Alias',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var grantField = Ext4.create('Ext.form.field.Display', {
            name: 'grant',
            value: this.initData['grant'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            fieldLabel: 'OGA Grant Number',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var hazardsField = Ext4.create('Ext.form.field.Text', {
            name: 'hazardslist',
            value: this.initData['hazardslist'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            fieldLabel: 'List Biological or Chemical agents (Required for NSI 0123D, NSI 0125D)',
            labelWidth: this.FIELD_LABEL_WIDTH
        });

        var requireDobField = Ext4.create('Ext.form.field.Checkbox', {
            name: 'dobrequired',
            checked: this.initData['dobrequired'],
            disabled: this.isUpdate && !LABKEY.user.canUpdate,
            fieldLabel: 'Check if Birthdates required',
            labelWidth: this.FIELD_LABEL_WIDTH,
            inputValue: 1,
            uncheckedValue: 0
        });

        return [{
            columnWidth: 0.5,
            defaults: {width: 400},
            items: [
                objectIdField,
                projectField,
                requestorField,
                this.getComboAttachedView(requestorField, requestorDetailsTpl),
                vendorField,
                this.getComboAttachedView(vendorField, vendorDetailsTpl)
            ]
        },{
            columnWidth: 0.5,
            defaults: {width: 400},
            items: [
                accountField,
                grantField,
                hazardsField,
                requireDobField
            ]
        }];
    },

    /**
     * Helper function to add a new first item to the store (to allow removing a selection from the combobox).
     * @param store
     */
    addBlankItemToComboStore : function(store)
    {
        // add a blank entry item to the store (i.e. to allow removing a selected value)
        store.insert(0, {objectid: null});

        // if the combo has an initial value, fire the change event so that the view updates
        var combo = this;
        if (combo.getValue() != null)
            combo.fireEvent('change', combo, combo.getValue());
    },

    /**
     * For new purchase orders, set the requestor field to the current user if they exist in the store.
     */
    checkForCurrentUser : function(store, records, requestorCombo)
    {
        var userRecord = store.findRecord('userid', LABKEY.user.id);
        if (requestorCombo.getValue() == null)
        {
            if (userRecord != null)
                requestorCombo.select(userRecord);
            else if (!LABKEY.user.canUpdate)
                this.fireEvent('noRequestorRecord', this);
        }
    },

    /**
     * Helper function to initialize an Ext.view.View component for the combobox selection additional details display.
     * @param comboField - the combobox component to attach the view to
     * @param tpl - the template that controls how the additional details are rendered
     * @returns {Ext.view.View}
     */
    getComboAttachedView : function(comboField, tpl)
    {
        var comboDetailsView = Ext4.create('Ext.view.View', {
            width: 500,
            height: 30,
            hideFieldLabel: true,
            cls: 'order-field-details',
            tpl: tpl
        });

        comboField.on('change', function(combo, newValue)
        {
            var record = combo.getStore().findRecord(combo.valueField, newValue);
            if (record != null)
            {
                comboDetailsView.update(record.data);
            }
        });

        return comboDetailsView;
    }
});