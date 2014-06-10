/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("dataview/DataViewsPanel.css");
LABKEY.requiresScript("ux/CheckCombo/CheckCombo.js");
LABKEY.requiresCss("ux/CheckCombo/CheckCombo.css");

Ext4.define('LABKEY.ext4.biotrust.ContactsPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame  : false,
            border : false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.grid = Ext4.create('Ext.grid.Panel', {
            border  : false,
            frame   : false,
            layout  : 'fit',
            scroll  : 'vertical',
            columns : this.getGridColumns(),
            store   : this.getContactsStore(),
            multiSelect : false,
            viewConfig : {
                stripeRows : true
            },
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // determine which pop-up dialog (edit, doc set, etc.) based on the class name of the target
                    if (e.target.className == 'edit-user-link')
                        this.userEditClicked(record);
                    else if (e.target.className == 'edit-views-link')
                        this.viewEditClicked(record);
                },
                scope : this
            }
        });

        this.items.push(this.grid);

        if (this.createContact) {
            this.tbar = ['->', {
                xtype   : 'button',
                text    : 'Add Contact',
                handler : function() {this.onAddContact();},
                scope   : this
            }];
        }

        this.callParent();
    },

    getGridColumns : function() {

        return [
        {
            text     : '&nbsp;',
            width    : 40,
            sortable : false,
            menuDisabled : true,
            scope    : this,
            renderer : function() {
                return '<span height="16px" class="edit-views-link" data-qtip="Edit/Delete Contact"></span>';
            }
        },
        {
            text     : 'Display Name',
            sortable : true,
            dataIndex: 'displayname',
            width    : 250,
            renderer : function(value, meta, rec) {
                if (this.createContact) {
                    return "<a onclick='' class='edit-user-link' data-qtip='click to edit the user information'>" + value + "</a>";
                }
                return Ext4.util.Format.htmlEncode(value);
            },
            scope    : this
        },{
            text     : 'First Name',
            sortable : true,
            dataIndex: 'firstname',
            width    : 150,
            renderer : Ext4.util.Format.htmlEncode
        },{
            text     : 'Last Name',
            sortable : true,
            dataIndex: 'lastname',
            width    : 150,
            renderer : Ext4.util.Format.htmlEncode
        },{
            text     : 'Email',
            sortable : true,
            dataIndex: 'email',
            width    : 250,
            renderer : Ext4.util.Format.htmlEncode
        },{
            text     : 'Alternate Email',
            sortable : true,
            dataIndex: 'alternateemail',
            width    : 250,
            renderer : Ext4.util.Format.htmlEncode
        },{
            text     : 'System User',
            sortable : true,
            dataIndex: 'systemuser',
            width    : 110,
            align    : 'center',
            renderer : function(value, meta, rec) {
                if (!value && rec.get("inactive"))
                    return 'No (inactive)';
                else
                    return value ? 'Yes' : 'No'
            }
        },{
            text     : 'Role',
            sortable : true,
            dataIndex: 'role',
            flex     : 1,
            renderer : Ext4.util.Format.htmlEncode
        }]
    },

    getContactsStore : function() {

        // define data models
        if (!Ext4.ModelManager.isRegistered('NWBT.data.Contacts')) {
            Ext4.define('NWBT.data.Contacts', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'userid', type : 'int'},
                    {name : 'rowid', type : 'int'},
                    {name : 'displayname', sortType:'asUCString'},
                    {name : 'firstname'},
                    {name : 'lastname'},
                    {name : 'email'},
                    {name : 'alternateemail'},
                    {name : 'addressstreet1'},
                    {name : 'addressstreet2'},
                    {name : 'addresscity'},
                    {name : 'addressstate'},
                    {name : 'addresszip'},
                    {name : 'location'},
                    {name : 'institution'},
                    {name : 'institutionother'},
                    {name : 'phonenumber'},
                    {name : 'mobilenumber'},
                    {name : 'pagernumber'},
                    {name : 'role'},
                    {name : 'systemuser', type : 'boolean'},
                    {name : 'inactive', type : 'boolean'}
                ]
            });
        }

        if (!this.contactsStore) {

            var config = {
                model   : 'NWBT.data.Contacts',
                autoLoad: true,
                pageSize: 10000,
                proxy   : {
                    type   : 'ajax',
                    url : LABKEY.ActionURL.buildURL('biotrust', 'getContacts.api'),
                    extraParams : {
                        returnUrl   : this.returnUrl
                    },
                    reader : {
                        type : 'json',
                        root : 'contacts'
                    }
                },
                listeners : {
                    load : function(s, recs, success, operation, ops) {
                        s.sort('displayname', 'ASC');
                    }
                }
            };

            this.contactsStore = Ext4.create('Ext.data.Store', config);
        }
        return this.contactsStore;
    },

    userEditClicked : function(record) {

        if (record.data)
        {
            this.onAddContact(record.data, record.data.role);
        }
    },

    viewEditClicked : function(record) {

        if (record)
        {
            var panel = Ext4.create('Ext.form.Panel', {
                border  : false,
                padding : 15,
                minWidth : 300,
                maxWidth : 500,
                defaults  : {
                    xtype: 'displayfield',
                    labelWidth : 100,
                    labelStyle: 'font-weight: bold;'
                },
                items   : [
                    {
                        fieldLabel: "Display Name",
                        value: Ext4.util.Format.htmlEncode(record.get("displayname"))
                    },{
                        fieldLabel: "Email",
                        value: Ext4.util.Format.htmlEncode(record.get("email"))
                    },{
                        fieldLabel: "Role",
                        value: Ext4.util.Format.htmlEncode(record.get("role"))
                    },{
                        fieldLabel: "Type",
                        value: record.get("systemuser") ? "in-system user" : "out-of-system user"
                    }
                ],
                buttonAlign : 'center',
                buttons: [{
                    text    : 'Edit',
                    scope   : this,
                    handler : function() {
                        win.close();
                        this.onAddContact(record.data, record.data.role);
                    }
                },{
                    text    : 'Delete',
                    handler : function() {
                        if (record.get("systemuser"))
                        {
                            Ext4.Msg.confirm('Confirm Contact Delete', 'Are you sure you want to remove all permissions to this folder<br/>for in-system contact "' + record.get('displayname') + '"?', function(btn){
                                if (btn == 'yes')
                                {
                                    var store = record.store;
                                    win.getEl().mask("Removing permissions...");
                                    Ext4.Ajax.request({
                                        url     : LABKEY.ActionURL.buildURL('biotrust', 'updateRoles.api'),
                                        method  : 'POST',
                                        jsonData  : {userId: record.get('userid'), removeAll: true},
                                        success: function() {
                                            win.getEl().unmask();
                                            win.close();
                                            store.load();
                                        },
                                        failure : function() {
                                            Ext4.Msg.alert('Failure', 'Error removing roles from contact.');
                                        },
                                        scope : this
                                    });
                                }
                            }, this);
                        }
                        else
                        {
                            Ext4.Msg.confirm('Confirm Contact Delete', 'Are you sure you want to delete out-of-system<br/>contact "' + record.get('displayname') + '" from this folder?', function(btn){
                                if (btn == 'yes')
                                {
                                    var store = record.store;
                                    win.getEl().mask("Deleting contact...");
                                    LABKEY.Query.deleteRows({
                                        schemaName: 'biotrust',
                                        queryName: 'Contacts',
                                        rows: [{rowId: record.get('rowid')}],
                                        success: function(){
                                            win.getEl().unmask();
                                            win.close();
                                            store.load();
                                        }
                                    });
                                }
                            }, this);
                        }
                    }
                },{
                    text    : 'Cancel',
                    handler : function() { win.close(); }
                }]
            });

            var win = Ext4.create('Ext.window.Window', {
                title: 'Edit Contact',
                cls: 'data-window',
                modal: true,
                autoScroll: true,
                items: panel
            });

            win.show();
        }
    },

    roleEditClicked : function(record) {

        if (record && record.data.userid)
        {
            var items = [];
            var existingRoleMap = {};

            if (record.data.role && record.data.role.length > 0)
            {
                for (var i=0; i < record.data.role.length; i++)
                    existingRoleMap[record.data.role[i]] = true;
            }

            for (i=0; i < this.roles.length; i++)
            {
                items.push({
                    xtype       : 'checkbox',
                    inputValue  : this.roles[i].uniqueName,
                    checked     : existingRoleMap[this.roles[i].name],
                    boxLabel    : this.roles[i].name,
                    name        : 'role'
                });
            }

            var panel = Ext4.create('Ext.form.Panel', {
                border  : false,
                padding : 20,
                items   : items,
                buttonAlign : 'center',
                fieldDefaults  : {
                    labelWidth : 120,
                    width      : 400,
                    labelSeparator : ''
                },
                buttons: [{
                    text    : 'Update',
                    scope   : this,
                    handler : function() {

                        var values = panel.getForm().getValues();
                        if (values) {

                            values.userId = record.data.userid;

                            Ext4.Ajax.request({
                                url     : LABKEY.ActionURL.buildURL('biotrust', 'updateRoles.api'),
                                method  : 'POST',
                                jsonData  : values,
                                success: function(response) {
                                    win.close();
                                    this.getContactsStore().load();
                                },
                                failure : function() {
                                    Ext4.Msg.alert('Failure');
                                },
                                scope : this
                            });
                       }
                    }
                },{
                    text    : 'Cancel',
                    handler : function() { win.close(); }
                }]
            });

            var win = Ext4.create('Ext.window.Window', {
                title: 'Update NWBT Role Assignment',
                cls: 'data-window',
                modal: true,
                autoScroll: true,
                items: panel
            });

            win.show();
        }
    },

    onAddContact : function(values, roles) {

        var panel = Ext4.create('LABKEY.ext4.biotrust.AddContactPanel',{
            values  : values || {},
            roles   : roles || []
        });

        panel.on('cancel', function(){win.close();});
        panel.on('save', function(){
            win.close();
            this.getContactsStore().load();
        }, this);

        var title = values ? 'Edit Contact Information' : 'Add a New NWBT Contact';
        var win = Ext4.create('Ext.window.Window', {
            title   : title,
            cls     : 'data-window',
            modal   : true,
            autoScroll: true,
            items   : panel
        });

        win.show();
    }
});

