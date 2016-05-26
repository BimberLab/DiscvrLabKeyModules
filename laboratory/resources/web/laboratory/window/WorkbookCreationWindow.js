Ext4.define('Laboratory.panel.WorkbookCreationPanel', {
    alias: 'widget.laboratory-workbookcreationpanel',
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            activeTab: 0,
            border: false,
            width: 620,
            frame: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'radiogroup',
                style: 'padding:5px;',
                hidden: this.canAddToExistingExperiment===false,
                columns: 2,
                width: 500,
                itemId: 'inputType',
                items: [{
                    boxLabel: 'Create New Workbook',
                    inputValue: 'new',
                    width: 250,
                    name: 'inputType',
                    checked: true
                },{
                    boxLabel: 'Add To Existing Workbook',
                    inputValue: 'existing',
                    width: 250,
                    name: 'inputType'
                }],
                listeners: {
                    change: {fn: function(btn, val){
                        var panel = btn.up('form');
                        if(val.inputType=='new')
                            panel.renderWorkbookForm.call(panel);
                        else
                            panel.renderExistingWorkbookForm.call(panel);
                    }, delay: 20}
                }

            },{
                itemId: 'renderArea',
                bodyStyle: 'padding: 5px;'
            }]
        });

        Ext4.applyIf(this, {
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.formSubmit
            },{
                text: 'Cancel',
                target: '_self',
                href: LABKEY.ActionURL.buildURL('project', 'home')
            }]
        });

        this.callParent(arguments);

        //skip if owned by a window, which will handle this for us
        if (LABKEY.container && LABKEY.container.type == 'workbook' && !this.skipInitialDoLoad){
                this.doLoad(LABKEY.ActionURL.getContainer());
        }
    },

    formSubmit: function(btn){
        btn.setDisabled(true);
        var panel = btn.up('form') || btn.up('window');
        var type = panel.down('#inputType');
        if (type.getValue().inputType=='new'){
            // this should really be enforced upstream.
            // it is possible some actions would be available to users w/ read access only, so we dont completely hide this dialog
            if(!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to create new workbooks.  Please choose an existing one.');
                btn.setDisabled(false);
                return;
            }

            Ext4.Msg.wait('Creating workbook');

            LABKEY.Security.createContainer({
                isWorkbook: true,
                title: panel.down('#titleField').getValue(),
                description: panel.down('#descriptionField').getValue(),
                folderType: Laboratory.Utils.getDefaultWorkbookFolderType(),
                success: function(data){
                    Ext4.Msg.hide();
                    this.doLoad(data.path);
                },
                scope: this,
                failure: function(error){
                    Ext4.Msg.hide();
                    btn.setDisabled(false);
                    if (error.exception)
                        alert(error.exception);
                    LABKEY.Utils.onError(error);
                }
            });
        }
        else {
            var combo = panel.down('#workbookName');
            var rowid = combo.getValue();
            if (!rowid){
                alert('Must pick a workbook');
                btn.setDisabled(false);
                return;
            }

            var rec = combo.store.getAt(combo.store.findExact('container/RowId', rowid));
            if (!rec){
                alert('Must pick a workbook');
                btn.setDisabled(false);
                return;
            }

            this.doLoad('/__r' + rowid);
        }
    },

    doLoad: function(containerPath){
        var controller = this.controller;
        var action = this.action;
        var urlParams = this.urlParams || {};

        window.location = LABKEY.ActionURL.buildURL(controller, action, containerPath, urlParams);
    },

    renderWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'form',
            itemId: 'workbookForm',
            border: false,
            defaults: {
                border: false,
                width: 600,
                labelAlign: 'top'
            },
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Title',
                name: 'title',
                itemId: 'titleField',
                value: LABKEY.Security.currentUser.displayName + ' ' + Ext4.Date.format(new Date(), 'Y-m-d'),
                selectOnFocus: true
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'description',
                itemId: 'descriptionField',
                height: 200
            }]
        });
    },

    renderExistingWorkbookForm: function(){
        var target = this.down('#renderArea');
        target.removeAll();
        target.add({
            xtype: 'labkey-combo',
            labelAlign: 'top',
            displayField: 'rowIdAndName',
            valueField: 'container/RowId',
            itemId: 'workbookName',
            fieldLabel: 'Choose Workbook',
            typeAhead: true,
            minChars: 0,
            forceSelection: true,
            caseSensitive: false,
            anyMatch: true,
            width: 400,
            queryMode: 'local',
            store: Ext4.create('LABKEY.ext4.data.Store', {
                schemaName: 'laboratory',
                queryName: 'workbooks',
                columns: 'workbookId,rowIdAndName,container/RowId,container/Title,container/CreatedBy,container/Name',
                sort: '-workbookId',
                autoLoad: true
            }),
            listeners: {
                render: function(field){
                    Ext4.defer(field.focus, 100, field);
                }
            }
        },{
            xtype: 'checkbox',
            fieldLabel: 'My Workbooks Only',
            labelWidth: 150,
            listeners: {
                change: function(btn, val){
                    var panel = btn.up('form');
                    var combo = panel.down('#workbookName');
                    combo.reset();

                    if(val)
                        combo.store.filter('container/CreatedBy', LABKEY.Security.currentUser.id);
                    else
                        combo.store.clearFilter();
                }
            }
        });
    },

    listeners: {
        beforerender: function(win){
            win.renderWorkbookForm();
        }
    }
});

Ext4.define('Laboratory.window.WorkbookCreationWindow', {
    extend: 'Ext.Window',
    alias: 'widget.laboratory-workbookcreationwindow',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'destroy',
            title: this.title || 'Import Data',
            modal: true,
            items: [Ext4.apply({
                xtype: 'laboratory-workbookcreationpanel',
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                frame: false,
                itemId: 'theForm',
                skipInitialDoLoad: true,
                title: null,
                action: this.action,
                urlParams: this.urlParams,
                controller: this.controller,
                canAddToExistingExperiment: this.canAddToExistingExperiment,
                buttons: []
            }, this.workbookPanelCfg)],
            listeners: {
                scope: this,
                delay: 100,
                show: function(win){
                    win.down('#titleField').focus(20);

                    new Ext4.util.KeyNav(win.getEl(), {
                        "enter" : function(e){
                            var form = this.down('#theForm');
                            form.formSubmit.call(form, form);
                        },
                        scope : this
                    });
                }
            },
            buttons: [{
                text: 'Submit'
                ,width: 50
                ,handler: function(btn){
                    var form = this.down('#theForm');
                    form.formSubmit.call(form, btn);
                    this.close();
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.close();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');

        if ((LABKEY.container && LABKEY.container.type == 'workbook')){
            this.on('beforeshow', function(){return false});
            var form = this.down('#theForm');
            form.doLoad.call(form, LABKEY.ActionURL.getContainer());
        }
    }
});