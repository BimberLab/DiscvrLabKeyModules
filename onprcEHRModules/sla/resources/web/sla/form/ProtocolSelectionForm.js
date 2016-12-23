/**
 * Ext component for display of the top level purchase order information form (see CreatePurchaseOrder.js for usage).
 */
Ext4.define('SLA.form.ProtocolForm', {
    extend: 'Ext.form.Panel',

    title: 'IACUC Protocol Selection',
    bodyStyle: 'padding: 10px;',

    isUpdate: false,
    initData: null,

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function()
    {
        if (this.initData != null)
            this.initData = Ext4.Number.from(this.initData, null);

        this.items = [
            this.getLoadingMsg(),
            this.getProtocolCombo()
        ];

        this.callParent();

        this.addEvents('protocolselect');

        var store = this.getProtocolCombo().getStore();
        store.on('load', function() {
            this.getLoadingMsg().hide();
            this.getProtocolCombo().show();
        }, this);
        store.load();
    },

    getLoadingMsg : function()
    {
        if (!this.loadingMsg)
        {
            this.loadingMsg = Ext4.create('Ext.Component', {
                html: 'Loading...'
            });
        }

        return this.loadingMsg;
    },

    /**
     * Initialize Ext combobox with the available protocols from the sla.protocols table.
     */
    getProtocolCombo : function()
    {
        if (!this.protocolCombo)
        {
            this.protocolCombo = Ext4.create('Ext.form.ComboBox', {
                name: 'protocol',
                hidden: true,
                disabled: this.isUpdate && !LABKEY.user.canUpdate,
                width: 890,
                fieldLabel: 'IACUC Protocol*',
                labelWidth: 120,
                store: Ext4.create('Ext.data.Store', {
                    fields: ['ProjectID', 'Alias', 'OGAGrantNumber', 'ParentIACUC', 'Title', 'IACUCCode', 'StartDate', 'EndDate', 'FirstName', 'LastName', 'Division', 'PIIacuc'],
                    proxy: SLA.util.PurchaseOrder.getComboStoreProxyConfig('PlandProtocols', 'sla_public'),
                    pageSize: -1, // show all rows
                    sorters: [{ property: 'LastName', direction: 'ASC' }]
                }),
                queryMode: 'local',
                forceSelection: true,
                allowBlank: false,
                emptyText: 'Select...',
                valueField: 'ProjectID',
                displayField: 'PIIacuc'
            });

            this.protocolCombo.on('select', this.protocolSelectionChange, this);

            // if projectId provided on the URL or exists from initData, select it after the store loads
            var urlProjectId = Number(LABKEY.ActionURL.getParameter('projectId'));
            this.protocolCombo.getStore().on('load', function (store)
            {
                var protRecord;
                if (this.initData != null)
                    protRecord = store.findRecord('ProjectID', this.initData);
                else if (!isNaN(urlProjectId))
                    protRecord = store.findRecord('ProjectID', urlProjectId);

                if (protRecord)
                {
                    this.protocolCombo.setValue(protRecord.get('ProjectID'));
                    this.protocolCombo.fireEvent('select', this.protocolCombo, [protRecord]);
                }
            }, this);
        }

        return this.protocolCombo;

    },

    /**
     * Function called on selection change of the protocol combo.
     * @param combo
     * @param records - array of selected records, expect this to always only have one row
     */
    protocolSelectionChange : function(combo, records)
    {
        if (Ext4.isArray(records) && records.length > 0)
        {
            // fire event so that other form elements can update accordingly
            this.fireEvent('protocolselect', this, records[0].data);
        }
    }
});