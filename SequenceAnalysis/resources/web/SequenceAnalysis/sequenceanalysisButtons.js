/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('SequenceAnalysis.Buttons');

SequenceAnalysis.Buttons = new function(){
    return {
        viewQuery: function(dataRegionName, options){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            options = options || {};

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            var params = {
                schemaName: options.schemaName ? options.schemaName : 'sequenceanalysis',
                'query.queryName': options.queryName
            };

            params['query.'+(options.keyField ? options.keyField :'analysis_id')+'~in'] = checked.join(';');

            changeLocation(LABKEY.ActionURL.buildURL(
                    'query',
                    'executeQuery.view',
                    LABKEY.ActionURL.getContainer(),
                    params
            ));

            function changeLocation(location){
                window.location = location;
            }
        },

        /**
         * The button handler used to deactivate records, such as SNPs and alignments, from a dataRegion.  Disabled due to
         * the inability to update records across containers in a single API call, which makes workbooks difficult
         */
        deactivateRecords: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.confirm("Deactivate Rows", "You are about to deactivate the selected rows.  They will no longer show up in reports.  Are you sure you want to do this?", function(button){
                if (button == 'yes'){
                    var toUpdate = [];
                    Ext4.each(checked, function(pk){
                        toUpdate.push({rowid: pk, status: false});
                    });

                    LABKEY.Query.updateRows({
                        schemaName: dataRegion.schemaName,
                        queryName: dataRegion.queryName,
                        rows: toUpdate,
                        success: function(result){
                            console.log('Success');
                        },
                        failure: LDK.Utils.getErrorCallback()
                    });
                }
            }, this);
        },

        /**
         * The button handler used to activate records, such as SNPs and alignments, from a dataRegion.  Disabled due to
         * the inability to update records across containers in a single API call, which makes workbooks difficult
         */
        activateRecords: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.confirm("Activate Rows", "You are able to activate the selected rows.  They will now show up in reports.  Are you sure you want to do this?", function(button){
                if (button == 'yes'){
                    var toUpdate = [];
                    Ext4.each(checked, function(pk){
                        toUpdate.push({rowid: pk, status: true});
                    });

                    LABKEY.Query.updateRows({
                        schemaName: dataRegion.schemaName,
                        queryName: dataRegion.queryName,
                        rows: toUpdate,
                        success: function(result){
                            console.log('Success');
                        },
                        failure: LDK.Utils.getErrorCallback()
                    });
                }
            }, this);
        },

        viewFiles: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            checked = checked.sort();
            LABKEY.Query.selectRows({
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_analyses',
                filters: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'runid',
                scope: this,
                success: function(data){
                    if (!data || !data.rows)
                        return;

                    var runIds = [];
                    Ext4.each(data.rows, function(row){
                        runIds.push(row.runid);
                    }, this);


                    var params = {
                        schemaName: 'exp',
                        'query.queryName': 'data'
                    };

                    params['query.run~in'] = runIds.join(';');

                    changeLocation(LABKEY.ActionURL.buildURL(
                            'query',
                            'executeQuery.view',
                            LABKEY.ActionURL.getContainer(),
                            params
                        ));


                },
                failure: LDK.Utils.getErrorCallback()
            });

            function changeLocation(location){
                window.location = location;
            }

        },

        viewSNPs: function(dataRegionName, options){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            options = options || {};

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                'sequenceanalysis',
                'snp_viewer.view',
                LABKEY.ActionURL.getContainer(),
                {analysisIds: checked.join(';')}
            );
        },

        performAnalysis: function(dataRegionName, btnEl){
            if (!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to analyze data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                controller: 'sequenceanalysis',
                action: 'alignmentAnalysis',
                urlParams: {
                    path: './',
                    analyses: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            }).show(btnEl);
        },

        performAnalysisFromReadsets: function(dataRegionName, btnEl){
            if (!LABKEY.Security.currentUser.canUpdate){
                alert('You do not have permission to analyze data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                controller: 'sequenceanalysis',
                action: 'sequenceAnalysis',
                urlParams: {
                    //taskId: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline',
                    path: './',
                    readsets: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            }).show(btnEl);
        },

        deactivateAlignments: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');

            var referenceMap = {};
            var readCountMap = {};
            var junctionRecordMap = {};
            var alignmentIdMap = {};
            var inactiveAlignmentIdMap = {};
            var alleleCombinations = {};

            var multi = new LABKEY.MultiRequest();
            Ext4.each(checked, function(id){
                multi.add(LABKEY.Query.selectRows, {
                    schemaName: 'SequenceAnalysis',
                    queryName: 'alignment_summary_junction',
                    timeout: 0,
                    includeTotalCount: false,
                    columns: 'rowid,analysis_id,alignment_id,alignment_id/total,ref_nt_id,ref_nt_id/name,alignment_id/container,alignment_id/container/path,status',
                    filterArray: [
                        LABKEY.Filter.create('alignment_id', id.replace(/,/g, ';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                    ],
                    scope: this,
                    success: function(data){
                        if (data.rows && data.rows.length){
                            Ext4.each(data.rows, function(row){
                                if (!junctionRecordMap[row.alignment_id])
                                    junctionRecordMap[row.alignment_id] = [];

                                junctionRecordMap[row.alignment_id].push(row);

                                var ref_name = row['ref_nt_id/name'];
                                if (!referenceMap[ref_name]){
                                    referenceMap[ref_name] = {
                                        rowid: ref_name,
                                        name: row['ref_nt_id/name']
                                    }
                                }

                                var alignment_id = row['alignment_id'];
                                if (!readCountMap[alignment_id])
                                    readCountMap[alignment_id] = row['alignment_id/total'];

                                if (!alignmentIdMap[alignment_id])
                                    alignmentIdMap[alignment_id] = [];
                                if (!inactiveAlignmentIdMap[alignment_id])
                                    inactiveAlignmentIdMap[alignment_id] = [];

                                if (row.status){
                                    alignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                                }
                                else {
                                    inactiveAlignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                                }
                            }, this);

                            for (var alignmentId in alignmentIdMap){
                                var alleleSet = alignmentIdMap[alignmentId];
                                alleleSet = Ext4.unique(alleleSet);
                                alleleSet = alleleSet.sort();
                                alleleSet = alleleSet.join(';');
                                if (!alleleCombinations[alleleSet]){
                                    alleleCombinations[alleleSet] = {
                                        active: alignmentIdMap[alignmentId],
                                        inactive: [],
                                        alignmentIds: [],
                                        alleles: {},
                                        inactiveAlleles: {}
                                    }
                                }
                                alleleCombinations[alleleSet].alignmentIds.push(alignmentId);

                                if (junctionRecordMap[alignmentId]){
                                    Ext4.each(junctionRecordMap[alignmentId], function(row){
                                        var allele = row['ref_nt_id/name'];
                                        if (row.status){
                                            if (!alleleCombinations[alleleSet].alleles[allele])
                                                alleleCombinations[alleleSet].alleles[allele] = {
                                                    total: 0,
                                                    alignmentIds: [],
                                                    rows: []
                                                };

                                            alleleCombinations[alleleSet].alleles[allele].rows.push(row);
                                            if (alleleCombinations[alleleSet].alleles[allele].alignmentIds.indexOf(row['alignment_id']) == -1){
                                                alleleCombinations[alleleSet].alleles[allele].alignmentIds.push(row['alignment_id']);
                                                alleleCombinations[alleleSet].alleles[allele].total += row['alignment_id/total'];
                                            }
                                        }
                                        else {
                                            if (!alleleCombinations[alleleSet].inactiveAlleles[allele])
                                                alleleCombinations[alleleSet].inactiveAlleles[allele] = {
                                                    total: 0,
                                                    alignmentIds: [],
                                                    rows: []
                                                };

                                            alleleCombinations[alleleSet].inactiveAlleles[allele].rows.push(row);
                                            if (alleleCombinations[alleleSet].inactiveAlleles[allele].alignmentIds.indexOf(row['alignment_id']) == -1){
                                                alleleCombinations[alleleSet].inactiveAlleles[allele].alignmentIds.push(row['alignment_id']);
                                                alleleCombinations[alleleSet].inactiveAlleles[allele].total += row['alignment_id/total'];
                                            }
                                        }
                                    }, this);
                                }
                            }
                        }
                    },
                    failure: LDK.Utils.getErrorCallback()
                });
            }, this);

            multi.send(onSuccess, this);

            function onSuccess(){
                var items = [];
                var idx = 1;
                for (var alleles in alleleCombinations){
                    var record = alleleCombinations[alleles];
                    var checkboxes = [];

                    var activeCount = 0;
                    var activeIds = Ext4.unique(record.alignmentIds);
                    Ext4.each(activeIds, function(id){
                        activeCount += readCountMap[id];
                    }, this);

                    var keys = Ext4.Object.getKeys(record.alleles);
                    keys = keys.sort();
                    Ext4.Array.forEach(keys, function(refName){
                        checkboxes.push({
                            xtype: 'checkbox',
                            inputValue: refName,
                            boxLabel: refName + ' (' + record.alleles[refName].total + ')',
                            checked: true,
                            rows: record.alleles[refName].rows
                        });
                    }, this);

                    var inactiveKeys = Ext4.Object.getKeys(record.inactiveAlleles);
                    inactiveKeys = inactiveKeys.sort();
                    Ext4.Array.forEach(inactiveKeys, function(refName){
                        checkboxes.push({
                            xtype: 'checkbox',
                            inputValue: refName,
                            boxLabel: refName + ' (' + record.inactiveAlleles[refName].total + ')',
                            checked: false,
                            rows: record.inactiveAlleles[refName].rows
                        });
                    }, this);

                    items.push({
                        border: false,
                        itemId: 'alleleSet' + idx,
                        defaults: {
                            border: false
                        },
                        items: [{
                            html: 'Group ' + idx,
                            style: 'padding-bottom: 5px;'
                        },{
                            layout: 'hbox',
                            style: 'padding-bottom: 5px;padding-left:5px;',
                            items: [{
                                xtype: 'ldk-linkbutton',
                                style: 'margin-left:5px;',
                                text: '[Check All]',
                                handler: function(btn){
                                    btn.up('panel').up('panel').down('checkboxgroup').items.each(function(item){
                                        item.setValue(true);
                                    });
                                }
                            },{
                                border: false,
                                html: '&nbsp;'
                            },{
                                xtype: 'ldk-linkbutton',
                                style: 'margin-left:5px;',
                                text: '[Uncheck All]',
                                handler: function(btn){
                                    btn.up('panel').up('panel').down('checkboxgroup').items.each(function(item){
                                        item.setValue(false);
                                    });
                                }
                            }]
                        },{
                            xtype: 'checkboxgroup',
                            width: 750,
                            style: 'margin-left:5px;',
                            columns: 2,
                            items: checkboxes
                        }]
                    });
                    idx++;
                }

                Ext4.Msg.hide();
                var window = Ext4.create('Ext.window.Window', {
                    modal: true,
                    title: 'Select Alleles',
                    width: 800,
                    maxHeight: 600,
                    autoScroll: true,
                    defaults: {
                        border: false,
                        bodyStyle: 'padding: 5px;'
                    },
                    items: [{
                        xtype: 'form',
                        width: 750,
                        border: false,
                        items: items,
                        defaults: {
                            border: false
                        }
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn){
                            var window = btn.up('window');
                            var fields = window.down('form').getForm().getFields();

                            var toUpdate = {};
                            var containerPath = [];
                            fields.each(function(cb){
                                if (cb.xtype == 'checkboxgroup')
                                    return;

                                if (!cb.isDirty())
                                    return;

                                Ext4.each(cb.rows, function(r){
                                    if (!toUpdate[r['alignment_id/container/path']])
                                        toUpdate[r['alignment_id/container/path']] = [];

                                    toUpdate[r['alignment_id/container/path']].push({
                                        rowid: r.rowid,
                                        status: cb.checked
                                    });

                                    containerPath.push(r['alignment_id/container/path']);
                                }, this);
                            }, this);

                            if (!LABKEY.Utils.isEmptyObj(toUpdate)){
                                var multi = new LABKEY.MultiRequest();

                                for (var container in toUpdate){
                                    multi.add(LABKEY.Query.updateRows, {
                                        containerPath: container,
                                        schemaName: 'sequenceanalysis',
                                        queryName: 'alignment_summary_junction',
                                        scope: this,
                                        rows: toUpdate[container],
                                        failure: LDK.Utils.getErrorCallback()
                                    });
                                }
                                multi.send(function(){
                                    window.close();
                                    dataRegion.refresh();
                                    Ext4.Msg.alert('Success', 'Records updated');
                                }, this);
                            }
                            else {
                                window.close();
                            }

                        }
                    },{
                        text: 'Close',
                        handler: function(btn){
                            btn.up('window').close();
                        }
                    }]
                }).show(btnEl);
            }
        },

        generateFastQc: function(dataRegionName, pkName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            pkName = pkName || 'readsets';
            var params = {};
            params[pkName] = checked;

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.alert('FastQC', 'You are about to run FastQC, a tool that generates reports on the selected sequence files.  Note: unless the report was previously cached for these files, it runs on the fly, meaning it may take time for the page to load, depending on the size of your input files.<p>FastQC is a third party tool, not written by the authors of this module.', function() {
                window.location = LABKEY.ActionURL.buildURL(
                    'sequenceanalysis',
                    'fastqcReport',
                    LABKEY.ActionURL.getContainer(),
                    params
                );
            }, this);
        },

        generateQualiMapReport: function(dataRegionName, pkName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            pkName = pkName || 'dataIds';
            var params = {};
            params[pkName] = checked;

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.alert('QualiMap', 'You are about to run QualiMap, an external tool that generates QC reports on the selected BAM files.  Note: this tool runs on the fly, meaning it may take time for the page to load, depending on the size of your input files.<p>QualiMap is a third party tool, not written by the authors of this module.', function(){
                window.location = LABKEY.ActionURL.buildURL(
                        'sequenceanalysis',
                        'qualiMapReport',
                        LABKEY.ActionURL.getContainer(),
                        params
                );
            }, this);
        },

        generateFastQcForAnalysis: function(dataRegionName, btnEl){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.alert('FastQC', 'You are about to run FastQC, a tool that generates reports on the selected sequence files.  Note: unless the report was previously cached for these files, it runs on the fly, meaning it may take time for the page to load, depending on the size of your input files.<p>FastQC is a third party tool, not written by the authors of this module.', function() {
                window.location = LABKEY.ActionURL.buildURL(
                        'sequenceanalysis',
                        'fastqcReport',
                        LABKEY.ActionURL.getContainer(),
                        {analysisIds: checked}
                );
            }, this);
        },

        generateIlluminaSampleFile: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'illuminaSampleSheetExport', null, {
                pks: checked,
                schemaName: 'sequenceanalysis',
                queryName: 'sequence_readsets',
                srcURL: LDK.Utils.getSrcURL()
            })
        },

        deleteTable: function(dataRegionName){
            if (!LABKEY.Security.currentUser.canDelete){
                alert('You do not have permission to delete data');
                return;
            }

            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('sequenceanalysis', 'deleteRecords', null, {
                schemaName: dataRegion.schemaName,
                'query.queryName': dataRegion.queryName,
                dataRegionSelectionKey: 'query',
                '.select': checked,
                returnURL: window.location.pathname + window.location.search
            });
        },

        sequenceOutputHandler: function(dataRegionName, handlerClass){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Checking files...');
            LABKEY.Ajax.request({
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatusForHandler'),
                params: {
                    handlerClass: handlerClass,
                    outputFileIds: checked
                },
                scope: this,
                failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
                success: LABKEY.Utils.getCallbackWrapper(function(results){
                    Ext4.Msg.hide();

                    var errors = [];
                    Ext4.Array.forEach(results.files, function(r){
                        if (!r.canProcess){
                            if (!r.fileExists){
                                errors.push('File does not exist for output: ' + r.outputFileId);
                            }
                            else if (!r.canProcess){
                                errors.push('Cannot process files of extension: ' + r.extension);
                            }
                        }
                    }, this);

                    if (errors.length){
                        errors = Ext4.Array.unique(errors);
                        Ext4.Msg.alert('Error', errors.join('<br>'));
                        return;
                    }

                    if (results.successUrl) {
                        window.location = results.successUrl;
                    }
                    else if (results.jsHandler){
                        var handlerFn = eval(results.jsHandler);
                        LDK.Assert.assertTrue('Unable to find JS handler: ' + results.jsHandler, Ext4.isFunction(handlerFn));

                        handlerFn(dataRegionName, checked, handlerClass);
                    }
                    else {
                        LDK.Utils.logError('Handler did not provide successUrl or jsHandler: ' + handlerClass);

                        Ext4.Msg.alert('Error', 'There was an error with this handler.  Please contact your site administrator');
                    }
                }, this)
            });
        },

        viewQualityMetrics: function(dataRegionName, fieldName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            var params = {
                schemaName: 'sequenceanalysis',
                'query.queryName': 'quality_metrics'
            };
            params['query.' + fieldName + '~in'] = checked.join(';');

            window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, params);
        }
    }
};