
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
            propertyJSX.push(tempProp)
        }
                 /* <div style={{ padding: '7px', width: '100%', maxHeight: 600, overflow: 'auto' }}>
                        <Table className={displays[i].name}>
                            <TableHead>
                                {propertyJSX[i]}
                            </TableHead>
                        </Table>
                   </div>*/
        var displayJSX = []
        for(var i = 0; i < displays.length; i++){
            displayJSX.push(
                <BaseCard title={displays[i].name}>
                    {propertyJSX[i]}
                </BaseCard>
            )
        }
        return displayJSX
    }

    function makeChart(samples){
        const [state, setState] = useState(null)
        useEffect(() => {
            setState(
                <BaseCard title="Genotypes">
                    <div>
                    Loading genotypes...
                    </div>
                </BaseCard>
            )
            var gtCounts = {}
            var gtTotal = 0;
            for(var i in samples){ // samples
                for(var gt in samples[i]){ // genotypes
                    if(samples[i][gt]){ // if genotype is not null, increment count
                        if(gtCounts[gt]){// if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts[gt] = gtCounts[gt] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1 // increment our total count
                        }
                        else { // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts[gt] = 1
                            gtTotal = gtTotal + 1 // increment our total count
                        }
                    }
                    else if(!samples[i][gt] && !gtCounts[gt]){ // if genotype is null and we don't have an entry for it
                        gtCounts[gt] = 0 // make an entry with no counts
                    }
                }
            }
            var gtBarData = [
            [
                'Genotype',
                'Percent',
                { role: 'style' },
                {
                  sourceColumn: 0,
                  role: 'annotation',
                  type: 'string',
                  calc: 'stringify',
                },
            ]]
            var color = 0
            for(var entry in gtCounts){
                if(color == 0){
                    gtBarData.push(
                        [entry, gtCounts[entry]/gtTotal, "#0088FF", null] // blue
                    )
                    color = 1
                }
                else if(color == 1){
                    gtBarData.push(
                        [entry, gtCounts[entry]/gtTotal, "#76BEFE", null] // light blue
                    )
                    color = 0
                }
            }
            setState(
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
                </BaseCard>)
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
                 {makeChart(samples)}
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}