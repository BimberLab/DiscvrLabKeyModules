/**
 * Helper utility functions for the SLA purchase order feature.
 */
Ext4.define('SLA.util.PurchaseOrder', {
    statics : {

        /**
         * Map used for the confirmation details form and review display.
         */
        HOUSING_AVAIL_MAP : {
            1: 'Confirmed',
            2: 'Modified',
            3: 'Denied'
        },

        /**
         * Get the purchase order data for the specified rowId.
         * Used for the update purchase order page and the review page to get the data in a common format.
         * @param rowId - the rowId from the sla.purchase table
         * @param callback - function to call on success of retriving the purchase order data or when an error occurs
         * @param scope - used for the scope of the callback function
         */
        getOrderData : function(rowId, callback, scope)
        {
            var response = {};

            if (rowId == null)
            {
                response.error = 'No purchase order rowid provided.';
                callback.call(scope, response);
            }
            else
            {
                // query for the sla.purchase table details for the specified purchase order rowId
                LABKEY.Query.selectRows({
                    schemaName: 'sla_public',
                    queryName: 'PublicPurchase',
                    filterArray: [LABKEY.Filter.create('rowid', rowId)],
                    scope: this,
                    success: function(data)
                    {
                        // we expect exactly one row in the response, so if that is not the case return an 'Invalid' error
                        if (data.rows.length != 1)
                        {
                            response.error = 'Invalid purchase order rowid: ' + rowId;
                            callback.call(scope, response);
                        }
                        else
                        {
                            // stash the sla.purchase table data for this rowId
                            response.purchase = data.rows[0];

                            if (!Ext4.isDefined(response.purchase.objectid))
                            {
                                response.error = 'No purchase details objectid for purchase order rowid: ' + rowId;
                                callback.call(scope, response);
                            }
                            else
                            {
                                // the sla.purchaseDetails table has a purchaseId column that is a foreign key
                                // back to the sla.purchase table, so get the PK value for the next query
                                var purchaseId = response.purchase.objectid;

                                // query the sla.purchaseDetails table to get the species grid details for the
                                // specified purchase order rowId
                                LABKEY.Query.selectRows({
                                    schemaName: 'sla_public',
                                    queryName: 'PublicPurchaseDetails',
                                    filterArray: [LABKEY.Filter.create('purchaseid', purchaseId)],
                                    sort: 'rowid',
                                    scope: this,
                                    success: function(data)
                                    {
                                        if (data.rows.length == 0)
                                        {
                                            // species grid rows are required to save a purchase order,
                                            // so we expect at least one row to be returned from this query
                                            response.error = 'No purchase details rows for purchase order rowid: ' + rowId;
                                        }
                                        else
                                        {
                                            // stash the sla.purchaseDetails table data for this purchaseId
                                            response.species = data.rows;
                                        }

                                        // now that we have the relevant data from the two purchase tables,
                                        // return that response to the callback function
                                        callback.call(scope, response);
                                    }
                                });
                            }
                        }
                    }
                });
            }
        },

        /**
         * Helper funtion to get the proxy config object for the store's LABKEY.Query.selectRows call
         * @param queryName
         * @returns {Object} - Ext proxy config object
         */
        getComboStoreProxyConfig : function(queryName, schemaName)
        {
            return {
                type: 'ajax',
                timeout: 300000, // 5 minute timeout
                url : LABKEY.ActionURL.buildURL('query', 'selectRows', null, {
                    schemaName: schemaName || 'sla',
                    queryName: queryName
                }),
                reader: {
                    type: 'json',
                    root: 'rows'
                }
            };
        },

        /**
         * Helper function to html encode a comment value and fix newlines for display.
         * @param value
         */
        encodeCommentForDisplay : function(value)
        {
            if (value != null)
            {
                value = Ext4.util.Format.htmlEncode(value);
                value = value.replace(/\n/g, '<br/>');
            }
            return value;
        }
    }
});