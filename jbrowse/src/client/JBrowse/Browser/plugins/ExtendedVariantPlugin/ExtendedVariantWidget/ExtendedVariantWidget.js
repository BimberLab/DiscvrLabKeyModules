
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
    //import { readConfObject } from '@jbrowse/core/configuration'
    var Ajax = require('@labkey/api').Ajax
    var Utils = require('@labkey/api').Utils
    var ActionURL = require('@labkey/api').ActionURL
    const { makeStyles } = jbrowse.jbrequire('@material-ui/core/styles')
    var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");
    var _core = require("@material-ui/core");
    var _SanitizedHTML = _interopRequireDefault(require("@jbrowse/core/ui/SanitizedHTML"));
    var _configuration = _interopRequireDefault(require("@jbrowse/core/configuration"))
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

    function makeDisplays(feat, displays){
        var propertyJSX = []
        for(var display in displays){
            var tempProp = []
            for(var property in displays[display].properties){
                if(feat["INFO"][displays[display].properties[property]]){
                    tempProp.push(
                        <TableRow>
                            {displays[display].properties[property]}: {feat["INFO"][displays[display].properties[property]]}
                        </TableRow>
                    )
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

        var parentTrackId = model.id.slice(8)
        var configDisplays = model.widgetDisplayInfo
        displays = makeDisplays(feat, configDisplays)
        for(var i in configDisplays){
            for(var j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties[j]] = null
            }
        }

        return (
            <Paper className={classes.root} data-testid="extended-variant-widget">
                <FeatureDetails
                 feature={feat}
                 {...props}
                 />
                {displays}
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}