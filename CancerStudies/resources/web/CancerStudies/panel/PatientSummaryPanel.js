Ext4.define('CancerStudies.panel.PatientSummaryPanel', {
	extend: 'Ext.panel.Panel',
	
	initComponent: function(){
		Ext4.apply(this, {
			border: false,
			items: [{
				xtype: 'dataview',
				store: {
					type: 'labkey-store',
					filterArray: this.filterArray,
					schemaName: 'study',
					queryName: 'demographics',
					columns: LABKEY.getModuleProperty('Study', 'subject').columnName + ',date,firstName,lastName,mrn,birth,gender,consentStatus,consentedDate,death,somaticMutations/totalVariants',
					autoLoad: true,
					listeners: {
						scope: this,
						load: function(s){
							if (!s.getCount()) {
								this.down('dataview').refresh();
							}
						}
					}
				},
				emptyText: LABKEY.getModuleProperty('Study', 'subject').nounSingular + ' Not Found',
				tpl: ['<tpl for=".">',
					'<b>Patient Information:</b><br>',
					'<table>',
					'<tr><td>' + LABKEY.getModuleProperty('Study', 'subject').nounSingular + ':</td><td>{' + LABKEY.getModuleProperty('Study', 'subject').columnName + '}</td></tr>',
					'<tr><td>MRN:</td><td>{mrn}</td></tr>',
					'<tr><td>Name:</td><td>{lastName}, {firstName}</td></tr>',
					'<tr><td>Gender:</td><td>{gender}</td></tr>',
					'<tr><td>Birth:</td><td>{birth:date("Y-m-d")}</td></tr>',
					'<tr><td>Death:</td><td>{death:date("Y-m-d")}</td></tr>',
					'<tr><td>Consent Status:</td><td>{[values.consentStatus || "Unknown"]} <tpl if="consentedDate">({consentedDate:date("Y-m-d")})</tpl></td></tr>',
					'</table>',
					'<br>',
					'<b>Data Summary:</b>',
					'<table>',
					'<tr><td>Images:</td><td>{[this.getDataLink(values, "somaticMutations/totalVariants", "images")]}</td></tr>',
					'<tr><td>Variants:</td><td>{[this.getDataLink(values, "somaticMutations/totalVariants", "variants")]}</td></tr>',
					'</table>',
					'</tpl>',
					{
						getDataLink: function(values, fieldName, queryName){
							var props = {
								schemaName: "study", 
								queryName: queryName	
							};

							props["query." + LABKEY.getModuleProperty('Study', 'subject').columnName + "~eq"] = values[LABKEY.getModuleProperty('Study', 'subject').columnName];
							
							return '<a href="' + LABKEY.ActionURL.buildURL("query", "executeQuery", null, props) + '">' + (values[fieldName] || "0") + '</a>';
						}
					}
				]
			}]
		});
		
		this.callParent(arguments);
	}
});