
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
    var Chart = require("react-google-charts").Chart
    var Ajax = require('@labkey/api').Ajax
    var Utils = require('@labkey/api').Utils
    var ActionURL = require('@labkey/api').ActionURL
    const { makeStyles } = jbrowse.jbrequire('@material-ui/core/styles')
    var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");
    var _core = require("@material-ui/core");
    var _styles = require("@material-ui/core/styles")
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
   var useStyles = (0, _styles.makeStyles)(function (theme) {
      return {
          table: {
              padding: 0,
              display: 'block'
          },
          link: {
              color: 'rgb(0, 0, 238)',
          },
          message: {
              paddingTop: theme.spacing(5),
              paddingLeft: theme.spacing(5),
              paddingRight: theme.spacing(5),
              maxWidth: 500
          },
        expansionPanelDetails: {
          display: 'block',
          padding: theme.spacing(1)
        },
        expandIcon: {
          color: '#FFFFFF'
        },
        paperRoot: {
          background: theme.palette.grey[100]
        },
        field: {
          display: 'flex',
          flexWrap: 'wrap'
        },
        fieldDescription: {
          '&:hover': {
            background: 'yellow'
          }
        },
        fieldName: {
          wordBreak: 'break-all',
          minWidth: '90px',
          maxWidth: '150px',
          borderBottom: '1px solid #0003',
          background: theme.palette.grey[200],
          marginRight: theme.spacing(1),
          padding: theme.spacing(0.5)
        },
        fieldValue: {
          wordBreak: 'break-word',
          maxHeight: 300,
          padding: theme.spacing(0.5),
          overflow: 'auto'
        },
        fieldSubValue: {
          wordBreak: 'break-word',
          maxHeight: 300,
          padding: theme.spacing(0.5),
          background: theme.palette.grey[100],
          border: "1px solid ".concat(theme.palette.grey[300]),
          boxSizing: 'border-box',
          overflow: 'auto'
        },
        accordionBorder: {
          marginTop: '4px',
          border: '1px solid #444'
        }
      };
    });

    function round(value, decimals) {
        return Number(Math.round(value+'e'+decimals)+'e-'+decimals);
    }

    function makeTable(data, classes){
        var tableBodyRows = []
        for(var i in data){
            var line = data[i].split('|')
            if(line[10]){
                line[10] = <div>{line[10]}</div>
            }

            tableBodyRows.push(
                <TableRow>
                        <TableCell>{line[1]}</TableCell>
                        <TableCell>{line[2]}</TableCell>
                        <TableCell>{line[3]} {line[4]}</TableCell>
                        <TableCell>{line[9]} {line[10]}</TableCell>
                </TableRow>
            )
        }
        return(
        <BaseCard title="Predicted Function">
            <Table className={classes.table}>
                <TableHead>
                    <TableRow className={classes.paperRoot}>
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
        </BaseCard>
        )

// A|intron_variant|MODIFIER|NTNG1|ENSMMUG00000008197|transcript|ENSMMUT00000072133.2|protein_coding|2/9|c.247-3863G>T||||||
// 0|1             |2       |3    |4                 |5         |6                   |7             |8  |9            ||||||
// Effect | Impact | Gene Name | Position / Consequence
// downstream_gene_variant | MODIFIER | LTB-TNF(ENSMMUG...-ENSMMUT...) | c.247-3863G
// 1 | 2 | 3+4 ask | 9+10

    }

    function makeDisplays(feat, displays, classes){
        var propertyJSX = []

        for(var display in displays){
            var tempProp = []
            for(var property in displays[display].properties){
                if(feat["INFO"][displays[display].properties[property]]){
                    if(feat["INFO"][displays[display].properties[property]].length == 1){
                        tempProp.push(
                            <div className={classes.field}>
                                <div className={classes.fieldName}>
                                    {displays[display].properties[property]}
                                </div>
                                <div className={classes.fieldValue}>
                                    {feat["INFO"][displays[display].properties[property]]}
                                </div>
                            </div>
                        )
                    }else if(feat["INFO"][displays[display].properties[property]].length > 1){
                        var children = []
                        for(var val in feat["INFO"][displays[display].properties[property]]){
                            children.push(
                                <div className={classes.fieldSubValue}>
                                    {feat["INFO"][displays[display].properties[property]][val]}
                                </div>
                            )
                        }
                        tempProp.push(
                            <div className={classes.field}>
                                <div className={classes.fieldName}>
                                    {displays[display].properties[property]}
                                </div>
                                {children}
                            </div>
                        )
                    }
                }
            }
            if(tempProp.length != 0){
                propertyJSX.push(tempProp)
            }
        }
                 /* <div style={{ padding: '7px', width: '100%', maxHeight: 600, overflow: 'auto' }}>
                        <Table className={displays[i].name}>
                            <TableHead>
                                {propertyJSX[i]}
                            </TableHead>
                        </Table>
                   </div>*/
        var displayJSX = []
        for(var i = 0; i < propertyJSX.length; i++){
            displayJSX.push(
                <BaseCard title={displays[i].name}>
                    {propertyJSX[i]}
                </BaseCard>
            )
        }
        return displayJSX
    }

    function makeChart(samples, ref, alt, classes){
        const [state, setState] = useState(null)
        useEffect(() => {
            setState(
                <BaseCard title="Genotypes">
                    <div>
                    Loading genotypes...
                    </div>
                </BaseCard>
            )
            var alleleCounts = {}
            var alleleTotal = 0
            alleleCounts[ref] = 0
            for(var i in alt){
                alleleCounts[alt[i]] = 0
            }
            var gtCounts = {}
            var gtTotal = 0
            for(var sample in samples){
                var gt = samples[sample]["GT"]
                for(var entry in gt){
                    // CASES
                    // ./. -- no call
                    if(gt[entry] == "./."){
                        if(gtCounts["No Call"]){                          // if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts["No Call"] = gtCounts["No Call"] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1                         // increment our total count
                        }
                        else {                                   // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts["No Call"] = 1
                            gtTotal = gtTotal + 1                // increment our total count
                        }
                    }
                    // int / int
                    else if(gt[entry]){
                        var gtKey;                               // should be an array of len 2 after split
                        if (gt[entry].includes("/")){            // unphased gts split on /
                            gtKey = gt[entry].split("/")
                        }
                        else if(gt[entry].includes("|")){        // phased gts split on |
                            gtKey = gt[entry].split("|")
                        }
                        for(var gtVal in gtKey){
                            if(gtKey[gtVal] == 0){
                                gtKey[gtVal] = ref
                                alleleCounts[ref] = alleleCounts[ref] + 1 // tick up allele count
                                alleleTotal = alleleTotal + 1
                            }
                            else{
                                gtKey[gtVal] = alt[gtKey[gtVal]-1]
                                alleleCounts[gtKey[gtVal]] = alleleCounts[gtKey[gtVal]] + 1 // tick up allele count
                                alleleTotal = alleleTotal + 1
                            }
                        }
                        gtKey = gtKey[0] + "/" + gtKey[1]         // for the purposes of the chart, phased/unphased can be counted as the same
                       if(gtCounts[gtKey]){                       // if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts[gtKey] = gtCounts[gtKey] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1                 // increment our total count
                        }
                        else {                                    // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts[gtKey] = 1
                            gtTotal = gtTotal + 1                 // increment our total count
                        }
                    }
                }
            }
            var gtBarData = [
            [
                'Genotype',
                'Total Count',
                { role: 'style' },
                {
                  sourceColumn: 0,
                  role: 'annotation',
                  type: 'string',
                  calc: 'stringify',
                },
            ]]
            for(var entry in gtCounts){
                var rounded = round(gtCounts[entry]/gtTotal*100, 1)
                gtBarData.push(
                    [entry, gtCounts[entry], "#0088FF", rounded+"%"]
                )
            }

            var alleleTableRows = []
            for(var allele in alleleCounts){
                alleleTableRows.push(
                    <TableRow>
                            <TableCell>{allele}</TableCell>
                            <TableCell>{round(alleleCounts[allele]/alleleTotal, 4)}</TableCell>
                    </TableRow>
                )
            }
            setState(
            <div>
                <BaseCard title="Allele Frequencies">
                    <Table className={classes.table}>
                        <TableHead>
                            <TableRow className={classes.paperRoot}>
                                    <TableCell>Sequence</TableCell>
                                    <TableCell>Fraction</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {alleleTableRows}
                        </TableBody>
                    </Table>
                </BaseCard>
                <BaseCard title="Genotypes">
                     <Chart
                       width={'250px'}
                       height={'200px'}
                       chartType="BarChart"
                       loader={<div>Loading Chart</div>}
                       data={gtBarData}
                       options={{
                         title: 'Genotype Frequency',
                         width: 300,
                         height: 200,
                         bar: { groupWidth: '95%' },
                         legend: { position: 'none' },
                       }}
                       // For tests
                       rootProps={{ 'data-testid': '6' }}
                     />
                </BaseCard>
            </div>)
        }, []);
        return state
    }
    function NewTable(props) {
        const classes = useStyles()
        const { feature } = props
        const { model } = props
        const feat = JSON.parse(JSON.stringify(model.featureData))
        const samples = model.featureData.samples
        console.log(model.featureData)
        feat["samples"] = null

        var displays;
        var configDisplays = model.extendedVariantDisplayConfig
        displays = makeDisplays(feat, configDisplays, classes)
        for(var i in configDisplays){
            for(var j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties[j]] = null
            }
        }

        var annTable;
        if (feat["INFO"]["ANN"]){
            annTable = makeTable(feat["INFO"]["ANN"], classes)
            feat["INFO"]["ANN"] = null
        }

        var message;
        if (model.message){
            message = <div className={classes.message} >{model.message}</div>
        }
        return (
            <Paper className={classes.root} data-testid="extended-variant-widget">
                {message}
                <FeatureDetails
                 feature={feat}
                 {...props}
                 />
                 {displays}
                 {annTable}
                 {makeChart(samples, feat["REF"], feat["ALT"], classes)}
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}