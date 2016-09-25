Ext4.define('GeneticsCore.panel.HaplotypePanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.geneticscore-haplotypepanel',
    analysisIds: null,
    showCheckBoxes: false,
    pctDifferentialFilter: 10,

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
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        return record.get('haplotype1') ? record.get('haplotype1').getName() : 'No Match';
                    }
                },{
                    header: 'Lineages Found',
                    width: 200,
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        var ret = [];
                        if (record.get('haplotype1')){
                            var lineageNames = record.get('haplotype1').getLineageNames();
                            Ext4.Array.forEach(lineageNames.sort(), function (l) {
                                var required = record.get('haplotype1').isRequired(l);
                                var present = record.get('matches1').indexOf(l) > -1;

                                ret.push('<span style="' + (present ? '' : 'text-decoration: line-through;') + (required ? 'font-weight: bold;' : '') + '">' + l + '</span>')
                            }, this);

                            ret.sort(function (a, b) {
                                return a.indexOf('line-through') > -1 ? 1 : -1;
                            });

                            var pct = 100 * record.get('matches1').length / lineageNames.length;
                            var minPct = 50;
                            return '<span style="' + (pct < minPct ? 'background-color: red' : '') + '">Found: ' + record.get('matches1').length + '/' + lineageNames.length + ' (' + Ext4.util.Format.number(pct, '0.00') + '%)' + (pct < minPct ? ' *LOW*' : '') + '</span><br><br>' + ret.join('<br>');
                        }
                        else {
                            return '';
                        }
                    }
                },{
                    header: 'Omit?',
                    xtype : 'checkcolumn',
                    dataIndex: 'omit1'
                },{
                    header: 'Haplotype 2',
                    dataIndex: 'haplotype2',
                    width: 200,
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        return record.get('haplotype2') ? record.get('haplotype2').getName() : 'No Match';
                    }
                },{
                    header: 'Lineages Found',
                    width: 200,
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        var ret = [];
                        if (record.get('haplotype2')){
                            var lineageNames = record.get('haplotype2').getLineageNames();
                            Ext4.Array.forEach(lineageNames.sort(), function (l) {
                                var required = record.get('haplotype2').isRequired(l);
                                var present = record.get('matches2').indexOf(l) > -1;

                                ret.push('<span style="' + (present ? '' : 'text-decoration: line-through;') + (required ? 'font-weight: bold;' : '') + '">' + l + '</span>')
                            }, this);

                            ret.sort(function (a, b)
                            {
                                return a.indexOf('line-through') > -1 ? 1 : -1;
                            });

                            var pct = 100 * record.get('matches2').length / lineageNames.length;
                            var minPct = 50;
                            return '<span style="' + (pct < minPct ? 'background-color: red' : '') + '">Found: ' + record.get('matches2').length + '/' + lineageNames.length + ' (' + Ext4.util.Format.number(pct, '0.00') + '%)' + (pct < minPct ? ' *LOW*' : '') + '</span><br><br>' + ret.join('<br>');
                        }
                        else {
                            return '';
                        }
                    }
                },{
                    header: 'Omit?',
                    xtype : 'checkcolumn',
                    dataIndex: 'omit2'
                },{
                    header: 'Summary',
                    width: 300,
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        var ret = [];
                        Ext4.Array.forEach(record.get('lineages').sort(), function(lineage){
                            var pctFromLocus = record.get('lineagePctMap')[lineage].percent_from_locus;
                            lineage = lineage.split(';');

                            var line = [];
                            Ext4.Array.forEach(lineage, function(l){
                                var present = record.get('matches1').indexOf(l) > -1 || record.get('matches2').indexOf(l) > -1;
                                line.push('<span style="' + (present ? '' : 'text-decoration: line-through;') + '">' + l + '</span>');
                            }, this);

                            ret.push(line.join(';') + ' (' + Ext4.util.Format.number(pctFromLocus, '0.00') + '%)</span>');
                        }, this);

                        ret.sort(function(a,b){
                            return a.indexOf('line-through') > -1 ? 1 : -1;
                        });

                        var pct = record.get('lineages').length ? 100 * record.get('totalPresent') / record.get('lineages').length : 0;
                        var minPct = 60;
                        return 'Found: ' + record.get('totalPresent') + '/' + record.get('lineages').length + ' (' + Ext4.util.Format.number(pct, '0.00') + '%)<br>' +
                                '<span style="' + (record.get('totalPctPresent') < minPct ? 'background-color: yellow' : '') +'">Pct Found: ' + Ext4.util.Format.number(record.get('totalPctPresent'), '0.00') + '%' + (record.get('totalPctPresent') && record.get('totalPctPresent') < minPct ? ' *LOW*' : '') +'</span>' +
                                '<br><br>' + ret.join('<br>');
                    }
                },{
                    header: 'Comment',
                    width: 200,
                    renderer: function(value, cellMetaData, record, rowIndex, colIndex, store) {
                        var multipleMatches = false;
                        var comment = record.get('comment') || [];
                        Ext4.Array.forEach(comment, function(c){
                            if (c.indexOf('Multiple Matches') > -1){
                                multipleMatches = true;
                                return false;
                            }
                        }, this);

                        if (multipleMatches){
                            cellMetaData.tdCls = 'labkey-grid-cell-invalid';
                        }

                        return comment.join('<br>');
                    }
                }],
                store: {
                    xtype: 'array',
                    fields: ['subjectId', 'locus', 'haplotype1', 'haplotype2', 'matches1', 'matches2', 'totalMatches', 'lineages', 'lineagePctMap', 'analysisId', 'totalPresent', 'totalPctPresent', {name: 'omit1', datatype: 'bool'}, {name: 'omit2', datatype: 'bool'}, 'comment']
                },
                tbar: [{
                    text: 'Reload Data',
                    scope: this,
                    handler: this.loadData
                },{
                    text: 'Publish Selected',
                    scope: this,
                    handler: function(btn){
                        var grid = btn.up('grid');
                        var selected = grid.getSelectionModel().getSelection();
                        if (!selected.length){
                            Ext4.Msg.alert('Error', 'No rows selected');
                            return;
                        }

                        var haplotypeNames = [];
                        var json = [];
                        Ext4.Array.forEach(selected, function(r){
                            function getComments(r, h1, h2){
                                if (!r.get('omit' + h2) && r.get('haplotype' + h2)){
                                    var ret = [];
                                    ret.push('Paired Haplotype: ' + r.get('haplotype' + h2).getName());
                                    ret.push('Total Lineages Found: ' + Ext4.util.Format.number(r.get('totalPresent'), '0.00'));
                                    ret.push('Total Pct Found: ' + Ext4.util.Format.number(r.get('totalPctPresent'), '0.00'));
                                    Ext4.Array.forEach(r.get('lineages'), function(l){
                                        var present = r.get('matches1').indexOf(l) > -1 || r.get('matches2').indexOf(l) > -1;
                                        var pctFromLocus = r.get('lineagePctMap')[l].percent_from_locus;
                                        if (!present && pctFromLocus > 5){
                                            ret.push('Missing ' + l + ': ' + Ext4.util.Format.number(pctFromLocus, '0.00') + '%');
                                        }
                                    }, this);
                                    return ret.join('\n');
                                }
                                else if (r.get('omit' + h2)){
                                    return 'Paired Haplotype: Cannot Call';
                                }
                            }

                            var omitMsg = 'Cannot Call Haplotype: ' + r.get('locus');
                            if (!r.get('omit1') && r.get('haplotype1')){
                                haplotypeNames.push(r.get('haplotype1').getName());
                                var pct = r.get('matches1').length / r.get('haplotype1').getLineageNames().length;
                                json.push({haplotype: r.get('haplotype1').getName(), analysisId: r.get('analysisId'), pct: pct, category: r.get('locus'), comments: getComments(r, 1, 2)});
                            }
                            else if (r.get('omit1')){
                                json.push({haplotype: omitMsg, analysisId: r.get('analysisId'), pct: 0.0, category: r.get('locus'), comments: getComments(r, 1, 2)});
                            }

                            if (!r.get('omit2') && r.get('haplotype2')){
                                haplotypeNames.push(r.get('haplotype2').getName());
                                var pct = r.get('matches2').length / r.get('haplotype2').getLineageNames().length;
                                json.push({haplotype: r.get('haplotype2').getName(), analysisId: r.get('analysisId'), pct: pct, category: r.get('locus'), comments: getComments(r, 2, 1)});
                            }
                            else if (r.get('omit2')){
                                json.push({haplotype: omitMsg, analysisId: r.get('analysisId'), pct: 0.0, category: r.get('locus'), comments: getComments(r, 2, 1)});
                            }

                            if (!r.get('haplotype1') && !r.get('haplotype2')){
                                var name = 'Cannot Call Haplotype: ' + r.get('locus');
                                haplotypeNames.push(name);
                                json.push({haplotype: name, analysisId: r.get('analysisId'), pct: 0, category: r.get('locus')});
                            }
                        }, this);

                        Ext4.Msg.confirm('Publish Selected?', 'You have chosen to published the following haplotypes:<br><br>' + (haplotypeNames.length > 8 ? 'Too many to display' : haplotypeNames.join('<br>')) + '<br><br>Continue?', function(val){
                            if (val == 'yes'){
                                Ext4.create('GeneticsCore.window.PublishResultsWindow', {
                                    actionName: 'cacheHaplotypes',
                                    json: json
                                }).show();
                            }
                        }, this);
                    }
                },{
                    text: 'Set Rows To Uncallable',
                    scope: this,
                    handler: function(btn) {
                        var grid = btn.up('grid');
                        Ext4.create('Ext.window.Window', {
                            width: 400,
                            bodyStyle: 'padding: 5px;',
                            items: [{
                                html: 'This will set any rows where the total percent explained by the pair of haplotypes is below the threshold to uncallable.  It can also be used to set any individual haplotype within the pair below the supplied percent to uncallable.  Please note: this can result in subjects where multple rows from a locus are present that are labeled uncallable.  You may want to use the neighboring button to prune these.',
                                border: false
                            },{
                                fieldLabel: 'Min % Explained By Pair',
                                value: 75,
                                xtype: 'ldk-integerfield',
                                itemId: 'minPctExplained'
                            },{
                                fieldLabel: 'Min % Found For Haplotype',
                                value: 50,
                                xtype: 'ldk-integerfield',
                                itemId: 'minPctForHaplotype'
                            }],
                            buttons: [{
                                text: 'Submit',
                                scope: this,
                                handler: function(btn){
                                    var minPctExplained = btn.up('window').down('#minPctExplained').getValue();
                                    var minPctForHaplotype = btn.up('window').down('#minPctForHaplotype').getValue();
                                    var totalAltered = 0;

                                    if (minPctForHaplotype){
                                        minPctForHaplotype = minPctForHaplotype / 100;
                                    }

                                    grid.store.each(function(rec){
                                        if (minPctExplained && rec.get('totalPctPresent') < minPctExplained){
                                            rec.set('haplotype1', null);
                                            rec.set('haplotype2', null);
                                            totalAltered++;
                                            return;
                                        }

                                        if (minPctForHaplotype){
                                            var altered = false;
                                            if (rec.get('haplotype1')){
                                                var pct = rec.get('matches1').length / rec.get('haplotype1').getLineageNames().length;
                                                if (pct < minPctForHaplotype){
                                                    rec.set('haplotype1', null);
                                                    altered = true;
                                                }
                                            }

                                            if (rec.get('haplotype2')){
                                                var pct = rec.get('matches2').length / rec.get('haplotype2').getLineageNames().length;
                                                if (pct < minPctForHaplotype){
                                                    rec.set('haplotype2', null);
                                                    altered = true;
                                                }
                                            }

                                            if (altered){
                                                totalAltered++;
                                            }
                                        }
                                    }, this);

                                    btn.up('window').close();

                                    if (totalAltered){
                                        Ext4.Msg.alert('Changes', 'Total rows changed: ' + totalAltered);
                                    }
                                }
                            },{
                                text: 'Cancel',
                                handler: function(btn){
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                },{
                    text: 'Remove Duplicate Uncallable Rows',
                    scope: this,
                    handler: function(btn){
                        var grid = btn.up('grid');

                        var uncallableRecMap = {};
                        var callableRecMap = {};
                        grid.store.each(function(rec){
                            var key = rec.get('analysisId') + '||' + rec.get('locus');
                            if (!rec.get('haplotype1') && !rec.get('haplotype2')){
                                uncallableRecMap[key] = uncallableRecMap[key] || [];
                                uncallableRecMap[key].push(rec);
                            }
                            else {
                                callableRecMap[key] = true;
                            }
                        }, this);

                        var totalRemoved = 0;
                        Ext4.Array.forEach(Ext4.Object.getKeys(uncallableRecMap), function(key){
                            if (callableRecMap[key]){
                                var toRemove = uncallableRecMap[key];
                                grid.store.remove(toRemove);
                                totalRemoved += toRemove.length;
                            }
                            else if (uncallableRecMap[key].length > 1){
                                var toRemove = uncallableRecMap[key];
                                toRemove.pop();

                                grid.store.remove(toRemove);
                                totalRemoved += toRemove.length;
                            }
                        }, this);

                        Ext4.Msg.alert('Changes', 'Total rows removed: ' + totalRemoved);
                    }
                },{
                    text: 'Remove Selected',
                    scope: this,
                    handler: function(btn){
                        var grid = btn.up('grid');
                        var selected = grid.getSelectionModel().getSelection();
                        if (!selected.length){
                            Ext4.Msg.alert('Error', 'No rows selected');
                            return;
                        }

                        Ext4.Msg.confirm('Remove Rows?', 'You are about to remove ' + selected.length + ' rows.  Continue?', function(val){
                            if (val == 'yes'){
                                grid.store.remove(selected);
                            }
                        }, this);
                    }
                }]
            }],
            buttonAlign: 'left',
        });

        this.callParent();

        this.loadData();
    },

    haplotypes: {},
    resultsByLoci: {},
    lineagePctMap: {},
    analysesMap: {},

    loadData: function(){
        Ext4.Msg.wait('Loading...');

        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'haplotype_sequences',
            columns: 'haplotype,haplotype/type,name,type,present,required',
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
                    this.haplotypes[row['haplotype/type']][row.haplotype] = this.haplotypes[row['haplotype/type']][row.haplotype] || GeneticsCore.panel.HaplotypePanel.Haplotype(row.haplotype, row['haplotype/type']);

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
                    row.lineages = row.lineages.replace('\n', ';');

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

        multi.send(this.onDataLoad, this);
    },

    onDataLoad: function(){
        Ext4.Msg.hide();
        var store = this.down('grid').store;
        var minPctExplained = this.down('#minPctExplained').getValue();
        var minPctForHaplotype = this.down('#minPctForHaplotype').getValue();
        store.removeAll();
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
                    var matchingLineages = h.getMatchingLineages(lineages);
                    if (matchingLineages){
                        var pctMatch = 100 * (matchingLineages.length / h.getLineageNames().length);
                        if (minPctForHaplotype && pctMatch < minPctForHaplotype){
                            console.log('haplotype below minPctForHaplotype: ' + analysisId + ', ' + haplotypeName + ', ' + pctMatch);
                        }
                        else {
                            haplotypeMatches[haplotypeName] = matchingLineages;
                        }
                    }
                }, this);

                //now find best two matches:
                var rankedMatches = {};
                var rankedPctMatches = {};
                var pctExplainedByMatch = {};
                Ext4.Array.forEach(Ext4.Object.getKeys(haplotypeMatches), function(h1){
                    Ext4.Array.forEach(Ext4.Object.getKeys(haplotypeMatches), function(h2){
                        var union = [];
                        union = union.concat(haplotypeMatches[h1]);
                        union = union.concat(haplotypeMatches[h2]);
                        union = Ext4.unique(union);

                        var names = [h1, h2].sort().join('<>');

                        //calculate total pct present
                        var totalPctPresent = 0;
                        Ext4.Array.forEach(union.sort(), function(l){
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
                            console.log('haplotype below minPctExplained: ' + analysisId + ', ' + names + ', ' + totalPctPresent);
                        }
                        else {
                            rankedPctMatches[totalPctPresent] = rankedPctMatches[totalPctPresent] || [];
                            if (rankedPctMatches[totalPctPresent].indexOf(names) == -1) {
                                rankedPctMatches[totalPctPresent].push(names);
                            }

                            pctExplainedByMatch[names] = totalPctPresent;

                            rankedMatches[union.length] = rankedMatches[union.length] || [];
                            if (rankedMatches[union.length].indexOf(names) == -1) {
                                rankedMatches[union.length].push(names);
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

                if (!highest && !highestByPct){
                    var record = store.createModel({
                        subjectId: this.analysesMap[analysisId].subjectId,
                        locus: locus,
                        haplotype1: null,
                        haplotype2: null,
                        matches1: [],
                        matches2: [],
                        totalMatches: 0,
                        lineages: [],
                        lineagePctMap: this.lineagePctMap[analysisId],
                        analysisId: analysisId,
                        totalPctPresent: 0,
                        comment: ['No Match']
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
                        Ext4.Array.forEach(highest, function(n){
                            //if there's a 20% differential, ignore the one explaining more hits
                            if (totalPct - pctExplainedByMatch[n] > this.pctDifferentialFilter){
                                matches.remove(n);
                            }
                        }, this);

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
                        var record = store.createModel({
                            subjectId: this.analysesMap[analysisId].subjectId,
                            locus: locus,
                            haplotype1: this.haplotypes[locus][names[0]],
                            haplotype2: this.haplotypes[locus][names[1]],
                            matches1: haplotypeMatches[names[0]] || [],
                            matches2: haplotypeMatches[names[1]] || [],
                            totalMatches: totalMatches,
                            lineages: lineages,
                            lineagePctMap: this.lineagePctMap[analysisId],
                            analysisId: analysisId,
                            comment: comments
                        });

                        var totalPresent = 0;
                        var totalPctPresent = 0;
                        Ext4.Array.forEach(record.get('lineages').sort(), function(l){
                            var pctFromLocus = record.get('lineagePctMap')[l].percent_from_locus;
                            var present = record.get('matches1').indexOf(l) > -1 || record.get('matches2').indexOf(l) > -1;

                            if (!present && l.split(';').length > 1){
                                Ext4.Array.forEach(l.split(';'), function(toTest){
                                    if (record.get('matches1').indexOf(toTest) > -1 || record.get('matches2').indexOf(toTest) > -1){
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
    },

    statics: {
        Haplotype: function (name, locus){
            var sequences = [];

            return {
                addSequence: function (rowMap){
                    sequences.push(rowMap);
                },
                getLineageNames: function(){
                    var ret = [];
                    Ext4.Array.forEach(sequences, function(s){
                        if (s.type.toLowerCase() == 'lineage'){
                            ret.push(s.name);
                        }
                        else {
                            console.log('not lineage: ' + s.type);
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
                isRequired: function(lineage){
                    var ret = false;
                    Ext4.Array.forEach(sequences, function(s){
                        if (s.name == lineage && s.required){
                            ret = true;
                            return false;
                        }
                    }, this);

                    return ret;
                },
                getMatchingLineages: function(lineages){
                    var hasRequired = true;
                    var matches = [];

                    Ext4.Array.forEach(sequences, function(c){
                        var isMatch = false;
                        Ext4.Array.forEach(lineages, function(l){
                            l = l.split(';');
                            if (l.indexOf(c.name) > -1){
                                isMatch = true;
                                return false;
                            }
                        }, this);

                        if (!isMatch && c.required){
                            hasRequired = false;
                            return false;
                        }

                        if (isMatch) {
                            matches.push(c.name);
                        }
                    }, this);

                    return !hasRequired || !matches.length ? null : matches;
                }
            }
        }
    }
});