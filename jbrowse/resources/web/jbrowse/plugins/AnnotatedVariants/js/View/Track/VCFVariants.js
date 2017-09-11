define([
            'dojo/_base/declare',
            'dojo/promise/all',
            'dojo/_base/array',
            'dojo/_base/lang',
            'JBrowse/View/Track/CanvasFeatures',
            //'./_VariantTrackVariantDetailsMixin',
            'JBrowse/Util'
        ],
        function(
                declare,
                all,
                array,
                lang,
                CanvasFeatures,
                //VariantTrackDetailMixin,
                //VariantDetailMixin,
                Util
        ) {
            return declare([ CanvasFeatures ], {
                //    return declare([ CanvasFeatures, VariantDetailMixin ], {
                constructor: function(args) {
                    //console.log('VCFVariants track added');
                },

                _isPassing: function(feat){
                    var f = feat.get('filter');
                    try {
                        return f.values.join('').toUpperCase() == 'PASS';
                    } catch(e) {
                        return f.toUpperCase() == 'PASS';
                    }
                },

                _defaultConfig: function() {
                    var thisB = this;
                    return Util.deepUpdate(
                            lang.clone(this.inherited(arguments)),
                            {
                                glyph : function(feature) {
                                    var thisGlyph = "AnnotatedVariants/View/FeatureGlyph/Diamond";
                                    if (feature.get('Type')) {
                                        if (! /SNV/.test ( feature.get('Type') ) ) { thisGlyph = "JBrowse/View/FeatureGlyph/Box"; }
                                    }

                                    if (!thisB._isPassing(feature)){
                                        thisGlyph = "JBrowse/View/FeatureGlyph/Box";
                                    }
                                    return thisGlyph;
                                },

                                style: {
                                    showLabels: false,

                                    color: function(feature){
                                        var effArray = feature.get('ANN') || [];
                                        if( effArray &&  typeof effArray == 'object' && 'values' in effArray )
                                            effArray = effArray.values;
                                        if( effArray && ! Array.isArray( effArray ) )
                                            effArray = [effArray];
                                        var isHigh = false;
                                        var isModerate = false;
                                        for ( var i in effArray ) {
                                            if ( /HIGH/.test ( effArray[i] ) ) { isHigh = true; }
                                            if ( /MODERATE/.test ( effArray[i] ) ) { isModerate = true; }
                                        }
                                        if ( isHigh ) { return 'red'; }
                                        if ( isModerate ) { return 'goldenrod'; }
                                        /* switch logic here to return blue for SNV or mutation feature types */
                                        if (feature.get('type') == 'mutation') {
                                            return 'blue';
                                        }
                                        else {
                                            if (feature.get('type') != 'SNV') {
                                                return 'green';
                                            }
                                            else {
                                                return 'blue';
                                            }
                                        }
                                    }
                                },

                                displayMode: 'normal',

                                onClick: {
                                    style: "width:700px",
                                    content: function(track, feature, featDiv, container) {
                                        //var url = thisB.resolveUrl(thisB.config.genotypeUrlTemplate, { name: feature.get('name'), vcfws_proteomeid: thisB.config.vcfws_proteomeid, vcfws_dataset: thisB.config.vcfws_dataset });
                                        //console.log("genotypeUrl: "+url);
                                        //var ret = dojo.xhrGet({
                                        //    url: url
                                        //}).then(function(genotypeUrlResult) {
                                            var content = track.defaultFeatureDetail(track, feature, featDiv, container, '{}');
                                            return content;
                                        //});
                                        return ret;
                                    },
                                    title: function(track, feature, div) {
                                        var ret = "Variant Call {name}";
                                        if (feature.get('start')) {
                                            /* add one back to "start" to match JBrowse interbase coordinates for VCF data (one is subtracted for rendering because all other data types are interbase?) */
                                            var realStart = feature.get('start') + 1;
                                            ret += ' at position ' + realStart;
                                        }
                                        ret += ": {description}";
                                        if (feature.get('score')) {
                                            ret += ' (score: ' + feature.get('score') + ')';
                                        }
                                        return ret;
                                    }
                                }
                            });
                },

                _trackMenuOptions: function() {
                    var thisB = this;

                    // disable default VCF FILTER column filters
                    return all([ this.inherited(arguments), this._snpeffImpactFilterTrackMenuOptions() ])
                            .then( function( options ) {
                                var o = options.shift();
                                var allowed = ['About this track', 'Pin to top', 'Edit config', 'Delete track', 'Save track data'];
                                o = array.filter(o, function(item) {
                                    return allowed.indexOf(item.label) == -1;
                                });

                                if (o[0].type == 'dijit/MenuSeparator'){
                                    o.shift();
                                }

                                o.push( { type: 'dijit/MenuSeparator' } );
                                o.push({
                                    label: 'Show sites not passing filters',
                                    type: "dijit/CheckedMenuItem",
                                    checked: thisB.config.filterEnabled['SHOW_FILTERED'] !== false,
                                    onClick: (function(filterName){ return function() {
                                        if (!this.checked) {
                                            thisB.addFeatureFilter(function(f) {
                                                var f = f.get('filter');
                                                try {
                                                    return f.values.join('').toUpperCase() == 'PASS';
                                                } catch(e) {
                                                    return f.toUpperCase() == 'PASS';
                                                }
                                            }, filterName);
                                        }
                                        else {
                                            thisB.removeFeatureFilter(filterName);
                                        }
                                        thisB.config.filterEnabled[filterName] = this.checked;
                                        thisB.redraw();
                                    }})('SHOW_FILTERED')
                                });

                                return o.concat.apply( o, options );
                            });
                },

                _snpeffImpactFilterTrackMenuOptions: function() {
                    var opts = [];
                    var thisB = this;
                    var impactList = ["HIGH", "MODERATE", "LOW", "MODIFIER"];
                    for( var i in impactList ) {
                        var filterName = impactList[i];
                        if (!(thisB.config.filterEnabled))
                            thisB.config.filterEnabled = {};
                        // default to true if not set or not already false due to a menu click
                        if (thisB.config.filterEnabled[filterName] != false) {
                            thisB.config.filterEnabled[filterName] = true;
                        }
                        opts.push({
                            label: 'Show sites with SnpEff IMPACT "'+filterName+'"',
                            type: "dijit/CheckedMenuItem",
                            checked: !!thisB.config.filterEnabled[filterName],
                            onClick: (function(filterName){ return function() {
                                if(!this.checked) {
                                    thisB.addFeatureFilter(function(feat) {
                                        var thisB = this;
                                        var effArray = feat.get('ANN');
                                        if( effArray &&  typeof effArray == 'object' && 'values' in effArray )
                                            effArray = effArray.values;
                                        if( effArray && ! lang.isArray( effArray ) )
                                            effArray = [effArray];
                                        // don't filter if nothing to filter on
                                        if (! effArray)
                                            return true;
                                        var impactList = ["HIGH", "MODERATE", "LOW", "MODIFIER"];
                                        for( var j in impactList ) {
                                            var thisFilter = impactList[j];
                                            // don't filter if any other snpeff impact filter is enabled and matched
                                            if (thisB.config.filterEnabled[thisFilter]) {
                                                var otherTest = new RegExp(thisFilter);
                                                for ( var k in effArray ) {
                                                    if (effArray[k] == filterName)
                                                        next;
                                                    var line = effArray[k];
                                                    if ( thisFilter.search( otherTest) != -1 && line.search( otherTest ) != -1 ) {
                                                        return true;
                                                    }
                                                }
                                            }
                                        }
                                        var thisTest = new RegExp(filterName);
                                        // filter if still here and we match the unchecked impact filter
                                        for ( var k in effArray ) {
                                            var line = effArray[k];
                                            if ( filterName.search( thisTest) != -1 && line.search( thisTest ) != -1 ) {
                                                return false;
                                            }
                                        }
                                        return true;
                                    }, 'snpeff_'+filterName);
                                }
                                else {
                                    thisB.removeFeatureFilter('snpeff_'+filterName);
                                }
                                thisB.config.filterEnabled[filterName] = this.checked;
                                thisB.redraw();
                            }})(filterName)
                        });
                    }

                    return opts;
                }
            });
        });
