Ext4.define('CancerStudies.panel.PatientTabbedReportPanel', {
	extend: 'CancerStudies.panel.TabbedReportPanel',

	initComponent: function () {
		this.filterTypes = [{
			xtype: 'ldk-singlesubjectfiltertype',
			inputValue: LDK.panel.SingleSubjectFilterType.filterName,
			label: 'Single Participant',
			hidden: true,
			participantId: this.participantId,
			getSubjects: function(){
				return [this.participantId];
			},
			checkValid: function(){
				return true;
			}
		}];
		
		this.callParent(arguments);

		this.down('#submitBtn').hidden = true;
	},

	getFilterOptionsItems: function(){
		var items = this.callParent();
		items[0].hidden = true;
		items[1].hidden = true;

		return items;
	}
});