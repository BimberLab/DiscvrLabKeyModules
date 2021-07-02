
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


    function MC(feat) {
        const classes = useStyles()
        if (!feat["INFO"]["MC"]){
            return null
        }
        var lines = feat["INFO"]["MC"][0].split("|")
        var linesJSX = []
        for (let i = 0; i < lines.length; i++) {
            linesJSX.push(lines[i], <br />)
        }
        return (
            <BaseCard title="MC">
               <div style={{ padding: '7px', width: '100%', maxHeight: 600, overflow: 'auto' }}>
                    <Table className={classes.table}>
                        <TableHead>
                            <TableRow>
                                {linesJSX}
                            </TableRow>
                        </TableHead>
                    </Table>
               </div>
            </BaseCard>
        )
    }


    function VariantTable(feat) {
        const classes = useStyles()
        if (!feat["INFO"]["CLNREVSTAT"]){
          return null
        }
        var headers = []
        var rows = []
        for (let i = 0; i<feat["INFO"]["CLNREVSTAT"].length; i++){
          headers.push(<TableCell>{i}</TableCell>)
          rows.push(<TableCell>{feat["INFO"]["CLNREVSTAT"][i]}</TableCell>)
        }

        return (
          <BaseCard title="CLNREVSTAT">
            <div style={{ width: '100%', maxHeight: 600, overflow: 'auto' }}>
                <Table className={classes.table}>
                    <TableHead>
                        <TableRow>
                            {headers}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows}
                    </TableBody>
                </Table>
            </div>
          </BaseCard>
        )
    }

    function makeDisplays(feat, displays){
        var propertyJSX = []
        for(var display in displays){
            var tempProp = []
            for(var property in displays[display].properties){
                tempProp.push(
                    <TableRow>
                        {displays[display].properties[property]}: {feat["INFO"][displays[display].properties[property]]}
                    </TableRow>
                )
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
        const { samples, ...rest } = feat
        var displays;


        var MCJSX = MC(feat)
        if(MCJSX){
            feat["INFO"]["MC"] = null
        }

        var CLNREVSTAT = VariantTable(feat)
        if(CLNREVSTAT){
            feat["INFO"]["CLNREVSTAT"] = null
        }

        var configDisplays = JSON.parse(window.sessionStorage.getItem("displays"))
        displays = makeDisplays(feat, configDisplays)
        for(var i in configDisplays){
            for(var j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties] = null
            }
        }

        return (
            <Paper className={classes.root} data-testid="variant-widget">
                <FeatureDetails
                 feature={rest}
                 {...props}
                 />
                {displays}
                {MCJSX}
                {CLNREVSTAT}
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}