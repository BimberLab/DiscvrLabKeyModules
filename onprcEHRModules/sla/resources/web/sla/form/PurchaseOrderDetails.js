/**
 * Ext component for displaying the purchase order details in a read only format (see reviewPurchaseOrder.html for usage).
 */
Ext4.define('SLA.panel.PurchaseOrderDetails', {
    extend: 'Ext.panel.Panel',

    bodyStyle: 'background-color: transparent;',
    border: false,
    width: 735,

    rowId: null, // required property to come from the instance creation in reviewPurchaseOrder.html
    purchaseData: {},
    speciesData: [],

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function()
    {
        this.callParent();

        // after the Ext component has been initialized, use the util helper to get the
        // purchase order data for the specified rowId
        SLA.util.PurchaseOrder.getOrderData(this.rowId, function(data)
        {
            if (data.error)
            {
                this.addErrorMessage(data.error);
            }
            else
            {
                // stash the response data for the purchase order as instance variables,
                // and call the function to render the content to the page
                this.purchaseData = data.purchase;
                this.speciesData = data.species;
                this.renderPurchaseOrderDetails();
            }
        }, this);
    },

    /**
     * Add a simple Ext component to the page with the error message.
     * @param msg - text to display for the error
     */
    addErrorMessage : function(msg)
    {
        this.add({
            xtype: 'box',
            html: '<div class="labkey-error">' + msg + '</div>'
        });
    },

    /**
     * Add each of the UI components to the page for the details view
     */
    renderPurchaseOrderDetails : function()
    {
        this.add(this.getPurchaseView());
        this.add(this.getSpeciesView());
        this.add(this.getConfirmationView());

        this.add(this.getPrintButton());
        this.add(this.getDoneButton());
    },

    /**
     * Initialize the Ext component that will display the top level purchase order details.
     * @returns {Ext.view.View}
     */
    getPurchaseView : function()
    {
        if (!this.purchaseView)
        {
            // map dobrequired value to display as Yes/No
            if (Ext4.isDefined(this.purchaseData.dobrequired))
                this.purchaseData.dobrequired = this.purchaseData.dobrequired == 0 ? 'No' : 'Yes';

            // using an Ext.view.View which will display the data from the this.purchaseData object into the template
            this.purchaseView = Ext4.create('Ext.view.View', {
                padding: '0 0 15px 0',
                data: this.purchaseData,
                tpl: new Ext4.XTemplate(
                    '<tpl if="rowid != undefined">',
                        '<div class="order-header">PURCHASE ORDER INFORMATION</div>',
                        '<span class="order-label">PROJECT: </span><span class="order-value">{projectname:htmlEncode}</span><br/>',
                        '<span class="order-label">PROTOCOL: </span><span class="order-value">{eIACUCNum:htmlEncode}</span><br/>',
                        '<span class="order-label">PRIMARY INVESTIGATOR: </span><span class="order-value">{investigator:htmlEncode}</span><br/>',
                        '<span class="order-label">REQUESTOR: </span><span class="order-value">{requestor:htmlEncode}</span><br/>',
                        //'<span class="order-label">Vendor:</span><span class="order-value">{vendor:htmlEncode}</span><br/>',
                        '<span class="order-label">ALIAS #: </span><span class="order-value">{account:htmlEncode}</span><br/>',
                        //'<span class="order-label">Grant Number:</span><span class="order-value">{grant:htmlEncode}</span><br/>',
                        //'<span class="order-label">Hazard(s):</span><span class="order-value">{hazardslist:htmlEncode}</span><br/>',
                        '<span class="order-label">BIRTH DATES REQUIRED: </span><span class="order-value">{dobrequired}</span><br/>',
                    '</tpl>'
                )
            });
        }

        return this.purchaseView;
    },

    /**
     * Initialize the Ext component that will display the individual 'tiles' for the species grid rows.
     * @returns {Ext.view.View}
     */
    getSpeciesView : function()
    {
        if (!this.speciesView)
        {
            // use an Ext.view.View that will loop over the this.speciesData array object to display one <div>
            // for each of the rows in the species grid (sla.purchaseDetails table) for this purchase order
            this.speciesView = Ext4.create('Ext.view.View', {
                data: this.speciesData,
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<tpl if="rowid != undefined">',
                            '<div class="order-species">',
                                '<span class="order-label">SPECIES: </span><span class="order-value">{species:htmlEncode}</span><br/>',
                                '<span class="order-label">SEX: </span><span class="order-value">{gender:htmlEncode}</span><br/>',
                                '<span class="order-label">STRAIN or STOCK NUM: </span><span class="order-value">{strain:htmlEncode}</span><br/>',
                                '<span class="order-label">AGE: </span><span class="order-value">{age:htmlEncode}</span><br/>',
                                '<span class="order-label">WEIGHT: </span><span class="order-value">{weight:htmlEncode}</span><br/>',
                                '<span class="order-label">GESTATION: </span><span class="order-value">{gestation:htmlEncode}</span><br/>',
                                '<span class="order-label">NUM ANIMALS ORDERED: </span><span class="order-value">{animalsordered}</span><br/>',
                                '<span class="order-label">REQUESTED ARRIVAL DATE: </span><span class="order-value">{requestedarrivaldate}</span><br/>',
                                '<span class="order-label">EXPECTED ARRIVAL DATE: </span><span class="order-value">{expectedarrivaldate}</span><br/>',
                                '<span class="order-label">SLA_DOB: </span><span class="order-value">{sla_DOB}</span><br/>',
                                '<span class="order-label">SLA VENDOR LOCATION: </span><span class="order-value">{vendorLocation}</span><br/>',
                                '<span class="order-label">SPECIAL INSTRUCTIONS: </span><span class="order-value">{housinginstructions:htmlEncode}</span><br/>',

                            '</div>',
                        '</tpl>',
                    '</tpl>'
                )
            });
        }

        return this.speciesView;
    },

    /**
     * Initialize the Ext component that will display the admin confirmation details for this order.
     * @returns {Ext.view.View}
     */
    getConfirmationView : function()
    {
        if (!this.confirmationView)
        {
            // fix up newline and htmlEncoding for comments
            if (this.purchaseData.darcomments)
                this.purchaseData.darcomments = SLA.util.PurchaseOrder.encodeCommentForDisplay(this.purchaseData.darcomments);
            if (this.purchaseData.comments)
                this.purchaseData.comments = SLA.util.PurchaseOrder.encodeCommentForDisplay(this.purchaseData.comments);

            // map housingconfirmed to display value
            if (this.purchaseData.housingconfirmed && this.purchaseData.housingconfirmed != null)
                this.purchaseData.housingconfirmed = SLA.util.PurchaseOrder.HOUSING_AVAIL_MAP[this.purchaseData.housingconfirmed];

            // using an Ext.view.View which will display the data from the this.purchaseData object into the template
            this.confirmationView = Ext4.create('Ext.view.View', {
                padding: '0 0 15px 0',
                data: this.purchaseData,
                tpl: new Ext4.XTemplate(
                    '<tpl if="rowid != undefined">',
                        '<div class="order-header">ORDER CONFIRMATION DETAILS</div>',
                        '<span class="order-label">CONFIRMATION #: </span><span class="order-value">{confirmationnum:htmlEncode}</span><br/>',
                        '<span class="order-label">VENDOR: </span><span class="order-value">{vendor:htmlEncode}</span><br/>',
                        '<span class="order-label">VENDOR CONTACT PERSON: </span><span class="order-value">{vendorcontact:htmlEncode}</span><br/>',
                        '<span class="order-label">HOUSING AVAILABILITY: </span><span class="order-value">{housingconfirmed}</span><br/>',
                        '<span class="order-label">ORDERED BY: </span><span class="order-value">{orderedby:htmlEncode}</span><br/>',
                        '<span class="order-label">ORDER DATE: </span><span class="order-value">{orderdate:date("Y-m-d")}</span><br/>',
                        '<span class="order-label">DCM COMMENTS: </span><span class="order-value">{darcomments}</span><br/>',
                        '<span class="order-label">ADMIN COMMENTS: </span><span class="order-value">{comments}</span><br/>',
                    '</tpl>'
                )
            });
        }

        return this.confirmationView;
    },

    /**
     * Initialize the button that will show this page details without the outer frame (i.e. print view)
     * @returns {Ext.button.Button}
     */
    getPrintButton : function()
    {
        if (!this.printButton)
        {
            this.printButton = Ext4.create('Ext.button.Button', {
                text: 'Print',
                margin: '0 5px 0 0',
                hidden: LABKEY.ActionURL.getParameter('_print'),
                handler: function()
                {
                    window.open(window.location.href + '&_print=1','_blank');
                }
            });
        }

        return this.printButton;
    },

    /**
     * Initialize the button that will link back to the container's begin page
     * @returns {Ext.button.Button}
     */
    getDoneButton : function()
    {
        if (!this.doneButton)
        {
            this.doneButton = Ext4.create('Ext.button.Button', {
                text: 'Done',
                hidden: LABKEY.ActionURL.getParameter('_print'),
                handler: function()
                {
                    // use the ActionURL builder to generate a URL to project/container begin page
                    window.location = LABKEY.ActionURL.buildURL('project', 'begin');
                }
            });
        }

        return this.doneButton;
    }
});