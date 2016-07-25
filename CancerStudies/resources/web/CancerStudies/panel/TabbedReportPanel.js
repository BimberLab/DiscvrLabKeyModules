Ext4.define('CancerStudies.panel.TabbedReportPanel', {
	extend: 'LDK.panel.TabbedReportPanel',
	
	statics: {
		getReportConfig: function(queryName, schemaName, category, title){
			return {
				id: queryName,
				name: queryName,
				category: category,
				schemaName: schemaName,
				queryName: queryName,
				label: title,
				reportType: 'query',
				subjectFieldName: LABKEY.getModuleProperty('Study', 'subject').columnName,
				dateFieldName: 'date'
			};
		}
	},
	
	initComponent: function(){
		Ext4.ns('CancerStudies.tabbedReports');

		Ext4.apply(this, {
			reportNamespace: CancerStudies.tabbedReports
		});

		this.callParent();
		
		this.loadReports();
	},
	
	getSimpleXYPlot: function(queryName, schemaName, category, title, plotConfig){
		if (!plotConfig || !plotConfig.x || !plotConfig.y){
			Ext4.Msg.alert('Error', 'Must provide the plotConfig and X/Y field names');
			return;
		}

		return {
			id: 'plot-' + queryName,
			name: queryName,
			category: category,
			schemaName: schemaName,
			queryName: queryName,
			label: title,
			reportType: 'js',
			subjectFieldName: LABKEY.getModuleProperty('Study', 'subject').columnName,
			dateFieldName: 'date',
			plotConfig: plotConfig,
			jsHandler: function(tabPanel, tab){
				var filterArray = tabPanel.getCombinedFilterArray(tab);

				LABKEY.Query.selectRows({
					schemaName: tab.report.schemaName,
					queryName: tab.report.queryName,
					filterArray: filterArray,
					scope: this,
					failure: LDK.Utils.getErrorCallback(),
					success: function(results){
						console.log(results);
						var x = [];
						var y = [];

						LDK.ConvertUtils.parseDatesInSelectRowsResults(results);

						Ext4.Array.forEach(results.rows, function(row){
							if (Ext4.isDefined(row[tab.report.plotConfig.x]) && Ext4.isDefined(row[tab.report.plotConfig.y])){
								x.push(row[tab.report.plotConfig.x]);
								y.push(row[tab.report.plotConfig.y]);
							}
						}, this);

						if (!results.rows.length){
							tab.add({
								html: 'No records found',
								border: false
							});
						}
						else {
							tab.add(Ext4.create('LDK.panel.ContentResizingPanel', {
								plotConfig: tab.report.plotConfig,
								width: '100%',
								minHeight: 50,
								border: false,
								listeners: {
									scope: this,
									afterRender: function(panel){
										var data = [{
											x: x,
											y: y,
											mode: 'lines+markers',
											type: 'scatter'
										}];

										Plotly.plot(panel.renderTarget, data, {
											title: panel.plotConfig.title,
											//autosize: false,
											//width: '100%', //why not working?
											displayModeBar: true,
											xaxis: {
												type: 'date',
												tickformat: '%x',
												//tickmode: 'auto',
												ticks: 'outside',
												tickwidth: 1,
												tickangle: 40,
												ticklen: 5,
												showticklabels: true,
												showline: false,
												showgrid: true,
												autorange: true
											},
											yaxis: {
												showgrid: true
											}
										});
									}
								}
							}));
						}
					}
				});
			}
		};
	}
});