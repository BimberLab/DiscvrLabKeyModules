/**
 * Ext component for display the editable grid of Species purchase order information (see CreatePurchaseOrder.js for usage).
 */
Ext4.define('SLA.form.SpeciesGrid', {
    extend: 'Ext.grid.Panel',

    // these properties are used by the base Ext.grid.Panel
    cls: 'order-species-grid',
    forceFit: true,
    selType: 'rowmodel',
    title: 'Species Purchase Information',
    viewConfig: {
        deferEmptyText: false,
        emptyText: 'Click "Add New Row" to create a new species record.',
        markDirty:false
    },

    isGridValid: undefined,
    isUpdate: false,
    initData: null, // provided on initialization for updating a purchase order
    requiredFields: ['species', 'gender', 'strain', 'room', 'requestedarrivaldate', 'animalsordered'],
    conditionalFields: ['age', 'gestation', 'weight'],

    /**
     * This function will be called to initialize this Ext component.
     * Note the call to this.callParent as this is an override.
     */
    initComponent: function()
    {
        this.disabled = this.isUpdate && !LABKEY.user.canUpdate;

        this.columns = this.getColumnConfigs();

        // load the initData, which is provided for an updatePurchaseOrder view, into an Ext store that
        // will be used by the grid to render rows
        this.store = Ext4.create('Ext.data.Store', {
            fields: Ext4.Array.pluck(this.columns, 'dataIndex').remove(undefined),
            data: this.initData
        });

        // if species provided on the URL, add a new row to the grid on ready
        var urlSpecies = LABKEY.ActionURL.getParameter('species');
        if (Ext4.isDefined(urlSpecies))
        {
            this.on('viewready', function() { this.addNewRow(urlSpecies); }, this);
        }

        // use the CellEditing plugin so that each cell in the grid can be edited in-place with a single click
        this.editing = Ext4.create('Ext.grid.plugin.CellEditing', {clicksToEdit: 1});
        this.plugins = [this.editing];

        // define a top toolbar for the grid that has the buttons for adding new rows and deleting rows
        if (!this.isUpdate || LABKEY.user.canUpdate)
        {
            this.dockedItems = [{
                xtype: 'toolbar',
                dock: 'top',
                cls: 'order-grid-toolbar',
                items: [
                    this.getAddNewRowButton(),
                    this.getDeleteRowButton()
                ]
            }];
        }

        this.callParent();

        this.addEvents('rowchange');

        // add an event listener to the grid to update the enabled state of the delete button on grid row selection change
        this.on('selectionchange', function(grid, selected)
        {
            this.getDeleteRowButton().setDisabled(selected.length == 0);
        }, this);

        // add an edit event listener for the grid to notify the parent panel that a change has been made
        this.on('edit', function(grid)
        {
            this.isGridValid = undefined;
            this.fireEvent('rowchange', this);
        }, this);
    },

    /**
     * Initialize the button to add a new row to the grid.
     * @returns {Ext.button.Button}
     */
    getAddNewRowButton: function()
    {
        if (!this.addNewButton)
        {
            this.addNewButton = Ext4.create('Ext.button.Button', {
                text: 'Add New Row',
                handler: function() {
                    this.addNewRow();
                },
                scope: this
            });
        }

        return this.addNewButton;
    },

    /**
     * Initialize the button to delete a row from the grid.
     * Note the button is initially disabled until a grid row is selected.
     * @returns {Ext.button.Button|*}
     */
    getDeleteRowButton: function()
    {
        if (!this.deleteButton)
        {
            this.deleteButton = Ext4.create('Ext.button.Button', {
                text: 'Delete Selected Row',
                disabled: true,
                handler: this.deleteRow,
                scope: this
            });
        }

        return this.deleteButton;
    },

    /**
     * Get an array of the grid column configurations which give the column a title,
     * map it to the data store, and set hidden state.
     * @returns {Array}
     */
    getColumnConfigs: function()
    {
        // Each columns config will have a text and dataIndex property. For those columns that are
        // just needed for persistence, they will be hidden using the hidden: true property. For those
        // editable columns, the editor property will be defined.
        return [
            {
                text: 'Object ID',
                dataIndex: 'objectid',
                hidden: true
            },
            {
                text: 'Purchase ID',
                dataIndex: 'purchaseid',
                hidden: true
            },
            {
                dataIndex: 'species',
                text: 'Species*',
                width: 300,
                editor: this.getComboColumnEditorConfig('Reference_Data','value', 'value','specie', 'sort_order')
            },
            {
                dataIndex: 'gender',
                text: 'Gender*',
                width: 250,
                editor: this.getComboColumnEditorConfig('Reference_Data','value', 'value','gender', 'sort_order')
            },
            {
                dataIndex: 'strain',
                text: 'Strain and (Stock Num)*',
                width: 250,
                renderer: 'htmlEncode',
                editor: {
                    xtype: 'textfield'
                }
            },
            {
                dataIndex: 'age',
                text: 'Age**',
                width: 250,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero
                    //BY LK
                    xtype: 'textfield',
                }
            },
            {
                dataIndex: 'gestation',
                text: 'Gestation**',
                width: 300,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                dataIndex: 'weight',
                text: 'Weight**',
                width: 250,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                dataIndex: 'room',
                text: 'Location*',
                width: 350,
                editor: this.getComboColumnEditorConfig('RodentLocations', 'location', 'location')
            },
            {
                dataIndex: 'requestedarrivaldate',
                text: 'Requested Arrival Date*',
                width: 300,
                renderer: Ext4.util.Format.dateRenderer('Y-m-d'),
                format: 'Y-m-d',
                editor: {
                    // use the input field that validates input as text
                    xtype: 'datefield',
                    minValue: new Date()
                }
            },
            {
                dataIndex: 'animalsordered',
                text: 'Num Animals Ordered*',
                width: 250,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero or decimal values
                    xtype: 'numberfield',
                    allowDecimals: false,
                    minValue: 0
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'boxesquantity',
                text: 'Boxes Quantity',
                width: 250,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero
                    xtype: 'numberfield',
                    minValue: 0
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'costperanimal',
                text: 'Cost Per Animal ($)',
                width: 200,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero
                    xtype: 'numberfield',
                    minValue: 0
                },
                renderer: function(value, metaData, record) {
                    return record.get('costperanimal') != null ? Ext4.util.Format.currency(record.get('costperanimal')) : null;
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'shippingcost',
                text: 'Shipping Cost ($)',
                width: 250,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero
                    xtype: 'numberfield',
                    minValue: 0
                },
                renderer: function(value, metaData, record) {
                    return record.get('shippingcost') != null ? Ext4.util.Format.currency(record.get('shippingcost')) : null;
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'totalcost',
                text: 'Total ($)',
                width: 200,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero
                    xtype: 'numberfield',
                    minValue: 0
                },

                renderer: function(value, metaData, record) {
                    if (value != null)
                        return Ext4.util.Format.currency(value);

                    //var boxesquantity = parseFloat(record.get('boxesquantity'));
                    var costperanimal = parseFloat(record.get('costperanimal'));
                    var shippingcost = parseFloat(record.get('shippingcost'));
                    var numordered = parseFloat(record.get('animalsordered'));

                    //if (!isNaN(boxesquantity) && !isNaN(costperanimal) && !isNaN(shippingcost))
                    if (!isNaN(numordered) && !isNaN(costperanimal) && !isNaN(shippingcost))
                        //return Ext4.util.Format.currency((boxesquantity * costperanimal) + shippingcost);
                        return Ext4.util.Format.currency((numordered * costperanimal) + shippingcost);
                    else
                        return null;
                }
            },
            {
                dataIndex: 'housinginstructions',
                text: 'Special Instructions',
                width: 350,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'sla_DOB',
                text: 'SLA DOB',
                width: 250,
                renderer: Ext4.util.Format.dateRenderer('Y-m-d'),
                format: 'Y-m-d',
                editor: {
                    // use the input field that validates input as a date
                    xtype: 'datefield',
                    //minValue: new Date()
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'vendorLocation',
                text: 'Vendor Location',
                width: 300,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'expectedarrivaldate',
                text: 'Expected Arrival Date',
                width: 300,
                renderer: Ext4.util.Format.dateRenderer('Y-m-d'),
                format: 'Y-m-d',
                editor: {
                    // use the input field that validates input as a date
                    xtype: 'datefield',
                    //minValue: new Date()
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'animalsreceived',
                text: 'Num Animals Received',
                width: 250,
                editor: {
                    // use the input field that validates input as numbers and doesn't
                    // allow values less than zero or decimal values
                    xtype: 'numberfield',
                    allowDecimals: false,
                    minValue: 0
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'receivedby',
                text: 'Received By',
                width: 250,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'receiveddate',
                text: 'Received Date',
                width: 250,
                renderer: Ext4.util.Format.dateRenderer('Y-m-d'),
                format: 'Y-m-d',
                editor: {
                    // use the input field that validates input as a date
                    xtype: 'datefield',
                   // minValue: new Date()
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'cancelledby',
                text: 'Cancelled By',
                width: 250,
                editor: {
                    // use the input field that validates input as text
                    xtype: 'textfield'
                }
            },
            {
                hidden: !LABKEY.user.canUpdate, // fields in this form should only be seen by Editors
                dataIndex: 'datecancelled',
                text: 'Cancelled Date',
                width: 250,
                renderer: Ext4.util.Format.dateRenderer('Y-m-d'),
                format: 'Y-m-d',
                editor: {
                    // use the input field that validates input as a date
                    xtype: 'datefield',
                    //minValue: new Date()
                }
            }
        ];
    },

    /**
     * Create a column editor configuration for an Ext combobox dropdown field which loads the options for the
     * dropdown from a LABKEY.Query.selectRows call to the specified query name in the sla schema.
     * @param name - the query name from the sla schema to load dropdown options
     * @returns {Object} - Ext config object to create a combobox component
     */
    getComboColumnEditorConfig: function(queryName, displayFieldName, valueFieldName, filterValue, sortColName)
    {
        var displayField = displayFieldName != undefined ? displayFieldName : queryName;
        var valueField = valueFieldName != undefined ? valueFieldName : queryName;

        var fields = [queryName];
        if (displayField != queryName)
            fields.push(displayField);
        if (valueField != queryName)
            fields.push(valueField);

        var proxyUrlParams = { schemaName: 'sla', queryName: queryName };
        // TODO should this also filter based on enddate in the Reference_Data case?
        if (filterValue) {
            proxyUrlParams['query.columnName~eq'] = filterValue;
        }
        if (sortColName) {
            proxyUrlParams['query.sort'] = sortColName;
        }

        return {
            xtype: 'combo',
            store: Ext4.create('Ext.data.Store', {
                fields: fields,
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('query', 'selectRows', null, proxyUrlParams),
                    reader: {
                        type: 'json',
                        root: 'rows'
                    }
                },
                pageSize: -1, // show all rows
                sorters: [{ property: name, direction: 'ASC' }],
                autoLoad: true,
                listeners: {
                    load: function(store)
                    {
                        // add a blank entry item to the store (i.e. to allow removing a selected value)
                        store.insert(0, {});
                    }
                }
            }),
            queryMode: 'local',
            forceSelection: true,
            emptyText: 'Select...',
            // override the dropdown field's display template so we can see the empty row in the first position
            tpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<div class="x4-boundlist-item">',
                        '<tpl if="' + displayField + ' != undefined && ' + displayField + '.length &gt; 0">{' + displayField + '}',
                        '<tpl else>&nbsp;',
                        '</tpl>',
                    '</div>',
                '</tpl>'
            ),
            displayField: displayField,
            valueField: valueField
        };
    },


    /**
     * Function called on click of the 'Add New Row' button for the grid.
     */
    addNewRow: function(species)
    {
        // if the grid is in edit mode, cancel the user's edit and add a new row to the grid's store
        this.editing.cancelEdit();
        var records = this.getStore().add({
            objectid: LABKEY.Utils.generateUUID(),
            species: species
        });
        this.editing.startEdit(records[0], this.columns[0]);

        this.getDeleteRowButton().enable();

        // fire an event so that the parent Ext form knows when to enable the submit button
        // (i.e. only allow a purchase order to be submitted/saved when there is at least
        // one row in the species grid)
        this.isGridValid = undefined;
        this.fireEvent('rowchange', this);
    },

    /**
     * Function called on click of the 'Delete Selected Row' button for the grid.
     */
    deleteRow: function()
    {
        // find the currently selected row in the grid and remove it from the store
        // note: this delete isn't persisted to the database until the form is submitted
        var record = this.getSelectionModel().getLastSelected();
        if (record)
        {
            this.getStore().remove(record);
        }

        // fire an event so that the parent Ext form knows when to enable the submit button
        // (i.e. only allow a purchase order to be submitted/saved when there is at least
        // one row in the species grid)
        this.isGridValid = undefined;
        this.fireEvent('rowchange', this);
    },

    /**
     * Called by the CreatePurchaseOrder class to check if the grid is valid (i.e. has at least one row and all
     * rows have required fields populated).
     */
    isValid: function()
    {
        if (this.isGridValid == undefined)
        {
            var rows = this.getStore().getRange();
            if (rows.length == 0)
            {
                this.isGridValid = false;
            }
            else
            {
                this.isGridValid = true;
                Ext4.each(rows, function (row)
                {
                    var nonNullFields = [];
                    Ext4.Object.each(row.data, function (key, value)
                    {
                        if (value != null && value != '')
                            nonNullFields.push(key);
                    });

                    // check required fields for each row
                    if (Ext4.Array.intersect(nonNullFields, this.requiredFields).length != this.requiredFields.length)
                    {
                        this.isGridValid = false;
                        return false; // break
                    }

                    // check conditional fields for each row
                    if (Ext4.Array.intersect(nonNullFields, this.conditionalFields).length == 0)
                    {
                        this.isGridValid = false;
                        return false; // break
                    }
                }, this);
            }
        }

        return this.isGridValid;
    }
});