/**
 * Panel to be added to the study registration wizard with a button to show the AddContactPanel
 */
Ext4.define('LABKEY.ext4.biotrust.AddContactButton', {
    extend : 'Ext.panel.Panel',
    alias: 'widget.addcontactbutton',

    constructor : function(config) {

        Ext4.applyIf(config, {
            frame: false,
            border: false,
            buttonText: 'Add New Contact',
            contactComboName: null
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        // check to see if this user has CreateContactsPermission
        LABKEY.Security.getUserPermissions({
            scope: this,
            success: function(data){
                if (data.container.effectivePermissions.indexOf("org.labkey.biotrust.security.CreateContactsPermission") > -1)
                {
                    this.add({
                        xtype: 'button',
                        text: this.buttonText,
                        tooltip: 'Add a New NWBT Contact',
                        scope: this,
                        handler: this.onAddContact
                    });
                }
            }
        });
    },

    onAddContact : function(values) {

       var panel = Ext4.create('LABKEY.ext4.biotrust.AddContactPanel',{
           values : values || {}
       });

       panel.on('cancel', function(){win.close();});
       panel.on('save', function(newContactValues){
           win.close();

           // reload the store for each ContactCombo and select the new value (by email) if it was the specific
           // combo that was used to add a new contact
           Ext4.each(Ext4.ComponentQuery.query('contactcombo'), function(combo){
               // if the combo was dirty, we have to reset that state after reload of combo store
               var comboDirty = combo.isDirty();

               combo.getStore().load({ scope: this, callback: function() {
                   combo.dirty = comboDirty;
                   if (combo.getName() == this.contactComboName)
                   {
                       var newIndex = combo.getStore().findExact("email", newContactValues.email);
                       if (newIndex > -1)
                       {
                           var record = combo.getStore().getAt(newIndex);
                           combo.select(record);
                           combo.selectComboRecord(combo, [record]);
                       }
                   }
               }});
           }, this);

       }, this);

       var win = Ext4.create('Ext.window.Window', {
           title   : 'Add a New NWBT Contact',
           cls     : 'data-window',
           modal   : true,
           autoScroll: true,
           items   : panel
       });

       win.show();
    }
});

Ext4.define('LABKEY.ext4.biotrust.AddContactPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame  : false,
            border : false,
            padding : 20,
            buttonAlign : 'center',
            fieldDefaults  : {
                labelWidth : 150,
                width      : 600,
                labelSeparator : ''
            }
        });
        this.fieldConfigs = this.getFieldConfigs();

        this.callParent([config]);
    },

    getFieldConfigs : function() {

        var config = {};

        config['firstName'] = {allowBlank : true, hidden : false, readOnly : false};
        config['lastName'] = {allowBlank : true, hidden : false, readOnly : false};
        config['displayName'] = {allowBlank : false, hidden : false, readOnly : false};
        config['email'] = {allowBlank : false, hidden : false, readOnly : false};
        config['alternateEmail'] = {allowBlank : true, readOnly : false};

        config['addressStreet1'] = {allowBlank : true, hidden : false, readOnly : false};
        config['addressStreet2'] = {allowBlank : true, hidden : false, readOnly : false};
        config['addressCity'] = {allowBlank : true, hidden : false, readOnly : false};
        config['addressState'] = {hidden : false, readOnly : false};
        config['addressZip'] = {allowBlank : true, hidden : false, readOnly : false};

        config['location'] = {allowBlank : true, hidden : false, readOnly : false};
        config['institution'] = {allowBlank : true, hidden : false, readOnly : false};
        config['institutionOther'] = {allowBlank : true, hidden : false, readOnly : false};

        config['phoneNumber'] = {allowBlank : true, hidden : false, readOnly : false};
        config['mobileNumber'] = {allowBlank : true, hidden : false, readOnly : false};
        config['pagerNumber'] = {allowBlank : true, hidden : false, readOnly : false};

        config['systemUser'] = {allowBlank : true, hidden : false, readOnly : false};
        config['role'] = {allowBlank : false, hidden : false, readOnly : false};

        return config;
    },

    initComponent : function() {

        this.items = [];
        this.systemUser = this.getContactValue(this.values, "SystemUser");

        // institution store
        var institutionStore = Ext4.create('Ext.data.Store', {
            fields  : ['name'],
            data    : [
                {'name' : 'Fred Hutchinson Cancer Research Center'},
                {'name' : 'Seattle Children\'s'},
                {'name' : 'Seattle Cancer Care Alliance'},
                {'name' : 'University of Washington'}
            ]
        });

        if (this.getContactValue(this.values, "UserId") > 0)
            this.items.push({xtype : 'hidden', name : 'userid', value : this.getContactValue(this.values, "UserId")});
        if (this.getContactValue(this.values, "RowId") > 0)
            this.items.push({xtype : 'hidden', name : 'rowId', value : this.getContactValue(this.values, "RowId")});

        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'First Name', name : 'firstName', value : this.getContactValue(this.values, "FirstName")}, this.fieldConfigs['firstName']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Last Name', name : 'lastName', value : this.getContactValue(this.values, "LastName")}, this.fieldConfigs['lastName']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : '<b>Display Name</b>', name : 'DisplayName', value : this.getContactValue(this.values, "DisplayName")}, this.fieldConfigs['displayName']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : '<b>Email</b>', name : 'email', value : this.getContactValue(this.values, "Email"),
            readOnly: this.getContactValue(this.values, "UserId") > 0, vtype: 'email'}, this.fieldConfigs['email']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Alternate Email', name : 'alternateEmail', value : this.getContactValue(this.values, "AlternateEmail"),
            hidden : !this.getContactValue(this.values, "UserId"), vtype: 'email'}, this.fieldConfigs['alternateEmail']));

        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Mailing Address 1', name : 'addressStreet1', value : this.getContactValue(this.values, "AddressStreet1")}, this.fieldConfigs['addressStreet1']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Mailing Address 2', name : 'addressStreet2', value : this.getContactValue(this.values, "AddressStreet2")}, this.fieldConfigs['addressStreet2']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'City', name : 'addressCity', value : this.getContactValue(this.values, "AddressCity")}, this.fieldConfigs['addressCity']));
        this.items.push({
            xtype: 'fieldcontainer', layout: 'hbox', fieldLabel : 'State', items: [
                Ext4.apply({
                    xtype : 'combo', name : 'addressState', value : this.getContactValue(this.values, "AddressState"), width: 100,
                    queryMode: "local", displayField: "value", valueField: "value", forceSelection: true, store: {
                        fields: ["value"], data : [ {value: "AK"}, {value: "AL"}, {value: "AR"}, {value: "AZ"}, {value: "CA"},
                        {value: "CO"}, {value: "CT"}, {value: "DC"}, {value: "DE"}, {value: "FL"},
                        {value: "GA"}, {value: "HI"}, {value: "IA"}, {value: "ID"}, {value: "IL"},
                        {value: "IN"}, {value: "KS"}, {value: "KY"}, {value: "LA"}, {value: "MA"},
                        {value: "MD"}, {value: "ME"}, {value: "MI"}, {value: "MN"}, {value: "MO"},
                        {value: "MS"}, {value: "MT"}, {value: "NC"}, {value: "ND"}, {value: "NE"},
                        {value: "NH"}, {value: "NJ"}, {value: "NM"}, {value: "NV"}, {value: "NY"},
                        {value: "OH"}, {value: "OK"}, {value: "OR"}, {value: "PA"}, {value: "RI"},
                        {value: "SC"}, {value: "SD"}, {value: "TN"}, {value: "TX"}, {value: "UT"},
                        {value: "VA"}, {value: "VT"}, {value: "WA"}, {value: "WI"}, {value: "WV"},
                        {value: "WY"} ]
                    }
                }, this.fieldConfigs['addressState']),
                { xtype: "label", width: 50 }, // separator
                Ext4.apply({
                    xtype : 'textfield', fieldLabel : 'Zip', labelWidth: 25, labelSeparator: '', width: 295,
                    name : 'addressZip', value : this.getContactValue(this.values, "AddressZip")
                }, this.fieldConfigs['addressZip'])
            ]
        })

        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Descriptive Location', name : 'location', value : this.getContactValue(this.values, "Location"),
            emptyText : 'Building, floor, etc.'}, this.fieldConfigs['location']));

        this.items.push(Ext4.apply({
            xtype       : 'combo',
            fieldLabel  : 'Institution',
            name        : 'institution',
            store       : institutionStore,
            editable    : false,
            queryMode   : 'local',
            displayField : 'name',
            value : this.getContactValue(this.values, "Institution")
        }, this.fieldConfigs['institution']));

        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Other Institution', name : 'institutionOther', value : this.getContactValue(this.values, "InstitutionOther"),
            emptyText : 'Institution name if not available in list'}, this.fieldConfigs['institutionOther']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Phone Number', name : 'phoneNumber', value : this.getContactValue(this.values, "PhoneNumber")}, this.fieldConfigs['phoneNumber']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Mobile Number', name : 'mobileNumber', value : this.getContactValue(this.values, "MobileNumber")}, this.fieldConfigs['mobileNumber']));
        this.items.push(Ext4.apply({xtype : 'textfield', fieldLabel : 'Pager Number', name : 'pagerNumber', value : this.getContactValue(this.values, "PagerNumber")}, this.fieldConfigs['pagerNumber']));

        this.items.push({
            xtype : 'fieldcontainer',
            fieldLabel: 'Inactive',
            defaults: { labelSeparator: '' },
            name: 'inactiveFieldContainer',
            hidden : !(this.getContactValue(this.values, "RowId") > 0),
            layout: 'hbox',
            items: [
                { xtype: "checkbox", name: "inactive", checked : this.getContactValue(this.values, "Inactive") },
                { xtype: "label", width: 15 },
                { xtype: "label", data: {}, tpl: ["<span data-qtip='Setting an out-of-system user as inactive will allow them to remain in the system but no longer appear in the wizard drop-down options.'><img src='" + LABKEY.contextPath + "/_images/info.png' /></a>"] }
            ]
        });

        this.items.push({
            xtype : 'fieldcontainer',
            fieldLabel: 'Grant access to this folder',
            defaults: { labelSeparator: '' },
            name: 'systemUserFieldContainer',
            hidden : this.getContactValue(this.values, "UserId") > 0 || this.getContactValue(this.values, "RowId") > 0,
            layout: 'hbox',
            items: [
                Ext4.apply({
                    xtype: "checkbox",
                    width: 15,
                    name: "systemUser",
                    checked : this.getContactValue(this.values, "SystemUser"),
                    listeners : {
                        scope: this,
                        'change': function(cb, value) {
                            //this.down('checkcombo[name=role]').setVisible(value);
                            //if (!value) this.down('checkcombo[name=role]').setValue(null);
                            this.down('textfield[name=alternateEmail]').setVisible(value);
                            if (!value)
                                this.down('textfield[name=alternateEmail]').setValue(null);
                        }
                    }
                }, this.fieldConfigs['systemUser']),
                { xtype: "label", width: 15 },
                { xtype: "label", data: {}, tpl: ["<span data-qtip='Create an in-system user account that will have permissions to log into the system to view data in this folder. Note: email address should match UW NetID.'><img style='margin-top: 4px;' src='" + LABKEY.contextPath + "/_images/info.png' /></a>"] }
            ]
        });

        this.items.push(Ext4.apply({
            xtype       : this.getContactValue(this.values, "SystemUser") ? 'checkcombo' : 'combo',
            fieldLabel  : '<b>Responsibility</b>',
            name        : 'role',
            store       : this.getRolesStore(),
            value       : this.roles,
            queryMode   : 'local',
            displayField : 'name',
            editable    : false
        }, this.fieldConfigs['role']));


        this.buttons = [{
            text    : 'Save',
            scope   : this,
            formBind : false,
            handler : function() {

                if (this.getForm().isValid())
                {
                    var values = this.getForm().getValues();
                    this.handleSave(values);
                }
            }
        },{
            text    : 'Convert to System User',
            hidden  : this.systemUser || !this.getContactValue(this.values, "RowId"),
            tooltip : 'Converts this out of system user into an in system user that can be fully managed.',
            handler : function() {

                if (this.getForm().isValid())
                {
                    var values = this.getForm().getValues();
                    this.handleConvert(values);
                }
            },
            scope   : this
        },{
            text    : 'Cancel',
            handler : function() { this.fireEvent('cancel'); },
            scope   : this
        }]

        this.callParent();
    },

    handleSave : function(values) {

        if (!values.userid && !values.rowId)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('biotrust', 'checkExistingContact.api'),
                method  : 'POST',
                jsonData  : values,
                success: function(response) {
                    var o = Ext4.decode(response.responseText);
                    if (o["exists"])
                        this.confirmExistingAccountUsage(values, o);
                    else
                        this.saveContact(values);
                },
                failure : function(response) {
                    LABKEY.Utils.displayAjaxErrorResponse(response);
                },
                scope : this
            });
        }
        else
        {
            this.saveContact(values);
        }
    },

    confirmExistingAccountUsage : function(values, existing) {

        // the account/record exists with the specified email address so confirm with user if we should use that account/record
        Ext4.Msg.confirm('Account Already Exists',
                'There is already an ' + (values["systemUser"] ? 'in-system' : 'out-of-system') + ' user account that matches this email address: '
                        + values["email"] + ' (' + existing["displayName"] + '). '
                        + 'Would you like to use that account for the specified ' + values["role"] + ' responsibility in this folder?',
                function(btn) {
                    if (btn == 'yes')
                        this.saveContact({
                            userid: existing["userId"], // existing in-system user account id
                            rowId: existing["rowId"], // existing out-of-system record id
                            email: values["email"],
                            systemUser: values["systemUser"],
                            role: values["role"]});
                }, this);
    },

    saveContact : function(values) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'createContact.api'),
            method  : 'POST',
            jsonData  : values,
            success: function(response) {
                this.fireEvent('save', values);
            },
            failure : function(response) {
                LABKEY.Utils.displayAjaxErrorResponse(response);
            },
            scope : this
        });
    },

    handleConvert : function(values) {

        Ext4.Msg.confirm('Convert to System User', 'Are you sure you want to convert the out-of-system user to an in-system user? This operation cannot be reversed.',
                function(btn){
                    if (btn == 'yes')
                    {
                        // check if the email address matches an existing system user account
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('biotrust', 'checkExistingContact.api'),
                            method  : 'POST',
                            jsonData  : {systemUser: true, email: values.email},
                            success: function(response) {
                                var o = Ext4.decode(response.responseText);
                                if (o["exists"])
                                {
                                    this.fireEvent('cancel');
                                    Ext4.Msg.show({
                                        title: "Error",
                                        msg: "There is already an in-system user account that matches this email address: "
                                                + values.email + " (" + o["displayName"] + "). To use that account, delete the matching out-of-system "
                                                + "contact from this folder and use the 'Add Contact' button to add the existing system account.",
                                        icon: Ext4.Msg.ERROR,
                                        buttons: Ext4.Msg.OK
                                    });
                                }
                                else
                                {
                                    this.convertContact(values);
                                }
                            },
                            failure : function(response) {
                                LABKEY.Utils.displayAjaxErrorResponse(response);
                            },
                            scope : this
                        });
                    }
                }, this);
    },

    convertContact : function(values) {
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'convertContact.api'),
            method  : 'POST',
            jsonData  : values,
            success   : function(response) {
                this.fireEvent('save');
            },
            failure : function(response) {
                LABKEY.Utils.displayAjaxErrorResponse(response);
            },
            scope : this
        });
    },

    getContactValue : function(values, field) {
        var field2 = field.charAt(0).toLowerCase() + field.slice(1);
        return values[field] || values[field2] || values[field.toLowerCase()];
    },

    getRolesStore : function() {

        // define data models
        Ext4.define('LABKEY.BioTrust.data.Roles', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'uniqueName'}
            ]
        });

        var config = {
            model   : 'LABKEY.BioTrust.data.Roles',
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('biotrust', 'getRoles.api'),
                extraParams : {
                    includeProjectRoles  : false
                },
                reader : {
                    type : 'json',
                    root : 'roles'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    }
});

/**
 * Panel to be added to the study registration wizard with a button to show the AddContactPanel
 */
Ext4.define('LABKEY.ext4.biotrust.CreateInvestigatorAccountPanel', {
    extend  : 'LABKEY.ext4.biotrust.AddContactPanel',
    alias   : 'widget.createaccount',

    constructor : function(config) {

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();
    },

    getFieldConfigs : function() {

        var config = this.callParent();

        // require first and last name
        config['firstName'] = {allowBlank : false, hidden : false, readOnly : false};
        config['lastName'] = {allowBlank : false, hidden : false, readOnly : false};
        config['email'] = {allowBlank : false, hidden : false, readOnly : true};

        // hide system user and role
        config['systemUser'] = {hidden : true, readOnly : false};
        config['role'] = {hidden : true, readOnly : false};

        return config;
    },

    handleSave : function(values) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('biotrust', 'createInvestigatorFolder.api'),
            method  : 'POST',
            jsonData  : values,
            success: function(response) {
                this.fireEvent('save');
            },
            failure : function(response) {
                LABKEY.Utils.displayAjaxErrorResponse(response);
            },
            scope : this
        });
    }
});
