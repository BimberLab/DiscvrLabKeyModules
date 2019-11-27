/**
 * Mixin to provide a `defaultFeatureDetail` method that is optimized
 * for displaying variant data from VCF files.
 */

define([
            'dojo/_base/declare',
            'dojo/_base/array',
            'dojo/_base/lang',
            'dojo/dom-construct',
            'dojo/dom-class',
            'dojo/promise/all',
            'dojo/when',
            'dojo/json',
            'JBrowse/Util',
            'JBrowse/View/Track/_FeatureDetailMixin',
            'JBrowse/View/Track/_NamedFeatureFiltersMixin',
            'JBrowse/Model/NestedFrequencyTable',
            'dstore/Memory',
            'dgrid/OnDemandGrid',
            'dgrid/extensions/DijitRegistry'
        ],
        function(
                declare,
                array,
                lang,
                domConstruct,
                domClass,
                all,
                when,
                JSON,
                Util,
                FeatureDetailMixin,
                NamedFeatureFiltersMixin,
                NestedFrequencyTable,
                MemoryStore,
                DGrid,
                DGridDijitRegistry
        ) {

            // make a DGrid that registers itself as a dijit widget
            var Grid = declare([DGrid,DGridDijitRegistry]);

            return declare( [FeatureDetailMixin, NamedFeatureFiltersMixin], {
                constructor: function(args) {
                    this.setupFields()
                },

                FIELDS: {
                    ANN: {
                        title: 'Predicted Impact (SnpEff)'
                    },
                    CADD_PH: {
                        title: 'CADD Functional Prediction Score'
                    },
                    ENC: {
                        title: 'ENCODE Class'
                    },
                    ENN: {
                        title: 'ENCODE Feature Name'
                    },
                    ENCDNA_SC: {
                        title: 'ENCODE DNase Sensitivity Score'
                    },
                    ENCTFBS_SC: {
                        title: 'ENCODE Transcription Factor Binding Site Score'
                    },
                    ENCTFBS_TF: {
                        title: 'ENCODE Transcription Factors'
                    },
                    ENCSEG_NM: {
                        title: 'ENCODE Segmentation Status'
                    },
                    FS_EN: {
                        title: 'Funseq Annotated?'
                    },
                    FS_NS: {
                        title: 'Funseq Noncoding Score',
                        range: [0,5.4]
                    },
                    FS_SN: {
                        title: 'Funseq Sensitive Region?'
                    },
                    FS_TG: {
                        title: 'Funseq Target Gene?'
                    },
                    FS_US: {
                        title: 'Funseq Ultra-Sensitive Region?'
                    },
                    FS_WS: {
                        title: 'Funseq Score'
                    },
                    FS_SC: {
                        title: 'Funseq Non-coding Score',
                        range: [0,6]
                    },
                    SF: {
                        title: 'Swiss Prot Protein Function'
                    },
                    SX: {
                        title: 'Swiss Prot Expression'
                    },
                    SD: {
                        title: 'Swiss Prot Disease Annotations'
                    },
                    SM: {
                        title: 'Swiss Prot Post-translational Modifications'
                    },
                    RFG: {
                        title: 'Mutation Type (RefSeq)'
                    },
                    PC_PL: {
                        title: 'Conservation Score Among 46 Placental Mammals (PhastCons)'
                    },
                    PC_PR: {
                        title: 'Conservation Score Among 46 Primates (PhastCons)'
                    },
                    PC_VB: {
                        title: 'Conservation Score Among 100 Vertebrate Species (PhastCons)'
                    },
                    PP_PL: {
                        title: 'Conservation Score Among 46 Placental Mammals (Phylop)'
                    },
                    PP_PR: {
                        title: 'Conservation Score Among 46 Primates (Phylop)'
                    },
                    PP_VB: {
                        title: 'Conservation Score Among 100 Vertebrate Species (Phylop)'
                    },
                    RDB_WS: {
                        title: 'RegulomeDB Score',
                        range: [1,6]
                    },
                    RDB_MF: {
                        title: 'RegulomeDB Motifs'
                    },
                    MAF: {
                        title: 'Minor Allele Frequency'
                    },
                    OMIMN: {
                        title: 'OMIM Number'
                    },
                    OMIMM: {
                        title: 'OMIM Method'
                    },
                    OMIMC: {
                        title: 'OMIM Comments'
                    },
                    OMIMMUS: {
                        title: 'OMIM Mouse Correlate'
                    },
                    OMIMD: {
                        title: 'OMIM Disorders'
                    },
                    OMIMS: {
                        title: 'OMIM Status'
                    },
                    OMIMT: {
                        title: 'OMIM Title'
                    },
                    GRASP_PH: {
                        title: 'GRASP Phenotype'
                    },
                    GRASP_AN: {
                        title: 'GRASP Ancestry'
                    },
                    GRASP_P: {
                        title: 'GRASP p-value'
                    },
                    GRASP_PL: {
                        title: 'GRASP Platform'
                    },
                    GRASP_PMID: {
                        title: 'GRASP PMID'
                    },
                    GRASP_RS: {
                        title: 'GRASP SNP RS Number'
                    },
                    ERBCTA_NM: {
                        title: 'Ensembl Regulatory Build Predicted State'
                    },
                    LF: {
                        title: 'Unable to Lift to Human'
                    },
                    NE: {
                        title: 'PolyPhen2 Score'
                    },
                    NF: {
                        title: 'PolyPhen2 Prediction'
                    },
                    NG: {
                        title: 'nsdb LRT Score'
                    },
                    NH: {
                        title: 'nsdb LRT Prediction'
                    },
                    NK: {
                        title: 'MutationAssessor Score'
                    },
                    NL: {
                        title: 'MutationAssessor Prediction'
                    },
                    NC: {
                        title: 'nsdb SIFT Score'
                    },
                    NJ: {
                        title: 'MutationTaster Prediction'
                    },
                    LOF: {
                        title: 'Predicted Loss-of-function'
                    },
                    FC: {
                        title: 'Probable Promoter (FAMTOM5)'
                    },
                    FE: {
                        title: 'Probable Enhancer (FANTOM5)'
                    },
                    TMAF: {
                        title: '1000Genomes Allele Frequency'
                    },
                    CLN_SIG: {
                        title: 'ClinVar Significance'
                    },
                    CLN_DN: {
                        title: 'ClinVar Disease/Concept'
                    },
                    CLN_DNINCL: {
                        title: 'ClinVar Disease/Concept'
                    },
                    CLN_ALLELE: {
                        title: 'ClinVar Allele'
                    },
                    CLN_ALLELEID: {
                        title: 'ClinVar Allele ID'
                    },
                    CLN_VI: {
                        title: 'ClinVar Sources'
                    },
                    CLN_DBVARID: {
                        title: 'ClinVar dbVAR Accessions'
                    },
                    CLN_GENEINFO: {
                        title: 'ClinVar Gene(s)'
                    },
                    CLN_RS: {
                        title: 'ClinVar RS Numbers'
                    },
                    LiftedContig: {
                        title: 'Lifted Position'
                    }
                },

                setupFields: function(){
                    for (var field in this.FIELDS){
                        if (this.FIELDS[field].title){
                            var title = this.FIELDS[field].title;
                            this.config['fmtDetailField_' + field] = this.getTitleFn(title);
                        }
                    }
                },

                getTitleFn: function(title){
                    return function(){
                        return title;
                    }
                },

                defaultFeatureDetail: function( /** JBrowse.Track */ track, /** Object */ f, /** HTMLElement */ featDiv, /** HTMLElement */ container) {
                    var thisB = this;

                    container = container || domConstruct.create('div', { className: 'popupcontainer', innerHTML: '' } );

                    // genotypes in a separate section
                    var genotypes = f.get('genotypes');

                    this._renderCoreDetails( track, f, featDiv, container);

                    this._renderAdditionalTagsDetail( track, f, featDiv, container );

                    thisB._renderCalledFraction( track, f, featDiv, container, genotypes );

                    thisB._renderGenotypes( container, track, f, featDiv, genotypes );

                    return container;
                },

                _renderCoreDetails: function( track, f, featDiv, container ) {
                    var coreDetails = dojo.create('div', { className: 'core' }, container );
                    var fmt = dojo.hitch( this, 'renderDetailField', coreDetails );
                    coreDetails.innerHTML += '<h2 class="sectiontitle">Variant Details</h2>';

                    fmt( 'Name', this.getFeatureLabel( f ),f );
                    fmt( 'Variant', this.getFeatureDescription( f ),f );
                    fmt(
                            'Position',
                            Util.assembleLocString({ start: f.get('start'),
                                end: f.get('end'),
                                ref: this.refSeq.name,
                                strand: f.get('strand')
                            }),f
                    );

                    var filters = f.get('filter');
                    if (!this._isPassing(f)){
                        fmt( 'Filters', filters, f );
                    }

                    if (this.config.ensemblId){
                        var contig = this.refSeq.name;
                        contig = contig.replace(/^chr/, '');
                        contig = contig.replace(/^0/, '');
                        var pos = contig + ':' + f.get('start') + '-' + f.get('end');

                        var ensemblUrl = this.config.ensemblUrl || 'www.ensembl.org';
                        var url = 'https://' + ensemblUrl + '/' + this.config.ensemblId +'/Location/View?db=core;r=' + pos;
                        fmt('External Links', '<a href="' + url +'" target="_blank">View Region In Ensembl</a>', f);
                    }

                    //fmt( 'Length', Util.addCommas(f.get('end')-f.get('start'))+' bp',f );
                },

                //marks tags to be skipped from attributes table
                _isReservedTag: function( t ) {
                    return this.inherited(arguments) || {description:1, reference_allele: 1, alternative_alleles: 1, seq_id: 1, filter:1}[t.toLowerCase()];
                },

                parseOmimd: function(val){
                    var ret = [];

                    var tokens = val.split(/\)(,(?!_[0-9]+)|$)/);
                    for (var i=0;i<tokens.length;i++){
                        var token = tokens[i];
                        if (!token || token === ',') {
                            continue;
                        }

                        var split = token.split(/(_){0,1}\([0-9]+$/);
                        if (!split.length) {
                            continue;
                        }

                        token = split[0];

                        var id = '';
                        var name = token;
                        var lastDigits = token.search(/\d+$/);
                        if (lastDigits > 0) {
                            id = token.substring(lastDigits);
                            name = name.substring(0, lastDigits);
                        }

                        if (name) {
                            name = name.replace(/[\{\}]/g, '');
                            name = name.replace(/_/g, ' ');
                            name = name.replace(/[, ]+$/g, '');
                            name = name.replace(/^[ ]+/g, '');
                            if (name) {
                                ret.push(name + (id ? ' (' + id + ')' : ''));
                            }
                        }
                    }

                    return ret;
                },

                /**
                fmtDetailLiftedContigValue: function(parent, title, val, f, class_) {
                    val = val || '';
                    if (!val){
                        return;
                    }

                    if (val && lang.isArray(val)) {
                        val = val.join(',');
                    }

                    if (f.get('LiftedStart')) {
                        val += ':' + f.get('LiftedStart');

                        if (f.get('LiftedStop')){
                            val += '-' + f.get('LiftedStop');
                        }
                    }

                    if (f.get('ReverseComplementedAlleles')) {
                        val += ' (*Reverse Complemented)';
                    }

                    array.forEach(parsed, function(val){
                        domConstruct.create('div', { className: 'value ' + class_, innerHTML: val }, parent );
                    }, this);

                    return 1;
                },
                 **/

                fmtDetailOMIMDValue: function(parent, title, val, f, class_) {
                    //example:
                    //this.parseOmimd("Spinal_muscular_atrophy,_distal,_autosomal_recessive,_4,_611067_(3),Charcot-Marie-Tooth_disease,_recessive_intermediate_C,_615376_(3)");
                    //this.parseOmimd("Charcot-Marie-Tooth_disease,_type_2A1,_118210_(3),_Pheochromocytoma,171300_(3),_{Neuroblastoma,_susceptibility_to,_1},_256700_(3)");
                    //this.parseOmimd("Obesity,_morbid,_due_to_leptin_receptor_deficiency,_614963_(3)");
                    //this.parseOmimd("Severe_combined_immunodeficiency_due_to_ADA_deficiency,_102700_(3),Adenosine_deaminase_deficiency,_partial,_102700_(3)");
                    //this.parseOmimd("{Malaria,_cerebral,_susceptibility_to},_611162_(3),_{Septic_shock,susceptibility_to}_(3),_{Asthma,_susceptibility_to},_600807_(3),_{Dementia,_vascular,_susceptibility_to}_(3),_{Migraine_without_aura,_susceptibility_to},157300_(3)_157300_(3)");

                    val = val || '';
                    if (val && lang.isArray(val)) {
                        val = val.join(',');
                    }

                    var owner = domConstruct.create('div', { className: 'value_container ' + class_ + ' multi_value'}, parent );

                    var parsed = this.parseOmimd(val) || [];
                    array.forEach(parsed, function(val){
                        domConstruct.create('div', { className: 'value ' + class_, innerHTML: val }, owner );
                    }, this);

                    return parsed.length;
                },

                //Adapted from: https://stackoverflow.com/questions/8493195/how-can-i-parse-a-csv-string-with-javascript-which-contains-comma-in-data
                // Return array of string values, or NULL if CSV string not well formed.
                CSVtoArray: function (text) {
                    text = text || '';
                    text = text.replace(/{/g, '\'');
                    text = text.replace(/}/g, '\'');
                    text = text.replace(/_,/g, ',');
                    text = text.replace(/,_/g, ',');

                    var re_valid = /^\s*(?:'[^'\\]*(?:\\[\S\s][^'\\]*)*'|"[^"\\]*(?:\\[\S\s][^"\\]*)*"|[^,'"\s\\]*(?:\s+[^,'"\s\\]+)*)\s*(?:,\s*(?:'[^'\\]*(?:\\[\S\s][^'\\]*)*'|"[^"\\]*(?:\\[\S\s][^"\\]*)*"|[^,'"\s\\]*(?:\s+[^,'"\s\\]+)*)\s*)*$/;
                    var re_value = /(?!\s*$)\s*(?:'([^'\\]*(?:\\[\S\s][^'\\]*)*)'|"([^"\\]*(?:\\[\S\s][^"\\]*)*)"|([^,'"\s\\]*(?:\s+[^,'"\s\\]+)*))\s*(?:,|$)/g;
                    // Return NULL if input string is not well formed CSV string.
                    if (!re_valid.test(text)) return null;
                    var a = [];                     // Initialize array to receive values.
                    text.replace(re_value, // "Walk" the string using replace with callback.
                            function(m0, m1, m2, m3) {
                                // Remove backslash from \' in single quoted values.
                                if      (m1 !== undefined) a.push(m1.replace(/\\'/g, "'"));
                                // Remove backslash from \" in double quoted values.
                                else if (m2 !== undefined) a.push(m2.replace(/\\"/g, '"'));
                                else if (m3 !== undefined) a.push(m3);
                                return ''; // Return empty string.
                            });
                    // Handle special case of empty last value.
                    if (/,\s*$/.test(text)) a.push('');
                    return a;
                },

                fmtDetailANNValue: function(parent, title, val, f, class_){
                    if (val && val.length){
                        var scrollableElement = dojo.create('div', { style: "overflow-y: scroll;" }, parent);
                        var tableElement = domConstruct.create('table', { className: "" }, scrollableElement );
                        var headerLine = domConstruct.create( 'tr', {className: 'dgrid-header '}, tableElement );
                        domConstruct.create('th', { className: 'dgrid-cell dgrid-sortable', innerHTML: "Effect" }, headerLine );
                        domConstruct.create('th', { className: 'dgrid-cell dgrid-sortable', innerHTML: "Impact" }, headerLine );
                        domConstruct.create('th', { className: 'dgrid-cell dgrid-sortable', innerHTML: "Gene Name" }, headerLine );
                        domConstruct.create('th', { className: 'dgrid-cell dgrid-sortable', innerHTML: "Position / Consequence" }, headerLine );

                        array.forEach(val, function(v){
                            var tr = domConstruct.create( 'tr', {}, tableElement );

                            v = v.split('|');

                            var geneId = '';
                            if (v[4].indexOf(v[3]) === -1){
                                geneId = ' (' + v[4] + ')';
                            }

                            dojo.create('td', { className: 'dgrid-cell', innerHTML: v[1] }, tr );
                            dojo.create('td', { className: 'dgrid-cell', innerHTML: v[2] }, tr );
                            dojo.create('td', { className: 'dgrid-cell', innerHTML: v[3] + geneId }, tr);
                            dojo.create('td', { className: 'dgrid-cell', innerHTML: v[9] + '<br>' + v[10]}, tr);
                        }, this);
                    }
                },

                tagCategories: {
                    predictedFunction: {
                        title: 'Predicted Function',
                        description: 'Annotations related to prediction of functional impact or coding potential.',
                        tags: ['ANN', 'CADD_PH', 'SD', 'SM', 'SX', 'NE', 'NF', 'NG', 'NH', 'NK', 'NL', 'NMD', 'NC', 'NJ', 'LOF', 'FC', 'FE']
                    },
                    regulatory: {
                        title: 'Regulatory Data',
                        description: 'Annotations related to overlap with known or predicted regulatory elements.',
                        tags: ['ENC', 'ENN', 'ENCDNA_SC', 'ENCTFBS_SC', 'ENCTFBS_TF', 'ENCSEG_NM', 'RDB_WS', 'RDB_MF', 'FS_EN', 'FS_WS', 'FS_NS', 'FS_SN', 'FS_TG', 'FS_US', 'FS_SC']
                    },
                    comparative: {
                        title: 'Comparative/Evolutionary Genomics',
                        description: '',
                        tags: ['PC_VB', 'PC_PL', 'PC_PR', 'PP_VB', 'PP_PL', 'PP_PR']
                    },
                    phenotypicData: {
                        title: 'Phenotypic Data',
                        tags: ['CLN_SIG', 'CLN_DN', 'CLN_DNINCL', 'CLN_ALLELE', 'CLN_ALLELEID', 'CLN_VI', 'CLN_DBVARID', 'CLN_GENEINFO', 'CLN_RS', 'OMIMN', 'OMIMT', 'OMIMD', 'OMIMS', 'OMIMM', 'OMIMC', 'OMIMMUS', 'GRASP_AN','GRASP_PH','GRASP_P','GRASP_PL','GRASP_PMID','GRASP_RS', 'CLN']
                    },
                    ignore: {
                        tags: ['SF', 'NA', 'SOR', 'CADD_RS', 'ENCDNA_CT', 'ERBCTA_CT', 'ENCDNA_SC', 'ERBCTA_SC', 'NM', 'CCDS', 'OREGANNO_PMID', 'OREGANNO_TYPE', 'RSID', 'SCSNV_ADA', 'SCSNV_RS', 'SP_SC', 'ENCTFBS_CL', 'ERBSEG_CT', 'ERBSEG_NM', 'ERBSEG_SC', 'ERBSUM_NM', 'ERBSUM_SC', 'ERBTFBS_PB', 'ERBTFBS_TF', 'CLN_DISDB', 'CLN_DISDBINCL', 'CLN_HGVS', 'CLN_REVSTAT', 'CLN_SIGINCL', 'CLN_VC', 'CLN_VCSO', 'CLN_MC', 'CLN_ORIGIN', 'CLN_SSR', 'genotypes']
                    }
                },

                _renderAdditionalTagsDetail: function( track, f, featDiv, container ) {
                    domConstruct.create(
                            'div',
                            { className: 'additional',
                                innerHTML: '<h2>Annotations for this variant are listed below.  Hover over any yellow label for additional information.</h2>'
                            },
                            container );

                    if (this.config.additionalFeatureMsg){
                        domConstruct.create(
                                'div',
                                { className: 'additional',
                                    innerHTML: '<div>' + this.config.additionalFeatureMsg + '</div>'
                                },
                                container );
                    }

                    var allTags = array.filter( f.tags(), function(t) {
                        return ! this._isReservedTag( t );
                    },this);

                    //split apart by category:
                    array.forEach(Util.dojof.keys(this.tagCategories), function(category){
                        var cInfo = this.tagCategories[category];
                        if (category !== 'ignore'){
                            this.renderTagSection(container, f, cInfo.title, cInfo.tags);
                        }

                        allTags = array.filter(allTags, function (t) {
                            return cInfo.tags.indexOf(t) === -1;
                        }, this);
                    }, this);

                    if (allTags.length) {
                        this.renderTagSection(container, f, 'Other Annotations', allTags);
                    }
                },

                renderTagSection: function(container, f, title, tags){
                    var atElement = domConstruct.create(
                            'div',
                            { className: 'additional',
                                innerHTML: '<h2 class="sectiontitle">' + title + '</h2>'
                            },
                            container );

                    var hasTag = false;
                    if (tags.length){
                        array.forEach( tags, function(t) {
                            if (f.tags().indexOf(t) !== -1){
                                hasTag = true;
                            }

                            this.renderDetailField( container, t, f.get(t), f );
                        }, this);
                    }

                    if (!hasTag) {
                        var p = domConstruct.create('div', { className: 'field_container' }, container );
                        domConstruct.create('h2', { className: 'field', innerHTML: 'No Annotations' }, p );
                    }
                },

                _renderGenotypes: function( parentElement, track, f, featDiv, genotypes  ) {
                    var thisB = this;
                    if( ! genotypes )
                        return;

                    var trackId = track.config.label;
                    var contig = f.get('seq_id');
                    var start = f.get('start') + 1; //convert to 1-based
                    var end = f.get('end');

                    var keys = Util.dojof.keys( genotypes ).sort();
                    var gCount = keys.length;
                    if( ! gCount )
                        return;

                    // get variants and coerce to an array
                    var alt = f.get('alternative_alleles');
                    if( alt &&  typeof alt == 'object' && 'values' in alt )
                        alt = alt.values;
                    if ( alt.match( /,/ ) ) {
                        alt = alt.split( /,/ );
                    }
                    if( alt && ! lang.isArray( alt ) )
                        alt = [alt];

                    var gContainer = domConstruct.create(
                            'div',
                            { className: 'genotypes',
                                innerHTML: '<h2 class="sectiontitle">Genotypes ('
                                + gCount + ')</h2>'
                            },
                            parentElement );


                    var gtUrl = track.browser.config.contextPath + track.browser.config.containerPath + '/jbrowse-genotypeTable.view?trackId=' + trackId + '&chr=' + contig + '&start=' + start + '&stop=' + end;
                    var linkContainer = domConstruct.create(
                            'a',
                            {
                                className: 'value_container genotypes',
                                innerHTML: 'Click here to view sample-level genotypes',
                                target: '_blank',
                                style: 'margin-top: 10px;',
                                href: gtUrl
                            }, parentElement );

                    function render( underlyingRefSeq ) {
                        var summaryElement = thisB._renderGenotypeHistogram( gContainer, genotypes, alt, underlyingRefSeq, f );
                    };

                    track.browser.getStore('refseqs', function( refSeqStore ) {
                        if( refSeqStore ) {
                            refSeqStore.getReferenceSequence(
                                    { ref: track.refSeq.name,
                                        start: f.get('start'),
                                        end: f.get('end')
                                    },
                                    render,
                                    function() { render(); }
                            );
                        }
                        else {
                            render();
                        }
                    });
                },

                _renderCalledFraction: function( track, f, featDiv, container, genotypes ) {
                    if( ! genotypes )
                        return;

                    var thisB = this;
                    // js port of perl parsing of all sample data; work in progress
                    // get AC field and coerce into an array (to match array of alt below)
                    var acField = f.get('AC') || [];
                    if( acField &&  typeof acField == 'object' && 'values' in acField )
                        acField = acField.values;
                    if( acField && ! lang.isArray( acField ) )
                        acField = [acField];

                    var anField = f.get('AN') || [];
                    var hasAF = new Boolean('AF' in f.data && typeof f.get('AF') == 'object');
                    var afField;
                    if (hasAF) {
                        afField = f.get('AF');
                        if ( afField && typeof afField == 'object' && 'values' in afField )
                            afField = afField.values;
                        if( afField && ! lang.isArray( afField ) )
                            afField = [afField];
                    }
                    // as of May 4, 2015, ignore all AF field data
                    hasAF = false;
                    af = "";
                    var alleles = [];
                    var alleleFreq = [];
                    var alleleCount = [];
                    var alleleFraction = [];
                    var totalVariantCount = 0;
                    var totalVariantFract = 0;

                    var ref = f.get('reference_allele');
                    // get variants and coerce to an array
                    var alt = f.get('alternative_alleles');
                    if( alt &&  typeof alt == 'object' && 'values' in alt )
                        alt = alt.values;
                    if ( alt && alt.match( /,/ ) ) {
                        alt = alt.split( /,/ );
                    }
                    if( alt && ! lang.isArray( alt ) )
                        alt = [alt];

                    var haveLongSeq = false;
                    if (ref.length > 5) {
                        haveLongSeq = true;
                    }
                    array.forEach( alt , function(thisAlt) {
                        if ( thisAlt.match( /\./ ) ) next;
                        if ( thisAlt.length > 5) {
                            haveLongSeq = true;
                        }
                    });

                    // add reference to front of array of all alleles
                    alleles.push( ref );
                    alleleFreq.push( 0 );
                    alleleCount.push( 0 );
                    array.forEach( alt , function(thisAlt) {
                        alleles.push( thisAlt );
                    });
                    array.forEach( acField , function(thisAC) {
                        totalVariantCount += parseInt(thisAC);
                        alleleCount.push( thisAC );
                    });
                    // set ref count to calculated value
                    alleleCount[0] = (parseInt(anField) - totalVariantCount);

                    // calculate fraction of each allele from the total called dataset, post-filter
                    for (var i in alleles) {
                        alleleFraction.push( (parseInt(alleleCount[i]) / parseInt(anField)).toFixed(3) );
                    }

                    // adapted from JBrowse/View/Track/_VariantDetailMixin _renderGenotypeSummary()
                    var counts = new NestedFrequencyTable();

                    for (var j = 1; j <= (parseInt(anField) - totalVariantCount); j++) {
                        counts.getNested(ref).increment();
                    }
                    for (var i = 0; i < alt.length; i++) {
                        for (var j = 1; j <= acField[i]; j++) {
                            counts.getNested(alt[i]).increment();
                        }
                    }
                    var total = counts.total();
                    if( ! total )
                        return;

                    var calledFractionContainer = domConstruct.create(
                            'div',
                            { className: 'calledFraction',
                                innerHTML: '<h2 class="sectiontitle">Allele Frequencies</h2>'
                            },
                            container );

                    var tableElement = domConstruct.create('table', {}, calledFractionContainer );
                    var headerLine = domConstruct.create( 'tr', {}, tableElement );
                    domConstruct.create('th', { className: 'allele header', innerHTML: "Sequence" }, headerLine );
                    domConstruct.create('th', { className: 'count header', innerHTML: "Fraction" }, headerLine );

                    function renderFreqTable( table, level ) {
                        var isRef = new RegExp( ref );
                        table.forEach( function( count, categoryName ) {
                            var tr = domConstruct.create( 'tr', {}, tableElement );
                            if (categoryName.match( isRef )) {
                                domConstruct.create('td', { className: 'alleleRef', innerHTML: categoryName+ " (ref)" }, tr );
                            } else {
                                domConstruct.create('td', { className: 'allele', innerHTML: categoryName }, tr );
                            }
                            domConstruct.create('td', { className: 'count', innerHTML: (count / anField).toFixed(3) }, tr );
                        });
                    }

                    renderFreqTable( counts, 0 );
                },

                _renderGenotypeHistogram: function( parentElement, genotypes, alt, underlyingRefSeq, feat ) {
                    var thisB = this;
                    if( ! genotypes )
                        return;

                    var keys = Util.dojof.keys( genotypes ).sort();
                    var gTotalCount = keys.length;
                    if( ! gTotalCount )
                        return;

                    var gCounts = [];
                    for( var gname in genotypes ) {
                        if( genotypes.hasOwnProperty( gname ) ) {
                            if (typeof genotypes[gname] != "function") {
                                // increment the appropriate count
                                var gt = genotypes[gname].GT;
                                if( typeof gt == 'object' && 'values' in gt )
                                    gt = gt.values[0];

                                if (gCounts[gt]) {
                                    gCounts[gt] = gCounts[gt] + 1;
                                } else {
                                    gCounts[gt] = 1;
                                }
                            }
                        }
                    }

                    var valueContainer = domConstruct.create(
                            //'div', { className: 'value_container big genotype_histogram' },
                            'div', { className: 'histogram_container big genotype_histogram' },
                            parentElement );

                    var tableElement = domConstruct.create('table', {}, valueContainer );

                    var noCallgt = "";
                    var isHaploid = false;
                    for( var gt in gCounts ) {
                        if ( gt.match( /\./ ) ) {
                            noCallgt = gt;
                        }
                        else {
                            if ( gt.match( /^\d+$/ ) ) {
                                // GT entirely numeric (no splitter) is haploid data
                                isHaploid = true;
                            }
                            var splitter = (gt.match(/[\|\/]/g)||[])[0]; // only accept | and / splitters since . can mean no call
                            //alt=alt[0].split(','); // force split on alt alleles
                            var refseq = underlyingRefSeq ? underlyingRefSeq : 'ref';

                            var haveLongSeq = false;
                            if (refseq.length > 5) {
                                haveLongSeq = true;
                            }
                            array.forEach( alt , function(thisAlt) {
                                if ( thisAlt.match( /\./ ) ) next;
                                if ( thisAlt.length > 5) {
                                    haveLongSeq = true;
                                }
                            });

                            var gtDisplay = array.map( splitter?gt.split(splitter):gt, function( gtIndex ) {
                                gtIndex = parseInt( gtIndex );
                                return gtIndex ? ( alt ? alt[gtIndex-1] : gtIndex ) : refseq;
                            }).join( ' '+splitter+' ' );

                            var thisPct = ( gCounts[gt] / gTotalCount ) * 100;

                            var tr = domConstruct.create( 'tr', {}, tableElement );
                            //domConstruct.create('td', { innerHTML: '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' }, tr );
                            if ( haveLongSeq ) {
                                domConstruct.create('td', { innerHTML: gt.toUpperCase() , title: gtDisplay.toUpperCase() }, tr );
                            } else {
                                domConstruct.create('td', { innerHTML: gtDisplay.toUpperCase() }, tr );
                            }
                            domConstruct.create('td', { innerHTML: "<table cellpadding=0 cellspacing=0 border=0><tr><td width="+thisPct+" bgcolor=blue>&nbsp;</td><td>&nbsp;"+thisPct.toFixed(1)+"%</td></tr></table>" }, tr );
                        }
                    }
                    if ( gCounts.hasOwnProperty( noCallgt ) ) {
                        var noCallPct = ( gCounts[noCallgt] / gTotalCount ) * 100;
                        var tr = domConstruct.create( 'tr', {}, tableElement );
                        domConstruct.create('td', { innerHTML: 'NA', title: "no data or call was "+gt }, tr );
                        domConstruct.create('td', { innerHTML: "<table cellpadding=0 cellspacing=0 border=0><tr><td width="+noCallPct+" bgcolor=blue>&nbsp;</td><td>&nbsp;"+noCallPct.toFixed(1)+"%</td></tr></table>" }, tr );
                    }
                    if ( isHaploid ) {
                        domConstruct.create(
                                'p', { className: 'histogram_container big genotype_histogram',
                                    innerHTML: 'Note: This is a haploid dataset' }, parentElement );
                    }
                },

                _renderGenotypeSummary: function( parentElement, genotypes, alt, underlyingRefSeq ) {
                    if( ! genotypes )
                        return;

                    var counts = new NestedFrequencyTable();
                    for( var gname in genotypes ) {
                        if( genotypes.hasOwnProperty( gname ) ) {
                            // increment the appropriate count
                            var gt = genotypes[gname].GT;
                            if( typeof gt == 'object' && 'values' in gt )
                                gt = gt.values[0];
                            if( typeof gt == 'string' )
                                gt = gt.split(/\||\//);

                            if( lang.isArray( gt ) ) {
                                // if all zero, non-variant/hom-ref
                                if( array.every( gt, function( g ) { return parseInt(g) == 0; }) ) {
                                    counts.getNested('non-variant').increment('homozygous for reference');
                                }
                                else if( array.every( gt, function( g ) { return g == '.'; }) ) {
                                    counts.getNested('non-variant').increment('no call');
                                }
                                else if( array.every( gt, function( g ) { return g == gt[0]; } ) ) {
                                    if( alt )
                                        counts.getNested('variant/homozygous').increment( alt[ parseInt(gt[0])-1 ] + ' variant' );
                                    else
                                        counts.getNested('variant').increment( 'homozygous' );
                                }
                                else {
                                    counts.getNested('variant').increment('heterozygous');
                                }
                            }
                        }
                    }

                    var total = counts.total();
                    if( ! total )
                        return;

                    var valueContainer = domConstruct.create(
                            'div', { className: 'value_container big genotype_summary' },
                            parentElement );
                    //domConstruct.create('h3', { innerHTML: 'Summary' }, valueContainer);

                    var tableElement = domConstruct.create('table', {}, valueContainer );

                    function renderFreqTable( table, level ) {
                        table.forEach( function( count, categoryName ) {
                            var tr = domConstruct.create( 'tr', {}, tableElement );
                            domConstruct.create('td', { className: 'category level_'+level, innerHTML: categoryName }, tr );
                            if( typeof count == 'object' ) {
                                var thisTotal = count.total();
                                domConstruct.create('td', { className: 'count level_'+level, innerHTML: thisTotal }, tr );
                                domConstruct.create('td', { className: 'pct level_'+level, innerHTML: Math.round(thisTotal/total*10000)/100 + '%' }, tr );
                                renderFreqTable( count, level+1 );
                            } else {
                                domConstruct.create('td', { className: 'count level_'+level, innerHTML: count }, tr );
                                domConstruct.create('td', { className: 'pct level_'+level, innerHTML: Math.round(count/total*10000)/100+'%' }, tr );
                            }
                        });
                    }

                    renderFreqTable( counts, 0 );

                    var totalTR = domConstruct.create('tr',{},tableElement );
                    domConstruct.create('td', { className: 'category total', innerHTML: 'Total' }, totalTR );
                    domConstruct.create('td', { className: 'count total', innerHTML: total }, totalTR );
                    domConstruct.create('td', { className: 'pct total', innerHTML: '100%' }, totalTR );
                }
            });
        });
