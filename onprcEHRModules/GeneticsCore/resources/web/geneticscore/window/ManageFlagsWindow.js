/**
 * @cfg dataRegion
 * @cfg subjectIds
 * @cfg successHandler
 * @cfg mode Either 'add' or 'remove'
 */
Ext4.define('GeneticsCore.window.ManageFlagsWindow', {
    extend: 'Ext.window.Window',
    mode: 'add',

    initComponent: function(){
        this.geneticsCtx = LABKEY.getModuleContext('GeneticsCore');
        if(!this.geneticsCtx){
            alert('GeneticsCore Module not enabled in this container');
            return;
        }

        this.ehrCtx = EHR.Utils.getEHRContext();
        if(!this.ehrCtx){
            alert('EHRStudyContainer has not been set in this folder');
            return;
        }

        Ext4.apply(this, {
            width: 315,
            autoHeight: true,
            modal: true,
            bodyStyle: 'padding:5px',
            closeAction: 'destroy',
            title: (this.mode == 'add' ? 'Add' : 'Remove') + ' Genetic Blood Draw Flags',
            items: [{
                xtype: 'form',
                fieldDefaults: {
                    width: 290,
                    labelAlign: 'top'
                },
                border: false,
                items: [{
                    html: 'Note: flags will only be added to living animals, and will only be added if a flag of this type does not already exist',
                    border: false,
                    style: 'padding-bottom: 10px;'
                },{
                    xtype: 'datefield',
                    itemId: 'dateField',
                    fieldLabel: this.mode == 'add' ? 'Date' : 'End Date',
                    value: new Date()
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Remark',
                    itemId: 'remarkField'
                },{
                    xtype: 'radiogroup',
                    itemId: 'flagTypes',
                    columns: 1,
                    defaults: {
                        xtype: 'radio',
                        name: 'flagTypes'
                    },
                    items: this.getRadioItems()
                }]
            }],
            buttons: [{
                xtype: 'button',
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                xtype: 'button',
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').destroy();
                }
            }]
        });

        this.callParent();
    },

    getRadioItems: function(){
        var items = [];

        for (var flag in this.geneticsCtx.GENETICS_FLAGS){
            items.push({
                boxLabel: this.geneticsCtx.GENETICS_FLAGS[flag],
                inputValue: this.geneticsCtx.GENETICS_FLAGS[flag]
            })
        }

        items = LDK.Utils.sortByProperty(items, 'boxLabel');
        return items;
    },

    onSubmit: function(btn){
        var params = {
            mode: this.mode,
            animalIds: this.subjectIds,
            performedby: LABKEY.Security.currentUser.displayName
        };

        var date = this.down('#dateField').getValue();
        if(!date){
            alert('Must enter a date');
            return;
        }
        params[this.mode == 'add' ? 'date' : 'enddate'] = date;

        var remark = this.down('#remarkField').getValue();
        if (remark)
            params.remark = remark;

        var flag = this.down('#flagTypes').getValue().flagTypes;
        if(!flag){
            alert('Must choose a flag');
            return;
        }
        params.flag = flag;

        Ext4.Msg.wait('Saving...');

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('geneticscore', 'manageFlags', this.ehrCtx.EHRStudyContainer),
            method: 'POST',
            params: params,
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.successHandler, this),
            failure: LDK.Utils.getErrorCallback()
        });
    },

    successHandler: function(response){
        Ext4.Msg.hide();
        this.close();

        var added = response.added || [];
        var removed = response.removed || [];
        Ext4.Msg.alert('Success', 'Flags have been updated.  A total of ' + added.length + ' animals had flags added and ' + removed.length + ' had flags removed.  These numbers may differ from the total rows selected because flags are only added/removed if the animal needs them, and will only be added to animals actively at the center.', function(){
            this.dataRegion.refresh();
        }, this);
    }
});