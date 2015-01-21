/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('SequenceAnalysis.Utils');

SequenceAnalysis.Utils = new function(){
    return {
        isEmptyObj: function(ob){
            for(var i in ob){
                return false;
            }
            return true;
        },

        /**
         * The purpose of this helper is to provide a listing of all items to display in the sequence module navigation pages.  It draws from
         * any items registered with SequenceAnalysisService.
         * @param [config.includeHidden] Optional. If true, non-visible items will be included.
         * @param config.success Success callback.  Will be passed a single object as an argument with the following properties
         * @param config.failure Error callback
         * @param config.scope Scope of callbacks
         * @private
         */
        getDataItems: function(config){
            config = config || {};

            var params = {};
            if (config.includeHidden)
                params.includeAll = config.includeHidden;

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('sequenceanalysis', 'getDataItems', config.containerPath, params),
                method : 'GET',
                failure: LDK.Utils.getErrorCallback({
                    callback: config.failure
                }),
                success: function(response){
                    var json = Ext4.decode(response.responseText);
                    if (config.success)
                        config.success.call((config.scope || this), json);
                }
            };

            return LABKEY.Ajax.request(requestConfig);
        },

        //adapted from: http://werxltd.com/wp/2011/03/08/calculate-a-color-gradient-in-javascript/
        ColorGradient: {
            hexdec: function(hex_string) {
                hex_string = (hex_string + '').replace(/[^a-f0-9]/gi, '');
                return parseInt(hex_string, 16);
            },

            dechex: function(number) {
                if (number < 0) {
                    number = 0xFFFFFFFF + number + 1;
                }
                return parseInt(number, 10).toString(16);
            },

            pad: function(number, length) {
                var str = '' + number;
                while (str.length < length) {
                    str = '0' + str;
                }
                return str;
            },

            calcgrad: function(val, color1, color2) {
                var cg = SequenceAnalysis.Utils.ColorGradient;
        //        if(!color1.match(/^#[0-9a-f]{6}/) || !color2.match(/^#[0-9a-f]{6}/)) return 'match err!';

                if (val > 1) {
                    val = 1;
                }
                if (val < 0) {
                    val = 0;
                }
                val = parseFloat(val);

                var c1 = [cg.hexdec(color1.substr(1,2)), cg.hexdec(color1.substr(3,2)), cg.hexdec(color1.substr(5,2))];
                var c2 = [cg.hexdec(color2.substr(1,2)), cg.hexdec(color2.substr(3,2)), cg.hexdec(color2.substr(5,2))];

                var arrColor = [];
                var color;
                for(var i=0;i<3;i++){
                    color = Math.max(c1[i], c2[i]) - Math.abs(c1[i]-c2[i])*val;
                    arrColor.push(color);

                }
                return '#'+cg.pad(cg.dechex(arrColor[0]),2)+cg.pad(cg.dechex(arrColor[1]),2)+cg.pad(cg.dechex(arrColor[2]),2);
            }
        },

        calcGradient: function(color, steps, pos, idx, snp){
            var increment = 100 / steps;
            var level;

            for (var i=0;i<=steps;i++){
                if(snp.adj_percent > (i*increment) && snp.adj_percent <= ((i+1)*increment)){
                    level = i;
                    break;
                }
            }

            //we set a gradient with X steps, between the color and %50 of its distinct from white
            //0.1 sets a minimum of 20% above white
            var val = (1/steps)*level + 0.2;
        //        if(snp.adj_percent> 10){
        //            console.log('SNP over 10%: '+pos+'/'+snp.adj_percent+'/'+level);
        //        }
            color = SequenceAnalysis.Utils.ColorGradient.calcgrad(val, '#FFFFFF', color);
            return color;
        },

        calcNTPositionForAA: function(aa, pos){
            var exons = aa.exonMap;
            var nt_positions = [];
            var codonPos = 1;
            var nt_position = 3 * pos - 2; //the position of the first NT in this codon

            //these will track the NT position of the current protein
            var startNT = 1;
            var endNT = 0;
            var exonLength;
            var exonPosition = 0;
            var exons = [];
            Ext4.each(aa.exonMap, function(exon, idx){
                exonLength = exon[1] - exon[0] +  1;
                endNT = startNT + exonLength - 1;
                while(nt_position >= startNT && nt_position <= endNT){
                    //find the corresponding position in this exon
                    exonPosition = nt_position - startNT + exon[0];
                    nt_positions.push(exonPosition);
                    exons.push(idx);
                    if(nt_positions.length == 3)
                        return false;  //jump to next exon

                    nt_position++;
                }

                startNT += exonLength;
            }, this);

            exons = Ext4.Array.unique(exons);

            return {
                nt_positions: nt_positions,
                exons: exons
            };
        }
    }
}
