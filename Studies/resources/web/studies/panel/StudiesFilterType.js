Ext4.define('Laboratory.panel.StudiesFilterType', {
    extend: 'LDK.panel.AbstractFilterType',
    alias: 'widget.studies-filtertype',

    statics: {
        filterName: 'study',
        label: 'Studies'
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
            html: 'Choose Study:',
            style: 'margin-bottom:10px'
        });

        toAdd.push({
            xtype: 'panel',
            items: [{
                xtype: 'combo',
                width: 265,
                itemId: 'studyField',
                displayField: 'studyName',
                valueField: 'studyName',
                multiSelect: false,
                forceSelection: true,
                triggerAction: 'all',
                queryMode: 'local',
                store: {
                    type: 'labkey-store',
                    schemaName: 'studies',
                    queryName: 'studies',
                    columns: 'studyName',
                    autoLoad: true
                },
                value: Ext4.isArray(ctx.studies) ? ctx.studies.join(';') : ctx.studies
            }]
        });

        return toAdd;
    },

    getFilters: function(){
        return {
            studies: this.getStudies()
        }
    },

    getFilterArray: function(tab){
        var filterArray = {
            removable: [],
            nonRemovable: []
        };

        if (this.reportQCStates?.length) {
            filterArray.nonRemovable.push(LABKEY.Filter.create('qcstate/label', this.reportQCStates, LABKEY.Filter.Types.EQUALS_ONE_OF));
        }

        var filters = this.getFilters();
        var report = tab.report;
        var studyFieldName = report.additionalFieldKeys?.studyAssignmentFieldKey;
        if (!studyFieldName){
            LDK.Utils.logToServer({
                message: 'A TabbedReport is attempting to load a study filter when it should have been stopped upstream',
                level: 'ERROR',
                includeContext: true
            });

            return filterArray;
        }

        const studyName = filters.studies[0];
        const projectFieldName = 'allProjectsPivot/' + studyName + '::lastStartDate';
        filterArray.nonRemovable.push(LABKEY.Filter.create(projectFieldName, null, LABKEY.Filter.Types.NONBLANK));

        return filterArray;
    },

    isValid: function(){
        var val = this.down('#studyField').getValue();
        if (!val || !val.length){
            return false;
        }

        return true;
    },

    getFilterInvalidMessage: function(){
        return 'Error: Must choose a study';
    },

    validateReportForFilterType: function(report){
        if (!report.additionalFieldKeys?.studyAssignmentFieldKey){
            return 'This report cannot be used with the selected filter type, because the report does not contain a field with study assignment information';
        }

        return null;
    },

    getTitle: function(){
        var studies = this.getStudies();

        if (studies && studies.length){
            return studies.join(', ');
        }

        return '';
    },

    getStudies: function(){
        var projectArray = this.down('#studyField').getValue();
        if (projectArray && !Ext4.isArray(projectArray)) {
            projectArray = [projectArray];
        }

        if (projectArray && projectArray.length > 0){
            projectArray = Ext4.unique(projectArray);
            projectArray.sort();
        }

        return projectArray;
    }
});