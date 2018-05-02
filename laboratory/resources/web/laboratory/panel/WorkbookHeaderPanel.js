/**
 * @param description
 * @param materials
 * @param methods
 * @param results
 */
Ext4.define('Laboratory.panel.WorkbookHeaderPanel', {
    extend: 'LDK.panel.WebpartPanel',
    initComponent: function(){
        Ext4.apply(this, {
            title: 'Details',
            defaults: {
                border: false
            },
            items: [{
                html: 'Description:',
                style: 'font-weight: bold;'
            },
                this.getFieldCfg('description', this.description)
                ,{
                    html: 'Materials:',
                    style: 'font-weight: bold;padding-top: 10px;'
                },
                this.getFieldCfg('materials', this.materials)
                ,{
                    html: 'Methods:',
                    style: 'font-weight: bold;padding-top: 10px;'
                },
                this.getFieldCfg('methods', this.methods)
                ,{
                    html: 'Results:',
                    style: 'font-weight: bold;padding-top: 10px;'
                },
                this.getFieldCfg('results', this.results)
                ,{
                    html: 'Tags:',
                    style: 'font-weight: bold;padding-top: 10px;'
                },
                this.getTagFieldCfg()
            ]
        });

        this.callParent(arguments);
    },

    getTagFieldCfg: function(){
        var cfg = {
            layout: 'hbox',
            itemId: 'tags',
            style: 'margin-bottom: 10px;margin-top: 10px;',
            border: false,
            items: []
        };

        cfg.items.push({
            xtype: 'button',
            style: 'margin: 2px;',
            text: 'Add Tag',
            scope: this,
            handler: function(btn){
                Ext4.create('Laboratory.window.WorkbookTagsWindow', {
                    containerId: LABKEY.Security.currentContainer.id,
                    workbookHeaderPanel: this,

                    onSubmit: function(){
                        var val = this.getValue();
                        if (!val){
                            Ext4.Msg.alert('Error', 'Must provide a tag to apply to these workbooks');
                            return;
                        }

                        this.workbookHeaderPanel.addTag(val);
                        this.close();
                    }
                }).show();
            }
        });

        if (this.tags && this.tags.length){
            Ext4.each(this.tags, function(tag){
                cfg.items.push({
                    xtype: 'button',
                    style: 'margin: 2px;',
                    text: tag + ' (X)',
                    tag: tag,
                    scope: this,
                    handler: function(btn){
                        Ext4.Msg.confirm('Remove Tag', 'Do you want to remove this tag?', function(val){
                            if (val == 'yes'){
                                this.tags.remove(btn.tag);
                                btn.destroy();
                                this.onUpdate();
                            }
                        }, this);
                    }
                });
            }, this);
        }

        return cfg;
    },

    addTag: function(val){
        this.tags = this.tags || [];
        this.tags.push(val);
        this.tags = Ext4.unique(this.tags);
        this.tags = this.tags.sort();
        this.onUpdate();
        this.redrawTagsButtons();
    },

    getFieldCfg: function(itemId, value){
        return {
            xtype: 'ldk-editinplaceelement',
            border: false,
            itemId: itemId,
            editable: LABKEY.Security.currentUser.canUpdate,
            value: value,
            emptyText: 'Click to enter text',
            style: 'padding-top: 10px;',
            listeners: {
                scope: this,
                update: this.onUpdate
            }
        }
    },

    redrawTagsButtons: function(){
        var tags = this.down('#tags');
        if (tags){
            tags.destroy();
        }

        this.add(this.getTagFieldCfg());
    },

    onUpdate: function(){
        var values = {};
        Ext4.each(['description', 'materials', 'methods', 'results'], function(text){
            values[text] = this.down('#' + text).getValue();
        }, this);

        values.tags = this.tags;
        values.forceTagUpdate = true;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('laboratory', 'updateWorkbook'),
            method: 'POST',
            params: values,
            failure: LDK.Utils.getErrorCallback()
        });
    }
});