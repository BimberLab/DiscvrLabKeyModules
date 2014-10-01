Ext4.ns('Laboratory.tabbedReports');

Laboratory.tabbedReports = new function(){
    return {
        subjectSummary: function(tabPanel, tab){
            var subjectIds = tab.filters.subjects;
            if (!subjectIds || !subjectIds.length){
                tab.add({
                    html: 'Either no subject IDs were provided or this report cannot be used with the selected filter type',
                    border: false
                });

                return;
            }

            tab.add({
                border: false,
                html: 'Loading...'
            });

            Laboratory.Utils.getSubjectIdSummary({
                subjectIds: subjectIds,
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    tab.removeAll();
                    console.log(results);

                    if (!results.results || !Ext4.Object.getKeys(results.results).length){
                        tab.add({
                            border: false,
                            html: 'No data found'
                        });
                        return;
                    }

                    var toAdd = [];

                    for (var subjectId in results.results){
                        var items = results.results[subjectId];

                        var config = {
                            xtype: 'ldk-navpanel',
                            style: 'padding-bottom: 10px;padding-left: 5px;',
                            defaults: {
                                border: false
                            },
                            sections: []
                        };

                        if(Ext4.isArray(items.data) && items.data.length){
                            config.sections.push({
                                header: 'Types of Data',
                                items: items.data
                            });
                        }

                        if(Ext4.isArray(items.samples) && items.samples.length){
                            config.sections.push({
                                header: 'Samples and Materials',
                                items: items.samples
                            });
                        }

                        toAdd.push({
                            html: '<b>' + subjectId + ':</b>',
                            style: 'padding-bottom: 10px;',
                            border: false
                        });

                        if (config.sections.length)
                            toAdd.push(config);
                        else {
                            toAdd.push({
                                html: 'No data',
                                border: false
                            });
                        }
                    }

                    tab.add(toAdd);
                }
            });
        }
    }
};