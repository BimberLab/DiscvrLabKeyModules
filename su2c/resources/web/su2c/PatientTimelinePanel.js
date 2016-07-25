Ext4.define('SU2C.panel.PatientTimelinePanel', {
	extend: 'Ext.panel.Panel',

	initComponent: function(){
		Ext4.apply(this, {
			border: false,
			items: [{
				html: 'This will show a timeline of patient events',
				border: false
			}]
		});

		this.callParent();
	}
});