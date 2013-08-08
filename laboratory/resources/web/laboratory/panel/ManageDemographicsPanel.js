Ext4.define('Laboratory.panel.ManageDemographicsPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.laboratory-managedemographicspanel',
    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            border: false,
            defaults: {
                border: false
            },
            items: [{
                html: 'Loading...'
            }]
        });

        this.callParent();

        Laboratory.Utils.getDemographicsSources({
            scope: this,
            includeTotals: true,
            success: this.onLoad
        });
    },

    onLoad: function(results){
        this.removeAll();

        var toAdd = {
            itemId: 'itemsPanel',
            layout: {
                type: 'table',
                columns: 2
            },
            defaults: {
                border: false,
                style: 'padding: 5px;'
            },
            bodyStyle: 'margin-bottom: 10px',
            items: [{
                html: '<b>Label</b>'
            },{
                html: '<b>Total Subjects</b>'
            }]
        };

        if (results.sources && results.sources.length){
            results.sources = LDK.Utils.sortByProperty(results.sources, 'label', false);
            Ext4.each(results.sources, function(source, idx){
                if (source){
                    toAdd.items = toAdd.items.concat([{
                        html: (source.canRead ? '<a href="' + source.browseUrl + '">' : '') + source.label + (!source.containerPath ? "" : ' (' + source.containerPath + ')') + (source.canRead ? '</a>' : '')
                    },{
                        html: source.canRead ? '<a href="' + source.browseUrl + '">' + source.total + '</a>' : 'User does not have permission to read this table'
                    }]);
                }
            }, this);
        }

        if (results.sources.length)
            this.add(toAdd);
        else
            this.add({html: 'No sources have been registered'});
    }
});
