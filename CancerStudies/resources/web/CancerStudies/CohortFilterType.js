Ext4.define('CancerStudies.panel.CohortFilterType', {
    extend: 'LDK.panel.AbstractFilterType',
    alias: 'widget.cancerstudies-cohortfiltertype',

    nounSingular: 'Cohort',
    
    statics: {
        filterName: 'cohort',
        label: 'Cohort'
    },

    initComponent: function(){
        this.items = this.getItems();

        this.callParent();
    },

    getItems: function(){
        var ctx = this.filterContext;
        var toAdd = [];

        toAdd.push({
            width: 200,
            html: 'Choose ' + this.nounSingular + ':',
            style: 'margin-bottom:10px'
        });

        toAdd.push({
            xtype: 'panel',
            items: [{
                xtype: 'combo',
                width: 265,
                itemId: 'cohortField',
                displayField: 'label',
                valueField: 'label',
                multiSelect: false,
                store: {
                    type: 'labkey-store',
                    schemaName: 'study',
                    queryName: 'cohort',
                    autoLoad: true
                },
                value: Ext4.isArray(ctx.cohorts) ? ctx.cohorts.join(';') : ctx.cohorts
            }]
        });

        return toAdd;
    },

    getFilters: function(){
        return {
            cohorts: this.getCohorts()
        }
    },

    getFilterArray: function(tab){
        var filterArray = {
            removable: [],
            nonRemovable: []
        };

        var filters = this.getFilters();
        var fieldName = 'patientId/cohort';
        filterArray.nonRemovable.push(LABKEY.Filter.create(fieldName, null, LABKEY.Filter.Types.NONBLANK));

        return filterArray;
    },

    checkValid: function(){
        var val = this.down('#cohortField').getValue();
        if(!val || !val.length){
            Ext4.Msg.alert('Error', 'Must choose a ' + this.nounSingular);
            return false;
        };

        return true;
    },

    validateReport: function(report){
        return null;
    },

    getTitle: function(){
        var cohorts = this.getCohorts();

        if (cohorts && cohorts.length){
            return cohorts.join(', ');
        }

        return '';
    },

    getCohorts: function(){
        var cohortArray = this.down('#cohortField').getValue();
        if (cohortArray || !Ext4.isArray(cohortArray))
            cohortArray = [cohortArray];

        if (cohortArray.length > 0){
            cohortArray = Ext4.unique(cohortArray);
            cohortArray.sort();
        }

        return cohortArray;
    }
});