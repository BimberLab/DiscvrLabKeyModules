
export default jbrowse => {
  const {
    Divider,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Chip,
    Tooltip,
    Link,
  } = jbrowse.jbrequire('@material-ui/core')
    var Ajax = require('@labkey/api').Ajax
    var Utils = require('@labkey/api').Utils
    var ActionURL = require('@labkey/api').ActionURL
    const { makeStyles } = jbrowse.jbrequire('@material-ui/core/styles')
    var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");
    var _core = require("@material-ui/core");
    var _SanitizedHTML = _interopRequireDefault(require("@jbrowse/core/ui/SanitizedHTML"));
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')


    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const PropTypes = jbrowse.jbrequire('prop-types')
    const React = jbrowse.jbrequire('react')
    const { useState, useEffect } = React

    const { FeatureDetails, BaseFeatureDetails, BaseCard } = jbrowse.jbrequire(
      '@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail',
    )

    const useStyles = makeStyles(() => ({
        table: {
            padding: 0,

        },
        link: {
            color: 'rgb(0, 0, 238)',
        },
    }))

    function makeGenotypes( parentElement, track, f, featDiv, genotypes ) {
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
        if (lang.isArray(alt) &&  alt.match( /,/ ) ) {
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
    }

    function makeTable(data){
        var tableBodyRows = []
        for(var i in data){
            var line = data[i].split('|')
            tableBodyRows.push(
                <TableRow>
                    <TableCell>{line[1]}</TableCell>
                    <TableCell>{line[2]}</TableCell>
                    <TableCell>{line[3]}</TableCell>
                    <TableCell>{line[9]}</TableCell>
                </TableRow>
            )
        }
        return(
        <Table>
        <TableHead>
            <TableRow>
                <TableCell>Effect</TableCell>
                <TableCell>Impact</TableCell>
                <TableCell>Gene Name</TableCell>
                <TableCell>Position/Consequence</TableCell>
            </TableRow>
        </TableHead>
        <TableBody>
            {tableBodyRows}
        </TableBody>
        </Table>
        )
// A|intron_variant|MODIFIER|NTNG1|ENSMMUG00000008197|transcript|ENSMMUT00000072133.2|protein_coding|2/9|c.247-3863G>T||||||
// A|intron_variant|MODIFIER|NTNG1|ENSMMUG00000008197|transcript|ENSMMUT00000046534.3|protein_coding|1/3|c.247-3863G>T||||||
// Effect | Impact | Gene Name | Position / Consequence
// downstream_gene_variant | MODIFIER | LTB-TNF(ENSMMUG...-ENSMMUT...) | c.247-3863G
// 1 | 2 | 3+4(+6?) | 9+10

    }

    function makeDisplays(feat, displays){
        var propertyJSX = []

        for(var display in displays){
            var tempProp = []
            for(var property in displays[display].properties){
                if(feat["INFO"][displays[display].properties[property]]){
                    //if(displays[display].properties[property] == "ANN"){
                     //   annTable = makeTable(feat["INFO"]["ANN"])
                    //}
                   // else{
                        tempProp.push(
                            <TableRow>
                                 {displays[display].properties[property]}: {feat["INFO"][displays[display].properties[property]]}
                            </TableRow>
                        )
                    //}
                }
            }
            propertyJSX.push(tempProp)
        }

        var displayJSX = []
        for(var i = 0; i < displays.length; i++){
            displayJSX.push(
                <BaseCard title={displays[i].name}>
                   <div style={{ padding: '7px', width: '100%', maxHeight: 600, overflow: 'auto' }}>
                        <Table className={displays[i].name}>
                            <TableHead>
                                {propertyJSX[i]}
                            </TableHead>
                        </Table>
                   </div>
                </BaseCard>
            )
        }
        return displayJSX
    }

    function NewTable(props) {
        const classes = useStyles()
        const { feature } = props
        const { model } = props
        const feat = JSON.parse(JSON.stringify(model.featureData))
        var displays;

        var configDisplays = model.extendedVariantDisplayConfig
        displays = makeDisplays(feat, configDisplays)
        for(var i in configDisplays){
            for(var j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties[j]] = null
            }
        }
        feat["samples"] = null
        var annTable;
        if (feat["INFO"]["ANN"]){
            annTable = makeTable(feat["INFO"]["ANN"])
            feat["INFO"]["ANN"] = null
        }
        return (
            <Paper className={classes.root} data-testid="extended-variant-widget">
                <FeatureDetails
                 feature={feat}
                 {...props}
                 />
                 <BaseCard>
                 <div>
                {annTable}
                </div>
                </BaseCard>
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}