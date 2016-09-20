Ext4.define('GeneticsCore.window.EditAlignmentsWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName, btnEl) {
            var dr = LABKEY.DataRegions[dataRegionName];
            LDK.Assert.assertNotEmpty('Unable to find dataregion in EditAlignmentsWindow', dr);

            var checked = dr.getChecked();
            if (!checked.length) {
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('GeneticsCore.window.EditAlignmentsWindow', {
                dataRegionName: dataRegionName,
                checked: checked
            }).show(btnEl);
        },

        editRow: function(rowId, dataRegionName){
            var dr = LABKEY.DataRegions[dataRegionName];
            LDK.Assert.assertNotEmpty('Unable to find dataregion in EditAlignmentsWindow', dr);

            if (Ext4.isArray(rowId)){
                rowId = rowId.join(',');
            }

            Ext4.create('GeneticsCore.window.EditAlignmentsWindow', {
                dataRegionName: dataRegionName,
                checked: [rowId]
            }).show();
        },

        editLineage: function (analysisId, lineage, dataRegionName, locusFilterOperator, minPctFromLocus) {
            var dr = LABKEY.DataRegions[dataRegionName];
            LDK.Assert.assertNotEmpty('Unable to find dataregion in EditAlignmentsWindow', dr);

            Ext4.Msg.wait('Loading...');

            var filter = [
                LABKEY.Filter.create('analysis_id', analysisId, LABKEY.Filter.Types.EQUAL),
                LABKEY.Filter.create('lineages', lineage, LABKEY.Filter.Types.EQUAL)
            ]

            if (locusFilterOperator){
                filter.push(LABKEY.Filter.create('percent_from_locus', minPctFromLocus, LABKEY.Filter.getFilterTypeForURLSuffix(locusFilterOperator)));
            }

            LABKEY.Query.selectRows({
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'alignment_summary_grouped',
                filterArray: filter,
                columns: 'rowids',
                failure: LDK.Utils.getErrorCallback(),
                scope: this,
                success: function(results){
                    Ext4.Msg.hide();

                    var rowIds = [];
                    Ext4.Array.forEach(results.rows, function(row){
                        if (Ext4.isArray(row.rowids)){
                            row.rowids = row.rowids.join(',');
                        }

                        rowIds.push(row.rowids);
                    }, this);

                    Ext4.create('GeneticsCore.window.EditAlignmentsWindow', {
                        dataRegionName: dataRegionName,
                        checked: rowIds
                    }).show();
                }
            });
        }
    },

    initComponent: function () {
        Ext4.apply(this, {
            modal: true,
            title: 'Select Alleles',
            width: 800,
            maxHeight: 600,
            autoScroll: true,
            doGroup: false,
            defaults: {
                border: false,
                bodyStyle: 'padding: 5px;'
            },
            items: [{
                xtype: 'form',
                width: 750,
                border: false,
                items: [{
                    html: 'Loading...',
                    border: false
                }],
                defaults: {
                    border: false
                }
            }],
            buttons: [{
                text: 'Toggle Grouping',
                scope: this,
                handler: function (btn) {
                    var window = btn.up('window');
                    this.doGroup = !this.doGroup;
                    window.onSuccess();
                }
            },{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    btn.setDisabled(true);
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
                            LABKEY.DataRegions[this.dataRegionName].clearSelected();
                            LABKEY.DataRegions[this.dataRegionName].refresh();

                            //TODO: consider other DRs
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
        });

        this.callParent(arguments);
        this.loadData();
    },
    
    loadData: function(){
        Ext4.Msg.wait('Loading...');

        this.referenceMap = {};
        this.readCountMap = {};
        this.junctionRecordMap = {};
        this.alignmentIdMap = {};
        this.inactiveAlignmentIdMap = {};

        var multi = new LABKEY.MultiRequest();
        Ext4.each(this.checked, function(id){
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
                            if (!this.junctionRecordMap[row.alignment_id])
                                this.junctionRecordMap[row.alignment_id] = [];

                            this.junctionRecordMap[row.alignment_id].push(row);

                            var ref_name = row['ref_nt_id/name'];
                            if (!this.referenceMap[ref_name]){
                                this.referenceMap[ref_name] = {
                                    rowid: ref_name,
                                    name: row['ref_nt_id/name']
                                }
                            }

                            var alignment_id = row['alignment_id'];
                            if (!this.readCountMap[alignment_id])
                                this.readCountMap[alignment_id] = row['alignment_id/total'];

                            if (!this.alignmentIdMap[alignment_id])
                                this.alignmentIdMap[alignment_id] = [];
                            if (!this.inactiveAlignmentIdMap[alignment_id])
                                this.inactiveAlignmentIdMap[alignment_id] = [];

                            if (row.status){
                                this.alignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                            }
                            else {
                                this.inactiveAlignmentIdMap[alignment_id].push(row['ref_nt_id/name']);
                            }
                        }, this);
                    }
                },
                failure: LDK.Utils.getErrorCallback()
            });
        }, this);

        multi.send(this.onSuccess, this);        
    },

    onSuccess: function() {
        this.alleleCombinations = {};

        for (var alignmentId in this.alignmentIdMap){
            var alleleSet = this.alignmentIdMap[alignmentId];
            alleleSet = Ext4.unique(alleleSet);
            alleleSet = alleleSet.sort();
            alleleSet = alleleSet.join(';');

            if (this.doGroup){
                alleleSet = '';
            }

            if (!this.alleleCombinations[alleleSet]){
                this.alleleCombinations[alleleSet] = {
                    active: this.alignmentIdMap[alignmentId],
                    inactive: [],
                    alignmentIds: [],
                    alleles: {},
                    inactiveAlleles: {},
                    total: 0
                }
            }

            if (this.junctionRecordMap[alignmentId]){
                Ext4.each(this.junctionRecordMap[alignmentId], function(row){
                    var allele = row['ref_nt_id/name'];

                    if (this.alleleCombinations[alleleSet].alignmentIds.indexOf(alignmentId) == -1){
                        this.alleleCombinations[alleleSet].total += row['alignment_id/total'];
                    }
                    this.alleleCombinations[alleleSet].alignmentIds.push(alignmentId);

                    if (row.status){
                        if (!this.alleleCombinations[alleleSet].alleles[allele])
                            this.alleleCombinations[alleleSet].alleles[allele] = {
                                total: 0,
                                alignmentIds: [],
                                rows: []
                            };

                        this.alleleCombinations[alleleSet].alleles[allele].rows.push(row);
                        if (this.alleleCombinations[alleleSet].alleles[allele].alignmentIds.indexOf(row['alignment_id']) == -1){
                            this.alleleCombinations[alleleSet].alleles[allele].alignmentIds.push(row['alignment_id']);
                            this.alleleCombinations[alleleSet].alleles[allele].total += row['alignment_id/total'];
                        }
                    }
                    else {
                        if (!this.alleleCombinations[alleleSet].inactiveAlleles[allele])
                            this.alleleCombinations[alleleSet].inactiveAlleles[allele] = {
                                total: 0,
                                alignmentIds: [],
                                rows: []
                            };

                        this.alleleCombinations[alleleSet].inactiveAlleles[allele].rows.push(row);
                        if (this.alleleCombinations[alleleSet].inactiveAlleles[allele].alignmentIds.indexOf(row['alignment_id']) == -1){
                            this.alleleCombinations[alleleSet].inactiveAlleles[allele].alignmentIds.push(row['alignment_id']);
                            this.alleleCombinations[alleleSet].inactiveAlleles[allele].total += row['alignment_id/total'];
                        }
                    }
                }, this);
            }
        }

        this.loadItems();
    },

    loadItems: function(){
        var items = [];
        var idx = 1;
        for (var alleles in this.alleleCombinations){
            var record = this.alleleCombinations[alleles];
            var checkboxes = [];

            var activeCount = 0;
            var activeIds = Ext4.unique(record.alignmentIds);
            Ext4.each(activeIds, function(id){
                activeCount += this.readCountMap[id];
            }, this);

            var keys = Ext4.Object.getKeys(record.alleles);
            keys = keys.sort();

            var hasAllelesMatchingTotal = false;
            Ext4.Array.forEach(keys, function(refName){
                if (record.alleles[refName].total == activeCount){
                    hasAllelesMatchingTotal = true;
                    return false;
                }
            }, this);

            Ext4.Array.forEach(keys, function(refName){
                var checkVal = (hasAllelesMatchingTotal ? record.alleles[refName].total == activeCount : true);
                checkboxes.push({
                    xtype: 'checkbox',
                    inputValue: refName,
                    boxLabel: refName +  (record.alleles[refName].total == activeCount ? '<span style="color: red;font-weight: bold">' : '') + ' (' + record.alleles[refName].total + ')' + (record.alleles[refName].total == activeCount ? '</span>' : ''),
                    checked: true,
                    rows: record.alleles[refName].rows,
                    total: record.alleles[refName].total,
                    listeners: {
                        scope: this,
                        beforerender: function(field){
                            if (!checkVal){
                                field.setValue(checkVal);
                            }
                        }
                    }
                });
            }, this);

            if (this.doGroup){
                checkboxes.sort(function(a,b){
                    return a.total < b.total ? 1 : a.total == b.total ? 0 : -1;
                });
            }

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
                    html: 'Group ' + idx + ' (' + record.total + ')',
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
        this.down('form').removeAll();
        this.down('form').add(items);
    }
});