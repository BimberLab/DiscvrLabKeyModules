/**
 * Ext component for displaying dashboard information about the purchase orders in this container
 * (see purchaseOrderDashboard.html for usages).
 */
Ext4.define('SLA.panel.PurchaseOrderDashboard', {
    extend: 'Ext.panel.Panel',

    bodyStyle: 'background-color: transparent;',
    border: false,

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function ()
    {
        this.adminContainer = LABKEY.getModuleContext('sla').SLAPurchaseOrderAdminContainer;

        this.callParent();

        if (this.adminContainer != null)
        {
            // query the sla.purchase table for details of the number of orders in each state
            LABKEY.Query.executeSql({
                schemaName: 'sla_public',
                sql: 'SELECT COUNT(p.rowid) AS TotalCount, '
                    + 'SUM(CASE WHEN (confirmationnum IS NULL AND datecancelled IS NULL) THEN 1 ELSE 0 END) AS MissingConfirmationNum, '
                    + 'SUM(CASE WHEN (housingconfirmed IS NULL AND datecancelled IS NULL) THEN 1 ELSE 0 END) AS MissingHousingConfirmation, '
                    + 'SUM(CASE WHEN (orderdate IS NULL AND datecancelled IS NULL) THEN 1 ELSE 0 END) AS PlacedOrders, '
                    + 'SUM(CASE WHEN (receiveddate IS NOT NULL AND datecancelled IS NULL) THEN 1 ELSE 0 END) AS ReceivedOrders, '
                    + 'SUM(CASE WHEN (receiveddate IS NULL AND datecancelled IS NULL AND confirmationnum IS NOT NULL ) THEN 1 ELSE 0 END) AS ToBeReceivedOrders '
                    + 'FROM PublicPurchase p, PublicPurchaseDetails pd WHERE p.objectid = pd.purchaseid',
                success: this.dashboardQuerySuccess,
                scope: this
            });
        }
        else
        {
            var modPropsLink = !LABKEY.user.isAdmin ? '' : LABKEY.Utils.textLink({
                text: 'configure',
                href: LABKEY.ActionURL.buildURL('admin', 'moduleProperties', null, {tabId: 'props'})
            });

            this.add({
                xtype: 'box',
                cls: 'labkey-error',
                html: 'The sla module property "SLAPurchaseOrderAdminContainer" must be configured. ' + modPropsLink
            });
        }
    },

    dashboardQuerySuccess : function(data)
    {
        var data = data.rows[0];

        // default the counts to zero instead of null
        Ext4.Object.each(data, function(key, value)
        {
            if (value == null)
                data[key] = 0;
        });

        // add user permissions information to the data object to know which sections/links to show
        data.UserIsAdmin = LABKEY.user.isAdmin;
        data.UserCanInsert = LABKEY.user.canInsert;
        data.UserCanUpdate = LABKEY.user.canUpdate;

        // link to the create new order API action
        data.CreateNewOrder = LABKEY.ActionURL.buildURL('sla', 'createPurchaseOrder');
        data.SavedDrafts = LABKEY.ActionURL.buildURL('sla', 'viewPurchaseOrderDrafts');

        // links to the SQL queries exposed from the sla_public linked schema
        data.TotalCountHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'All Purchase Orders',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails'
        });
        data.ByRequestor = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Check Orders by Requestor',
            schemaName: 'sla_public',
            queryName: 'PurchaseOrdersByRequestor'
        });
        data.MissingConfirmationNumHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Pending Orders',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.confirmationnum~isblank': null,
            'query.datecancelled~isblank': null
        });
        data.PlacedOrdersHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Orders Placed',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.orderdate~isblank': null,
            'query.datecancelled~isblank': null
        });
        data.ReceivedOrdersHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Orders Received',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.receiveddate~isnonblank': null,
            'query.datecancelled~isblank': null
        });
        data.CancelledOrdersHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Cancelled Orders',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.datecancelled~isnonblank': null
        });
        data.MissingReceivedDateHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Order Receiving Details',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.receiveddate~isblank': null,
            'query.datecancelled~isblank': null,
            'query.confirmationnum~isnonblank': null
        });
        data.OrdersNotReceivedHref = LABKEY.ActionURL.buildURL('sla', 'purchaseOrderReport', null, {
            pageTitle: 'Update Order',
            schemaName: 'sla_public',
            queryName: LABKEY.user.canUpdate ? 'PurchaseOrderDetailsAdmin' : 'PurchaseOrderDetails',
            'query.receiveddate~isblank': null,
            'query.datecancelled~isblank': null
        });

        // links to the admin related tables and insert actions
        if (LABKEY.user.canUpdate)
        {
            data.ViewVendorHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', this.adminContainer, {
                schemaName: 'sla',
                'query.queryName': 'Vendors'
            });
            data.AddVendorHref = LABKEY.ActionURL.buildURL('ehr', 'updateQuery', this.adminContainer, {
                schemaName: 'sla',
                'query.queryName': 'Vendors',
                showImport: true
            });
            data.ViewRequestorHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', this.adminContainer, {
                schemaName: 'sla',
                'query.queryName': 'Requestors'
            });
            data.AddRequestorHref = LABKEY.ActionURL.buildURL('ehr', 'updateQuery', this.adminContainer, {
                schemaName: 'sla',
                'query.queryName': 'Requestors',
                showImport: true
            });
            data.ViewDataAccessHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', this.adminContainer, {
                schemaName: 'onprc_billing',
                'query.queryName': 'dataAccess'
            });
            data.AddDataAccessHref = LABKEY.ActionURL.buildURL('ldk', 'updateQuery', this.adminContainer, {
                schemaName: 'onprc_billing',
                'query.queryName': 'dataAccess',
                showImport: true
            });
        }

        this.add(Ext4.create('Ext.view.View', {
            data: data,
            tpl: new Ext4.XTemplate(
                '<tpl if="TotalCount != undefined">',

                    '<div class="order-dashboard-section">',
                        '<div class="order-dashboard-header">Data Entry</div>',
                        '<tpl if="UserCanInsert">',
                            '<div class="order-dashboard-item"><a class="labkey-text-link" href="{CreateNewOrder}">Create Purchase Order</a></div>',
                            '<div class="order-dashboard-item"><a class="labkey-text-link" href="{SavedDrafts}">Saved Drafts</a></div>',
                        '</tpl>',
                        '<div class="order-dashboard-item"><a class="labkey-text-link" href="{ByRequestor}">Check Order Status</a></div>',
                    '</div>',
                    '<div class="order-dashboard-section order-dashboard-summary">',
                        '<div class="order-dashboard-header">Reports</div>',
                        '<div class="order-dashboard-item">Pending Orders: <a href="{MissingConfirmationNumHref}">{MissingConfirmationNum}</a></div>',
                        '<div class="order-dashboard-item">Orders Received: <a href="{ReceivedOrdersHref}">{ReceivedOrders}</a></div>',
                        //'<div class="order-dashboard-item">Orders Placed: <a href="{PlacedOrdersHref}">{PlacedOrders}</a></div>',
                        '<div class="order-dashboard-item">History of All Purchase Orders: <a href="{TotalCountHref}">{TotalCount}</a></div>',
                    '</div>',

                    '<tpl if="UserCanUpdate">',
                        '<div class="order-dashboard-section order-dashboard-summary">',
                            '<div class="order-dashboard-header">SLAU Administration</div>',
                            '<div class="order-dashboard-item">Pending Orders Waiting for Confirmation: <a href="{MissingConfirmationNumHref}">{MissingConfirmationNum}</a></div>',
                            '<div class="order-dashboard-item">Enter Order Receiving Details: <a href="{MissingReceivedDateHref}">{ToBeReceivedOrders}</a></div>',
                            '<br>',
                            //'<div class="order-dashboard-item"><a class="labkey-text-link" href="{MissingReceivedDateHref}">Update Purchase Order</a></div>',
                            '<div class="order-dashboard-item"><a class="labkey-text-link" href="{OrdersNotReceivedHref}">Update Purchase Order</a></div>',
                            '<div class="order-dashboard-item"><a class="labkey-text-link" href="{ByRequestor}">View Orders by Requestor</a></div>',
                            '<div class="order-dashboard-item"><a class="labkey-text-link" href="{CancelledOrdersHref}">View Cancelled Orders</a></div>',
                            '<div class="order-dashboard-item">',
                                'Vendors&nbsp;&nbsp;&nbsp;',
                                '<a class="labkey-text-link" href="{ViewVendorHref}">View</a> ',
                                '<a class="labkey-text-link" href="{AddVendorHref}">Maintenance</a>',
                            '</div>',
                        '</div>',
                    '</tpl>',

                    '<tpl if="UserIsAdmin">',
                        '<div class="order-dashboard-section order-dashboard-summary">',
                            '<div class="order-dashboard-header">ISE Administration</div>',
                            //'<div class="order-dashboard-item">',
                            //    'Vendors&nbsp;&nbsp;&nbsp;',
                            //    '<a class="labkey-text-link" href="{ViewVendorHref}">View</a> ',
                            //    '<a class="labkey-text-link" href="{AddVendorHref}">Maintenance</a>',
                            //'</div>',
                            '<div class="order-dashboard-item">',
                                'Requestors&nbsp;&nbsp;&nbsp;',
                                '<a class="labkey-text-link" href="{ViewRequestorHref}">View</a>',
                                '<a class="labkey-text-link" href="{AddRequestorHref}">Maintenance</a>',
                            '</div>',
                            '<div class="order-dashboard-item">',
                                'Data Access&nbsp;&nbsp;&nbsp;',
                                '<a class="labkey-text-link" href="{ViewDataAccessHref}">View</a> ',
                                '<a class="labkey-text-link" href="{AddDataAccessHref}">Maintenance</a>',
                            '</div>',
                        '</div>',
                    '</tpl>',
                '</tpl>'
            )
        }));
    }
});