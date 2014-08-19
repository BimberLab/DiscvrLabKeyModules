Ext4.define('SequenceAnalysis.window.CreateReferenceLibraryWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.CreateReferenceLibraryWindow', {
                dataRegionName: dataRegionName,
                rowIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Create Reference Genome',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This will create a reference genome from the selected sequences.  In this context, genome refers to a collection of reference sequences, and does not necessarily represent a true genome.  This genome can be reused between analyses.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'panel',
                itemId: 'fieldPanel',
                defaults: {
                    border: false,
                    labelWidth: 180,
                    width: 550
                },
                items: [{
                    xtype: 'textfield',
                    fieldLabel: 'Name',
                    itemId: 'name',
                    allowBlank: false
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Description',
                    itemId: 'description'
                },{
                    xtype: 'checkbox',
                    itemId: 'customIntervals',
                    fieldLabel: 'Choose Custom Intervals',
                    helpPopup: 'This option will allow you to construct a genome using custom segments/intervals from the selected reference sequences.  This is often useful if you want to inspect a custom set of regions of interest.',
                    scope: this,
                    checked: false,
                    listeners: {
                        scope: this,
                        change: function (field, val) {
                            this.toggleItems(val);
                        }
                    }
                },{
                    xtype: 'panel',
                    itemId: 'intervalArea',
                    defaults: {
                        border: false,
                        labelWidth: 180,
                        width: 550
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

        Ext4.QuickTips.init();
    },

    getIntervalItems: function(showDetail){
        var ret = [];

        if (showDetail){
            ret.push({
                html: 'Enter desired intervals below in the format start-stop (ie. 300-400).  To specify multiple intervals, separate with commas (ie. 300-400,500-600).',
                style: 'padding-bottom: 10px;'
            });

            Ext4.Array.forEach(this.sequenceData, function(i){
                ret.push({
                    xtype: 'textfield',
                    name: 'interval',
                    refId: i.rowid,
                    fieldLabel: i.name,
                    validator: function(val){
                        if (val === null || val === ''){
                            return true;
                        }

                        if (val.match(/ /g)){
                            return 'The value cannot contain spaces';
                        }

                        var tokens = val.split(',');
                        var ret = true;
                        Ext4.Array.forEach(tokens, function(t){
                            if (!t.match(/^[0-9]+-[0-9]+$/)){
                                ret =  'Invalid interval: [' + t + ']';
                                return false;
                            }
                        }, this);

                        return ret;
                    }
                });
            }, this);
        }

        return ret;
    },

    toggleItems: function(val){
        if (val && !this.sequenceData){
            this.loadSequenceData();
            return;
        }

        var target = this.down('#intervalArea');
        target.removeAll();
        target.add(this.getIntervalItems(val));
    },

    loadSequenceData: function(){
        Ext4.Msg.wait('Loading...');

        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'ref_nt_sequences',
            columns: 'rowid,name',
            filterArray: [LABKEY.Filter.create('rowid', this.rowIds.join(';'), LABKEY.Filter.Types.IN)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                Ext4.Msg.hide();

                if (!results || !results.rows || !results.rows.length){
                    Ext4.Msg.alert('Error', 'Error, no matching rows found');
                    return;
                }

                this.sequenceData = [];
                Ext4.Array.forEach(results.rows, function(row){
                    this.sequenceData.push({
                        name: row.name,
                        rowid: row.rowid
                    });
                }, this);

                this.toggleItems(true);
            }
        });
    },

    onSubmit: function(btn){
        var name = this.down('#name').getValue();
        if (!name){
            Ext4.Msg.alert('Error', 'Must provide a name');
            return;
        }

        var description = this.down('#description').getValue();
        var jsonData = {
            name: name,
            description: description,
            sequenceIds: this.rowIds
        };

        if (this.down('#customIntervals').getValue()){
            var intervals = [];
            var error = false;
            Ext4.Array.forEach(this.query('field[name=interval]'), function(field){
                if (!field.isValid()){
                    error = true;
                    return false;
                }

                intervals.push(field.getValue());
            }, this);

            if (error){
                Ext4.Msg.alert('Error', 'One or more intervals in not valid.  Hover over the red fields for more detail.');
                return;
            }

            jsonData.intervals = intervals;
        }

        Ext4.Msg.wait('Loading...');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'createReferenceLibrary', null, null),
            jsonData: jsonData,
            scope: this,
            success: function(){
                Ext4.Msg.hide();

                this.close();

                Ext4.Msg.alert('Success', 'Pipeline job started!', function(){
                    window.location = LABKEY.ActionURL.buildURL('pipeline-status', 'showList');
                }, this);
            },
            failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback())
        });
    }
});
