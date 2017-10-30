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
                        range: [0.6]
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

                defaultFeatureDetail: function( /** JBrowse.Track */ track, /** Object */ f, /** HTMLElement */ featDiv, /** HTMLElement */ container, /** genotypeUrlTemplate xhr query result object */ genotypeUrlResult ) {
                    var thisB = this;

                    container = container || domConstruct.create('div', { className: 'popupcontainer', innerHTML: '' } );

                    // genotypes in a separate section
                    //var genotypes = this._parse_genotypes( f, genotypeUrlResult );
                    var genotypes = {};

                    //console.log("genotypes = "+genotypes);

                    this._renderCoreDetails( track, f, featDiv, container, genotypes );

                    this._renderAdditionalTagsDetail( track, f, featDiv, container );

                    //thisB._renderCalledFraction( track, f, featDiv, container, genotypes );

                    //thisB._renderGenotypes( container, track, f, featDiv, genotypes );

                    //thisB._renderSnpEff( container, track, f, featDiv, genotypes );

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

                    //fmt( 'Length', Util.addCommas(f.get('end')-f.get('start'))+' bp',f );
                },

                //marks tags to be skipped from attributes table
                _isReservedTag: function( t ) {
                    return this.inherited(arguments) || {description:1, reference_allele: 1, alternative_alleles: 1, seq_id: 1, filter:1}[t.toLowerCase()];
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
                        description: 'Annotations related to overlap with know or predicted regulatory elements.',
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
                        tags: ['SF', 'NA', 'SOR', 'CADD_RS', 'ENCDNA_CT', 'ERBCTA_CT', 'ENCDNA_SC', 'ERBCTA_SC', 'NM', 'CCDS', 'OREGANNO_PMID', 'OREGANNO_TYPE', 'RSID', 'SCSNV_ADA', 'SCSNV_RS', 'SP_SC', 'ENCTFBS_CL', 'ERBSEG_CT', 'ERBSEG_NM', 'ERBSEG_SC', 'ERBSUM_NM', 'ERBSUM_SC', 'ERBTFBS_PB', 'ERBTFBS_TF', 'CLN_DISDB', 'CLN_DISDBINCL', 'CLN_HGVS', 'CLN_REVSTAT', 'CLN_SIGINCL', 'CLN_VC', 'CLN_VCSO', 'CLN_MC', 'CLN_ORIGIN', 'CLN_SSR']
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

                    console.log(allTags);
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

                // _renderCalledFraction: function( track, f, featDiv, container, genotypes ) {
                //     var thisB = this;
                //     // js port of perl parsing of all sample data; work in progress
                //     // get AC field and coerce into an array (to match array of alt below)
                //     var acField = f.get('AC');
                //     if( acField &&  typeof acField == 'object' && 'values' in acField )
                //         acField = acField.values;
                //     if( acField && ! lang.isArray( acField ) )
                //         acField = [acField];
                //
                //     var anField = f.get('AN');
                //     var hasAF = new Boolean('AF' in f.data && typeof f.get('AF') == 'object');
                //     var afField;
                //     if (hasAF) {
                //         afField = f.get('AF');
                //         if ( afField && typeof afField == 'object' && 'values' in afField )
                //             afField = afField.values;
                //         if( afField && ! lang.isArray( afField ) )
                //             afField = [afField];
                //     }
                //     // as of May 4, 2015, ignore all AF field data
                //     hasAF = false;
                //     af = "";
                //     var alleles = [];
                //     var alleleFreq = [];
                //     var alleleCount = [];
                //     var alleleFraction = [];
                //     var totalVariantCount = 0;
                //     var totalVariantFract = 0;
                //
                //     var ref = f.get('reference_allele');
                //     // get variants and coerce to an array
                //     var alt = f.get('alternative_alleles');
                //     if( alt &&  typeof alt == 'object' && 'values' in alt )
                //         alt = alt.values;
                //     if ( alt.match( /,/ ) ) {
                //         alt = alt.split( /,/ );
                //     }
                //     if( alt && ! lang.isArray( alt ) )
                //         alt = [alt];
                //
                //     var haveLongSeq = false;
                //     if (ref.length > 5) {
                //         haveLongSeq = true;
                //     }
                //     array.forEach( alt , function(thisAlt) {
                //         if ( thisAlt.match( /\./ ) ) next;
                //         if ( thisAlt.length > 5) {
                //             haveLongSeq = true;
                //         }
                //     });
                //
                //     // add reference to front of array of all alleles
                //     alleles.push( ref );
                //     alleleFreq.push( 0 );
                //     alleleCount.push( 0 );
                //     array.forEach( alt , function(thisAlt) {
                //         alleles.push( thisAlt );
                //     });
                //     array.forEach( acField , function(thisAC) {
                //         totalVariantCount += parseInt(thisAC);
                //         alleleCount.push( thisAC );
                //     });
                //     // set ref count to calculated value
                //     alleleCount[0] = (parseInt(anField) - totalVariantCount);
                //
                //     // calculate fraction of each allele from the total called dataset, post-filter
                //     for (var i in alleles) {
                //         alleleFraction.push( (parseInt(alleleCount[i]) / parseInt(anField)).toFixed(3) );
                //     }
                //
                //     // adapted from JBrowse/View/Track/_VariantDetailMixin _renderGenotypeSummary()
                //     var counts = new NestedFrequencyTable();
                //
                //     for (var j = 1; j <= (parseInt(anField) - totalVariantCount); j++) {
                //         counts.getNested(ref).increment();
                //     }
                //     for (var i = 0; i < alt.length; i++) {
                //         for (var j = 1; j <= acField[i]; j++) {
                //             counts.getNested(alt[i]).increment();
                //         }
                //     }
                //     var total = counts.total();
                //     if( ! total )
                //         return;
                //
                //     var calledFractionContainer = domConstruct.create(
                //             'div',
                //             { className: 'calledFraction',
                //                 innerHTML: '<h2 class="sectiontitle">Genotyped Alleles for '+f.get('name')+'</h2>'
                //             },
                //             container );
                //
                //     var tableElement = domConstruct.create('table', {}, calledFractionContainer );
                //     var headerLine = domConstruct.create( 'tr', {}, tableElement );
                //     domConstruct.create('th', { className: 'allele header', innerHTML: "Sequence" }, headerLine );
                //     domConstruct.create('th', { className: 'count header', innerHTML: "Called Fraction" }, headerLine );
                //
                //     function renderFreqTable( table, level ) {
                //         var isRef = new RegExp( ref );
                //         table.forEach( function( count, categoryName ) {
                //             var tr = domConstruct.create( 'tr', {}, tableElement );
                //             if (categoryName.match( isRef )) {
                //                 domConstruct.create('td', { className: 'alleleRef', innerHTML: categoryName+ " (ref)" }, tr );
                //             } else {
                //                 domConstruct.create('td', { className: 'allele', innerHTML: categoryName }, tr );
                //             }
                //             domConstruct.create('td', { className: 'count', innerHTML: (count / anField).toFixed(3) }, tr );
                //         });
                //     }
                //
                //     for (var i = 0; i < alleles.length; i++) {
                //         var tr = domConstruct.create( 'tr', {}, tableElement );
                //         if (i == 0) {
                //             if (haveLongSeq) {
                //                 domConstruct.create('td', { className: 'alleleRef', innerHTML: i+ ": "+alleles[i]+ " (ref)" }, tr );
                //             } else {
                //                 domConstruct.create('td', { className: 'alleleRef', innerHTML: alleles[i]+ " (ref)" }, tr );
                //             }
                //         }
                //         else {
                //             if (haveLongSeq) {
                //                 domConstruct.create('td', { className: 'allele', innerHTML: i+": "+alleles[i] }, tr );
                //             } else {
                //                 domConstruct.create('td', { className: 'allele', innerHTML: alleles[i] }, tr );
                //             }
                //         }
                //         domConstruct.create('td', { className: 'count', innerHTML: (alleleCount[i] / anField).toFixed(3) }, tr );
                //     }
                //     if (haveLongSeq) {
                //         domConstruct.create('p', { innerHTML: 'Note: Alleles are represented by the above numbers in the tables below'}, container );
                //     }
                //
                //     //renderFreqTable( counts, 0 );
                // },

                // _renderGenotypes: function( parentElement, track, f, featDiv, genotypes  ) {
                //     var thisB = this;
                //     if( ! genotypes )
                //         return;
                //
                //     var keys = Util.dojof.keys( genotypes ).sort();
                //     var gCount = keys.length;
                //     if( ! gCount )
                //         return;
                //
                //     // get variants and coerce to an array
                //     var alt = f.get('alternative_alleles');
                //     if( alt &&  typeof alt == 'object' && 'values' in alt )
                //         alt = alt.values;
                //     if ( alt.match( /,/ ) ) {
                //         alt = alt.split( /,/ );
                //     }
                //     if( alt && ! lang.isArray( alt ) )
                //         alt = [alt];
                //
                //     var gContainer = domConstruct.create(
                //             'div',
                //             { className: 'genotypes',
                //                 innerHTML: '<h2 class="sectiontitle">Genotypes ('
                //                 + gCount + ')</h2>'
                //             },
                //             parentElement );
                //
                //     function render( underlyingRefSeq ) {
                //         // var summaryElement = thisB._renderGenotypeSummary( gContainer, genotypes, alt, underlyingRefSeq );
                //         var summaryElement = thisB._renderGenotypeHistogram( gContainer, genotypes, alt, underlyingRefSeq, f );
                //
                //         var valueContainer = domConstruct.create(
                //                 'div',
                //                 {
                //                     className: 'value_container genotypes'
                //                 }, gContainer );
                //
                //         thisB.renderDetailValueGrid(
                //                 valueContainer,
                //                 'Genotypes',
                //                 f,
                //                 // iterator
                //                 function() {
                //                     if( ! keys.length )
                //                         return null;
                //                     var k = keys.shift();
                //                     var value = genotypes[k];
                //                     var item = { id: k };
                //                     for( var field in value ) {
                //                         item[ field ] = thisB._mungeVal( value[field], field, alt, underlyingRefSeq, f );
                //                     }
                //                     return item;
                //                 },
                //                 {
                //                     descriptions: (function() {
                //                         if( ! keys.length )
                //                             return {};
                //
                //                         var subValue = genotypes[keys[0]];
                //                         var descriptions = {};
                //                         for( var k in subValue ) {
                //                             descriptions[k] = subValue[k].meta && subValue[k].meta.description || null;
                //                         }
                //                         return descriptions;
                //                     })()
                //                 }
                //         );
                //     };
                //
                //     track.browser.getStore('refseqs', function( refSeqStore ) {
                //         if( refSeqStore ) {
                //             refSeqStore.getReferenceSequence(
                //                     { ref: track.refSeq.name,
                //                         start: f.get('start'),
                //                         end: f.get('end')
                //                     },
                //                     render,
                //                     function() { render(); }
                //             );
                //         }
                //         else {
                //             render();
                //         }
                //     });
                // },

                // _renderSnpEff: function( parentElement, track, f, featDiv, genotypes ) {
                //     var thisB = this;
                //
                //     // munge snpeff lines into an array
                //     var effArray = f.get('EFF');
                //
                //     if( effArray &&  typeof effArray == 'object' && 'values' in effArray )
                //         effArray = effArray.values;
                //     if( effArray && ! lang.isArray( effArray ) )
                //         effArray = [effArray];
                //     if (! effArray)
                //         return;
                //
                //     // get variants and coerce to an array
                //     var alt = f.get('alternative_alleles');
                //     if( alt &&  typeof alt == 'object' && 'values' in alt )
                //         alt = alt.values;
                //     if( alt && ! lang.isArray( alt ) )
                //         alt = [alt];
                //
                //     var snpeffBreak = domConstruct.create('br', {}, parentElement );
                //     var effContainer = domConstruct.create(
                //             'div',
                //             { className: 'snpeff',
                //                 innerHTML: '<h2 class="sectiontitle">SnpEff Variant Annotation, where available:</h2>'
                //             },
                //             parentElement );
                //
                //     function render( underlyingRefSeq ) {
                //         var refseq = underlyingRefSeq ? underlyingRefSeq : 'ref';
                //
                //         var haveLongSeq = false;
                //         if (refseq.length > 5) {
                //             haveLongSeq = true;
                //         }
                //         array.forEach( alt , function(thisAlt) {
                //             if ( thisAlt.match( /\./ ) ) next;
                //             if ( thisAlt.length > 5) {
                //                 haveLongSeq = true;
                //             }
                //         });
                //
                //         // pull out SnpEff columns (between single quotes in the EFF header metadata)
                //         var columnPrototype = f.parser.header.info.EFF.description.toString();
                //         if (! columnPrototype)
                //             return;
                //
                //         /** old expect string: Predicted effects for this variant.Format: 'Effect ( Effect_Impact | Functional_Class | Codon_Change | Amino_Acid_change| Amino_Acid_length | Gene_Name | Transcript_BioType | Gene_Coding | Transcript_ID | Exon  | GenotypeNum [ | ERRORS | WARNINGS ] )'
                //          1) capture everything after the left paranthesis
                //          2) remove everthing after the left bracket
                //          3) split on the remaining pipe chars
                //          (We ignore any ERROR or WARNING columns)
                //          or lately (SnpEff v4.3i):
                //          new expect string: Predicted effects for this variant.Format: 'Effect ( Effect_Impact | Functional_Class | Codon_Change | Amino_Acid_Change| Amino_Acid_length | Gene_Name | Transcript_BioType | Gene_Coding | Transcript_ID | Exon_Rank  | Genotype [ | ERRORS | WARNINGS ] )'
                //          meaning we no longer necessarily capture GenotypeNum nor need to convert 0 to ref allele, 1 to first alt, etc.
                //          */
                //         var headerColumns = columnPrototype.split('(')[1].split('[')[0].split('|');;
                //
                //         var snpEffColumns = new Array();
                //         var snpEffColumnDataCounts = new Array();
                //         // to convert 0/1 to ref/alt etc. if still that old style snpeff data
                //         var GenotypeNumColumn = 0;
                //         // to hide Gene_Name column from display
                //         var GeneNameColumn = 0;
                //         // to hide Transcript_BioType and Gene_Coding columns (always blank or "protein_coding" and "CODING" values for Phytozome running snpeff on gene model GFF3 )
                //         var TranscriptBioTypeColumn = 0;
                //         var GeneCodingColumn = 0;
                //
                //         var columnCount = 0;
                //         for (var col in headerColumns) {
                //             if ((/Genotype\_*Num/).test(headerColumns[col]) == true) {
                //                 GenotypeNumColumn = columnCount;
                //                 snpEffColumns[col] = "Genotype";
                //             }
                //             else if ((/Gene_Name/).test(headerColumns[col]) == true) {
                //                 GeneNameColumn = columnCount;
                //                 snpEffColumns[col] = "Gene Name";
                //             }
                //             else if ((/Transcript_BioType/).test(headerColumns[col]) == true) {
                //                 TranscriptBioTypeColumn = columnCount;
                //                 snpEffColumns[col] = "Transcript BioType";
                //             }
                //             else if ((/Gene_Coding/).test(headerColumns[col]) == true) {
                //                 GeneCodingColumn = columnCount;
                //                 snpEffColumns[col] = "Gene Coding";
                //             }
                //             else {
                //                 snpEffColumns[col] = headerColumns[col];
                //             }
                //             snpEffColumnDataCounts[col] = 0;
                //             columnCount += 1;
                //         }
                //
                //         // count columns with data, so we can skip display of completely empty columns
                //         var haveLongEffect = false;
                //         var effectCharLimit = 11;
                //         var haveLongDataColumns = false;
                //         var haveLongHiddenDataColumns = false;
                //         var annotationCharLimit = 8;
                //         for ( var i in effArray ) {
                //             var line = effArray[i].split('(');
                //             var effect = line[0];
                //             // only consider this line effect long if it exceeds char limit and is not compound like SPLICE_SITE_DONOR+INTRON
                //             var compoundEffect = Boolean( (/\+/).test(effect) );
                //             if (! haveLongEffect && effect.length > effectCharLimit && ! compoundEffect ) { haveLongEffect = true; }
                //             var detailArray = line[1].split('|');
                //             for ( var c in detailArray ) {
                //                 if ( detailArray[c] != "" ) {
                //                     var hiddenColumn = Boolean( c == GeneNameColumn || c == TranscriptBioTypeColumn || c == GeneCodingColumn);
                //                     if (hiddenColumn) {
                //                         haveLongHiddenDataColumns = true;
                //                     }
                //                     else {
                //                         // don't count column 8 (Transcript Id)
                //                         if (! haveLongDataColumns && c != 8 && detailArray[c].length > annotationCharLimit) {
                //                             haveLongDataColumns = true;
                //                         }
                //                     }
                //                     snpEffColumnDataCounts[c] += 1;
                //                 }
                //             }
                //         }
                //
                //         // LOF or NMD tables would go here...
                //
                //         // regular SnpEff table
                //         /* var tableElement = dojo.create('table', { }, effContainer ); */
                //         var scrollableElement = dojo.create('div', { style: "overflow: scroll; width: 650px; border: 2px solid;" }, effContainer );
                //         var tableElement = domConstruct.create('table', { id: "snpEffAnnotTable" }, scrollableElement );
                //         var headerLine = domConstruct.create( 'tr', {}, tableElement );
                //         domConstruct.create('th', { className: 'header', innerHTML: "Effect" }, headerLine );
                //
                //         var totalDisplayColumnCount = 0;
                //         var headerColumnCount = 0;
                //         for( var c = 0; c < snpEffColumns.length; c++ ) {
                //             var hiddenColumn = Boolean( headerColumnCount == GeneNameColumn || headerColumnCount == TranscriptBioTypeColumn || headerColumnCount == GeneCodingColumn);
                //             if ( snpEffColumnDataCounts[c] > 0 && ! hiddenColumn ) {
                //                 var columnName = snpEffColumns[c].replace(/_/g, ' ');
                //                 domConstruct.create('th', { className: 'header', innerHTML: columnName }, headerLine );
                //                 totalDisplayColumnCount++;
                //             }
                //             headerColumnCount += 1;
                //         }
                //
                //         for ( var i in effArray ) {
                //             var tr = domConstruct.create( 'tr', {}, tableElement );
                //             var line = effArray[i].split('(');
                //             var effect = line[0];
                //             if (effect.length > effectCharLimit) {
                //                 if ((/\+/).test(effect) == true) {
                //                     effect = effect.replace(/\+/g, '<br/>+  ');
                //                     dojo.create('td', { className: 'effect', innerHTML: effect }, tr );
                //                 }
                //                 else {
                //                     dojo.create('td', { className: 'effectLong', title: effect, innerHTML: effect.substr(0, effectCharLimit)+'...' }, tr );
                //                 }
                //             } else {
                //                 dojo.create('td', { className: 'effect', innerHTML: effect }, tr );
                //             }
                //
                //             var details = line[1].substring(0, line[1].length - 1);
                //             var detailArray = details.split('|');
                //             if ( detailArray.length > snpEffColumns.length) {
                //                 domConstruct.create('td', { className: 'parseError', colspan: totalDisplayColumnCount, innerHTML: "<i>error: returned " + detailArray.length + " columns</i></td></tr>" }, tr );
                //             }
                //             else {
                //                 var columnCount = 0;
                //                 for ( var c in snpEffColumns ) {
                //                     var hiddenColumn = Boolean( columnCount == GeneNameColumn || columnCount == TranscriptBioTypeColumn || columnCount == GeneCodingColumn);
                //                     if ( snpEffColumnDataCounts[c] > 0 && ! hiddenColumn ) {
                //                         if ( columnCount == GenotypeNumColumn ) {
                //                             if ( haveLongSeq ) {
                //                                 domConstruct.create('td', { className: 'snpEffDetailLong '+snpEffColumns[c], title: detailArray[c], innerHTML: detailArray[c] }, tr );
                //                             } else {
                //                                 domConstruct.create('td', { className: 'snpEffDetail '+snpEffColumns[c], innerHTML: detailArray[c] }, tr );
                //                             }
                //                         } else {
                //                             // originally, always display Gene_Name (5) and Transcript_ID (8); Gene_Name now hidden
                //                             if (c !=5 && c != 8 && detailArray[c].length > annotationCharLimit) {
                //                                 var detailShort = detailArray[c].substr(0, annotationCharLimit)+"...";
                //                                 domConstruct.create('td', { className: 'snpEffDetailLong '+snpEffColumns[c], title: detailArray[c], innerHTML: detailShort }, tr );
                //                             } else {
                //                                 domConstruct.create('td', { className: 'snpEffDetail '+snpEffColumns[c], innerHTML: detailArray[c] }, tr );
                //                             }
                //                         }
                //                     }
                //                     if (detailArray.length == (snpEffColumns.length - 1) ) {
                //                         domConstruct.create('td', { className: snpEffColumns[c], innerHTML: '&nbsp;' }, tr );
                //                     }
                //                     columnCount += 1;
                //                 }
                //             }
                //         }
                //
                //         if ( haveLongEffect || haveLongDataColumns ) {
                //             dojo.create('p', { innerHTML: 'Note: long text annotations have been truncated (grey entries in the table above). Hover over the shortened version to view the full term.' }, effContainer );
                //         }
                //         if (document.getElementById("snpEffAnnotTable").offsetWidth > 650) {
                //             dojo.create('p', { innerHTML: "Note: scroll right to view additional SnpEff columns"}, effContainer );
                //         }
                //     };
                //
                //     track.browser.getStore('refseqs', function( refSeqStore ) {
                //         if( refSeqStore ) {
                //             refSeqStore.getReferenceSequence(
                //                     { ref: track.refSeq.name,
                //                         start: f.get('start'),
                //                         end: f.get('end')
                //                     },
                //                     render,
                //                     function() { render(); }
                //             );
                //         }
                //         else {
                //             render();
                //         }
                //     });
                // },

                // // based on VariantDetailMixin _mungeGenotypeVal(), but made more generic to handle special formatting of other genotype columns
                // _mungeVal: function( value, fieldname, alt, underlyingRefSeq, feat ) {
                //     // display changes based on other subfields can access those through the feat object
                //     if( fieldname == 'GT' ) {
                //         // handle the GT field specially, translating the genotype indexes into the actual ALT strings
                //         var value_parse = value.values[0];
                //
                //         // don't alter "no data" ./. type GT
                //         if (value_parse.match( /\./ )) {
                //             return value_parse;
                //         }
                //
                //         var haveLongSeq = false;
                //         if (underlyingRefSeq.length > 5) {
                //             haveLongSeq = true;
                //         }
                //         array.forEach( alt , function(thisAlt) {
                //             if ( thisAlt.match( /\./ ) ) next;
                //             if ( thisAlt.length > 5) {
                //                 haveLongSeq = true;
                //             }
                //         });
                //
                //         var splitter = (value_parse.match(/[\|\/]/g)||[])[0]; // only accept | and / splitters since . can mean no call
                //         if (! lang.isArray (alt) ) {
                //             alt=alt[0].split(','); // force split on alt alleles
                //         }
                //         // original display: var refseq = underlyingRefSeq ? 'ref ('+underlyingRefSeq+')' : 'ref';
                //         var refseq = underlyingRefSeq ? underlyingRefSeq.toUpperCase() : 'ref';
                //         if (! haveLongSeq) {
                //             value = array.map( splitter?value_parse.split(splitter):value_parse, function( gtIndex ) {
                //                 gtIndex = parseInt( gtIndex );
                //                 return gtIndex ? ( alt ? alt[gtIndex-1].toUpperCase() : gtIndex ) : refseq;
                //             }).join( ' '+splitter+' ' );
                //         }
                //         else {
                //             value = value_parse;
                //         }
                //     }
                //     return value;
                // },

                // _renderGenotypeHistogram: function( parentElement, genotypes, alt, underlyingRefSeq, feat ) {
                //     var thisB = this;
                //     if( ! genotypes )
                //         return;
                //
                //     var keys = Util.dojof.keys( genotypes ).sort();
                //     var gTotalCount = keys.length;
                //     if( ! gTotalCount )
                //         return;
                //
                //     var gCounts = [];
                //     for( var gname in genotypes ) {
                //         if( genotypes.hasOwnProperty( gname ) ) {
                //             if (typeof genotypes[gname] != "function") {
                //                 // increment the appropriate count
                //                 var gt = genotypes[gname].GT;
                //                 if( typeof gt == 'object' && 'values' in gt )
                //                     gt = gt.values[0];
                //
                //                 if (gCounts[gt]) {
                //                     gCounts[gt] = gCounts[gt] + 1;
                //                 } else {
                //                     gCounts[gt] = 1;
                //                 }
                //             }
                //         }
                //     }
                //
                //     var valueContainer = domConstruct.create(
                //             //'div', { className: 'value_container big genotype_histogram' },
                //             'div', { className: 'histogram_container big genotype_histogram' },
                //             parentElement );
                //
                //     var tableElement = domConstruct.create('table', {}, valueContainer );
                //
                //     var noCallgt = "";
                //     var isHaploid = false;
                //     for( var gt in gCounts ) {
                //         if ( gt.match( /\./ ) ) {
                //             noCallgt = gt;
                //         }
                //         else {
                //             if ( gt.match( /^\d+$/ ) ) {
                //                 // GT entirely numeric (no splitter) is haploid data
                //                 isHaploid = true;
                //             }
                //             var splitter = (gt.match(/[\|\/]/g)||[])[0]; // only accept | and / splitters since . can mean no call
                //             //alt=alt[0].split(','); // force split on alt alleles
                //             var refseq = underlyingRefSeq ? underlyingRefSeq : 'ref';
                //
                //             var haveLongSeq = false;
                //             if (refseq.length > 5) {
                //                 haveLongSeq = true;
                //             }
                //             array.forEach( alt , function(thisAlt) {
                //                 if ( thisAlt.match( /\./ ) ) next;
                //                 if ( thisAlt.length > 5) {
                //                     haveLongSeq = true;
                //                 }
                //             });
                //
                //             var gtDisplay = array.map( splitter?gt.split(splitter):gt, function( gtIndex ) {
                //                 gtIndex = parseInt( gtIndex );
                //                 return gtIndex ? ( alt ? alt[gtIndex-1] : gtIndex ) : refseq;
                //             }).join( ' '+splitter+' ' );
                //
                //             var thisPct = ( gCounts[gt] / gTotalCount ) * 100;
                //
                //             var tr = domConstruct.create( 'tr', {}, tableElement );
                //             //domConstruct.create('td', { innerHTML: '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' }, tr );
                //             if ( haveLongSeq ) {
                //                 domConstruct.create('td', { innerHTML: gt.toUpperCase() , title: gtDisplay.toUpperCase() }, tr );
                //             } else {
                //                 domConstruct.create('td', { innerHTML: gtDisplay.toUpperCase() }, tr );
                //             }
                //             domConstruct.create('td', { innerHTML: "<table cellpadding=0 cellspacing=0 border=0><tr><td width="+thisPct+" bgcolor=blue>&nbsp;</td><td>&nbsp;"+thisPct.toFixed(1)+"%</td></tr></table>" }, tr );
                //         }
                //     }
                //     if ( gCounts.hasOwnProperty( noCallgt ) ) {
                //         var noCallPct = ( gCounts[noCallgt] / gTotalCount ) * 100;
                //         var tr = domConstruct.create( 'tr', {}, tableElement );
                //         domConstruct.create('td', { innerHTML: 'NA', title: "no data or call was "+gt }, tr );
                //         domConstruct.create('td', { innerHTML: "<table cellpadding=0 cellspacing=0 border=0><tr><td width="+noCallPct+" bgcolor=blue>&nbsp;</td><td>&nbsp;"+noCallPct.toFixed(1)+"%</td></tr></table>" }, tr );
                //     }
                //     if ( isHaploid ) {
                //         domConstruct.create(
                //                 'p', { className: 'histogram_container big genotype_histogram',
                //                     innerHTML: 'Note: This is a haploid dataset' }, parentElement );
                //     }
                // },

                // _renderGenotypeSummary: function( parentElement, genotypes, alt, underlyingRefSeq ) {
                //     if( ! genotypes )
                //         return;
                //
                //     var counts = new NestedFrequencyTable();
                //     for( var gname in genotypes ) {
                //         if( genotypes.hasOwnProperty( gname ) ) {
                //             // increment the appropriate count
                //             var gt = genotypes[gname].GT;
                //             if( typeof gt == 'object' && 'values' in gt )
                //                 gt = gt.values[0];
                //             if( typeof gt == 'string' )
                //                 gt = gt.split(/\||\//);
                //
                //             if( lang.isArray( gt ) ) {
                //                 // if all zero, non-variant/hom-ref
                //                 if( array.every( gt, function( g ) { return parseInt(g) == 0; }) ) {
                //                     counts.getNested('non-variant').increment('homozygous for reference');
                //                 }
                //                 else if( array.every( gt, function( g ) { return g == '.'; }) ) {
                //                     counts.getNested('non-variant').increment('no call');
                //                 }
                //                 else if( array.every( gt, function( g ) { return g == gt[0]; } ) ) {
                //                     if( alt )
                //                         counts.getNested('variant/homozygous').increment( alt[ parseInt(gt[0])-1 ] + ' variant' );
                //                     else
                //                         counts.getNested('variant').increment( 'homozygous' );
                //                 }
                //                 else {
                //                     counts.getNested('variant').increment('heterozygous');
                //                 }
                //             }
                //         }
                //     }
                //
                //     var total = counts.total();
                //     if( ! total )
                //         return;
                //
                //     var valueContainer = domConstruct.create(
                //             'div', { className: 'value_container big genotype_summary' },
                //             parentElement );
                //     //domConstruct.create('h3', { innerHTML: 'Summary' }, valueContainer);
                //
                //     var tableElement = domConstruct.create('table', {}, valueContainer );
                //
                //     function renderFreqTable( table, level ) {
                //         table.forEach( function( count, categoryName ) {
                //             var tr = domConstruct.create( 'tr', {}, tableElement );
                //             domConstruct.create('td', { className: 'category level_'+level, innerHTML: categoryName }, tr );
                //             if( typeof count == 'object' ) {
                //                 var thisTotal = count.total();
                //                 domConstruct.create('td', { className: 'count level_'+level, innerHTML: thisTotal }, tr );
                //                 domConstruct.create('td', { className: 'pct level_'+level, innerHTML: Math.round(thisTotal/total*10000)/100 + '%' }, tr );
                //                 renderFreqTable( count, level+1 );
                //             } else {
                //                 domConstruct.create('td', { className: 'count level_'+level, innerHTML: count }, tr );
                //                 domConstruct.create('td', { className: 'pct level_'+level, innerHTML: Math.round(count/total*10000)/100+'%' }, tr );
                //             }
                //         });
                //     }
                //
                //     renderFreqTable( counts, 0 );
                //
                //     var totalTR = domConstruct.create('tr',{},tableElement );
                //     domConstruct.create('td', { className: 'category total', innerHTML: 'Total' }, totalTR );
                //     domConstruct.create('td', { className: 'count total', innerHTML: total }, totalTR );
                //     domConstruct.create('td', { className: 'pct total', innerHTML: '100%' }, totalTR );
                // }
            });
        });
