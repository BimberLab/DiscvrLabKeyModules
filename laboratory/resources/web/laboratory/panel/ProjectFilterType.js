Ext4.define('Laboratory.panel.ProjectFilterType', {
    extend: 'LDK.panel.AbstractFilterType',
    alias: 'widget.laboratory-projectfiltertype',

    statics: {
        filterName: 'project',
        label: 'Subject Groups'
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
            html: 'Choose Projects/Groups:',
            style: 'margin-bottom:10px'
        });

        toAdd.push({
            xtype: 'panel',
            items: [{
                xtype: 'combo',
                width: 265,
                itemId: 'projectField',
                displayField: 'project',
                valueField: 'project',
                multiSelect: false,
                store: {
                    type: 'labkey-store',
                    schemaName: 'laboratory',
                    sql: 'select distinct project from laboratory.project_usage WHERE project IS NOT NULL GROUP BY project',
                    autoLoad: true
                },
                value: LABKEY.ExtAdapter.isArray(ctx.projects) ? ctx.projects.join(';') : ctx.projects
            },{
                xtype: 'radiogroup',
                columns: 1,
                minHeight: 60,
                width: 700,
                itemId: 'projectFilterMode',
                defaults: {
                    xtype: 'radio',
                    name: 'projectFilterMode'
                },
                items: [{
                    boxLabel: 'Include if subject was ever a member of the project/group',
                    inputValue: 'allProjects',
                    checked: ctx.projectFilterMode != 'overlappingProjects'
                },{
                    boxLabel: 'Include only if the sample date overlaps with assignment to that project/group',
                    inputValue: 'overlappingProjects',
                    checked: ctx.projectFilterMode == 'overlappingProjects'
                }]
            }]
        });

        return toAdd;
    },

    getFilters: function(){
        return {
            projects: this.getProjects(),
            projectFilterMode: this.down('#projectFilterMode').getValue().projectFilterMode
        }
    },

    getFilterArray: function(tab){
        var filterArray = {
            removable: [],
            nonRemovable: []
        };

        var filters = this.getFilters();
        var report = tab.report;
        var projectFieldName = (filters.projectFilterMode == 'overlappingProjects') ? report.overlappingProjectsFieldName : report.allProjectsFieldName;
        if (!projectFieldName){
            if (filters.projectFilterMode == 'overlappingProjects' && !report.overlappingProjectsFieldName){
                projectFieldName = report.allProjectsFieldName;

                if (projectFieldName)
                    Ext4.Msg.alert('Warning', 'This reports supports project filtering, but cannot filter by overlapping projects, since it lacks a properly configured date field.  All animals assigned to the project will be shown');
                else
                    return filterArray;
            }
            else {
                LDK.Utils.logToServer({
                    message: 'A TabbedReport is attempting to load a project filter when it should have been stopped upstream',
                    level: 'ERROR',
                    includeContext: true
                });

                return filterArray;
            }
        }

        projectFieldName = projectFieldName + '/' + filters.projects[0] + '::lastStartDate';
        filterArray.nonRemovable.push(LABKEY.Filter.create(projectFieldName, null, LABKEY.Filter.Types.NONBLANK));

        return filterArray;
    },

    checkValid: function(){
        var val = this.down('#projectField').getValue();
        if(!val || !val.length){
            Ext4.Msg.alert('Error', 'Must choose a project');
            return false;
        };

        return true;
    },

    validateReport: function(report){
        if (!report.allProjectsFieldName){
            return 'This report cannot be used with the selected filter type, because the report does not contain a field with project information';
        }

        return null;
    },

    getTitle: function(){
        var projects = this.getProjects();

        if (projects && projects.length){
            return projects.join(', ');
        }

        return '';
    },

    getProjects: function(){
        var projectArray = this.down('#projectField').getValue();
        if (projectArray || !Ext4.isArray(projectArray))
            projectArray = [projectArray];

        if (projectArray.length > 0){
            projectArray = Ext4.unique(projectArray);
            projectArray.sort();
        }

        return projectArray;
    }
});