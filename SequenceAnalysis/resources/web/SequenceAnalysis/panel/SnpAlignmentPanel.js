/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('SequenceAnalysis.panel.SnpAlignmentPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.alignmentpanel',

    MAX_ROWS: 200000,
    FEATURE_COLOR_MAP: {
        'CTL Epitope': 'yellow',
        'Protein Domain': 'cyan'
    },

    initComponent: function(){
        Ext4.tip.QuickTipManager.init(true, {
            constrainPosition: true,
            dismissDelay: 0,
            //shadow: 'drop',
            //shadowOffset: 0,
            maxWidth: 2000
        });

        Ext4.apply(this, {
            //NOTE: removed to allow panel to auto-expand as alignments rendered
            //width: '100%',
            bodyBorder: false,
            border: false,
            ref_aa_features: {},
            tracks: [],
            defaults: {
                border: false,
                bodyStyle:'padding:5px',
                width: 400,
                msgTarget: 'side'
            },
            items: [{
                title: 'Analyses',
                width: null,
                style: 'padding-bottom:10px;',
                border: true,
                itemId: 'sampleInfo',
                items: [{
                    xtype: 'ldk-querypanel',
                    border: false,
                    queryConfig: {
                        schemaName: 'sequenceanalysis',
                        queryName: 'sequence_analyses',
                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                        filterArray: [LABKEY.Filter.create('rowid', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
                        sort: 'rowid'
                    }
                }]
            }]
        });

        this.callParent();
        this.populateSamples();
    },

    onSampleLoad: function(){
        this.add({
            xtype: 'alignmentconfigpanel',
            allowableStrains: this.allowableStrains,
            style: 'padding-bottom:10px;',
            itemId: 'alignConfig'
        },{
            xtype: 'panel',
            itemId: 'alignmentArea',
            title: 'Alignment',
            width: '100%',
            collapsible: true,
            style: 'padding-bottom:10px;',
            border: true,
            items: [{
                xtype: 'ldk-contentresizingpanel',
                border: false,
                itemId: 'alignmentDiv'
            }]
        });
    },

    doAlignment: function(){
        var proteinField = this.down('#proteinField');
        this.ref_aa_ids = proteinField.getValue();

        if(!this.ref_aa_ids || !this.ref_aa_ids.length){
            alert('Must pick one or more proteins');
            return;
        }

        this.ref_nt_ids = [];

        proteinField.store.each(function(r){
            if(this.ref_aa_ids.indexOf(r.get('rowid')) != -1){
                this.ref_nt_ids.push(r.get('ref_nt_id'));
            }
        }, this);

        this.ref_nt_ids = Ext4.unique(this.ref_nt_ids);

        this.queryDB();
    },

    populateSamples: function(){
        LABKEY.Query.executeSql({
            schemaName: 'sequenceanalysis',
            sql: "SELECT distinct(ref_nt_id.subset) as strain FROM sequenceanalysis.sequence_coverage WHERE (analysis_id="+this.analysisIds.join(" OR analysis_id=")+")",
            scope: this,
            success: function(data){
                this.allowableStrains = [];
                var errorMsg = 'There are no reference sequences shared by all the samples, so no report can be shown';
                if(data.rows && data.rows.length){
                    Ext4.Array.forEach(data.rows, function(r){
                        this.allowableStrains.push(r.strain);
                    }, this);

                    this.allowableStrains = Ext4.unique(this.allowableStrains);
                    if(!this.allowableStrains.length)
                        alert(errorMsg);
                    else
                        this.onSampleLoad();
                }
                else {
                    alert(errorMsg)
                }
            }
        });

        LABKEY.Query.selectRows({
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_analyses',
            includeTotalCount: false,
            columns: 'rowid,readset,readset/name,ref_nt_id,sampleid,readset/sampleid/samplename,readset/sampledate,readset/subjectid,readset/comment,type',
            sort: 'readset/name',
            filterArray: [LABKEY.Filter.create('rowid', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            scope: this,
            success: function(data){
                if(data.rows && data.rows.length){
                    var sampleIds = [];
                    this.analysesRecords = {};
                    var sortedIds = [];
                    Ext4.Array.forEach(data.rows, function(r){
                        if(r.readset)
                            sampleIds.push(r.readset);
                        this.analysesRecords[r.rowid] = r;

                        sortedIds.push(r.rowid);
                    }, this);

                    this.analysisIds = sortedIds;
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    queryDB: function(){
        var loaded = 0;
        if(this.snps){
            Ext4.Array.forEach(this.analysisIds, function(id){
                if(this.snps[id]){
                    loaded++;
                }
            }, this);

            if(loaded == this.analysisIds.length){
                this.onLoadComplete();
                return;
            }
        }

        Ext4.Msg.wait('Fetching Data...');
        Ext4.Ajax.timeout = 5 * 60 * 1000;
        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'SequenceAnalysis',
            queryName: 'aa_snps_by_codon',
            maxRows: this.MAX_ROWS,
            timeout: 0,
            includeTotalCount: false,
            columns: 'analysis_id,q_aa,codon,ref_aa,ref_aa_id,ref_aa_insert_index,ref_aa_position,ref_nt_id,ref_nt_positions,pct,readcount,depth,adj_depth,ref_aa_id/name,ref_nt_id/name',
            filterArray: [
                LABKEY.Filter.create('analysis_id', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF),
                //NOTE: added to reduce the total rowcount being sent to the client, as these datapoints rarely alter presentation
                LABKEY.Filter.create('pct', 0.005, LABKEY.Filter.Types.GT)
            ],
            scope: this,
            success: this.processSnps,
            failure: LDK.Utils.getErrorCallback()
        });

        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'SequenceAnalysis',
            queryName: 'ref_aa_sequences',
            includeTotalCount: false,
            columns: 'rowid,name,exons,sequence,ref_nt_id,isComplement',
            scope: this,
            success: function(data){
                if(data && data.rows){
                    this.ref_aa_sequences = {};
                    var r;
                    for (var i=0;i<data.rows.length;i++){
                        r = data.rows[i];
                        this.ref_aa_sequences[r.rowid] = r;

                        var exons = r.exons.split(';');
                        r.exonMap = Ext4.Array.map(exons, function(i){
                            var a = i.split('-');
                            return [Number(a[0]), Number(a[1])]
                        });

                        r.exonMap = r.exonMap.sort(function(a, b){
                            return a[0] > b[0] ? 1 :
                                   a[0] < b[0] ? -1 : 0;
                        });
                    };
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'SequenceAnalysis',
            queryName: 'sequence_coverage',
            maxRows: -1,
            includeTotalCount: false,
            columns: 'rowid,analysis_id,ref_nt_id,ref_nt_position,ref_nt_insert_index,depth,adj_depth,total_a,total_t,total_g,total_c,total_n,total_del,wt',
            filterArray: [
                LABKEY.Filter.create('analysis_id', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF),
                LABKEY.Filter.create('ref_nt_insert_index', 0, LABKEY.Filter.Types.EQUAL)
            ],
            scope: this,
            success: function(data){
                //NOTE: we always use coverage from insert_index of 0
                if(data && data.rows){
                    this.coverageMap = {};
                    var r;
                    for (var i=0; i<data.rows.length;i++){
                        r = data.rows[i];
                        if(!this.coverageMap[r.analysis_id])
                            this.coverageMap[r.analysis_id] = {};

                        if(!this.coverageMap[r.analysis_id][r.ref_nt_id])
                            this.coverageMap[r.analysis_id][r.ref_nt_id] = {};

                        if(!this.coverageMap[r.analysis_id][r.ref_nt_id][r.ref_nt_position])
                            this.coverageMap[r.analysis_id][r.ref_nt_id][r.ref_nt_position] = {};

                        this.coverageMap[r.analysis_id][r.ref_nt_id][r.ref_nt_position][r.ref_nt_insert_index] = r;

                    };
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        //configure tracks
        var filters = [
            LABKEY.Filter.create('ref_nt_id', this.ref_nt_ids.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
        ];

        multi.add(LABKEY.Query.selectRows, {
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            schemaName: 'sequenceanalysis',
            queryName: 'aa_features_combined',
            includeTotalCount: false,
            columns: 'rowid,ref_nt_id,ref_aa_id,name,category,aa_start,aa_stop,aa_sequence,description',
            filterArray: filters,
            sort: 'aa_start',
            scope: this,
            success: function(data){
                this.ref_aa_features_data = data.rows;
            }
        });

        this.startTime = new Date();
        multi.send(this.onLoadComplete, this);
    },

    processSnps: function(data){
        this.snps = {};
        this.inserts = {};

        console.log('total AA snps loaded: ' + data.rows.length);
        if (data.rows.length == this.MAX_ROWS){
            var msg = 'More than ' + this.MAX_ROWS + ' SNPs were returned, which is above the max supported in this viewer.  Data could potentially be lost';
            alert(msg);
            LDK.Utils.logToServer({
                message: msg
            });
        }

        //note: iterate these first, in case a given reference has no SNPs
        Ext4.Array.forEach(this.analysisIds, function(analysis_id){
            this.snps[analysis_id] = {};
        }, this);

        var row;
        for (var i=0;i<data.rows.length;i++){
            row = data.rows[i];
            if(!this.snps[row.analysis_id][row.ref_aa_id]){
                this.snps[row.analysis_id][row.ref_aa_id] = {refName: row['ref_aa_id/name'], snps: {}};
                this.inserts[row.ref_aa_id] = this.inserts[row.ref_aa_id] || {};
            }
            if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position]){
                this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position] = {};
                this.inserts[row.ref_aa_id][row.ref_aa_position] = this.inserts[row.ref_aa_id][row.ref_aa_position] || {};
            }
            if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]){
                this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index] = {ref_aa: row.ref_aa, num_reads: 0, adj_num_reads: 0, percent: 0, adj_percent: 0, depth: row.depth, adj_depth: row.adj_depth, codons: {}};
            }

            if(Number(row.ref_aa_insert_index) > 0){
                if(!this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index])
                    this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index] = {maxPct: 0, samples: {}};

                this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index]['samples'][row.analysis_id] = row;
            }

            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['num_reads'] += row.readcount;
            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['adj_num_reads'] += row.readcount;

            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['percent'] += row.pct;
            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['adj_percent'] += row.pct;

            if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['codons'][row.codon]){
                this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['codons'][row.codon] = {count: 0, adj_count: 0, q_aa: (row.q_aa || 'x'), depth: row.depth, percent: row.pct, adj_percent: row.pct, adj_depth: row.adj_depth, ref_nt_positions: row.ref_nt_positions}
            }

            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['codons'][row.codon]['count'] += row.readcount;
            this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]['codons'][row.codon]['adj_count'] += row.readcount;
        };
    },

    onLoadComplete: function(){
        Ext4.Msg.hide();
        Ext4.Msg.wait('Drawing Alignment...');

        //force recaching of colors
        this.getColorSettings(true);

        var loadTime = new Date();
        if (this.startTime){
            console.log('Load Time: '+(loadTime - this.startTime)/1000 + ' seconds');
        }

        if(!this.snps){
            alert('Problem loading data');
            Ext4.Msg.hide();
            return;
        }

        this.tracks = [];

        this.ref_aa_features = {};
        if(this.ref_aa_features_data){
            Ext4.Array.forEach(this.ref_aa_features_data, function(r){
                var catField = this.down('#aaFeaturesCategoryField');
                if(catField.getValue()){
                    if(catField.getValue().indexOf(r.category) == -1)
                        return;
                }

                if(!this.ref_aa_features[r.ref_aa_id])
                    this.ref_aa_features[r.ref_aa_id] = [];

                //necessary to cleanup the drug resistance field which is created by group_concat
                if(r.description && Ext4.isArray(r.description)){
                    r.description = r.description.join(', ');
                    r.description = r.description.replace(/\n/g, '<br>');
                }

                this.ref_aa_features[r.ref_aa_id].push(r);
            }, this);

            this.tracks.push({
                data: this.ref_aa_features,
                name: 'AA Features'
            });
        }

        if(this.down('#showElispot').getValue()){
            console.log('ELISPOT not yet implemented');
//            this.tracks.push({
//                data: this.elispot_data,
//                name: 'ELISPOT Results'
//            });
        }

        this.buildRefPositionMap();
        this.buildSequences();

        var target = this.down('#alignmentDiv');
        var targetEl = Ext4.fly(target.renderTarget);
        targetEl.update('');

        var toAdd = [];
        Ext4.Array.forEach(this.ref_aa_ids, function(ref_id){
            toAdd = toAdd.concat(this.renderAlignment(ref_id));
            console.log('Rendered alignment for ' + ref_id + ': '+((new Date()) - loadTime)/1000 + ' seconds');
        }, this);

        targetEl.createChild({
            tag: 'div',
            children: toAdd
        });

        this.startTime = null;
        target.doLayout();
        console.log('Processing Time: '+((new Date()) - loadTime)/1000 + ' seconds');

        Ext4.Msg.hide();
    },

    buildRefPositionMap: function(){
        this.refPositions = {};
        var ref;
        var refId;
        for(var i=0;i<this.ref_aa_ids.length;i++){
            refId = this.ref_aa_ids[i];
            ref = this.ref_aa_sequences[refId];
            if(!this.refPositions[refId])
                this.refPositions[refId] = {};

            for(var j=1;j<=ref.sequence.length;j++){
                this.refPositions[refId][j] = {0: {residue: ref.sequence.substr((j-1), 1)}};
            }
        }

        if(this.inserts && !SequenceAnalysis.Utils.isEmptyObj(this.inserts)){
            var minPct = this.down('#minMutationPct').getValue();
            var minReads = this.down('#minReadNum').getValue();
            var percent = 0;
            var reads = 0;

            for(var ref_id in this.inserts){
                for(var pos in this.inserts[ref_id]){
                    for(var idx in this.inserts[ref_id][pos]){
                        for(var a in this.inserts[ref_id][pos][idx].samples){
                            //find the highest percent of all samples
                            if(percent < this.inserts[ref_id][pos][idx].samples[a].pct)
                                percent = this.inserts[ref_id][pos][idx].samples[a].pct;

                            if(reads < this.inserts[ref_id][pos][idx].samples[a].readcount)
                                reads = this.inserts[ref_id][pos][idx].samples[a].readcount;
                        };

                        if((percent==0 && reads==0) || minPct && minPct > percent || minReads && minReads > reads)
                            continue;

                        this.refPositions[ref_id][pos][idx] = {residue: '-'};
                    }
                }
            }
        }

        this.refSequences = {};
        for(ref_id in this.refPositions){
            this.refSequences[ref_id] = [];
            for(pos in this.refPositions[ref_id]){
                for(idx in this.refPositions[ref_id][pos]){
                    this.refSequences[ref_id].push('<span '+(idx!=0 ? ' style="background-color:#C0C0C0;"' : '')+' data-qtip="Position: '+(idx==0 ? pos : pos+'.'+idx)+'">'+this.refPositions[ref_id][pos][idx].residue+'</span>');
                }
            }
        }
    },

    buildSequences: function(){
        this.querysequences = {};
        var a;
        for (var i=0;i<this.analysisIds.length;i++){
            a = this.analysisIds[i];
            this.querysequences[a] = this.buildSequence(a);
        };
    },

    buildSequence: function(analysis_id){
        var obj = {};
        var snp;
        var q_aa;
        var i;
        var residue;
        var minPct = this.down('#minMutationPct').getValue();
        var minReads = this.down('#minReadNum').getValue();
        var minCoverage = this.down('#minCoverage').getValue();

        for(var ref_id in this.refPositions){
            obj[ref_id] = [];
            for(var pos in this.refPositions[ref_id]){
                for(var idx in this.refPositions[ref_id][pos]){
                    var coverInfo = this.calcNTPositionForAA(ref_id, analysis_id, pos);
                    var ntPositions = coverInfo.nt_positions ? coverInfo.nt_positions.join('/') : '';

                    var cover = coverInfo.avgCover ? LABKEY.Utils.roundNumber(coverInfo.avgCover, 2) : 0;

                    if(!cover || cover < minCoverage){
                        residue = (idx==0) ? ':' : '-';
                        obj[ref_id].push({
                            tag: 'span',
                            html: residue,
                            style: this.getSnpStyle(analysis_id, ref_id, pos, idx, residue, {isIndel: !(idx==0)}),
                            'data-qtip': 'Avg. Depth: ' + (!cover ? 0 : cover) + '<br>AA Position: ' + pos + (idx != 0 ? '.' + idx : '') + (ntPositions ? '<br>NT Positions: ' + ntPositions : '')
                        });
                        continue;
                    }

                    if(this.snps[analysis_id] &&
                        this.snps[analysis_id][ref_id] &&
                        this.snps[analysis_id][ref_id]['snps'][pos] &&
                        this.snps[analysis_id][ref_id]['snps'][pos][idx]
                    ){
                        snp = this.snps[analysis_id][ref_id]['snps'][pos][idx];

                        this.processSnp(snp, minPct, minReads);
                        if(!snp.show){
                            residue = (idx==0) ? '.' : '-';
                            obj[ref_id].push({
                                tag: 'span',
                                html: residue,
                                style: this.getSnpStyle(analysis_id, ref_id, pos, idx, residue, {isIndel: !(idx==0)}),
                                'data-qtip': 'Avg. Depth: ' + cover + (ntPositions ? '<br>NT Positions: ' + ntPositions : '')
                            });
                            continue;
                        }

                        if(snp.displayResidues.length == 1){
                            residue = snp.displayResidues[0];
                        }
                        else if(snp.displayResidues.length == 0){
                            residue = '.';
                        }
                        //incomplete codons or indels
                        else if (snp.displayResidues.indexOf(':') != -1 ||
                            snp.displayResidues.indexOf('?') != -1 ||
                            snp.displayResidues.indexOf('+') != -1
                        ){
                            var incomplete = 0;
                            var codon;
                            for(var i in snp.codons){
                                codon = snp.codons[i];
                                if(!codon.q_aa){
                                    console.error('no qq_aa');
                                    console.log(codon);
                                    return;
                                }

                                if(codon.q_aa && (codon.q_aa.match(':') || codon.q_aa.match(/\?/) || codon.q_aa.match(/\+/)))
                                    incomplete += codon.adj_percent;
                            };

                            //if a signficant percent of mutations at this position are indels, color as indel
                            if((incomplete / snp.adj_percent) > .3)
                                snp.isIndel = true;

                            residue = 'X';
                        }
                        else {
                            residue = 'X';
                        }

                        obj[ref_id].push({
                            tag: 'span',
                            html: residue,
                            style: this.getSnpStyle(analysis_id, ref_id, pos, idx, residue, snp),
                            'data-qtip': Ext4.util.Format.htmlEncode(this.renderSnpQtip(analysis_id, this.refPositions[ref_id][pos][0].residue, ref_id, pos, idx, snp)),
                            'data-qwidth': "800",
                            'data-hide': 'user',
                            'data-qclass': 'snp-qtip-border'
                        });
                    }
                    else {
                        residue = (idx==0) ? '.' : '-';
                        obj[ref_id].push({
                            tag: 'span',
                            html: residue,
                            style: this.getSnpStyle(analysis_id, ref_id, pos, idx, residue, {isIndel: !(idx==0)}),
                            'data-qtip': 'Avg. Depth: ' + cover + (ntPositions ? '<br>NT Positions: ' + ntPositions : '')
                        });
                    }
                }
            }
        }

        return obj;
    },

    processSnp: function(snp, minPct, minReads){
        if((minPct && minPct>snp.adj_percent) || (minReads && minReads>snp.adj_num_reads)){
            snp.show = false;
            return;
        }

        snp.show = true;

        if(!snp.aa_map){
            snp.aa_map = {};
            for(var i in snp.codons){
                if(!snp.aa_map[snp.codons[i].q_aa])
                    snp.aa_map[snp.codons[i].q_aa] = {adj_percent: 0, adj_count: 0};

                snp.aa_map[snp.codons[i].q_aa].adj_percent += snp.codons[i].adj_percent;
                snp.aa_map[snp.codons[i].q_aa].adj_count += snp.codons[i].adj_count;
            }
        }

        snp.displayResidues = [];
        for(i in snp.aa_map){
            if(minPct && snp.aa_map[i].adj_percent < minPct){
                snp.aa_map[i].show = false;
                continue;
            }

            if(minReads && snp.aa_map[i].adj_count < minReads){
                snp.aa_map[i].show = false;
                continue;
            }

            snp.aa_map[i].show = true;
            if(snp.displayResidues.indexOf(i) == -1)
                snp.displayResidues.push(i);
        }
    },

    getColorSettings: function(reset){
        var colorPanel = this.down('#snpColoration');

        if (!reset && this._settings){
            return this._settings;
        }

        this._settings = {
            useGradient: colorPanel.down('#useGradientField').getValue(),
            noCoverColor: '#' + colorPanel.down('#nocoverColorField').getValue(),
            synColor: '#' + colorPanel.down('#synColorField').getValue(),
            synColorSteps: colorPanel.down('#synColorStepsField').getValue(),
            fsColor: '#' + colorPanel.down('#fsColorField').getValue(),
            fsColorSteps: colorPanel.down('#fsColorStepsField').getValue(),
            nsColor: '#' + colorPanel.down('#nsColorField').getValue(),
            nsColorSteps: colorPanel.down('#nsColorStepsField').getValue()
        };

        return this._settings;
    },

    getSnpStyle: function(analysis_id, ref_id, pos, idx, residue, snp){
        var settings = this.getColorSettings();
        var refAA = this.refPositions[ref_id][pos][idx].residue;

        var color;
        var steps;
        var snpCopy = snp || {};
        if(residue == ':'){
            return 'background-color: ' + settings.noCoverColor + ';';
        }
        else if (residue == '.'){
            return 'background-color: transparent;'
        }
        else if(residue == refAA && residue != '-'){
            color = settings.synColor;
            if(settings.useGradient)
                steps = settings.synColorSteps;
        }
        else if (refAA == '-' || residue == '+'  || residue == '?' || idx > 0 || snpCopy.isIndel){
            color = settings.fsColor;
            if(settings.useGradient)
                steps = settings.fsColorSteps;
        }
        else {
            color = settings.nsColor;
            if(settings.useGradient)
                steps = settings.nsColorSteps;
        }

        if(settings.useGradient)
            color = SequenceAnalysis.Utils.calcGradient(color, steps, pos, idx, snp);

        return 'background-color: '+color+';'
    },

    renderAlignment: function(ref_id){
        var rowLength = this.down('#rowLength').getValue();
        var rowSize = this.ref_aa_sequences[ref_id].sequence.length;
        var numRows = !rowLength ? 1 : Math.ceil(rowSize / rowLength);
        var trackData = this.buildTrackData(ref_id, rowLength);

        var config = {
            tag: 'table',
            border: 1,
            style: 'border-collapse:collapse;',
            children: []
        };

        config.children.push({
            tag: 'tr',
            children: [{
                tag: 'td',
                html: ''
            },{
                tag: 'td',
                html: 'Start',
                style: 'padding: 5px;text-align:center;'
            },{
                tag: 'td',
                html: ''
            },{
                tag: 'td',
                html: 'End',
                style: 'padding: 5px;text-align:center;'
            }]
        });

        var offset = 0;
        for (var rowNum = 0; rowNum < numRows; rowNum++){
            var rowStart = rowNum * rowLength;
            var rowEnd = !rowLength ? this.refSequences[ref_id].length : Math.min(rowStart + rowLength, this.refSequences[ref_id].length);

            var newTracks = this.renderFeatureRow(trackData, rowStart, rowEnd);
            config.children = config.children.concat(newTracks);

            //count insertions within this row
            var refSequence = this.refSequences[ref_id].slice(rowStart, rowEnd).join('');
            var insertions = refSequence.match(/>-<\/span>/g);
            if (insertions)
                offset += insertions.length;

            //iterate each ref ID, showing this row
            config.children.push({
                tag: 'tr',
                children: [{
                    tag: 'td',
                    html: ''
                },{
                    tag: 'td',
                    style: 'padding: 5px;text-align:center;',
                    html: rowStart + 1 - offset
                },{
                    tag: 'td',
                    style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                    html: refSequence
                },{
                    tag: 'td',
                    style: 'padding: 5px;text-align:center;',
                    html: rowEnd - offset
                }]
            });

            Ext4.Array.forEach(this.analysisIds, function(a, idx){
                config.children.push({
                    tag: 'tr',
                    children: [
                        this.renderSampleName(a)
                    ,{
                        tag: 'td',
                        html: ''
                    },{
                        tag: 'td',
                        style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                        children: this.querysequences[a][ref_id].slice(rowStart, rowEnd)
                    },{
                        tag: 'td',
                        html: ''
                    }]
                });
            }, this);
        }

        return [{
            tag: 'h3',
            html: this.ref_aa_sequences[ref_id].name+':',
            style: 'padding-top: 10px;'
        }, config];
    },

    renderSampleName: function(analysisId){
        var name = this.analysesRecords[analysisId]['readset/name'];

        var qtip = [
            'Analysis Id: ' + analysisId,
            'Readset: ' + this.analysesRecords[analysisId]['readset/name'] + ' (' + this.analysesRecords[analysisId]['readset'] + ')'
        ];

        if(this.analysesRecords[analysisId]['readset/subjectid'])
            qtip.push('Subject Id: ' + this.analysesRecords[analysisId]['readset/subjectid']);
        if(this.analysesRecords[analysisId]['readset/sampledate'])
            qtip.push('Sample Date: ' + this.analysesRecords[analysisId]['readset/sampledate']);
        if(this.analysesRecords[analysisId]['readset/comment'])
            qtip.push('Readset Comments: ' + this.analysesRecords[analysisId]['readset/comment']);

        qtip = qtip.join('<br>');

        return {
            tag: 'td',
            style: 'padding: 5px;white-space:nowrap;',
            html: name,
            'data-qtip': qtip
        }
    },

    buildTrackData: function(ref_id, rowLen){
        var tracks = [{}];
        Ext4.Array.forEach(this.tracks, function(track){
            var data = track.data;

            if(data && data[ref_id]){
                Ext4.Array.forEach(data[ref_id], function(feature){
                    var start = Number(feature.aa_start);
                    var stop = Number(feature.aa_stop);
                    var effective_length = Math.max(stop - start + 1, feature.name.length);
                    var foundPosition = false;

                    Ext4.each(tracks, function(track){
                        //verify the current track is available
                        for(var i = (start-1); i<(start + effective_length);i++){
                            if(track[i]){
                                return;
                            }
                        }

                        //otherwise add to current track
                        foundPosition = true;
                        this.addFeatureToTrack(track, feature);
                        return false; //stop iterating other tracks
                    }, this);

                    if(!foundPosition){
                        //create new track
                        var newTrack = {};
                        this.addFeatureToTrack(newTrack, feature);

                        tracks.push(newTrack);
                    }
                }, this);
            }
        }, this);

        var rows = [];
        Ext4.Array.forEach(tracks, function(track){
            var row = [];
            var counter = 0;
            var previousFeatureStart = '';
            for(var pos in this.refPositions[ref_id]){
                for(var idx in this.refPositions[ref_id][pos]){
                    var thisPosition = '';
                    counter++;

                    //if we are beginning a row and previously ended mid-feature, restart
                    if (counter % rowLen == 1){
                        if (previousFeatureStart)
                            thisPosition += previousFeatureStart;
                    }

                    //for now features can only begin/end on ref positions, not indels
                    if(idx == 0 && track[pos]){
                        if(track[pos].isStart){
                            previousFeatureStart = '<span data-qtip="'+Ext4.util.Format.htmlEncode(track[pos].qtip)+'" style="background-color:'+track[pos].color+'">';
                            thisPosition += previousFeatureStart;
                        }
                        thisPosition += track[pos].value;

                        if(track[pos].isEnd){
                            thisPosition += '</span>';
                            previousFeatureStart = null;
                        }
                    }
                    else {
                        thisPosition += '&nbsp;';
                    }

                    //if we are within a feature at row end, terminate feature
                    if (counter % rowLen == 0){
                        if (previousFeatureStart)
                            thisPosition += '</span>';
                    }

                    row.push(thisPosition);
                }
            }

            rows.push(row);
        }, this);

        return rows;
    },

    renderFeatureRow: function(rows, rowStart, rowEnd){
        //create HTML from tracks
        var featureRows = [];
        Ext4.Array.forEach(rows, function(row){
            var rowStr = row.slice(rowStart, rowEnd).join('');

            //discard empty rows
            if (rowStr.replace(/&nbsp;/g, '') == '')
                return;

            featureRows.push(rowStr);
        }, this);

        return {
            tag: 'tr',
            style: 'border:0px',
            children: [{
                tag: 'td',
                style: 'border:0px',
                html: ''
            },{
                tag: 'td',
                style: 'border:0px',
                html: ''
            },{
                tag: 'td',
                style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                html: featureRows.join('<br>')
            },{
                tag: 'td',
                style: 'border:0px',
                html: ''
            }]
        };
    },

    addFeatureToTrack: function(track, feature){
        var refLength = this.ref_aa_sequences[feature.ref_aa_id].sequence.length;
        var start = Number(feature.aa_start);
        var stop = Math.min(Number(feature.aa_stop), refLength);
        var effective_length = Math.max(stop - start + 1, feature.name.length);

        for(var i = start; i<(start + effective_length);i++){
            var value = feature.name[i - start] || '&nbsp;';
            track[i] = {value: value};

            if(i==start){
                track[i].isStart = true;
                track[i].qtip = [
                    'Name: ' + feature.name,
                    'Category: ' + (feature.category || ''),
                    'AA Start: ' + feature.aa_start,
                    'AA Stop: ' + feature.aa_stop
                ];

                if(feature.description)
                    track[i].qtip.push('Description: ' + feature.description);

                track[i].qtip = track[i].qtip.join('<br>');
                track[i].color = this.FEATURE_COLOR_MAP[feature.category] || 'yellow';
            }
            if(i==stop)
                track[i].isEnd = true;
        }
    },

    calcNTPositionForAA: function(ref_aa_id, analysis_id, pos){
        var aa = this.ref_aa_sequences[ref_aa_id];
        var ref_nt_id = aa.ref_nt_id;
        var exons = aa.exonMap;

        var nt_positions = [];
        var codonPos = 1;
        var nt_position = 3 * pos - 2; //the position of the first NT in this codon

        //these will track the NT position of the current protein
        var startNT = 1;
        var endNT = 0;
        var exonLength;
        var exonPosition = 0;
        var exon;
        for (var i=0;i<aa.exonMap.length;i++){
            exon = aa.exonMap[i];
            exonLength = exon[1] - exon[0] +  1;
            endNT = startNT + exonLength - 1;
            while(nt_position >= startNT && nt_position <= endNT){
                //find the corresponding position in this exon
                exonPosition = nt_position - startNT + exon[0];
                nt_positions.push(exonPosition);
                if(nt_positions.length == 3)
                    break;  //jump to next exon

                nt_position++;
            }

            startNT += exonLength;
        };

        var cover = [];
        var p;
        for (var i=0; i<nt_positions.length;i++){
            p = nt_positions[i];
            if(!this.coverageMap[analysis_id] ||
                !this.coverageMap[analysis_id][ref_nt_id] ||
                !this.coverageMap[analysis_id][ref_nt_id][p] ||
                !this.coverageMap[analysis_id][ref_nt_id][p][0] ||
                !this.coverageMap[analysis_id][ref_nt_id][p][0].adj_depth
            ){
                cover.push(0);
            }
            else {
                cover.push(this.coverageMap[analysis_id][ref_nt_id][p][0].adj_depth);
            }
        };

        return {
            nt_positions: nt_positions,
            cover: cover,
            avgCover: (cover[0] + cover[1] + cover[2]) / 3
        }
    },

    renderSnpQtip: function(analysis_id, ref_base, ref_id, pos, idx, snp){
        var style = 'class="snp-qtip"';
        var html = '<table '+style+'>' +
                '<tr '+style+'>' +
                '<th>AA Position</th>' +
                '<th>NT Position(s)</th>' +
                '<th>Ref AA</th>' +
                '<th>Codon</th>' +
                '<th>Translation</th>' +
                '<th>Depth</th>' +
                '<th>Reads</th>' +
                //'<th>Incomplete Codons</th>' +
                '<th>Adj Count</th>' +
                '<th>Adj Percent</th>' +
                //'<th>Include</th>' +
                '</tr>';

        var codons = [];
        for(var i in snp.codons){
            codons.push(snp.codons[i]);
            snp.codons[i].show = snp.aa_map[snp.codons[i].q_aa].show;
            snp.codons[i].codon = i;

        }
        codons.sort(function(a, b){
            if(a.show && !b.show)
                return -1;
            else if (b.show && !a.show)
                return 1;
            else {
                if(a.adj_count > b.adj_count)
                    return -1;
                else if(a.adj_count > b.adj_count)
                    return 1;
            }
            return 0;
        })

        var codon;
        for (var i=0;i<codons.length;i++){
            codon = codons[i];
            html += '<tr '+style+'>' +
                    '<td>'+(idx==0 ? pos : pos+'.'+idx)+'</td>' +
                    '<td>'+codon.ref_nt_positions+'</td>' +
                    '<td>'+(idx==0 ? ref_base : '-')+'</td>' +
                    '<td>'+codon.codon+'</td><td>'+codon.q_aa+'</td>' +
                    '<td>'+LABKEY.Utils.roundNumber(codon.depth, 2)+'</td><td>'+codon.adj_count+'</td>' +
                    //'<td>'+codon.incomplete_codons+'</td>' +
                    '<td>'+(codon.show ? '' : '<del>')+codon.adj_count+(codon.show ? '' : '</del>')+'</td>' +
                    '<td>'+(codon.show ? '' : '<del>')+(codon.adj_percent ? LABKEY.Utils.roundNumber(codon.adj_percent, 2) : '')+(codon.show ? '' : '</del>')+'</td>' +
                    //'<td>'+(codon.show ? 'x' : '')+'</td>' +
                    '</tr>';
        };

        html += '<tr><td>Total Non-WT</td><td></td><td></td><td></td><td></td><td></td><td></td><td>'+LABKEY.Utils.roundNumber(snp.adj_num_reads, 2)+'</td><td>'+(snp.adj_percent ? LABKEY.Utils.roundNumber(snp.adj_percent, 2) : '')+'</td>';
        html += '<tr><td>Total WT</td><td></td><td></td><td></td><td></td><td></td><td></td><td>'+LABKEY.Utils.roundNumber((snp.adj_depth - snp.adj_num_reads), 2)+'</td><td>'+(snp.adj_percent ? LABKEY.Utils.roundNumber(100-snp.adj_percent, 2) : '100')+'</td>';
        html += '</table>';
        html += '<i>**Strikethrough over the percent indicates this mutation is below the filter threshold. This is calculated per AA variation not <br>' +
                     'per codon, meaning individual rows can be below the threshold if multiple codons combine to reach the threshold.</i><br><br>';
        html += '<i>**To hide this tooltip click elsewhere on the screen.</i>'

        return html;
    }
});

