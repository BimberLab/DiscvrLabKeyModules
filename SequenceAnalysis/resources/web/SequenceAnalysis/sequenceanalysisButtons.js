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
                    dataRegion.containerPath,
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
                filterArray: [
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
                            dataRegion.containerPath,
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
                dataRegion.containerPath,
                {analysisIds: checked.join(';')}
            );
        },

        viewAlignmentsPivoted: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL(
                    'sequenceanalysis',
                    'lineagePivot',
                    dataRegion.containerPath,
                    {analysisIds: checked.join(';')}
            );
        },

        performAnalysis: function(dataRegionName){
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
                autoShow: true,
                controller: 'sequenceanalysis',
                action: 'alignmentAnalysis',
                urlParams: {
                    path: './',
                    analyses: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            });
        },

        performAnalysisFromReadsets: function(dataRegionName){
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
                autoShow: true,
                controller: 'sequenceanalysis',
                action: 'sequenceAnalysis',
                urlParams: {
                    //taskId: 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline',
                    path: './',
                    readsets: checked.join(';')
                },
                workbookFolderType: Laboratory.Utils.getDefaultWorkbookFolderType()
            });
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
                    dataRegion.containerPath,
                    params
                );
            }, this);
        },

        generateFastQcForAnalysis: function(dataRegionName){
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
                        dataRegion.containerPath,
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

            SequenceAnalysis.Buttons.goToAction(LABKEY.ActionURL.buildURL('sequenceanalysis', 'deleteRecords', null), {
                schemaName: dataRegion.schemaName,
                'query.queryName': dataRegion.queryName,
                dataRegionSelectionKey: 'query',
                '.select': checked,
                returnURL: window.location.pathname + window.location.search
            });
        },

        goToAction: function(href, params, method){
            var form = document.createElement('form');
            form.setAttribute('method', (method || 'post'));
            form.setAttribute('action', href);
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'X-LABKEY-CSRF';
            input.value = LABKEY.CSRF;
            form.appendChild(input);
            form.style.display = 'hidden';

            if (params != null){
                for (var name in params) {
                    var val = params[name];
                    if (!Ext4.isArray(val)) {
                        val = [val];
                    }

                    Ext4.Array.forEach(val, function(v){
                        var input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = name;
                        input.value = v;
                        form.appendChild(input);
                    }, this);
                }
            }
            document.body.appendChild(form);
            form.submit();
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
        },

        viewQualityMetricsForOutputFiles: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                Ext4.Msg.alert('Error', 'Must select one or more rows');
                return;
            }

            checked = checked.sort();
            Ext4.Msg.wait('Loading...');
            LABKEY.Query.selectRows({
                schemaName: 'sequenceanalysis',
                queryName: 'outputfiles',
                filterArray: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'dataid',
                scope: this,
                success: function (data){
                    Ext4.Msg.hide();

                    if (!data || !data.rows){
                        Ext4.Msg.alert('Error', 'No matching rows found');
                        return;
                    }

                    var dataIds = [];
                    Ext4.each(data.rows, function (row){
                        dataIds.push(row.dataid);
                    }, this);

                    var params = {
                        schemaName: 'sequenceanalysis',
                        'query.queryName': 'quality_metrics'
                    };

                    params['query.dataid~in'] = dataIds.join(';');

                    window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery.view', dataRegion.containerPath, params);
                },
                failure: LDK.Utils.getErrorCallback()
            });

        }
    }
};