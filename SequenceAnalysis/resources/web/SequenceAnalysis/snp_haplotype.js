/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace('SequenceAnalysis.ext');  //, 'Ext.ux.form'

LABKEY.requiresExt4ClientAPI();

LABKEY.requiresScript('SequenceAnalysis/color-field.js');
LABKEY.requiresCss('SequenceAnalysis/color-field.css');
LABKEY.requiresScript('SequenceAnalysis/Utils.js');

SequenceAnalysis.ext.AlignmentConfigPanel = Ext.extend(Ext.FormPanel, {
    initComponent: function(){

        Ext.apply(this, {
            autoHeight: true
            ,title: 'Alignment Settings'
            ,width: '100%'
            ,bodyBorder: true
            ,border: true
            ,collapsible: true
            ,bodyStyle:'padding:5px 5px 5px 5px'
            ,defaultType: 'textfield'
            ,monitorValid: true
            ,defaults: {
                //border: false
                bodyStyle:'padding:5px 5px 5px 5px'
                ,width: 210
                ,msgTarget: 'side'
            }
            ,items: [{
                xtype:'fieldset'
                ,title: 'Reference Sequence'
                ,ref: 'refSequence'
                ,collapsible: true
                ,autoHeight:true
                ,width: 'auto'
                ,defaults: {width: 210}
                ,items :[{
                    xtype: 'combo',
                    ref: 'virusStrain',
                    fieldLabel: 'Virus Strain',
                    allowBlank: true,
                    mode: 'local',
                    triggerAction: 'all',
                    displayField: 'virus_strain',
                    valueField: 'virus_strain',
                    lazyRender: false,
                    //value: (this.allowableStrains.length==0 ? this.allowableStrains[0] : null),
                    store: new LABKEY.ext.Store({
                        schemaName: 'sequenceanalysis',
                        queryName: 'virus_strains',
                        filterArray: [LABKEY.Filter.create('virus_strain', this.allowableStrains.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
                        sort: 'virus_strain',
                        owner: this,
                        autoLoad: true,
                        listeners: {
                            load: function(s){
                                if(this.getCount() == 1){
                                    var val = this.getAt(0).get('virus_strain');
                                    this.owner.refSequence.virusStrain.setValue(val);
                                    this.owner.refSequence.virusStrain.fireEvent('change', this.owner.refSequence.virusStrain, val);
                                }
                            }
                        }
                    }),
                    listeners: {
                        scope: this,
                        change: function(combo, val){
                            if(!val) return;

                            var target = this.refSequence.proteins;
                            target.setDisabled(false);
                            target.setValue('');

                            var filter = LABKEY.Filter.create('ref_nt_id/subset', val, LABKEY.Filter.Types.EQUAL);
                            target.store.baseParams[filter.getURLParameterName('query')] = filter.getURLParameterValue();
                            target.store.load();

                            if(target.view)
                                target.view.setStore(target.store);
                        }
                    }
                },{
                    xtype: 'combo',
                    ref: 'proteins',
                    fieldLabel: 'Protein',
                    allowBlank: true,
                    mode: 'local',
                    triggerAction: 'all',
                    disabled: true,
                    displayField: 'name',
                    valueField: 'rowid',
                    //value: 1,
                    lazyRender: false,
                    store: new LABKEY.ext.Store({
                        schemaName: 'sequenceanalysis',
                        queryName: 'ref_aa_sequences',
                        columns: 'rowid,name,ref_nt_id',
                        sort: 'exons'
                    }),
                    listeners: {
                        scope: this,
                        change: function(btn){
                            this.ownerCt.segmentsChanged = true;
                        }
                    }
                },{
                    xtype: 'textfield',
                    maskRe: /[0-9;-]/,
                    fieldLabel: 'Segments',
                    ref: 'segments',
                    //value: '38-80;85-90',
                    helpPopup: 'This allows you to pick specific regions of the protein to display.  For a single region, enter in the format: 20-40.  If you want to include multple segments (ie. to inspect linkage), enter segments in the format: 20-40;45-50',
                    listeners: {
                        scope: this,
                        change: function(btn){
                            this.ownerCt.segmentsChanged = true;
                        }
                    }
                },{
                    xtype: 'displayfield',
                    value: 'For a single segment enter start/stop AA (ie. 45-50).  For multiple segments separate with semicolons (ie. 45-50;60-70)'
                }]
            },{
                xtype:'fieldset'
                ,title: 'Variant Filters'
                ,ref: 'variantFilters'
                ,hidden: false
                ,collapsible: true
                ,forceLayout: true
                ,collapsed: false
                ,autoHeight:true
                ,width: 'auto'
                ,items: [{
                    xtype: 'numberfield',
                    value: 1,
                    fieldLabel: 'Min Variant %',
                    ref: 'minVariantPct',
                    helpPopup: 'Only variants present above this level will be shown.  Others will be collapsed into the category \'other\'.'
                },{
                    xtype: 'numberfield',
                    value: 5,
                    fieldLabel: 'Min Variant Read Number',
                    ref: 'minVariantReadNum',
                    helpPopup: 'Only variants present at least this many reads will be shown.'
                },{
                    xtype: 'numberfield',
                    value: 5,
                    fieldLabel: 'Max Non-Covered Bases',
                    ref: 'maxNonCovered',
                    helpPopup: 'Any variant with more than the specified number of positions lacking coverage will be removed from this analysis.'
                },{
                    xtype: 'checkbox',
                    value: '',
                    hidden: true,
                    fieldLabel: 'Consider All Samples In Filter',
                    ref: 'considerAllSamples',
                    helpPopup: 'If checked, if a variant is above the thresholds in at least one of the samples, it will be displayed in every sample where it is present, even if it is not otherwise above the thresholds.'
                }]
            },{
                xtype: 'fieldset',
                ref: 'snpColoration',
                width: '100%',
                collapsible: true,
                collapsed: true,
                forceLayout: true,
                title: 'SNP Coloration',
                defaults: {border: false},
                border: true,
                labelWidth: 150,
                items: [{
                    fieldLabel: 'Non-synonymous SNPs',
                    labelWidth: 90,
                    xtype: 'colorfield',
                    ref: 'nsColor',
                    width: 100,
                    value: '#FFFF00'
                },{
                    fieldLabel: 'Synonymous SNPs',
                    labelWidth: 90,
                    xtype: 'colorfield',
                    ref: 'synColor',
                    width: 100,
                    value: '#00FF00'
                },{
                    fieldLabel: 'Frameshift Mutations',
                    border: false,
                    labelWidth: 90,
                    xtype: 'colorfield',
                    ref: 'fsColor',
                    width: 100,
                    value: '#FF0000'
                },{
                    fieldLabel: 'No Coverage',
                    border: false,
                    xtype: 'colorfield',
                    ref: 'nocoverColor',
                    width: 100,
                    value: '#C0C0C0'
                }]
            }]
            ,buttonAlign: 'left'
            ,buttons: [{
                text: 'Refresh Alignment'
                ,scope: this
                ,handler: function(btn){
                    this.ownerCt.doAlignment()
                }
            }]

        });

        SequenceAnalysis.ext.AlignmentConfigPanel.superclass.initComponent.call(this);
    }
});
Ext.reg('alignmentconfigpanel', SequenceAnalysis.ext.AlignmentConfigPanel);


Ext.QuickTips.init();

Ext.apply(Ext.QuickTips.getQuickTip(), {
    constrainPosition: true,
    dismissDelay: 0,
    autoHide: false,
    shadow: 'drop',
    closable: true,
    shadowOffset: 0
//   showDelay: 50,      // Show 50ms after entering target
//    trackMouse: true
});

SequenceAnalysis.ext.AlignmentDetailsPanel = Ext.extend(Ext.Panel, {
    initComponent: function(){


        Ext.apply(this, {
            autoHeight: true
            ,width: 'auto'
            ,bodyBorder: false
            ,border: false
            ,defaults: {
                border: false
                ,bodyStyle:'padding:5px 5px 5px 5px'
                ,width: 210
                ,msgTarget: 'side'
            }
            ,items: [{
                title: 'Analyses',
                width: '100%',
                style: 'padding-bottom:10px;',
                border: true,
                ref: 'sampleInfo',
                items: [{
                    tag: 'div',
                    border: false,
                    id: 'sampleInfoDiv',
                    html: 'Loading...'
                }]
            }]
        });

        SequenceAnalysis.ext.AlignmentDetailsPanel.superclass.initComponent.call(this);

        this.populateSamples();
        this.queryRefAA();

    },
    onSampleLoad: function(){
        this.add({
            xtype: 'alignmentconfigpanel',
            allowableStrains: this.allowableStrains,
            style: 'padding-bottom:10px;',
            ref: 'alignConfig'
        },{
            xtype: 'panel',
            ref: 'alignmentArea',
            title: 'Alignment',
            width: '100%',
            collapsible: true,
            style: 'padding-bottom:10px;',
            border: true,
            buttonAlign: 'left',
            //bodyBorder: false,
            autoHeight: true,
            items: [{
                tag: 'div',
                border: false,
                id: 'alignmentDiv'
            }]
        });
        this.doLayout();
    },
    doAlignment: function(){
        var values = this.alignConfig.getForm().getFieldValues(true);
        this.ref_aa_ids = this.alignConfig.refSequence.proteins.getValue();

        if(!this.ref_aa_ids){
            alert('Must pick one or more proteins');
            return;
        }

        //TODO: eventually support multiple proteins
        this.ref_aa_ids = [this.ref_aa_ids]; //.split(';');
        Ext.each(this.ref_aa_ids, function(e, idx){
            this.ref_aa_ids[idx] = Number(e);
        }, this);

        this.ref_nt_ids = [];

        this.alignConfig.refSequence.proteins.store.each(function(r){
            if(this.ref_aa_ids.indexOf(r.get('rowid')) != -1){
                this.ref_nt_ids.push(r.get('ref_nt_id'));
            }
        }, this);

        this.segments = this.parseSegments();

        if(!this.segments || !this.segments.length){
            Ext.Msg.hide();
            return;
        }

        this.queryDB();
    },
    parseSegments: function(){
        var segments = this.alignConfig.refSequence.segments.getValue();
        var ref_aa = this.alignConfig.refSequence.proteins.getValue();

        if(!segments){
            alert('Must enter more or more segments to view');
            return;
        }

        var newSegments = [];

        segments = segments.replace(/\s+/g, '');
        segments = segments.split(';');
        Ext.each(segments, function(segment){
            segment = segment.split('-');
            if(!segment || segment.length!=2){
                alert('Bad segment: ' + segment.join('-'));
                newSegments = null;
                return false;
            }
            segment[0] = Number(segment[0]);
            segment[1] = Number(segment[1]);
            if(isNaN(segment[0] || isNaN(segment[1]))){
                alert('Bad segment: ' + segment.join('-'));
                return false;
            }
            if(segment[0] < 1){
                alert('Segment start cannot be less than 1');
                return false;
            }

            var length = this.ref_aa_sequences[ref_aa].sequence.length;
            if(segment[1] > length){
                alert('Segment end cannot be greater than the protein length ('+length+')');
                return false;
            }

            if(segment[1] <= segment[0]){
                alert('Invalid start/stop corrdinates for segment: ' + segment.join('-'));
                newSegments = null;
                return false;
            }

            var nt_array = [];
            var exons = [];
            var start;
            var stop;
            var result;
            for (var i = segment[0];i <= segment[1]; i++){
                result = SequenceAnalysis.Utils.calcNTPositionForAA(this.ref_aa_sequences[ref_aa], i);
                nt_array.push(result.nt_positions);
                exons = exons.concat(result.exons);

                if(i == segment[0])
                    start = result.nt_positions[0];
                if(i == segment[1])
                    stop = result.nt_positions[2];
            }

            var borders = [];
            exons = Ext4.Array.unique(exons);
            if(exons.length == 1){
                borders.push([start, stop]);
            }
            else {
                var seg = [];
                Ext4.each(exons, function(e, idx){
                    var exon = this.ref_aa_sequences[ref_aa].exonMap[e];

                    if(idx == 0)
                        borders.push([start, exon[1]]);
                    else if(idx == exons.length - 1)
                        borders.push([exon[0], stop]);
                    else
                        borders.push([exon[0], exon[1]]);
                }, this);
            }
            exons = Ext4.Array.unique(exons);
            newSegments.push({
                aa_start: segment[0],
                aa_stop: segment[1],
                nt_start: start,
                nt_stop: stop,
                borders: borders,
                exons: exons,
                ref_aa: ref_aa,
                nt_positions: nt_array
            });
        }, this);

        return newSegments;
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
                    Ext.each(data.rows, function(r){
                        this.allowableStrains.push(r.strain);
                    }, this);

                    this.allowableStrains = Ext.unique(this.allowableStrains);

                    if(!this.allowableStrains.length)
                        alert(errorMsg);
                    else
                        this.onSampleLoad();
                }
                else {
                    alert(errorMsg);
                }
            }
        });

        LABKEY.Query.selectRows({
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_analyses',
            containerPath: Laboratory.Utils.getQueryContainerPath(),
            includeTotalCount: false,
            columns: 'rowid,readset,readset/name,ref_nt_id,sampleid,readset/sampleid/samplename,sampleid/sampledate,type',
            filterArray: [LABKEY.Filter.create('rowid', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            scope: this,
            success: function(data){
                if(data.rows && data.rows.length){
                    var sampleIds = [];
                    this.analysesRecords = {};
                    Ext.each(data.rows, function(r){
                        if(r.readset)
                            sampleIds.push(r.readset);
                        this.analysesRecords[r.rowid] = r;
                    }, this);

                    if(sampleIds.length){
                        LDK.Utils.getReadOnlyQWP({
                            schemaName: 'sequenceanalysis',
                            queryName: 'sequence_analyses',
                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                            filterArray: [LABKEY.Filter.create('rowid', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
                            sort: 'rowid'
                         }).render('sampleInfoDiv');
                    }
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },
    queryRefAA: function(){
        LABKEY.Query.selectRows({
            schemaName: 'SequenceAnalysis',
            queryName: 'ref_aa_sequences',
            includeTotalCount: false,
            columns: 'rowid,name,exons,sequence,ref_nt_id',
            scope: this,
            success: function(data){
                if(data && data.rows){
                    this.ref_aa_sequences = {};
                    Ext.each(data.rows, function(r){
                        this.ref_aa_sequences[r.rowid] = r;

                        var exons = r.exons.split(';');
                        r.exonMap = Ext4.Array.map(exons, function(i){
                            var a = i.split('-');
                            return [Number(a[0]), Number(a[1])]
                        });
                    }, this);
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },
    queryDB: function(){
        //only reload data if segments have changed
        if(this.segmentsChanged === false){
            this.onLoadComplete();
            return;
        }

        Ext.Msg.wait('Fetching Data...');
        Ext.Ajax.timeout = 0;

        var whereClauses = this.generateWhereClause();
        var id_clause = ' AND analysis_id IN ('+this.analysisIds.join(',')+')';

        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.executeSql, {
            schemaName: 'SequenceAnalysis',
            sql: 'SELECT analysis_id, alignment_id, q_aa, q_codon, ref_aa, ref_aa_id, ref_aa_insert_index, ref_aa_position, ref_nt_id ' +
                'FROM sequenceanalysis.aa_snps ' +
                'WHERE '+whereClauses.aa_clause + id_clause + ' AND ref_aa_id IN ('+this.ref_aa_ids.join(',')+')',
            timeout: 0,
            includeTotalCount: false,
            scope: this,
            success: function(data){
                this.snps = {};
                this.inserts = {};

                Ext.each(data.rows, function(row){
                    if(!this.snps[row.analysis_id]){
                        this.snps[row.analysis_id] = {};
                    }
                    if(!this.snps[row.analysis_id][row.ref_aa_id]){
                        this.snps[row.analysis_id][row.ref_aa_id] = {refName: this.ref_aa_sequences[row.ref_aa_id].name, snps: {}};
                        this.inserts[row.ref_aa_id] = this.inserts[row.ref_aa_id] || {};
                    }
                    if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position]){
                        this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position] = {};
                        this.inserts[row.ref_aa_id][row.ref_aa_position] = this.inserts[row.ref_aa_id][row.ref_aa_position] || {};
                    }
                    if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index]){
                        this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index] = {}
                    }
                    if(!this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index][row.alignment_id]){
                        this.snps[row.analysis_id][row.ref_aa_id]['snps'][row.ref_aa_position][row.ref_aa_insert_index][row.alignment_id] = row;
                    }

                    if(Number(row.ref_aa_insert_index) > 0){
                        if(!this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index])
                            this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index] = {};
                        if(!this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index][row.analysis_id])
                            this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index][row.analysis_id] = {};

                        this.inserts[row.ref_aa_id][row.ref_aa_position][row.ref_aa_insert_index][row.analysis_id][row.alignment_id] = row;
                    }
                }, this);
            },
            failure: LDK.Utils.getErrorCallback()
        });

        var align_sql = 'SELECT rowid, analysis_id, read_id, ref_nt_id, readname, orientation, q_start, q_stop, ref_start, ref_stop, num_mismatches ' +
            'FROM sequenceanalysis.sequence_alignments ' +
            'WHERE '+whereClauses.alignment_clause + id_clause + ' AND ref_nt_id IN ('+this.ref_nt_ids.join(',')+')';

        multi.add(LABKEY.Query.executeSql, {
            schemaName: 'SequenceAnalysis',
            includeTotalCount: false,
            sql: align_sql,
            filterArray: [LABKEY.Filter.create('analysis_id', this.analysisIds.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            sort: 'ref_start',
            scope: this,
            success: function(data){
                if(data && data.rows){
                    this.alignmentMap = {};
                    this.readSummary = {};
                    Ext.each(data.rows, function(r){
                        if(!this.alignmentMap[r.analysis_id]){
                            this.alignmentMap[r.analysis_id] = {};
                            this.readSummary[r.analysis_id] = 0;
                        }

                        this.readSummary[r.analysis_id]++;

                        if(!this.alignmentMap[r.analysis_id])
                            this.alignmentMap[r.analysis_id] = {};

                        if(!this.alignmentMap[r.analysis_id][r.readname])
                            this.alignmentMap[r.analysis_id][r.readname] = {};

                        this.alignmentMap[r.analysis_id][r.readname][r.rowid] =r;

                    }, this);
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });

        this.startTime = new Date();
        multi.send(this.onLoadComplete, this);
    },
    generateWhereClause: function(){
        var nt_clause = [];
        var aa_clause = [];
        var alignment_clause = [];
        var nts;
        var start;
        var stop;
        Ext4.each(this.segments, function(segment){
            aa_clause.push('ref_aa_position >= '+segment.aa_start+' AND ref_aa_position <= '+segment.aa_stop);

            Ext4.each(segment.borders, function(border){
                nt_clause.push('ref_nt_position >= '+border[0]+' AND ref_nt_position <= '+border[1]);
                alignment_clause.push(' ('+
                    '(ref_start >= '+border[0]+' AND ref_start <= '+border[1] + ') OR ' +
                    '(ref_stop >= '+border[0]+' AND ref_stop <= '+border[1] + ') OR ' +
                    '(ref_start <= '+border[0]+' AND ref_stop >= '+border[1] + ') ' +
                    ') '
                );
            }, this);
        }, this);

        aa_clause = '(' + aa_clause.join(' OR ') + ')';
        nt_clause = '(' + nt_clause.join(' OR ') + ')';
        alignment_clause = '(' + alignment_clause.join(' OR ') + ')';

        return {
            aa_clause: aa_clause,
            nt_clause: nt_clause,
            alignment_clause: alignment_clause
        }
    },
    onLoadComplete: function(){
        Ext.Msg.hide();
        var loadTime = new Date();
        this.segmentsChanged = false;

        console.log('Load Time: '+(loadTime - this.startTime)/1000 + ' seconds');

        if(!this.snps){
            alert('Problem loading data');
            return;
        }

        this.buildSequences();
        this.filterSequences();
        this.buildRefPositionMap();

        var target = Ext.get('alignmentDiv');
        target.update('');

        this.renderAlignments(target);

        console.log('Processing Time: '+((new Date()) - loadTime)/1000 + ' seconds');
    },
    buildRefPositionMap: function(){
        this.refPositions = {};
        var insertionMap = {};
        var seqMap = {};

        Ext4.each(this.segments, function(segment, i){
            for(var pos=segment.aa_start;pos<=segment.aa_stop;pos++){
                seqMap[pos] = {0: ''};
            }
        }, this);

        //find all insertions in filtered set:
        Ext4.each(this.allsequences.readsToShow, function(seqObj){
            Ext4.Object.merge(insertionMap, seqObj.inserts);
            Ext4.Object.merge(seqMap, seqObj.map);
        }, this);

        var ref_id = this.ref_aa_ids[0];
        var aa_ref;
        var residue;
        this.refPositions[ref_id] = {};
        this.refSequences = {};
        this.refSequences[ref_id] = '';

        Ext4.each(this.segments, function(segment, i){
            aa_ref = this.ref_aa_sequences[segment.ref_aa];
            for(var pos=segment.aa_start;pos<=segment.aa_stop;pos++){
                for(var idx in seqMap[pos]){
                    this.refPositions[ref_id][pos] = this.refPositions[ref_id][pos] || {};
                    if(idx == 0)
                        residue = aa_ref.sequence.substr((pos-1), 1);
                    else
                        residue = '-';

                    this.refSequences[ref_id] += '<span '+(idx!=0 ? ' style="background-color:#C0C0C0;"' : '')+' ext:qtip="Reference: '+aa_ref.name+'<br>Position: '+(idx==0 ? pos : pos+'.'+idx)+'">'+residue+'</span>';
                    this.refPositions[ref_id][pos][idx] = residue;
                }
            }

            if(i+1 < this.segments.length){
                this.refSequences[ref_id] += '/';
            }
        }, this);
    },
    buildSequences: function(){
        this.querysequences = {};
        this.allsequences = {};

        var sequenceObj;
        var maxNonCovered = this.alignConfig.variantFilters.maxNonCovered.getValue();
        Ext.each(this.analysisIds, function(analysis_id){
            this.querysequences[analysis_id] = {sequences: {}, totalReads: 0, discardedReads: 0, other: {
                pct: 0,
                total: 0,
                reads: {}
            }};
            for(var readname in this.alignmentMap[analysis_id]){
                sequenceObj = this.buildSequence(analysis_id, readname);

                if(!Ext.isEmpty(maxNonCovered)){
                    var match = sequenceObj.sequence.match(/:/g);
                    if(match && match.length > maxNonCovered){
                        this.querysequences[analysis_id].discardedReads++;
                        continue;
                    }
                }

                if(!this.querysequences[analysis_id].sequences[sequenceObj.sequence])
                    this.querysequences[analysis_id].sequences[sequenceObj.sequence] = {reads: {}, total: 0, percent: 0, map: sequenceObj.map, inserts: sequenceObj.inserts};

                if(!this.allsequences[sequenceObj.sequence])
                    this.allsequences[sequenceObj.sequence] = {total: 0, maxPercent: 0, maxReads: 0, map: sequenceObj.map, inserts: sequenceObj.inserts};

                this.querysequences[analysis_id].sequences[sequenceObj.sequence]['reads'][readname] = sequenceObj;
                this.querysequences[analysis_id].sequences[sequenceObj.sequence].total++;
                this.querysequences[analysis_id].totalReads++;
                this.allsequences[sequenceObj.sequence].total++;
            }

            //calculate percents
            var pct;
            for(var sequence in this.querysequences[analysis_id].sequences){
                pct = 100 * (this.querysequences[analysis_id].sequences[sequence].total / this.querysequences[analysis_id].totalReads);

                //pct = LABKEY.Utils.roundNumber(pct, 2);
                this.querysequences[analysis_id].sequences[sequence].percent = pct;

                if(pct > this.allsequences[sequence].maxPercent)
                    this.allsequences[sequence].maxPercent = pct;

                if(this.querysequences[analysis_id].sequences[sequence].total > this.allsequences[sequence].maxReads)
                    this.allsequences[sequence].maxReads = this.querysequences[analysis_id].sequences[sequence].total;
            }

        }, this);

    },
    buildSequence: function(analysis_id, readname){
        var aa_ref;
        var sequence = '';
        var map = {};
        var inserts = {};
        var base;
        var nt_positions;
        var alignments = this.alignmentMap[analysis_id][readname];
        Ext4.each(this.segments, function(segment, i){
            aa_ref = this.ref_aa_sequences[segment.ref_aa];
            for(var pos=segment.aa_start;pos<=segment.aa_stop;pos++){
                base = '';
                map[pos] = {};
                for(var alignment_id in alignments){
                    for(var idx in this.snps[analysis_id][segment.ref_aa]['snps'][pos]){
                        if(this.snps[analysis_id][segment.ref_aa]['snps'][pos] &&
                            this.snps[analysis_id][segment.ref_aa]['snps'][pos][idx] &&
                            this.snps[analysis_id][segment.ref_aa]['snps'][pos][idx][alignment_id]
                        ){
                            base += this.snps[analysis_id][segment.ref_aa]['snps'][pos][idx][alignment_id].q_aa;
                            map[pos][idx] = this.snps[analysis_id][segment.ref_aa]['snps'][pos][idx][alignment_id].q_aa;
                            if(idx > 0){
                                inserts[pos] = inserts[pos] || {};
                                inserts[pos][idx] = this.snps[analysis_id][segment.ref_aa]['snps'][pos][idx][alignment_id].q_aa;
                            }
                        }
                    }
                }

                if(!base){
                    var covered = 0;
                    nt_positions = segment.nt_positions[pos - segment.aa_start];
                    Ext.each(nt_positions, function(nt){
                        for(var alignment in alignments){
                            if(alignments[alignment].ref_start <= nt && alignments[alignment].ref_stop >= nt)
                                covered++;
                        }
                    }, this);

                    if(covered == 3)
                        base = '.';
                }
                base = base || ':';

                if(!map[pos][0])
                    map[pos][0] = base;

                sequence += base;
            }

            //add divider between segments
            if(i+1 < this.segments.length){
                sequence += '/';
            }
        }, this);

        return {
            sequence: sequence,
            map: map,
            inserts: inserts
        };

    },
    filterSequences: function(){
        var minVariantPct = this.alignConfig.variantFilters.minVariantPct.getValue();
        var minVariantReadNum = this.alignConfig.variantFilters.minVariantReadNum.getValue();
        var considerAllSamples = this.alignConfig.variantFilters.considerAllSamples.getValue();
        var maxPct;
        var maxReadNum;

        this.allsequences.readsToShow = {};

        //determine which sequences to include:
        for (var analysis_id in this.querysequences){
            this.querysequences[analysis_id].readsToShow = [];

            for(var sequence in this.querysequences[analysis_id].sequences){
                var include = true;
                if(considerAllSamples){
                    maxPct = this.allsequences[sequence].maxPercent;
                    maxReadNum = this.allsequences[sequence].maxReads;
                }
                else {
                    maxPct = this.querysequences[analysis_id].sequences[sequence].percent;
                    maxReadNum = this.querysequences[analysis_id].sequences[sequence].total;
                }

                if(minVariantPct && maxPct < minVariantPct)
                    include = false;
                if(minVariantReadNum && maxReadNum < minVariantReadNum)
                    include = false;

                if(include){
                    this.allsequences.readsToShow[sequence] = this.allsequences[sequence];
                    this.allsequences[sequence].sequence = sequence;
                    this.querysequences[analysis_id].readsToShow.push({sequence: sequence, percent: this.querysequences[analysis_id].sequences[sequence].percent});
                }
                else {
                    this.querysequences[analysis_id].other.pct += this.querysequences[analysis_id].sequences[sequence].percent;
                    this.querysequences[analysis_id].other.total += this.querysequences[analysis_id].sequences[sequence].total;
                    this.querysequences[analysis_id].sequences[sequence].isSkipped = true;
                    this.querysequences[analysis_id].other.reads[sequence] = this.querysequences[analysis_id].sequences[sequence];
                }
            }

            this.querysequences[analysis_id].readsToShow = this.querysequences[analysis_id].readsToShow.sort(function(a,b){
                return a.percent > b.percent ? -1 :
                        a.percent < b.percent ? 1 : 0;
            });
        }

        //sort total readset
        this.allsequences.readsToShow = Ext4.Object.getValues(this.allsequences.readsToShow).sort(function(a,b){
            return a.maxPercent > b.maxPercent ? -1 :
                    a.maxPercent < b.maxPercent ? 1 : 0;
        });
    },
    buildDisplaySequence: function(sequence, sample){
        var obj = [];
        var snp_obj;
        var q_aa;
        var residue;
        var ref_id;

        Ext4.each(this.segments, function(segment, i){
            ref_id = this.ref_aa_sequences[segment.ref_aa].rowid;
            for(var pos=segment.aa_start;pos<=segment.aa_stop;pos++){
                //iterate the ref map so we only include the correct indels
                for(var idx in this.refPositions[ref_id][pos]){
                    residue = sample.map[pos][idx] || (idx==0 ? ':' : '-');

                    if(residue == '.' || residue == '/'){
                        obj.push({
                            tag: 'span',
                            html: residue
                        });
                    }
                    else {
                        obj.push({
                            tag: 'span',
                            html: residue,
                            style: this.getSnpStyle(residue, ref_id, pos, idx),
                            'ext:qtip': 'Position: ' + pos + (idx>0 ? '.'+idx : ''),
                            'ext:qwidth': 'auto',
                            'ext:qclass': 'snp-qtip-border'
                            //'ext:hide': "user"
                        });
                    }
                }
            }

            //add divider between segments
            if(i+1 < this.segments.length){
                obj.push({
                    tag: 'span',
                    html: '/'
                });
            }
        }, this);

        return obj;
    },
    getSnpStyle: function(residue, ref_id, pos, idx){
        var refAA = this.refPositions[ref_id][pos][idx];
        var colorPanel = this.alignConfig.snpColoration;
        var color;

        if(residue == ':'){
            color = colorPanel.nocoverColor.getValue();
        }
        else if(residue == '-' || residue == '.'){
            color = 'transparent';
        }
        else if(residue == refAA && residue != '-'){
            color = colorPanel.synColor.getValue();
        }
        else if (refAA == '-' || residue == '+' || residue == '?'){
            color = colorPanel.fsColor.getValue();
        }
        else {
            color = colorPanel.nsColor.getValue();
        }

        return 'background-color: '+color+';'
    },
    renderAlignments: function(target){
        Ext.each(this.analysisIds, function(analysis_id){
            var config = this.renderAlignment(analysis_id);

            target.createChild({
                tag: 'h3',
                html: this.getAnalysisTitle(analysis_id)
            });
            target.createChild(config);
        }, this);

        this.renderSummary(target);
    },
    renderAlignment: function(analysis_id){
        var ref_id = this.ref_aa_ids[0];
        var config = {
            tag: 'table',
            border: 1,
            style: 'border-collapse:collapse;',
            children: [{
                tag: 'tr',
                children: [{
                    tag: 'td',
                    style: 'white-space:nowrap;padding:5px;font-weight:bold;',
                    html: this.ref_aa_sequences[this.ref_aa_ids[0]].name + ': '+ this.alignConfig.refSequence.segments.getValue()
                },{
                    tag: 'td',
                    style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                    html: this.refSequences[ref_id]
                }]
            }]
        };

        var obj;
        var seq;
        Ext.each(this.querysequences[analysis_id].readsToShow, function(seqObj){
            seq = this.buildDisplaySequence(seqObj.sequence, this.querysequences[analysis_id].sequences[seqObj.sequence]);
            obj = this.querysequences[analysis_id];
            config.children.push({
                tag: 'tr',
                children: [
                    this.renderReadName(analysis_id, obj.sequences[seqObj.sequence], obj.totalReads)
                ,{
                    tag: 'td',
                    style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                    children: seq
                }]
            });
        }, this);

        //include other
        if(this.querysequences[analysis_id].other.pct){
            this.querysequences[analysis_id].other.pct = this.querysequences[analysis_id].other.pct;
            config.children.push({
                tag: 'tr',
                children: [{
                    tag: 'td',
                    style: 'padding: 5px;',
                    html: 'Percent: '+LABKEY.Utils.roundNumber(this.querysequences[analysis_id].other.pct, 2)+', Reads: '+this.querysequences[analysis_id].other.total,
                    //'ext:hide': "user",
                    'ext:qtip': [
                        'Total Reads: ' + this.querysequences[analysis_id].totalReads,
                        'Distinct Variants: ' + Ext4.Object.getKeys(this.querysequences[analysis_id].other.reads).length,
                        'Analysis Id: ' + analysis_id,
                        'Readset: ' + this.analysesRecords[analysis_id]['readset/name'] + ' (' + this.analysesRecords[analysis_id]['readset'] + ')',
                        (this.analysesRecords[analysis_id]['readset/sampleid/samplename'] ? 'Sample Name: ' + this.analysesRecords[analysis_id]['readset/sampleid/samplename'] : ''),
                        (this.analysesRecords[analysis_id]['readset/sampleid/sampledate'] ? 'Sample Date: ' + this.analysesRecords[analysis_id]['readset/sampleid/sampledate'] : '')
                    ].join('<br>')
                },{
                    tag: 'td',
                    style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                    children: {
                        html: 'Other'
                    }
                }]
            });
        }
        return config;
    },
    renderReadName: function(analysisId, seqInfo, total){
        return {
            tag: 'td',
            style: 'padding: 5px;',
            html: 'Percent: '+LABKEY.Utils.roundNumber(seqInfo.percent, 2)+', Reads: '+seqInfo.total,
            //'ext:hide': "user",
            'ext:qtip': [
                'Total Reads: ' + total,
                'Analysis Id: ' + analysisId,
                'Readset: ' + this.analysesRecords[analysisId]['readset/name'] + ' (' + this.analysesRecords[analysisId]['readset'] + ')',
                (this.analysesRecords[analysisId]['readset/sampleid/samplename'] ? 'Sample Name: ' + this.analysesRecords[analysisId]['readset/sampleid/samplename'] : null),
                (this.analysesRecords[analysisId]['readset/sampleid/sampledate'] ? 'Sample Date: ' + this.analysesRecords[analysisId]['readset/sampleid/sampledate'] : '')
            ].join('<br>')
        }
    },
    renderSummary: function(target){
        var ref_id = this.ref_aa_ids[0];
        var header = {
            tag: 'tr',
            children: [{
                tag: 'td',
                style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                html: this.refSequences[ref_id]
            }]
        };

        var totalsRow = {
            tag: 'tr',
            children: [{
                tag: 'td',
                style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                html: 'Total Reads'
            }]
        }

        Ext.each(this.analysisIds, function(analysis_id){
            header.children.push({
                tag: 'td',
                style: 'text-align:center;padding:5px;',
                html: this.getAnalysisTitle(analysis_id)
            });
            totalsRow.children.push({
                tag: 'td',
                style: 'text-align:center;padding:5px;',
                html: this.querysequences[analysis_id].totalReads
            });
        }, this);

        var config = {
            tag: 'table',
            border: 1,
            style: 'border-collapse:collapse;',
            children: [header]
        };

        var obj;
        var seq;
        var row;
        var pct;
        var reads;
        Ext.each(this.allsequences.readsToShow, function(seqObj){
            row = {
                tag: 'tr',
                children: [{
                    tag: 'td',
                    style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                    children: this.buildDisplaySequence(seqObj.sequence, seqObj)
                }]
            }

            Ext.each(this.analysisIds, function(analysis_id){
                obj = this.querysequences[analysis_id].sequences[seqObj.sequence];
                //NOTE: a read can be skipped during each the display of each analysis, but it might have been included in the summary
                // because it was other the threshold in some other sample.  this throws off the 'other' category, so we recalc here
                if(obj && obj.isSkipped){
                    this.querysequences[analysis_id].other.pct -= obj.percent;
                    this.querysequences[analysis_id].other.total -= obj.total;
                    delete this.querysequences[analysis_id].other.reads[seqObj.sequence];
                }
                pct = obj ? LABKEY.Utils.roundNumber(obj.percent, 2) : '-';
                reads = obj ? 'Reads: ' + obj.total + '<br>Depth: '+this.querysequences[analysis_id].totalReads : 'N/A';

                row.children.push({
                    tag: 'td',
                    style: 'text-align:center;',
                    'ext:qtip': reads,
                    //'ext:hide': "user",
                    html: pct
                });
            }, this);

            config.children.push(row);
        }, this);

        //include other
        row = {
            tag: 'tr',
            children: [{
                tag: 'td',
                style: 'font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;',
                html: 'Other'
            }]
        }

        Ext.each(this.analysisIds, function(analysis_id){
            for(var seq in this.querysequences[analysis_id].sequences){
                if(this.allsequences.readsToShow.indexOf(seq) != -1){
                    console.log(this.querysequences[analysis_id].sequences[seq]);
                }
            }

            row.children.push({
                tag: 'td',
                style: 'text-align:center;',
                'ext:qtip': this.querysequences[analysis_id].other.total ? 'Reads: ' + this.querysequences[analysis_id].other.total + '<br>Depth: '+this.querysequences[analysis_id].totalReads + '<br>Distinct Variants: ' + Ext4.Object.getKeys(this.querysequences[analysis_id].other.reads).length : 'N/A',
                //'ext:hide': "user",
                html: this.querysequences[analysis_id].other.pct ? LABKEY.Utils.roundNumber(this.querysequences[analysis_id].other.pct, 2) : '-'
            });
        }, this);

        config.children.push(row);
        config.children.push(totalsRow);

        target.createChild({
            tag: 'h3',
            html: 'Summary of ' + this.getSegmentDisplayString(this.ref_aa_ids[0])
        });
        target.createChild(config);

    },
    getAnalysisTitle: function(analysis_id){
        return this.analysesRecords[analysis_id]['readset/name'] + ' (' + analysis_id + ')';
    },
    getSegmentDisplayString: function(aa_ref){
        return this.ref_aa_sequences[aa_ref].name + ': '+ this.alignConfig.refSequence.segments.getValue()
    }
});

