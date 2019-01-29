/**
 copy of JBrowse/View/FeatureGlyph/Diamond.js that forces width = height
 */

define(['dojo/_base/declare',
            'dojo/_base/lang',
            'JBrowse/View/FeatureGlyph/Box'],
        function(declare,
                 lang,
                 Box) {

            return declare(Box, {

                renderBox: function( context, viewInfo, feature, top, overallHeight, parentFeature, style ) {

                    var left  = viewInfo.block.bpToX( feature.get('start') );
                    //var width = viewInfo.block.bpToX( feature.get('end') ) - left;
                    //left = Math.round( left );
                    //width = Math.round( width );

                    style = style || lang.hitch( this, 'getStyle' );

                    var height = this._getFeatureHeight( viewInfo, feature );
                    if( ! height )
                        return;
                    if( height != overallHeight )
                        top += Math.round( (overallHeight - height)/2 );

                    var width = height;

                    // background
                    var bgcolor = style( feature, 'color' );
                    if( bgcolor ) {
                        context.fillStyle = bgcolor;
                        context.beginPath();
                        context.moveTo(left,top+height/2);
                        context.lineTo(left + Math.max(1,width)/2,top);
                        context.lineTo(left+Math.max(1,width),top+height/2);
                        context.lineTo(left + Math.max(1,width)/2,top+height);
                        context.closePath();
                        context.fill();
                    }
                    else {
                        context.clearRect( left, top, Math.max(1,width), height );
                    }

                    // foreground border
                    var borderColor, lineWidth;
                    if( (borderColor = style( feature, 'borderColor' )) && ( lineWidth = style( feature, 'borderWidth')) ) {
                        if( width > 3 ) {
                            context.lineWidth = lineWidth;
                            context.strokeStyle = borderColor;

                            // need to stroke a smaller rectangle to remain within
                            // the bounds of the feature's overall height and
                            // width, because of the way stroking is done in
                            // canvas.  thus the +0.5 and -1 business.
                            //context.stroke();
                            context.beginPath();
                            context.moveTo(left,top+height/2);
                            context.lineTo(left + Math.max(1,width)/2,top);
                            context.lineTo(left+Math.max(1,width),top+height/2);
                            context.lineTo(left + Math.max(1,width)/2,top+height);
                            context.closePath();
                            context.stroke();
                        }
                        else {
                            context.globalAlpha = lineWidth*2/width;
                            context.fillStyle = borderColor;
                            context.beginPath();
                            context.moveTo(left,top+height/2);
                            context.lineTo(left + Math.max(1,width)/2,top);
                            context.lineTo(left+Math.max(1,width),top+height/2);
                            context.lineTo(left + Math.max(1,width)/2,top+height);
                            context.closePath();
                            context.fill();
                            context.globalAlpha = 1;

                        }

                    }
                },

                makeFeatureDescriptionLabel: function(feature){
                    var ret = this.inherited(arguments);

                    if (feature.get('start')){
                        var realStart = feature.get('start') + 1;
                        ret.text += '<br>Position: ' + realStart;
                    }

                    var effArray = feature.get('ANN') || [];
                    if( effArray &&  typeof effArray === 'object' && 'values' in effArray )
                        effArray = effArray.values;
                    if( effArray && ! Array.isArray( effArray ) )
                        effArray = [effArray];

                    var texts = [];
                    for ( var i in effArray ) {
                        if ( /HIGH/.test ( effArray[i] ) ) { texts.push("<br>SnpEff: Predicted High Impact"); }
                        if ( /MODERATE/.test ( effArray[i] ) ) { texts.push("<br>SnpEff: Predicted Moderate Impact"); }
                    }

                    var unique = {};
                    texts = dojo.filter(texts, function(value) {
                        if (!unique[value]) {
                            unique[value] = true;
                            return true;
                        }
                        return false;
                    }).sort();

                    ret.text += texts.join('');


                    return ret;
                }

            });
        });
