//import BaseFeatureDetailWidget from '@jbrowse/plugin-alignments'
//import { Ajax, Utils, ActionURL } from '@labkey/api'

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
    var displays;
    function getDisplays(){

    }


    function MC(feat) {
      const classes = useStyles()
      // TODO - split on |, return a line for each pipe
      if (!feat["INFO"]["MC"]){
        return null
      }
      var lines = feat["INFO"]["MC"][0].split("|")
      console.log(lines)
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
      //if (!feature.consequence) {
      //  return null
      //}

      //const consequences = feature.consequence.hits.edges
      //TODO - CLNREVSTAT table
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
        console.log(feat)
        const [displays, setDisplays] = useState(null);
        useEffect(() => {
            Ajax.request({
                url: ActionURL.buildURL('jbrowse', 'getSession.api'),
                method: 'GET',
                success: async function(res){
                    console.log("Trying...")
                    let jsonRes = JSON.parse(res.response);
                    setDisplays(makeDisplays(feat, jsonRes.displays))
                    console.log("Did it! Here it is -", displays)
                },
                failure: function(res){
                    console.error("ERROR: Could not get displays from config file.");
                    console.error(res)
                },
                params: {session: session}
            })
        }, []);
        const { samples, ...rest } = feat
        var MCJSX = MC(feat)
        if(MCJSX){
            feat["INFO"]["MC"] = null
        }
        var CLNREVSTAT = VariantTable(feat)
        if(CLNREVSTAT){
            feat["INFO"]["CLNREVSTAT"] = null
        }

        /*var testKey = React.createElement(_core.Tooltip, {
            title: "key!!",
            placement: "left"
          });
        var testValue = React.createElement(_SanitizedHTML.default, {
              html: String("value!!")
            });*/
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
    //            <BaseFeatureDetails feature={feat} {...props} />
   //  <Divider />
    // volvox features
    // ALT
    // CHROM
    // FILTER
    // ID
    // INFO
    //  - AC1
    //  - AF1
    //  - DP
    //  - DP4
    //  - FQ
    //  - MQ
    //  - VDB
    // POS
    // QUAL
    // REF
    // description
    // end
    // refName
    // samples
    // start
    // type
    // uniqueId
    /*            <BaseCard title="NewTable">
                    <div style={{ width: '100%', maxHeight: 600, overflow: 'auto' }}>
                        <Table className={classes.table}>
                            <TableHead>
                                <TableRow>
                                    <TableCell>refName</TableCell>
                                    <TableCell>Description</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                <TableCell>{feat.refName}</TableCell>
                                <TableCell>{feat.description}</TableCell>
                            </TableBody>
                        </Table>
                    </div>
                </BaseCard>
                */

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}