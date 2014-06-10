/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("/survey/UsersCombo.js");

Ext4.define('LABKEY.ext4.biotrust.ContactCombo', {

    extend  : 'LABKEY.ext4.UsersCombo',
    alias   : 'widget.contactcombo',

    selectedRecord : null,

    constructor : function(config){

        config.valueField = 'userid';
        config.displayField = 'displayname';

        Ext4.define('NWBT.data.Contacts', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'userid', type : 'int', convert : function(value, rec){
                    return (rec.data.systemuser == 'true') ? value : rec.data.rowid;
                }},
                {name : 'rowid', type : 'int'},
                {name : 'displayname', sortType:'asUCString'},
                {name : 'firstname'},
                {name : 'lastname'},
                {name : 'email'},
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
                {name : 'systemuser', type : 'string'},
                {name : 'inactive', type : 'boolean'}
            ]
        });

        config.store = Ext4.create('Ext.data.Store', {
            autoLoad: true,
            pageSize: 10000,
            model : 'NWBT.data.Contacts',
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('biotrust', 'getContacts.api'),
                reader : {
                    type : 'json',
                    root : 'contacts'
                }
            }
        });

        config.tpl = Ext4.create('Ext.XTemplate',
            '<tpl for=".">',
                '<tpl switch="systemuser">',
                    '<tpl case="false">',
                        '<div class="x4-boundlist-item">{displayname} <span style="font-style: italic;">(out-of-system)</span></div>',
                    '<tpl default>',
                        '<div class="x4-boundlist-item">{displayname}</div>',
                '</tpl>',
            '</tpl>'
        );

        config.displayTpl = Ext4.create('Ext.XTemplate',
            '<tpl for=".">',
                '<tpl switch="systemuser">',
                    '<tpl case="false">',
                        '{displayname} (out-of-system)',
                    '<tpl default>',
                        '{displayname}',
                '</tpl>',
            '</tpl>'
        );

        this.callParent([config]);
    },

    initComponent : function() {

        this.getStore().on('load', function(store) {
            store.sort('displayname', 'ASC');
            store.insert(0, {userid : -1, displayname : 'None'});
        }, this);

        this.on('select', this.selectComboRecord);

        this.callParent();
    },

    selectComboRecord : function(combo, selected) {
        combo.selectedRecord = selected[0];
    },

    getSubmitValue : function() {
        if (this.selectedRecord)
            return this.selectedRecord.get('userid');
        else
            return this.getValue();
    },

    isSelectedUserInSystem : function() {
        if (this.selectedRecord)
            return this.selectedRecord.get('systemuser') == 'true';
        else
            return false;
    }
});