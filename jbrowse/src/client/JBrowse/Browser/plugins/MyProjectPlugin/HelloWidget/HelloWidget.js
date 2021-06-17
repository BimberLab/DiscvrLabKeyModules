//import BaseFeatureDetailWidget from '@jbrowse/plugin-alignments'

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

   const { makeStyles } = jbrowse.jbrequire('@material-ui/core/styles')

    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const PropTypes = jbrowse.jbrequire('prop-types')
    const React = jbrowse.jbrequire('react')
    const { useState, useEffect } = React

    const { BaseFeatureDetails, BaseCard } = jbrowse.jbrequire(
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

    function NewTable(props) {
        const classes = useStyles()
        const { feature } = props
        const { model } = props
        const feat = JSON.parse(JSON.stringify(model.featureData))
        console.log(feat)
        return (
        <Paper className={classes.root} data-testid="hello-widget">
            <BaseCard title="NewTable">
                <div style={{ width: '100%', maxHeight: 600, overflow: 'auto' }}>
                    <Table className={classes.table}>
                        <TableHead>
                            <TableRow>
                            <TableCell>{feat.refName}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell>Hello</TableCell>
                            </TableRow>
                        </TableHead>
                    </Table>
                </div>
            </BaseCard>
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

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}