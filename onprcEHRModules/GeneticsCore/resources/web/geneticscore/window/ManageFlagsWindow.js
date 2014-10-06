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
                html: 'Loading...',
                border: false
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

        LABKEY.Query.selectRows({
            containerPath: this.ehrCtx.EHRStudyContainer,
            schemaName: 'ehr_lookups',
            queryName: 'flag_values',
            filterArray: [
                LABKEY.Filter.create('category', 'Genetics'),
                LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: this.onLoad
        })
    },

    onLoad: function(results){
        if (!results || !results.rows || !results.rows.length){
            Ext4.Msg.alert('Error', 'No genetics flags found');
            return;
        }

        var items = [{
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
                items: this.getRadioItems(results)
            }]
        }];

        this.removeAll();
        this.add(items);
    },

    getRadioItems: function(results){
        var items = [];

        Ext4.Array.forEach(results.rows, function(row){
            items.push({
                boxLabel: row.value,
                inputValue: row.objectid
            });
        }, this);

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

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('ehr', 'manageFlags', this.ehrCtx.EHRStudyContainer),
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