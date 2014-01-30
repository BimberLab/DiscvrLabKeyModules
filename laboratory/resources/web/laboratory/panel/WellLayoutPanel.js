Ext4.define('Laboratory.ext.WellLayoutPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.welllayoutpanel',
    config: {
        wellField: 'well',
        sortField: 'addressbyrow_96',
        displayWell: 'well_96',
        orientation: 'row',
        categoryMap: {
            Unknown: '#0000FF',
            'Neg Control': '#FFFF00',
            'Pos Control': '#FFC0CB',
            Standard: '#008000'
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            layout: 'hbox',
            minHeight: 170,
            items: [{
                itemId: 'plate',
                border: true,
                layout: {
                    type: 'table',
                    columns: 12
                }
            },{
                itemId: 'legend',
                border: false,
                defaults: {
                    border: false
                },
                layout: {
                    type: 'table',
                    columns: 2
                },
                bodyStyle: 'padding-left: 20px'
            }]
        });

        this.getStore();
        this.callParent();

        this.targetStore.on('datachanged', this.drawWell, this, {buffer: 200, delay: 50});
        this.targetStore.on('update', this.drawWell, this, {buffer: 200, delay: 50});

        if (!this.targetStore.isLoading())
            this.drawWell();
    },

    drawWell: function(){
        if (!this.store.getCount()){
            this.store.on('load', this.drawWell, this, {single: true});
            return;
        }

        var platePanel = this.down('#plate');
        var legendPanel = this.down('#legend');
        var notAssigned = 0;
        var unknownWells = [];

        var wellMap = {};
        this.targetStore.each(function(rec){
            var well = rec.get(this.wellField);
            if (!well){
                notAssigned++;
                return;
            }
            wellMap[well] = rec;

            var wellRecordIdx = this.store.findExact(this.displayWell, well);
            if (wellRecordIdx == -1){
                unknownWells.push(well);
            }
        }, this);

        platePanel.removeAll();
        legendPanel.removeAll();

        this.store.sort(this.sortField, 'ASC');

        var toAdd = [];
        var categories = [];
        this.store.each(function(record, idx){
            var well = record.get(this.displayWell);

            var color = '';
            if(wellMap[well]){
                var row = wellMap[well];
                color = this.categoryMap[row.get('category')];
                categories.push(row.get('category'));
            }

            toAdd.push({
                html: well,
                border: true,
                minWidth: 80,
                bodyStyle: color ? 'background-color: ' + color + ';' : ''
            });
        }, this);

        platePanel.add(toAdd);

        var legendItems = [];
        categories = Ext4.unique(categories);
        categories = categories.sort();
        Ext4.each(categories, function(item){
            legendItems.push({
                html: item + ':',
                bodyStyle: 'padding-bottom: 3px;padding-right: 5px;'
            });
            legendItems.push({
                html: '&nbsp;',
                width: 30,
                border: true,
                bodyStyle: 'background-color: ' + this.categoryMap[item] + ';'
            });
        }, this);

        if (notAssigned){
            legendItems.push({
                html: 'Not Assigned:',
                bodyStyle: 'padding-bottom: 3px;padding-right: 5px;'
            });
            legendItems.push({
                html: notAssigned + '&nbsp;',
                border: false
            });
        }

        if (unknownWells.length){
            legendItems.push({
                html: 'Unknown Wells:',
                bodyStyle: 'padding-bottom: 3px;padding-right: 5px;'
            });
            unknownWells = unknownWells.sort();
            legendItems.push({
                html: unknownWells.join(', '),
                border: false
            });
        }
        legendPanel.add(legendItems)
    },

    getStoreId: function(){
        return LABKEY.ext4.Util.getLookupStoreId({
            lookup: {
                schemaName: 'laboratory',
                queryName: 'well_layout'
            }
        }) + '_WellLayoutPanel'
    },

    getStore: function(){
        this.store = Ext4.StoreMgr.get(this.getStoreId());

        if (!this.store){
            this.store = Ext4.create('LABKEY.ext4.data.Store', {
                storeId: this.getStoreId(),
                schemaName: 'laboratory',
                queryName: 'well_layout',
                columns: '*',
                filterArray: [
                    LABKEY.Filter.create('plate', 1, LABKEY.Filter.Types.EQUAL)
                ],
                autoLoad: true
            });
        }
    }

});