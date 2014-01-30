Ext4.define('Laboratory.window.WellAssignmentWindow', {
    extend: 'Ext.window.Window',
    config: {
        targetStore: null,
        categoryFieldName: 'category',
        wellField: null,
        displayWellField: null
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            width: 600,
            title: 'Configure Plate',
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'form',
                border: false,
                defaults: {
                    border: false
                },
                fieldDefaults: {
                    width: 450,
                    labelWidth: 175
                },
                items: [{
                    html: 'This helper is designed to assist with laying out wells in a plate.  There are 2 options.  You can either assign well numbers based on the order of samples in the grid, or you can order samples based on their category (ie. Unknown, Control, etc).  The latter is most common in cases where standards are always placed in the same wells.  If you have not already appended standards and controls, click the button below to do so now.',
                    style: 'padding-bottom: 10px;'
                },{
                    xtype: 'button',
                    border: true,
                    style: 'margin-bottom:10px;margin-left:10px;',
                    text: 'Append Standards',
                    scope: this,
                    handler: function(btn){
                        Ext4.create('Laboratory.ext.AppendStandardsWindow', {
                            targetStore: this.targetStore
                        }).show(btn);

                        //force reset of grouped fields
                        btn.up('window').down('#groupBy').setValue(false);
                    }
                },{
                    xtype: 'combo',
                    helpPopup: 'This will assign wells to the samples, starting with A1, continuing in order until all samples are assigned.  It can either fill samples by row or column.',
                    itemId: 'typeField',
                    store: {
                        type: 'array',
                        fields: ['name', 'label'],
                        data: [
                            ['addressbycolumn_96', 'By Column (ie. A1, B1, etc.)'],
                            ['addressbyrow_96', 'By Row (ie. A1, A2, A3, etc.)']
                        ]
                    },
                    value: 'addressbyrow_96',
                    fieldLabel: 'Fill Direction',
                    valueField: 'name',
                    displayField: 'label'
                },{
                    xtype: 'checkbox',
                    helpPopup: 'This allows you to assign wells to samples, grouping by category.  For each category (ie. Unknown, Control), you can specify the start well.  Samples will be assigned to wells, in the order they appear in the grid.  If you leave a category blank, existing samples from this category will be left alone.',
                    fieldLabel: 'Group By Category',
                    itemId: 'groupBy',
                    listeners: {
                        change: function(field, val){
                            var win = field.up('window');
                            var panel = win.down('#categoryArea');
                            panel.removeAll();
                            if (val){
                                panel.add(win.getCategoryItems());
                            }
                        }
                    }
                },{
                    xtype: 'form',
                    itemId: 'categoryArea'
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: function(btn){
                    var win = btn.up('window');
                    win.onSubmit(btn);
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }] });

        this.wellStore = this.getStore('_WellAssignmentWindow');
        this.callParent();
    },

    getCategoryItems: function(){
        var recordMap = {};
        var categories = [];
        this.targetStore.each(function(rec){
            var category = rec.get(this.categoryFieldName);
            if (!recordMap[category]){
                recordMap[category] = [];
                categories.push(category);
            }

            recordMap[category].push(rec);
        }, this);

        categories.sort();

        var config = {
            xtype: 'form',
            bodyStyle: 'padding-left: 10px;',
            itemId: 'categoryFields',
            items: [{
                html: 'Below are the sample categories, along with the sample count for each.  For each group, samples we be assigned to wells starting with the chosen well, then will be filled in order based on the fill direction selected above.  Empty wells will be added as needed between groups.',
                border: false,
                itemId: 'categories',
                style: 'padding-bottom: 10px;padding-top: 5px;'
            }]
        };

        Ext4.each(categories, function(c){
            config.items.push({
                xtype: 'combo',
                queryMode: 'local',
                width: 350,
                category: c,
                store: this.getStore('_WellLayoutCombo'),
                displayField: 'well_96',
                valueField: 'rowid',
                editable: true,
                fieldLabel: c + ' (' + recordMap[c].length + ')'
            })
        }, this);

        return config;
    },

    getStoreId: function(suffix){
        return LABKEY.ext4.Util.getLookupStoreId({
            lookup: {
                schemaName: 'laboratory',
                queryName: 'well_layout'
            }
        }) + (suffix ? suffix : '')
    },

    getStore: function(suffix){
        var store = Ext4.StoreMgr.get(this.getStoreId(suffix));

        if (!store){
            store = Ext4.create('LABKEY.ext4.data.Store', {
                storeId: this.getStoreId(suffix),
                schemaName: 'laboratory',
                queryName: 'well_layout',
                columns: '*',
                filterArray: [
                    LABKEY.Filter.create('plate', 1, LABKEY.Filter.Types.EQUAL)
                ],
                autoLoad: true,
                remoteSort: false
            });
        }

        return store;
    },

    onSubmit: function(){
        if (this.wellStore.isLoading()){
            this.wellStore.on('load', this.onSubmit, this, {single: true});
            return;
        }

        if (!this.down('form').getForm().isValid()){
            return;
        }
        Ext4.Msg.wait('Loading...');

        var group = this.down('#groupBy').getValue();
        var sortField = this.down('#typeField').getValue();
        this.wellStore.sort(sortField, 'ASC');

        var wellIdxMap = {};
        this.wellStore.each(function(r, idx){
            wellIdxMap[r.get(this.displayWellField)] = idx;
        }, this);

        if (group){
            var categoryMap = {};
            var categories = [];
            var ignoredCategories = [];
            this.targetStore.each(function(record, idx){
                var category = record.get(this.categoryFieldName);
                if (!categoryMap[category]){
                    categoryMap[category] = [];
                    categories.push(category);
                }
                categoryMap[category].push(record);
            }, this);

            var categoryStarts = {};
            var error = false;
            this.down('#categoryFields').getForm().getFields().each(function(field){
                var val = field.getValue();

                if (!val){
                    ignoredCategories.push(field.category);
                    return;
                }

                var idx = field.store.findExact('rowid', val);
                var comboRec = field.store.getAt(idx);
                if (!comboRec){
                    alert('Unrecognized well: ' + val);
                    return false;
                }

                var wellIdx = this.wellStore.findExact('well_96', comboRec.get(this.displayWellField));
                categoryStarts[field.category] = wellIdx;
            }, this);

            if (error){
                Ext4.Msg.hide();
                return;
            }

            ignoredCategories = ignoredCategories.sort();

            var occupiedWells = [];
            this.targetStore.each(function(record, idx){
                var category = record.get(this.categoryFieldName);
                if (ignoredCategories.indexOf(category) != -1){
                    var well = record.get(this.wellField);
                    if (well){
                        var wellIdx = this.wellStore.findExact('well_96', well);
                        if (wellIdx > -1)
                            occupiedWells.push(wellIdx);
                    }
                }
            }, this);
            occupiedWells = occupiedWells.sort();

            for (var cat in categoryStarts){
                if (!this.validateStart(cat, categoryStarts, categoryMap, occupiedWells)){
                    return;
                }
            }

            for (var cat in categoryStarts)
                this.setGroupedWells(cat, categoryStarts, categoryMap);
        }
        else {
            this.targetStore.suspendEvents();
            this.targetStore.each(function(record, idx){
                record.set(this.wellField, this.wellStore.getAt(idx).get(this.displayWellField));
                record.raw[this.wellField] = null;
            }, this);
            this.targetStore.resumeEvents();
        }

        this.targetStore.suspendEvents();
        this.targetStore.sort({
            sorterFn: function(rec1, rec2){
                var well1 = rec1.get('well');
                var well2 = rec2.get('well');
                return wellIdxMap[well1] < wellIdxMap[well2] ? -1 :
                    wellIdxMap[well1] > wellIdxMap[well2] ? 1 : 0;
            }
        });
        this.targetStore.resumeEvents();
        this.targetStore.fireEvent('datachanged');

        if (this.targetGrid){
            this.targetGrid.getView().refresh();
        }

        this.close();
        Ext4.Msg.hide();
    },

    validateStart: function(category, categoryStarts, categoryMap, occupiedWells){
        var start1 = categoryStarts[category];
        var stop1 = start1 + categoryMap[category].length - 1;

        for (var i=0;i<occupiedWells.length;i++){
            var idx = occupiedWells[i];
            if (idx >= start1 && idx <= stop1){
                var rec = this.wellStore.getAt(idx);
                var wellName = rec.get(this.displayWellField);
                alert('Error: ' + category + ' conflicts with an existing sample in well: ' + wellName);
                Ext4.Msg.hide();
                return false;
            }
        };

        if (stop1 >= this.wellStore.getCount()){
            alert('Error: ' + category + ' extends beyond the plate');
            Ext4.Msg.hide();
            return false;
        }

        for (var category2 in categoryStarts){
            if (category2 == category)
                continue;

            var start2 = categoryStarts[category2];
            var stop2 = start2 + categoryMap[category2].length - 1;

            if (start2 <= stop1 && stop2 >= start1){
                alert('Error: ' + category + ' overlaps with ' + category2);
                Ext4.Msg.hide();
                return false;
            }
        }
        return true;
    },

    setGroupedWells: function(category, categoryStarts, categoryMap){
        var start = categoryStarts[category];
        var records = categoryMap[category];
        this.targetStore.suspendEvents();
        for (var i=0;i<records.length;i++){
            var record = records[i];
            var wellIdx = start + i;
            var wellRec = this.wellStore.getAt(wellIdx);
            record.set(this.wellField, wellRec.get(this.displayWellField));
            record.raw[this.wellField] = null;
        }
        this.targetStore.resumeEvents();
    }
});

