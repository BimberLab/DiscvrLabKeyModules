Ext4.define('JBrowse.window.ModifyJsonConfigWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('JBrowse.window.ModifyJsonConfigWindow', {
                dataRegionName: dataRegionName,
                jsonFiles: checked
            }).show();

        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Modify Track Config',
            width: 700,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'You have selected ' + this.jsonFiles.length + ' tracks to modify.  This is an admin tool to modify track attributes.  You can select from pre-defined attributes, or enter them as arbitrary name/value pairs.<br><br>To remove an attribute, enter that attribute with a blank value.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'ldk-gridpanel',
                clicksToEdit: 1,
                width: 680,
                tbar: [{
                    text: 'Add',
                    menu: [{
                        text: 'Visible By Default',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('visibleByDefault', true, 'BOOLEAN');
                        }
                    },{
                        text: 'Index Features',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('doIndex', false, 'BOOLEAN');
                        }
                    },{
                        text: 'Max Score',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('max_score', 1, 'INT');
                        }
                    },{
                        text: 'Category',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('category', null, 'STRING');
                        }
                    },{
                        text: 'Chunk Size Limit',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('chunkSizeLimit', null, 'INT');
                        }
                    },{
                        text: 'Max Track Height',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('maxHeight', 1000, 'INT');
                        }
                    },{
                        text: 'XY Plot',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('type', 'JBrowse/View/Track/Wiggle/XYPlot', 'STRING');
                        }
                    },{
                        text: 'Omit This Track',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('omitTrack', true, 'BOOLEAN');
                        }
                    },{
                        text: 'Add to Text Search Index?',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('includeInSearch', true, 'BOOLEAN');
                        }
                    },{
                        text: 'Exclude from Text Search Index?',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('excludeFromSearch', true, 'BOOLEAN');
                        }
                    },{
                        text: 'Create Full Text Index?',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('createFullTextIndex', true, 'BOOLEAN');
                        }
                    },{
                        text: 'Info Fields For Full Text Search',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute('infoFieldsForFullTextSearch', null, 'STRING');
                        }
                    },{
                        text: 'Other',
                        scope: this,
                        handler: function (gridBtn) {
                            this.addAttribute(null, null);
                        }
                    }]
                },LABKEY.ext4.GRIDBUTTONS.DELETERECORD({
                    text: 'Remove'
                })],
                store: {
                    type: 'array',
                    fields: ['attribute', {name: 'value', type: 'object'}, {name: 'dataType', type: 'object'}]
                },
                columns: [{
                    dataIndex: 'attribute',
                    width: 150,
                    header: 'Attribute',
                    editor: {
                        xtype: 'textfield',
                        allowBlank: false,
                        stripCharsRe: /(^['"]+)|(['"]+$)/g
                    }
                },{
                    dataIndex: 'value',
                    width: 300,
                    header: 'Value',
                    editor: {
                        xtype: 'textfield',
                        allowBlank: true,
                        stripCharsRe: /(^['"]+)|(['"]+$)/g
                    }
                },{
                    dataIndex: 'dataType',
                    width: 150,
                    header: 'Data Type',
                    editor: {
                        xtype: 'combo',
                        allowBlank: false,
                        forceSelection: true,
                        valueField: 'name',
                        displayField: 'name',
                        value: 'STRING',
                        store: {
                            type: 'array',
                            fields: ['name'],
                            data: [
                                ['STRING'],
                                ['INT'],
                                ['FLOAT'],
                                ['BOOLEAN']
                            ]
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    addAttribute: function(attribute, value, dataType){
        var grid = this.down('grid');
        var store = grid.store;
        var recs = store.add(store.createModel({
            attribute: attribute,
            value: value,
            dataType: dataType || 'STRING'
        }));

        var idx = store.indexOf(recs[0]);
        var cellEditing = grid.getPlugin(grid.editingPluginId);
        cellEditing.completeEdit();
        cellEditing.startEditByPosition({row: idx, column: 1});
    },

    getValue: function(){
        var ret = {};
        var hasError = false;
        this.down('ldk-gridpanel').store.each(function(r, i) {
            if (!r.data.attribute){
                Ext4.Msg.alert('Error', 'All rows must have a value for attribute');
                hasError = true;
                return false;
            }

            if (Ext4.isString(r.data.value)){
                if (r.data.value.toLowerCase() === 'true'){
                    r.data.value = true;
                }
                else if (r.data.value.toLowerCase() === 'false'){
                    r.data.value = false;
                }
            }

            var type = Ext4.data.Types[r.data.dataType || 'STRING'];
            LDK.Assert.assertNotEmpty('Unable to find datatype: ' + r.data.dataType, type);

            ret[r.data.attribute] = type.convert(r.data.value);
        }, this);

        if (hasError){
            return null;
        }
        else if (Ext4.isEmpty(ret)){
            Ext4.Msg.alert('Error', 'No values entered');
            return null;
        }

        return ret;
    },

    onSubmit: function(){
        var rowJson = this.getValue();
        if (!rowJson){
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('jbrowse', 'modifyAttributes'),
            method: 'POST',
            jsonData: {
                jsonFiles: this.jsonFiles,
                attributes: rowJson
            },
            scope: this,
            success: function(){
                Ext4.Msg.alert('Success', 'Rows updated', function(){
                    var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                    if (dataRegion){
                        dataRegion.refresh();
                    }
                }, this);
                this.close();
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});