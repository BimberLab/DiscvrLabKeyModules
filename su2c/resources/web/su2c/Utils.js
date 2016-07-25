Ext4.ns('SU2C.Utils');

SU2C.Utils = new function () {
	return {
		getTabbedReports: function(){
			var reports = [{
				label: 'Patient Timeline',
				name: 'patientTimeline',
				id: 'patientTimeline',
				category: 'Data',
				reportType: 'js',
				subjectFieldName: LABKEY.getModuleProperty('Study', 'subject').columnName,
				dateFieldName: 'date',
				jsHandler: function(tabPanel, tab){
					if (!tab.filters.subjects || tab.filters.subjects.length > 1){
						tab.add({html: 'This report only supports a single patient at a time.', border: false});
					}
					else {
						tab.add(Ext4.create('SU2C.panel.PatientTimelinePanel', {
							filterArray: tabPanel.getCombinedFilterArray(tab)
						}));	
					}
				}
			},
				CancerStudies.panel.TabbedReportPanel.getReportConfig('Demographics', 'study', 'Data', 'Demographics'),
				CancerStudies.panel.TabbedReportPanel.getReportConfig('Events', 'study', 'Data', 'Events'),
				//CancerStudies.panel.TabbedReportPanel.getReportConfig('IHC Slides', 'study', 'IHC', 'Slides'),
				CancerStudies.panel.TabbedReportPanel.getReportConfig('IHC Images', 'study', 'Data', 'Images'),
				CancerStudies.panel.TabbedReportPanel.getReportConfig('Quantitative Image Analysis', 'study', 'Data', 'Quantitative Image Analysis'),
				CancerStudies.panel.TabbedReportPanel.getReportConfig('Variants', 'study', 'Data', 'Variants'),{
					label: 'Mutation Mapper',
					name: 'mutationMapper',
					id: 'mutationMapper',
					category: 'Data',
					reportType: 'js',
					subjectFieldName: LABKEY.getModuleProperty('Study', 'subject').columnName,
					dateFieldName: 'date',
					jsHandler: function(tabPanel, tab){
						tab.add(Ext4.create('CancerStudies.panel.MutationMapperPanel', {
							filterArray: tabPanel.getCombinedFilterArray(tab)
						}));
					}
				}
			];
			//reports = LDK.Utils.sortByProperty(reports, 'name', false);
			//reports = LDK.Utils.sortByProperty(reports, 'sort_order', false);
			//reports = LDK.Utils.sortByProperty(reports, 'name', false);

			return reports;
		}
	}	
};