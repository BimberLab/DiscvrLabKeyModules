Ext4.define('OpenLdapSync.panel.LdapSettingsPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.openldapsync-ldapsettingspanel',

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            border: false,
            style: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This module It is designed to provide a mechanism to automatically sync users and groups from an external LDAP server with users/groups in LabKey.  ' +
                    'This sync is unidirectional, meaning changes in the LDAP server will be reflected in LabKey, but not the reverse.  ' +
                    'Because the sync needs to perform searches against the LDAP server, rather just just authenticate, it operates somewhat differently than LabKey\'s built-in LDAP authentication module.  As such, they are configured separarely, and enabling one does not currently enable the other.',
                style: 'padding-bottom: 20px;'
            },{
                html: 'To help manage users, <a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', 'Shared', {schemaName: 'OpenLdapSync', queryName: 'ldapSyncDiscrepancies'}) + '">click here</a> to view a list of users present in LABKEY, but not synced via LDAP, or records of users previously synced from LDAP that no longer have a LABKEY user.  The latter is common if the LDAP record was deactivated.  Because users can be created in LabKey outside of the LDAP sync this is not inherently a problem, but this report should provide information that may help with auditing or managing deactivated users.  This report does not take your site\'s domain name into account; however, you can filter the email address column using a contains filter.',
                style: 'padding-bottom: 20px;'
            },{
                xtype: 'panel',
                itemId: 'serverSettings',
                defaults: {
                    border: false
                },
                items: [{
                    html: 'Loading...'
                }]
            }]
        });

        this.callParent();

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('openldapsync', 'getLdapSettings'),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(this.onLoad, this),
            failure: LABKEY.Utils.getCallbackWrapper(this.onError, this)
        });

        this.on('render', function(panel){
            if (!panel.ldapSettings){
                panel.mask('Loading...');
            }
        });
    },

    onLoad: function(results){
        this.ldapSettings = results;

        this.down('#serverSettings').removeAll();
        this.add({
            style: 'padding-bottom: 20px;',
            defaults: {
                border: false
            },
            items: [{
                html: '<b>Connection Settings</b>',
                style: 'padding-bottom: 10px;'
            },{
                html: 'You must provide a service account, which will be used to authenticate all LDAP searches.  The account must have sufficient permissions to perform the searches configured below.<br>' +
                        'Unless selected, SSL will not be used.  The port will default to 389, or 636 if using SSL.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'form',
                itemId: 'connectionForm',
                style: 'padding-bottom: 10px;',
                fieldDefaults: {
                    labelWidth: 160,
                    width: 600
                },
                items: [{
                    xtype: 'textfield',
                    fieldLabel: 'LDAP Host',
                    itemId: 'host',
                    name: 'host',
                    value: this.ldapSettings.host
                },{
                    xtype: 'numberfield',
                    hideTrigger: true,
                    keyNavEnabled: false,
                    spinUpEnabled: false,
                    spinDownEnabled: false,
                    allowDecimals: false,
                    fieldLabel: 'Port',
                    itemId: 'port',
                    name: 'port',
                    value: this.ldapSettings.port
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Principal',
                    itemId: 'principal',
                    name: 'principal',
                    value: this.ldapSettings.principal
                },{
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: 'Credentials',
                    itemId: 'credentials',
                    name: 'credentials',
                    value: this.ldapSettings.credentials
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Use SSL?',
                    itemId: 'useSSL',
                    name: 'useSSL',
                    checked: !!this.ldapSettings.useSSL
                },{
                    xtype: 'textfield',
                    fieldLabel: 'SSL Protocol',
                    itemId: 'sslProtocol',
                    name: 'sslProtocol',
                    value: this.ldapSettings.sslProtocol
                },{
                    style: 'padding-bottom: 10px;padding-top: 10px;',
                    border: false,
                    maxWidth: 1000,
                    html: 'Once you have configured these settings and hit save, click the button below to test whether LabKey can authenticate.  Note: this check only verifies that settings supplied in the tomcat config are able to authenticate against that server, not whether that user has adequate search permissions.'
                },{
                    xtype: 'button',
                    border: true,
                    text: 'Test Connection',
                    handler: function(btn){
                        Ext4.Msg.wait('Testing Connection...');

                        LABKEY.Ajax.request({
                            url : LABKEY.ActionURL.buildURL('openldapsync', 'testLdapConnection'),
                            method : 'POST',
                            success: LABKEY.Utils.getCallbackWrapper(function(response){
                                Ext4.Msg.hide();
                                Ext4.Msg.alert('Success', 'Authentication was successful');
                            }, this),
                            failure: LABKEY.Utils.getCallbackWrapper(this.onError, this)
                        });
                    }
                }]
            },{
                html: '<b>Search Strings (optional, but probably required)</b>',
                style: 'padding-bottom: 10px;padding-top: 20px;'
            },{
                html: 'In addition to the credentials, you can enter additional search strings that will be used when querying users or groups.  If you do not have experience with these, we recommend installing <a href="https://directory.apache.org/studio/downloads.html">Apache Directory Studio</a> or a similar LDAP browser first.  From this tool, connect to your LDAP server using the same credentials you entered into the tomcat config.  Attempt to perform a search using these strings and verify the right result is returned.  Examples of search strings are:<br><br>' +
                    'Base search string: "DC=ldap,DC=myserver,DC=edu"<br>' +
                    'Group search string: "ou=My Department"<br>' +
                    'User search string: "ou=User Accounts".<br><br>' +
                    'User search filter: "(!(userAccountControl:1.2.840.113556.1.4.803:=2))".<br><br>' +
                    'When searching for groups, the base string will be concatenated with the user string to produce "ou=My Department,DC=ldap,DC=myserver,DC=edu".  This means that the search is limited to the domain ldap.myserver.edu and users will only be returned who are members of the organizational unit "My Department"<br><br>' +
                    'When searching for users, the base string will be concatenated with the user string to produce "ou=User Accounts,DC=ldap,DC=myserver,DC=edu".  This means that the search is limited to the domain ldap.myserver.edu and users will only be returned who are members of the organizational unit "User Accounts".  The filter string above is often used top limit to active accounts only.<br>',

                style: 'padding-bottom: 10px;'
            },{
                xtype: 'form',
                itemId: 'settingsForm',
                style: 'padding-bottom: 10px;',
                fieldDefaults: {
                    labelWidth: 180,
                    width: 600
                },
                items: [{
                    xtype: 'textfield',
                    fieldLabel: 'Base Search String',
                    itemId: 'baseSearchString',
                    name: 'baseSearchString',
                    value: this.ldapSettings.baseSearchString
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Group Search String',
                    itemId: 'groupSearchString',
                    name: 'groupSearchString',
                    value: this.ldapSettings.groupSearchString
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Group Filter String',
                    itemId: 'groupFilterString',
                    name: 'groupFilterString',
                    value: this.ldapSettings.groupFilterString
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Group Object Class String',
                    helpPopup: 'The LDAP object class that marks group entries. The default value is group',
                    itemId: 'groupObjectClass',
                    name: 'groupObjectClass',
                    value: this.ldapSettings.groupObjectClass
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Group Name Suffix',
                    itemId: 'groupSyncNameSuffix',
                    name: 'groupSyncNameSuffix',
                    style: 'padding-bottom: 10px;',
                    helpPopup: 'If provided, this string will be appended to any LDAP groups created in LabKey.  For example, if you set this to \' (LDAP\'), the LDAP group MyGroup would get synced as \'MyGroup (LDAP)\'',
                    value: this.ldapSettings.groupSyncNameSuffix
                },{
                    xtype: 'textfield',
                    fieldLabel: 'User Search String',
                    itemId: 'userSearchString',
                    helpPopup: 'If you selected to sync \'All Users\', this is the search string applied.',
                    name: 'userSearchString',
                    value: this.ldapSettings.userSearchString
                },{
                    xtype: 'textfield',
                    fieldLabel: 'User Filter String',
                    helpPopup: 'If you selected to sync \'All Users\', this is the filter string applied.',
                    itemId: 'userFilterString',
                    name: 'userFilterString',
                    value: this.ldapSettings.userFilterString
                },{
                    xtype: 'textfield',
                    fieldLabel: 'User Object Class String',
                    helpPopup: 'This LDAP object class that marks user entires.  The default is user.',
                    itemId: 'userObjectClass',
                    name: 'userObjectClass',
                    value: this.ldapSettings.userObjectClass
                }]
            },{
                html: 'The following values are the complete search/filter strings that will be sent to the LDAP server, based on your selections above.  If you encounter problems, you can input these directly into an LDAP utility and inspect the results:',
                style: 'padding-top: 10px;padding-bottom: 10px;'
            },{
                xtype: 'form',
                itemId: 'searchFilterPanel',
                style: 'padding-bottom: 10px;',
                fieldDefaults: {
                    labelWidth: 260,
                    width: 800
                },
                items: [{
                    xtype: 'displayfield',
                    fieldLabel: 'Final User Search String',
                    itemId: 'completeUserSearchString',
                    name: 'completeUserSearchString',
                    value: this.ldapSettings.completeUserSearchString
                },{
                    xtype: 'displayfield',
                    fieldLabel: 'Final User Filter String',
                    itemId: 'completeUserFilterString',
                    name: 'completeUserFilterString',
                    value: this.ldapSettings.completeUserFilterString
                },{
                    xtype: 'displayfield',
                    fieldLabel: 'Final Group Search String',
                    itemId: 'completeGroupSearchString',
                    name: 'completeGroupSearchString',
                    value: this.ldapSettings.completeGroupSearchString
                },{
                    xtype: 'displayfield',
                    fieldLabel: 'Final Group Filter String',
                    itemId: 'completeGroupFilterString',
                    name: 'completeGroupFilterString',
                    value: this.ldapSettings.completeGroupFilterString
                },{
                    xtype: 'displayfield',
                    fieldLabel: 'Final Group Membership Filter String',
                    itemId: 'completeGroupMemberFilterString',
                    name: 'completeGroupMemberFilterString',
                    value: this.ldapSettings.completeGroupMemberFilterString
                }]
            },{
                html: '<b>Field Mapping</b>',
                style: 'padding-bottom: 10px;'
            },{
                html: 'When users and groups are synced from the LDAP server, values from the LDAP entry will be used to populate fields in LabKey such as the email, displayName, etc.  You can customize which LDAP field will be used to populate each of these.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'form',
                itemId: 'fieldMappingForm',
                style: 'padding-bottom: 10px;',
                items: this.getFieldMappingFormItems()
            },{
                html: '<b>Sync Behavior</b>',
                style: 'padding-bottom: 10px;'
            },{
                itemId: 'syncBehaviorForm',
                defaults: {
                    labelAlign: 'top'
                },
                bodyStyle: 'padding-bottom: 20px;',
                items: [{
                    xtype: 'radiogroup',
                    fieldLabel: 'Read userAccountControl attribute to determine if active?',
                    columns: 1,
                    itemId: 'userAccountControlBehavior',
                    defaults: {
                        name: 'userAccountControlBehavior'
                    },
                    items: [{
                        boxLabel: 'Yes',
                        inputValue: 'true',
                        checked: true
                    },{
                        boxLabel: 'No',
                        inputValue: 'false'
                    }]
                },{
                    xtype: 'radiogroup',
                    columns: 1,
                    fieldLabel: 'When A User Is Deleted From LDAP',
                    itemId: 'userDeleteBehavior',
                    defaults: {
                        name: 'userDeleteBehavior'
                    },
                    items: [{
                        boxLabel: 'Deactivate User From LabKey',
                        inputValue: 'deactivate',
                        checked: true
                    },{
                        boxLabel: 'Delete User From LabKey',
                        inputValue: 'delete'
                    }]
                },{
                    xtype: 'radiogroup',
                    columns: 1,
                    fieldLabel: 'When A Group Is Deleted From LDAP',
                    itemId: 'groupDeleteBehavior',
                    defaults: {
                        name: 'groupDeleteBehavior'
                    },
                    items: [{
                        boxLabel: 'Delete Group From LabKey',
                        inputValue: 'delete',
                        checked: true
                    },{
                        boxLabel: 'Do Nothing',
                        inputValue: 'doNothing'
                    }]
                },{
                    xtype: 'radiogroup',
                    columns: 1,
                    fieldLabel: 'Group Membership Sync Method',
                    itemId: 'memberSyncMode',
                    defaults: {
                        name: 'memberSyncMode'
                    },
                    items: [{
                        boxLabel: 'Remove all members from the LabKey group that are not present in the corresponding LDAP group',
                        inputValue: 'mirror',
                        checked: true
                    },{
                        boxLabel: 'Remove all LDAP users from the LabKey group that are not present in the corresponding LDAP group (this allows non-LDAP users to be added within LabKey)',
                        inputValue: 'removeDeletedLdapUsers'
                    },{
                        boxLabel: 'Do Nothing',
                        inputValue: 'noAction'
                    }]
                },{
                    xtype: 'radiogroup',
                    columns: 1,
                    fieldLabel: 'Set the LabKey user\'s information (name, email, etc), based on LDAP.  This will overwrite any changes made in LabKey',
                    itemId: 'userInfoChangedBehavior',
                    defaults: {
                        name: 'userInfoChangedBehavior'
                    },
                    items: [{
                        boxLabel: 'Yes',
                        inputValue: 'true',
                        checked: true
                    },{
                        boxLabel: 'No',
                        inputValue: 'false'
                    }]
                    //TODO: what if the user email changes?
                }]
            },{
                html: '<b>Choose What to Sync</b>',
                style: 'padding-bottom: 10px;'
            },{
                itemId: 'syncModePanel',
                style: 'padding-bottom: 10px;',
                defaults: {
                    border: false
                },
                items: [{
                    xtype: 'radiogroup',
                    columns: 1,
                    itemId: 'syncMode',
                    items: [{
                        name: 'syncMode',
                        boxLabel: 'All Users (subject to filter strings above)',
                        inputValue: 'usersOnly',
                        checked: true
                    },{
                        name: 'syncMode',
                        boxLabel: 'All Users and Groups (subject to filter strings above)',
                        inputValue: 'usersAndGroups'
                    },{
                        name: 'syncMode',
                        boxLabel: 'Sync Only Specific Groups and Their Members',
                        inputValue: 'groupWhitelist'
                    }],
                    listeners: {
                        scope: this,
                        change: function(field, val, oldVal){
                            var panel = field.up('#syncModePanel');
                            var groupSelection = panel.down('#groupSelection');
                            groupSelection.removeAll();

                            if (val.syncMode === 'groupWhitelist') {
                                groupSelection.add(this.getGroupSelectionCfg('include'));
                            }
                        }
                    }
                },{
                    itemId: 'groupSelection',
                    style: 'padding-bottom: 10px;'
                }]
            },{
                html: '<b>Schedule</b>',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'checkbox',
                labelWidth: 170,
                fieldLabel: 'Is Enabled?',
                name: 'enabled',
                value: true
            },{
                xtype: 'textfield',
                labelWidth: 170,
                width: 400,
                fieldLabel: 'LabKey User',
                name: 'labkeyAdminEmail',
                helpPopup: 'This is the name of a valid LabKey site admin account.  This account will not be used to communicate with the LDAP server and does not need to be defined there.  It will be used internally when saving the audit trail and performing other DB operations.'
            },{
                xtype: 'numberfield',
                name: 'frequency',
                labelWidth: 170,
                width: 400,
                fieldLabel: 'Sync Frequency (Hours)',
                minValue: 0,
                allowDecimals: false
            },{
                layout: 'hbox',
                style: 'margin-top: 20px',
                defaults: {
                    style: 'margin-right: 5px;margin-bottom: 10px;'
                },
                items: [{
                    xtype: 'button',
                    border: true,
                    text: 'Save All Settings On Page',
                    handler: function(btn){
                        btn.up('openldapsync-ldapsettingspanel').doSaveSettings();
                    }
                },{
                    xtype: 'button',
                    border: true,
                    text: 'Preview Sync',
                    handler: function(btn){
                        Ext4.Msg.confirm('Preview Sync', 'This will perform a mock LDAP sync using the last saved settings, which do not necessarily match the current settings on this page.  If you would like to test new settings, please save first.  Do you want to continue?', function(choice){
                            if (choice != 'yes')
                                return;

                            btn.up('openldapsync-ldapsettingspanel').doSync(true);
                        }, this);
                    }
                },{
                    xtype: 'button',
                    border: true,
                    text: 'Sync Now',
                    handler: function(btn){
                        Ext4.Msg.confirm('Perform LDAP Sync', 'This will sync with the LDAP server using the last saved settings, which do not necessarily match the current settings on this page.  If you would like to test new settings, please save first.  Do you want to continue?', function(choice){
                            if (choice != 'yes')
                                return;

                            btn.up('openldapsync-ldapsettingspanel').doSync(false);
                      }, this);
                    }
                }]
            }]
        });

        this.setFieldsFromValues(results);
        this.unmask();
    },

    doSync: function(forPreview){
        Ext4.Msg.wait('Performing sync using last saved settings');

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('openldapsync', 'initiateLdapSync'),
            method : 'POST',
            params: {
                forPreview: forPreview
            },
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();

                Ext4.create('Ext.window.Window', {
                    modal: true,
                    closeAction: 'destroy',
                    title: 'LDAP Sync Results',
                    style: 'padding: 5px;',
                    width: 900,
                    height: 600,
                    defaults: {
                        border: false
                    },
                    items: [{
                        html: 'Below are the messages generated by the sync.  ' +
                            (forPreview ? 'No changes were performed.<br><br>' + 'NOTE: Because users & groups were not actually created, we cannot completely simulate what will happen to group membership.  If the users/groups already existed the messages should be accurate, but the may not be for users/groups that need to be created.' : '')
                    },{
                        xtype: 'textarea',
                        width: 885,
                        height: 500,
                        value: results.messages.join('\n')
                    }],
                    buttons: [{
                        text: 'Close',
                        handler: function(btn){
                            btn.up('window').close();
                        }
                    }]
                }).show();
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(this.onError, this)
        });
    },

    onError: function(exception, responseObj){
        console.error(arguments);

        var msg = LABKEY.Utils.getMsgFromError(responseObj, exception, {
            showExceptionClass: false,
            msgPrefix: 'Error: '
        });

        Ext4.Msg.hide();
        Ext4.Msg.alert('Error', msg);
    },

    getGroupSelectionCfg: function(mode){
        this.groupStore = this.groupStore || Ext4.create('Ext.data.Store', {
            fields: ['name', 'dn'],
            proxy: {
                type: 'memory'
            }
        }) ;

        var cfg = {
            style: 'padding-bottom: 20px;padding-left: 10px',
            border: false,
            items: [{
                xtype: 'itemselector',
                style: 'padding: 10px;',
                border: true,
                height: 250,
                width: 800,
                autoScroll: true,
                labelAlign: 'top',
                displayField: 'name',
                valueField: 'dn',
                multiSelect: false,
                store: this.groupStore,
                buttons: ['add', 'remove'],
                listeners: {
                    scope: this,
                    render: function(){
                        this.loadGroups(); //only call this after items added to panel
                    }
                }
            },{
                xtype: 'button',
                text: 'Reload Group List',
                style: 'margin-bottom: 10px',
                handler: function(btn){
                    var panel = btn.up('openldapsync-ldapsettingspanel');
                    panel.groupStore.removeAll();
                    panel.loadGroups();
                }
            }]
        };

//        if (mode == 'exclude'){
//            Ext4.apply(cfg.items[0], {
//                itemId: 'disallowedDn',
//                name: 'disallowedDn',
//                fieldLabel: 'Include All Groups Except Those Listed Here',
//                value: (this.ldapSettings ? this.ldapSettings.disallowedDn : null)
//            });
//        }
        if (mode === 'include') {
            Ext4.apply(cfg.items[0], {
                itemId: 'allowedDn',
                name: 'allowedDn',
                fieldLabel: 'Choose Only Those Groups To Include',
                value: (this.ldapSettings ? this.ldapSettings.allowedDn : null)
            });
        }

        return cfg
    },

    onGroupsLoad: function(results){
        var toAdd = [];
        for (var i=0;i<results.groups.length;i++){
            toAdd.push(this.groupStore.createModel(results.groups[i]));
        }


        this.groupStore.add(toAdd);
        this.groupStore.sort('name');

        var field = this.down('itemselector');
        field.bindStore(this.groupStore)
    },

    loadGroups: function(callback, scope){
        Ext4.Msg.wait('Loading groups using last saved config...');

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('openldapsync', 'listLdapGroups'),
            method : 'POST',
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(function(results){
                Ext4.Msg.hide();
                this.onGroupsLoad(results);
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(this.onError, this)
        });
    },

    getFieldMappingFormItems: function(){
        var fields = [{
            displayName: 'Email',
            itemId: 'emailFieldMapping',
            name: 'emailFieldMapping'
        },{
            displayName: 'Display Name',
            itemId: 'displayNameFieldMapping',
            name: 'displayNameFieldMapping'
        },{
            displayName: 'First Name',
            itemId: 'firstNameFieldMapping',
            name: 'firstNameFieldMapping'
        },{
            displayName: 'Last Name',
            itemId: 'lastNameFieldMapping',
            name: 'lastNameFieldMapping'
        },{
            displayName: 'Phone Number',
            itemId: 'phoneNumberFieldMapping',
            name: 'phoneNumberFieldMapping'
        },{
            displayName: 'UID',
            helpPopup: 'This should hold the value that uniquely identifies this record on the LDAP server.  Usually this would be the login, but it could also be the distinguishing name or objectId',
            itemId: 'uidFieldMapping',
            name: 'uidFieldMapping'
        },{
            displayName: 'IM',
            itemId: 'imFieldMapping',
            name: 'imFieldMapping'
        }];


        var toAdd = [];
        Ext4.each(fields, function(field){
            toAdd.push({
                xtype: 'textfield',
                labelWidth: 120,
                width: 300,
                fieldLabel: field.displayName,
                helpPopup: field.helpPopup,
                name: field.name,
                value: null
            });
        }, this);

        return toAdd;
    },

    doSaveSettings: function(){
        var vals = this.getForm().getFieldValues();
        Ext4.Msg.wait('Saving...');

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('openldapsync', 'setLdapSettings'),
            method : 'POST',
            jsonData: vals,
            success: LABKEY.Utils.getCallbackWrapper(this.afterSaveSettings, this),
            failure: LABKEY.Utils.getCallbackWrapper(this.onError, this)
        });
    },

    afterSaveSettings: function(results){
        Ext4.Msg.hide();
        Ext4.Msg.alert('Success', 'Settings saved', function(){
            window.location.reload();
        });
    },

    setFieldsFromValues: function(results){
        this.ldapSettings = results;
        for (var prop in this.ldapSettings){
            var field = this.down('[name=' + prop + ']');
            if (field){
                if (field.isXType('radiogroup')){
                    var obj = {};
                    obj[field.name || field.itemId] = this.ldapSettings[prop];
                    field.setValue(obj);
                }
                else {
                    field.setValue(this.ldapSettings[prop]);
                }
            }
        }
    }
});