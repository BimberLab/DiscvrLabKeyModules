Ext4.define('GeneticsCore.panel.HaplotypePanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.geneticscore-haplotypepanel',
    analysisIds: null,
    showCheckBoxes: false,

    initComponent: function(){
        Ext4.apply(this, {
            width: '100%',
            border: false,
            defaults: {
                border: false,
                labelWidth: 220
            },
            items: [{
                html: 'This will use the lineage output from above and attempt to identify the haplotypes that best explain the lineages from this subject (i.e. which two account for the most total lineages).  If you edit the data above, you will need to manually refresh the page or hit the reload button below to update this table.',
                maxWidth: 1000,
                style: 'padding-bottom: 10px'
            },{
                xtype: 'ldk-numberfield',
                style: 'padding-top: 10px',
                fieldLabel: 'Min Pct From Lineage',
                itemId: 'minPct',
                value: 0.25
            },{
                fieldLabel: 'Min % Found For Haplotype',
                value: 25,
                xtype: 'ldk-integerfield',
                itemId: 'minPctForHaplotype'
            },{
                fieldLabel: 'Min % Explained By Pair',
                value: 60,
                xtype: 'ldk-integerfield',
                itemId: 'minPctExplained'
            },{
                fieldLabel: 'Threshold For Secondary Hits',
                helpPopup: 'Normally this will retain only the haplotypes that explain the highest percent of lineages.  If provided, other matches within this percent of the highest will also be included.',
                value: 10,
                xtype: 'ldk-integerfield',
                itemId: 'pctDiffThreshold'
            },{
                fieldLabel: 'Pct Differential Filter',
                helpPopup: 'If there is a differential greater than this value between the haplotypes that explain the highest # of alleles compared to the highest percent, the matches explaining the greatest number will be discarded.',
                value: 10,
                xtype: 'ldk-integerfield',
                itemId: 'pctDifferentialFilter'
            },{
                xtype: 'ldk-linkbutton',
                text: 'Click Here View Haplotype Definitions',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', queryName: 'haplotype_sequences'}),
                linkTarget: '_blank',
                linkCls: 'labkey-text-link',
                style: 'padding-top: 10px;'
            },{
                xtype: 'ldk-gridpanel',
                border: true,
                stripeRows: false,
                style: 'padding-top: 10px;padding-bottom: 10px;',
                columns: [{
                    header: 'Analysis Id',
                    dataIndex: 'analysisId',
                    width: 100
                },{
                    header: 'Subject Id',
                    dataIndex: 'subjectId',
                    width: 100
                },{
                    header: 'Locus',
                    dataIndex: 'locus',
                    width: 100
                },{
                    header: 'Haplotype 1',
                    dataIndex: 'haplotype1',
                    width: 200,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        if (record.get('haplotypeMatch1') && record.get('haplotypeMatch1').getHaplotype() && record.get('haplotypeMatch1').getHaplotype().getColor()) {
                            cellMetaData.style = 'background-color: ' + record.get('haplotypeMatch1').getHaplotype().getColor() + ';';
                        }

                        return record.get('haplotypeMatch1') ? record.get('haplotypeMatch1').getHaplotype().getName() : 'No Match';
                    }
                },{
                    header: 'Lineages Found',
                    width: 200,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        return GeneticsCore.panel.HaplotypePanel.lineageColRenderer(record, '1');
                    }
                },{
                    header: 'Omit?',
                    xtype: 'checkcolumn',
                    dataIndex: 'omit1'
                },{
                    header: 'Haplotype 2',
                    dataIndex: 'haplotype2',
                    width: 200,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        if (record.get('haplotypeMatch2') && record.get('haplotypeMatch2').getHaplotype() && record.get('haplotypeMatch2').getHaplotype().getColor()) {
                            cellMetaData.style = 'background-color: ' + record.get('haplotypeMatch2').getHaplotype().getColor() + ';';
                        }

                        return record.get('haplotypeMatch2') ? record.get('haplotypeMatch2').getHaplotype().getName() : 'No Match';
                    }
                },{
                    header: 'Lineages Found',
                    width: 200,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        return GeneticsCore.panel.HaplotypePanel.lineageColRenderer(record, '2');
                    }
                },{
                    header: 'Omit?',
                    xtype: 'checkcolumn',
                    dataIndex: 'omit2'
                },{
                    header: 'Summary',
                    width: 300,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        cellMetaData.style = 'white-space: normal;';

                        var ret = [];
                        Ext4.Array.forEach(record.get('allLineagesInAnalysis').sort(), function (lineage) {
                            var pctFromLocus = record.get('lineagePctMap')[lineage].percent_from_locus;
                            lineage = lineage.split(';');

                            var line = [];
                            Ext4.Array.forEach(lineage, function (l) {
                                var present = (record.get('haplotypeMatch1') && record.get('haplotypeMatch1').isPresentFromAnalysis(l)) || (record.get('haplotypeMatch2') && record.get('haplotypeMatch2').isPresentFromAnalysis(l));
                                line.push('<span style="' + (present ? '' : 'text-decoration: line-through;') + '">' + l + '</span>');
                            }, this);

                            ret.push(line.join(';') + ' (' + Ext4.util.Format.number(pctFromLocus, '0.00') + '%)</span>');
                        }, this);

                        ret.sort(function (a, b) {
                            return a.indexOf('line-through') > -1 ? 1 : -1;
                        });

                        var pct = record.get('allLineagesInAnalysis').length ? 100 * record.get('totalPresent') / record.get('allLineagesInAnalysis').length : 0;
                        var minPct = 60;
                        return 'Found: ' + record.get('totalPresent') + '/' + record.get('allLineagesInAnalysis').length + ' (' + Ext4.util.Format.number(pct, '0.00') + '%)<br>' +
                                '<span style="' + (record.get('totalPctPresent') < minPct ? 'background-color: yellow;' : '') + '">Pct Found: ' + Ext4.util.Format.number(record.get('totalPctPresent'), '0.00') + '%' + (record.get('totalPctPresent') && record.get('totalPctPresent') < minPct ? ' *LOW*' : '') + '</span>' +
                                '<br><br>' + ret.join('<br>');
                    }
                }, {
                    header: 'Comment',
                    width: 200,
                    renderer: function (value, cellMetaData, record, rowIndex, colIndex, store) {
                        var multipleMatches = false;
                        var comment = record.get('comment') || [];
                        Ext4.Array.forEach(comment, function (c) {
                            if (c.indexOf('Multiple Matches') > -1) {
                                multipleMatches = true;
                                return false;
                            }
                        }, this);

                        if (multipleMatches) {
                            cellMetaData.tdCls = 'labkey-grid-cell-invalid';
                        }

                        return comment.join('<br>');
                    }
                }],
                store: {
                    xtype: 'array',
                    fields: ['subjectId', 'locus', 'sortOrder', 'haplotypeMatch1', 'haplotypeMatch2', 'lineagePctMap', 'analysisId', 'allLineagesInAnalysis', 'allLineagesFound', 'totalPresent', 'totalPctPresent', {name: 'omit1', datatype: 'bool'}, {name: 'omit2', datatype: 'bool'}, 'comment']
                },
                tbar: [{
                    text: 'Reload Data',
                    scope: this,
                    handler: this.loadData
                }, {
                    text: 'Publish Selected',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');
                        var selected = grid.getSelectionModel().getSelection();
                        if (!selected.length) {
                            Ext4.Msg.alert('Error', 'No rows selected');
                            return;
                        }

                        var haplotypeNames = [];
                        var json = [];
                        Ext4.Array.forEach(selected, function (r) {
                            function getComments(r, h1, h2) {
                                if (!r.get('omit' + h2) && r.get('haplotypeMatch' + h2)) {
                                    var ret = [];
                                    ret.push('Paired Haplotype: ' + r.get('haplotypeMatch' + h2).getHaplotype().getName());
                                    ret.push('Total Lineages Found: ' + r.get('totalPresent'));
                                    ret.push('Total Pct Found: ' + Ext4.util.Format.number(r.get('totalPctPresent'), '0.00'));
                                    Ext4.Array.forEach(r.get('allLineagesInAnalysis'), function (l) {
                                        var present = (r.get('haplotypeMatch1') && r.get('haplotypeMatch1').isPresentFromAnalysis(l)) || (r.get('haplotypeMatch2') && r.get('haplotypeMatch2').isPresentFromAnalysis(l));
                                        var pctFromLocus = r.get('lineagePctMap')[l].percent_from_locus;
                                        if (!present && pctFromLocus > 5) {
                                            ret.push('Missing ' + l + ': ' + Ext4.util.Format.number(pctFromLocus, '0.00') + '%');
                                        }
                                    }, this);
                                    return ret.join('\n');
                                }
                                else if (r.get('omit' + h2)) {
                                    return 'Paired Haplotype: Cannot Call';
                                }
                            }

                            var omitMsg = 'Cannot Call Haplotype: ' + r.get('locus');
                            if (!r.get('omit1') && r.get('haplotypeMatch1')) {
                                haplotypeNames.push(r.get('haplotypeMatch1').getHaplotype().getName());
                                var pct = r.get('haplotypeMatch1').getPercentMatch();
                                json.push({
                                    haplotype: r.get('haplotypeMatch1').getHaplotype().getName(),
                                    analysisId: r.get('analysisId'),
                                    pct: pct,
                                    category: r.get('locus'),
                                    comments: getComments(r, 1, 2)
                                });
                            }
                            else if (r.get('omit1')) {
                                haplotypeNames.push(omitMsg);
                                json.push({
                                    haplotype: omitMsg,
                                    analysisId: r.get('analysisId'),
                                    pct: 0.0,
                                    category: r.get('locus'),
                                    comments: getComments(r, 1, 2)
                                });
                            }

                            if (!r.get('omit2') && r.get('haplotypeMatch2')) {
                                haplotypeNames.push(r.get('haplotypeMatch2').getHaplotype().getName());
                                var pct = r.get('haplotypeMatch2').getPercentMatch();
                                json.push({
                                    haplotype: r.get('haplotypeMatch2').getHaplotype().getName(),
                                    analysisId: r.get('analysisId'),
                                    pct: pct,
                                    category: r.get('locus'),
                                    comments: getComments(r, 2, 1)
                                });
                            }
                            else if (r.get('omit2')) {
                                haplotypeNames.push(omitMsg);
                                json.push({
                                    haplotype: omitMsg,
                                    analysisId: r.get('analysisId'),
                                    pct: 0.0,
                                    category: r.get('locus'),
                                    comments: getComments(r, 2, 1)
                                });
                            }

                            if (!r.get('haplotypeMatch1') && !r.get('haplotypeMatch2')) {
                                var name = 'Cannot Call Haplotype: ' + r.get('locus');
                                haplotypeNames.push(name);
                                json.push({
                                    haplotype: name,
                                    analysisId: r.get('analysisId'),
                                    pct: 0,
                                    category: r.get('locus')
                                });
                            }
                        }, this);

                        Ext4.Msg.confirm('Publish Selected?', 'You have chosen to published the following haplotypes:<br><br>' + (haplotypeNames.length > 8 ? 'Too many to display' : haplotypeNames.join('<br>')) + '<br><br>Continue?', function (val) {
                            if (val == 'yes') {
                                Ext4.create('GeneticsCore.window.PublishResultsWindow', {
                                    actionName: 'cacheHaplotypes',
                                    json: json
                                }).show();
                            }
                        }, this);
                    }
                }, {
                    text: 'Set Rows To Uncallable',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');
                        Ext4.create('Ext.window.Window', {
                            width: 400,
                            bodyStyle: 'padding: 5px;',
                            items: [{
                                html: 'This will set any rows where the total percent explained by the pair of haplotypes is below the threshold to uncallable.  It can also be used to set any individual haplotype within the pair below the supplied percent to uncallable.  Please note: this can result in subjects where multple rows from a locus are present that are labeled uncallable.  You may want to use the neighboring button to prune these.',
                                border: false
                            }, {
                                fieldLabel: 'Min % Explained By Pair',
                                value: 75,
                                xtype: 'ldk-integerfield',
                                itemId: 'minPctExplained'
                            }, {
                                fieldLabel: 'Min % Found For Haplotype',
                                value: 50,
                                xtype: 'ldk-integerfield',
                                itemId: 'minPctForHaplotype'
                            }],
                            buttons: [{
                                text: 'Submit',
                                scope: this,
                                handler: function (btn) {
                                    var minPctExplained = btn.up('window').down('#minPctExplained').getValue();
                                    var minPctForHaplotype = btn.up('window').down('#minPctForHaplotype').getValue();
                                    var totalAltered = 0;

                                    if (minPctForHaplotype) {
                                        minPctForHaplotype = minPctForHaplotype / 100;
                                    }

                                    grid.store.each(function (rec) {
                                        if (minPctExplained && rec.get('totalPctPresent') < minPctExplained) {
                                            rec.set('haplotypeMatch1', null);
                                            rec.set('haplotypeMatch2', null);
                                            totalAltered++;
                                            return;
                                        }

                                        if (minPctForHaplotype) {
                                            var altered = false;
                                            if (rec.get('haplotypeMatch1')) {
                                                var pct = rec.get('haplotypeMatch1').getPercentMatch();
                                                if (pct < minPctForHaplotype) {
                                                    rec.set('haplotypeMatch1', null);
                                                    altered = true;
                                                }
                                            }

                                            if (rec.get('haplotypeMatch2')) {
                                                var pct = rec.get('haplotypeMatch2').getPercentMatch();
                                                if (pct < minPctForHaplotype) {
                                                    rec.set('haplotypeMatch2', null);
                                                    altered = true;
                                                }
                                            }

                                            if (altered) {
                                                totalAltered++;
                                            }
                                        }
                                    }, this);

                                    btn.up('window').close();

                                    if (totalAltered) {
                                        Ext4.Msg.alert('Changes', 'Total rows changed: ' + totalAltered);
                                    }
                                }
                            }, {
                                text: 'Cancel',
                                handler: function (btn) {
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                }, {
                    text: 'Remove Duplicate Uncallable Rows',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');

                        var uncallableRecMap = {};
                        var callableRecMap = {};
                        grid.store.each(function (rec) {
                            var key = rec.get('analysisId') + '||' + rec.get('locus');
                            if (!rec.get('haplotype1') && !rec.get('haplotype2')) {
                                uncallableRecMap[key] = uncallableRecMap[key] || [];
                                uncallableRecMap[key].push(rec);
                            }
                            else {
                                callableRecMap[key] = true;
                            }
                        }, this);

                        var totalRemoved = 0;
                        Ext4.Array.forEach(Ext4.Object.getKeys(uncallableRecMap), function (key) {
                            if (callableRecMap[key]) {
                                var toRemove = uncallableRecMap[key];
                                grid.store.remove(toRemove);
                                totalRemoved += toRemove.length;
                            }
                            else if (uncallableRecMap[key].length > 1) {
                                var toRemove = uncallableRecMap[key];
                                toRemove.pop();

                                grid.store.remove(toRemove);
                                totalRemoved += toRemove.length;
                            }
                        }, this);

                        Ext4.Msg.alert('Changes', 'Total rows removed: ' + totalRemoved);
                    }
                }, {
                    text: 'Remove Selected',
                    scope: this,
                    handler: function (btn) {
                        var grid = btn.up('grid');
                        var selected = grid.getSelectionModel().getSelection();
                        if (!selected.length) {
                            Ext4.Msg.alert('Error', 'No rows selected');
                            return;
                        }

                        Ext4.Msg.confirm('Remove Rows?', 'You are about to remove ' + selected.length + ' rows.  Continue?', function (val) {
                            if (val == 'yes') {
                                grid.store.remove(selected);
                            }
                        }, this);
                    }
                }]
            },{
                itemId: 'messageArea',
                border: false
            }],
            buttonAlign: 'left',
        });

        this.callParent();

        this.loadData();
    },

    haplotypes: {},
    resultsByLoci: {},
    lineageToAlleleMap: {},
    lineagePctMap: {},
    analysesMap: {},

    loadData: function(){
        Ext4.Msg.wait('Loading...');

        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'haplotype_sequences',
            columns: 'haplotype,haplotype/type,haplotype/type/sort_order,haplotype/color,name,type,present,required',
            filterArray: [
                LABKEY.Filter.create('haplotype/datedisabled', null, LABKEY.Filter.Types.ISBLANK)
            ],
            apiVersion: 13.2,
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.haplotypes = {};
                Ext4.Array.forEach(results.rows, function(row){
                    this.haplotypes[row['haplotype/type']] = this.haplotypes[row['haplotype/type']] || {};
                    this.haplotypes[row['haplotype/type']][row.haplotype] = this.haplotypes[row['haplotype/type']][row.haplotype] || GeneticsCore.panel.HaplotypePanel.Haplotype(row.haplotype, row['haplotype/type'], row['haplotype/type/sort_order'], row['haplotype/color']);

                    this.haplotypes[row['haplotype/type']][row.haplotype].addSequence(row);
                }, this);
            }
        });

        var minPct = this.down('#minPct').getValue();
        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'alignment_summary_by_lineage',
            columns: 'analysis_id,analysis_id/readset,analysis_id/readset/subjectId,lineages,loci,total,total_reads,percent,total_reads_from_locus,percent_from_locus',
            apiVersion: 13.2,
            scope: this,
            filterArray: [
                LABKEY.Filter.create('analysis_id', this.analysisIds.join(';'), LABKEY.Filter.Types.IN),
                LABKEY.Filter.create('percent_from_locus', minPct || 0, LABKEY.Filter.Types.GTE)
            ],
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.resultsByLoci = {};
                this.lineagePctMap = {};
                this.analysesMap = {};
                Ext4.Array.forEach(results.rows, function(row){
                    if (Ext4.isArray(row.lineages)){
                        row.lineages = row.lineages.join(';');
                    }
                    row.lineages = row.lineages.replace(/\n/g, ';');

                    if (!this.resultsByLoci[row.analysis_id]){
                        this.resultsByLoci[row.analysis_id] = {};
                        this.lineagePctMap[row.analysis_id] = {}
                    }
                    this.resultsByLoci[row.analysis_id][row.loci] = this.resultsByLoci[row.analysis_id][row.loci] || {};
                    this.resultsByLoci[row.analysis_id][row.loci][row.lineages] = row;

                    this.lineagePctMap[row.analysis_id][row.lineages] = row;

                    this.analysesMap[row.analysis_id] = {
                        subjectId: row['analysis_id/readset/subjectId']
                    };
                }, this);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'alignment_summary_grouped',
            columns: 'analysis_id,lineages,loci,alleles,total_reads,percent,total_reads_from_locus,percent_from_locus',
            apiVersion: 13.2,
            scope: this,
            filterArray: [
                LABKEY.Filter.create('analysis_id', this.analysisIds.join(';'), LABKEY.Filter.Types.IN),
                LABKEY.Filter.create('percent_from_locus', minPct || 0, LABKEY.Filter.Types.GTE)
            ],
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.lineageToAlleleMap = {};
                Ext4.Array.forEach(results.rows, function(row){
                    if (Ext4.isArray(row.lineages)){
                        row.lineages = row.lineages.join(';');
                    }
                    row.lineages = row.lineages ? row.lineages.replace(/\n/g, ';') : '';

                    if (Ext4.isArray(row.alleles)){
                        row.alleles = row.alleles.join(';');
                    }
                    row.alleles = row.alleles.replace(/\n/g, ';');
                    var alleles = row.alleles ? row.alleles.split(';') : [];

                    if (!this.lineageToAlleleMap[row.analysis_id]){
                        this.lineageToAlleleMap[row.analysis_id] = {};
                    }
                    this.lineageToAlleleMap[row.analysis_id][row.lineages] = this.lineageToAlleleMap[row.analysis_id][row.lineages] || [];
                    this.lineageToAlleleMap[row.analysis_id][row.lineages] = this.lineageToAlleleMap[row.analysis_id][row.lineages].concat(alleles);
                }, this);
            }
        });

        multi.send(this.onDataLoad, this);
    },

    onDataLoad: function(){
        Ext4.Msg.hide();
        var store = this.down('grid').store;
        var minPctExplained = this.down('#minPctExplained').getValue();
        var minPctForHaplotype = this.down('#minPctForHaplotype').getValue();
        var pctDiffThreshold = this.down('#pctDiffThreshold').getValue() || 0;
        var pctDifferentialFilter = this.down('#pctDifferentialFilter').getValue();

        store.removeAll();
        var messages = [];
        Ext4.each(Ext4.Object.getKeys(this.resultsByLoci), function(analysisId){
            Ext4.each(Ext4.Object.getKeys(this.resultsByLoci[analysisId]), function(locus){
                var lineages = Ext4.Object.getKeys(this.resultsByLoci[analysisId][locus]);
                if (!this.haplotypes[locus]){
                    //console.log('no haplotypes for: ' + locus);
                    return;
                }

                var haplotypeMatches = {};
                Ext4.Array.forEach(Ext4.Object.getKeys(this.haplotypes[locus]), function(haplotypeName){
                    var h = this.haplotypes[locus][haplotypeName];
                    var hm = h.getMatch(lineages, this.lineageToAlleleMap[analysisId]);
                    if (hm){
                        var pctMatch = hm.getPercentMatch();
                        if (minPctForHaplotype && pctMatch < minPctForHaplotype){
                            messages.push('Haplotype below \'Min Pct For Haplotype\': ' + haplotypeName + ' (percent matching: ' + pctMatch + ').  Lower this threshold and reload to include it.');
                        }
                        else {
                            haplotypeMatches[haplotypeName] = hm;
                        }
                    }
                }, this);

                //now find best two matches:
                var rankedMatches = {};
                var rankedPctMatches = {};
                var pctExplainedByMatch = {};
                Ext4.Array.forEach(Ext4.Object.getKeys(haplotypeMatches), function(h1){
                    Ext4.Array.forEach(Ext4.Object.getKeys(haplotypeMatches), function(h2){
                        var matchesUnion = [];
                        matchesUnion = matchesUnion.concat(haplotypeMatches[h1].getAnalysisLineageNamesFound());
                        matchesUnion = matchesUnion.concat(haplotypeMatches[h2].getAnalysisLineageNamesFound());
                        matchesUnion = Ext4.unique(matchesUnion);
                        var names = [h1, h2].sort().join('<>');

                        //calculate total pct present
                        var totalPctPresent = 0;
                        Ext4.Array.forEach(matchesUnion.sort(), function(l){
                            if (this.lineagePctMap[analysisId][l]){
                                totalPctPresent += this.lineagePctMap[analysisId][l].percent_from_locus;
                            }
                            else {
                                Ext4.Array.forEach(Ext4.Object.getKeys(this.lineagePctMap[analysisId]), function(key){
                                    var arr = key.split(';');
                                    if (arr.indexOf(l) > -1){
                                        totalPctPresent += this.lineagePctMap[analysisId][key].percent_from_locus;
                                    }
                                }, this);
                            }
                        }, this);
                        totalPctPresent = Ext4.util.Format.number(totalPctPresent, '0.00');

                        if (minPctExplained && totalPctPresent < minPctExplained) {
                            messages.push('Haplotype below \'Min Pct Explained For Pair\': ' + names.replace('<>', '/') + ' (' + totalPctPresent + '% explained).  Lower this threshold and reload to include them.');
                        }
                        else {
                            rankedPctMatches[totalPctPresent] = rankedPctMatches[totalPctPresent] || [];
                            if (rankedPctMatches[totalPctPresent].indexOf(names) == -1) {
                                rankedPctMatches[totalPctPresent].push(names);
                            }

                            pctExplainedByMatch[names] = totalPctPresent;

                            rankedMatches[matchesUnion.length] = rankedMatches[matchesUnion.length] || [];
                            if (rankedMatches[matchesUnion.length].indexOf(names) == -1) {
                                rankedMatches[matchesUnion.length].push(names);
                            }
                        }
                    }, this);
                }, this);

                var rank = Ext4.Array.map(Ext4.Object.getKeys(rankedMatches), function(x){return parseInt(x)}).sort(function(a,b){return a - b});
                var totalMatches = rank.pop();
                var highest = rankedMatches[totalMatches];

                var pctRank = Ext4.Array.map(Ext4.Object.getKeys(rankedPctMatches), function(x){return Ext4.util.Format.number(parseFloat(x), '0.00')}).sort(function(a,b){return a - b});
                var totalPct = pctRank.pop();
                var highestByPct = rankedPctMatches[totalPct];
                if (pctRank.length){
                    Ext4.Array.forEach(pctRank, function(p){
                        if ((totalPct - p) <= pctDiffThreshold){
                            console.log('adding based on pctDiffThreshold: ' + rankedPctMatches[p].join(','));
                            highestByPct = highestByPct.concat(rankedPctMatches[p]);
                        }
                    }, this);
                }

                if (!highest && !highestByPct){
                    var record = store.createModel({
                        analysisId: analysisId,
                        subjectId: this.analysesMap[analysisId].subjectId,
                        locus: locus,
                        sortOrder: 0,
                        haplotypeMatch1: null,
                        haplotypeMatch2: null,
                        allLineagesInAnalysis: [],
                        lineagePctMap: this.lineagePctMap[analysisId],
                        comment: ['No Match'],
                        totalPctPresent: 0,
                        totalPresent: 0
                    });

                    store.add(record);
                }
                else {
                    var matches = [];

                    //first determine if the two interest.  if so, use this.  otherwise take the union
                    if (highest && highestByPct){
                        matches = Ext4.Array.intersect(highest, highestByPct);
                    }

                    if (!matches.length){
                        if (highest)
                        {
                            matches = matches.concat(highest);
                        }

                        if (highestByPct) {
                            matches = matches.concat(highestByPct);
                        }
                        matches = Ext4.unique(matches);
                    }

                    //attempt to resolve:
                    if (highest && highestByPct && matches.length > 1){
                        var initial = matches.length;
                        if (pctDifferentialFilter) {
                            Ext4.Array.forEach(highest, function (n) {
                                //if there's a 20% differential, ignore the one explaining more hits
                                if (totalPct - pctExplainedByMatch[n] > pctDifferentialFilter) {
                                    matches.remove(n);
                                }
                            }, this);
                        }

                        if (matches.length != initial){
                            console.log('haplotypes remaining after pct filter: ' + matches.length + ' (' + initial + ')');
                        }
                    }

                    Ext4.Array.forEach(matches, function (names){
                        var comments = [];
                        if (matches.length > 1){
                            comments.push('Multiple Matches: ' + matches.length);
                        }

                        if (highest && highest.indexOf(names) > -1){
                            comments.push('Highest # Matches');
                        }

                        if (highestByPct && highestByPct.indexOf(names) > -1){
                            comments.push('Highest % Explained');
                        }

                        names = names.split('<>');

                        var minSort = 999;
                        if (haplotypeMatches[names[0]]){
                            if (haplotypeMatches[names[0]].getHaplotype().getSortOrder() && haplotypeMatches[names[0]].getHaplotype().getSortOrder() < minSort){
                                minSort = haplotypeMatches[names[0]].getHaplotype().getSortOrder();
                            }
                        }

                        var record = store.createModel({
                            subjectId: this.analysesMap[analysisId].subjectId,
                            analysisId: analysisId,
                            sortOrder: minSort,
                            locus: locus,
                            haplotypeMatch1: haplotypeMatches[names[0]] || [],
                            haplotypeMatch2: haplotypeMatches[names[1]] || [],
                            allLineagesInAnalysis: lineages,
                            lineagePctMap: this.lineagePctMap[analysisId],
                            comment: comments
                        });

                        var totalPresent = 0;
                        var totalPctPresent = 0;
                        var allLineagesFound = [];
                        if (record.get('haplotypeMatch1')){
                            allLineagesFound = allLineagesFound.concat(record.get('haplotypeMatch1').getAnalysisLineageNamesFound());
                        }
                        if (record.get('haplotypeMatch2')){
                            allLineagesFound = allLineagesFound.concat(record.get('haplotypeMatch2').getAnalysisLineageNamesFound());
                        }
                        allLineagesFound = Ext4.unique(allLineagesFound);
                        record.set('allLineagesFound', allLineagesFound);

                        Ext4.Array.forEach(record.get('allLineagesInAnalysis').sort(), function(l){
                            var pctFromLocus = record.get('lineagePctMap')[l].percent_from_locus;
                            var present = allLineagesFound.indexOf(l) > -1;

                            if (!present && l.split(';').length > 1){
                                Ext4.Array.forEach(l.split(';'), function(toTest){
                                    if (allLineagesFound.indexOf(toTest) > -1){
                                        present = true;
                                    }
                                }, this);
                            }

                            if (present){
                                totalPresent++;
                                totalPctPresent += pctFromLocus
                            }
                        }, this);

                        record.set('totalPctPresent', totalPctPresent);
                        record.set('totalPresent', totalPresent);

                        store.add(record);
                    }, this);
                }
            }, this);
        }, this);

        store.sort([{property: 'subjectId'}, {property: 'analysisId'}, {property: 'sortOrder'}, {property: 'locus'}]);

        this.down('#messageArea').removeAll();
        if (messages.length) {
            this.down('#messageArea').add({
                html: 'The following haplotypes were omitted:<br><br>' + messages.join('<br>'),
                border: false
            });
        }
    },

    statics: {
        HaplotypeMatch: function(haplotype, matchMap){
                        
            return {
                getHaplotype: function(){
                    return haplotype;
                },
                
                getPercentMatch: function(){
                    return 100 * (this.getAnalysisLineageNamesFound().length / haplotype.getSequences().length);
                },
                getAnalysisLineageNamesFound: function(){
                    return Ext4.Object.getValues(matchMap);
                },
                isPresentForHaplotype: function(seqName){
                    return matchMap[seqName] != null;
                },
                isPresentFromAnalysis: function(lineage){
                    return this.getAnalysisLineageNamesFound().indexOf(lineage) > -1;
                }
            }
        },
        
        Haplotype: function (name, locus, sortOrder, color){
            var sequences = [];

            return {
                addSequence: function (rowMap){
                    if (rowMap.type){
                        rowMap.type = rowMap.type.toLowerCase();
                    }
                    
                    sequences.push(rowMap);
                },
                getSequences: function(){
                    return sequences;
                },
                getSortOrder: function(){
                    return sortOrder;
                },
                getColor: function(){
                    return color;
                },
                getSortedSequences: function(){
                    sequences = LDK.Utils.sortByProperty(sequences, 'type');
                    sequences = LDK.Utils.sortByProperty(sequences, 'name');

                    return sequences;
                },
                getAlleleNames: function(){
                    return this.getNamesOfType('allele');
                },
                getLineageNames: function(){
                    return this.getNamesOfType('lineage');
                },
                getNamesOfType: function(type){
                    var ret = [];
                    Ext4.Array.forEach(sequences, function(s){
                        if (s.type == type){
                            ret.push(s.name);
                        }
                        else {
                            console.log('not ' + type + ': ' + s.type);
                            console.log(s);
                        }
                    }, this);

                    return ret;
                },
                getName: function (){
                    return name;
                },
                getLocus: function (){
                    return locus;
                },
                isRequired: function(toTest){
                    //NOTE: this assumes unique names between alleles and lineages
                    var ret = false;
                    Ext4.Array.forEach(sequences, function(s){
                        if (s.name == toTest && s.required){
                            ret = true;
                            return false;
                        }
                    }, this);

                    return ret;
                },
                getMatch: function(lineagesFromAnalysis, lineageToAlleleMap){
                    var hasRequired = true;
                    var matchMap = {};

                    Ext4.Array.forEach(sequences, function(c){
                        var matchName = null;
                        
                        if (c.type == 'allele') {
                            for (var lineage in lineageToAlleleMap) {
                                if (lineageToAlleleMap[lineage].indexOf(c.name) > -1) {
                                    matchName = lineage;
                                }
                            }
                        }
                        else if (c.type == 'lineage'){
                            Ext4.Array.forEach(lineagesFromAnalysis, function(lineage){
                                var l = lineage.split(';');
                                if (l.indexOf(c.name) > -1){
                                    matchName = lineage;
                                    return false;
                                }
                            }, this);
                        }
                        else {
                            console.error('unknown type: ' + c.type);
                        }
                        
                        if (!matchName && c.required){
                            hasRequired = false;
                            return false;
                        }

                        if (matchName) {
                            matchMap[c.name] = matchName;
                        }
                    }, this);

                    if (!hasRequired || Ext4.isEmpty(matchMap)){
                        return null;
                    }
                    else {
                        return GeneticsCore.panel.HaplotypePanel.HaplotypeMatch(this, matchMap);
                    }
                }
            }
        },

        lineageColRenderer: function(record, num){
            var ret = [];
            if (record.get('haplotypeMatch' + num)){
                var hm = record.get('haplotypeMatch' + num);

                var sequences = hm.getHaplotype().getSortedSequences();
                var notFound = [];
                Ext4.Array.forEach(sequences, function (l) {
                    var required = l.required;
                    var present = hm.isPresentForHaplotype(l.name);

                    if (present) {
                        ret.push('<span style="' + (required ? 'font-weight: bold;' : '') + '">' + l.name + '</span>');
                    }
                    else {
                        notFound.push('<span style="' + ('text-decoration: line-through;') + (required ? 'font-weight: bold;' : '') + '">' + l.name + '</span>');
                    }
                }, this);

                ret = ret.concat(notFound);

                var pct = hm.getPercentMatch();
                var minPct = 50;

                return '<span style="' + (pct < minPct ? 'background-color: red' : '') + '">Found: ' + hm.getAnalysisLineageNamesFound().length + '/' + hm.getHaplotype().getSequences().length + ' (' + Ext4.util.Format.number(pct, '0.00') + '%)' + (pct < minPct ? ' *LOW*' : '') + '</span><br><br>' + ret.join('<br>');
            }
            else {
                return '';
            }
        }
    }
});