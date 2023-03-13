import { observer } from 'mobx-react';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { Widget } from '@jbrowse/core/util';
import { AppBar, Box, Button, Dialog, Grid, MenuItem, Toolbar, Typography, Paper } from '@material-ui/core';
import ScopedCssBaseline from '@material-ui/core/ScopedCssBaseline';
import { DataGrid, GridColDef, GridRenderCellParams, GridToolbar } from '@mui/x-data-grid';
import { columns } from '../constants';
import { APIDataToRows } from '../dataUtils';

import '../VariantTable.css';
import '../../jbrowse.css';
import { getGenotypeURL, navigateToBrowser, fetchLuceneQuery } from '../../utils';
import LoadingIndicator from './LoadingIndicator';
import { Row } from '../types';
import Search from './Search';

const VariantTableWidget = observer(props => {
  const { assembly, assemblyName, trackId, locString, parsedLocString, sessionId, session, pluginManager } = props
  const { view } = session

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  if (!track) {
    return (<p>Unknown track: {trackId}</p>)
  }

  function handleSearch(data) {
    console.log("handlesearch data:", data)
    setFeatures(APIDataToRows(data.data, trackId))
  }

  function handleMenu(item) {
    switch(item) {
      case "filterSample":
        session.showWidget(sampleFilterWidget)
        addActiveWidgetId(sampleFilterWidget.id)
        break;
      case "filterInfo":
        session.showWidget(infoFilterWidget)
        addActiveWidgetId(infoFilterWidget.id)
        break;
      case "browserRedirect":
        navigateToBrowser(sessionId, locString, trackId, track)
        break;
    }
  }

  // Manager for the activeWidgetList
  function addActiveWidgetId(id: string) {
    setActiveWidgetList([id, ...activeWidgetList])
  }

  // MaterialUI modal handlers
  function handleModalClose(widget) {
    session.hideWidget(widget)
  }
  
  // Menu handlers
  function handleMenuClick(e, set) {
    set(e.currentTarget)
  }

  function handleMenuClose(set) {
    set(null)
  }

  // Contains all features from the API call once the useEffect finished
  //const [features, setFeatures] = useState<ExtendedVcfFeature[]>([])
  const [features, setFeatures] = useState<Row[]>([])

  // Active widget ID list to force rerender when a JBrowseUIButton is clicked
  const [activeWidgetList, setActiveWidgetList] = useState<string[]>([])

  // Widget states
  const [sampleFilterWidget, setSampleFilterWidget] = useState<Widget | undefined>()
  const [infoFilterWidget, setInfoFilterWidget] = useState<Widget | undefined>()

  // Menu management
  const [anchorFilterMenu, setAnchorFilterMenu] = useState(null)
  const [isValidLocString, setIsValidLocString] = useState(true)

  // False until initial data load or an error:
  const [dataLoaded, setDataLoaded] = useState(!parsedLocString)

  const [pageSize, setPageSize] = React.useState<number>(25);

  // API call to retrieve the requested features.
  useEffect(() => {
    async function fetch() {
    const queryParam = new URLSearchParams(window.location.search)

      fetchLuceneQuery(queryParam.get('searchString'), sessionId, queryParam.get('offset'), (res) => {
        setFeatures(resToArray(queryParam.get('offset')))
        setDataLoaded(true)
      })
    }

    fetch()

  }, [pluginManager, parsedLocString, session.visibleWidget])

  if (!view) {
      return
  }

  if (!track) {
      return(<p>Unable to find track: {trackId}</p>)
  }

  const showDetailsWidget = (rowIdx: number) => {
    const feature = features[rowIdx]
    const trackId = getConf(track, 'trackId')
    const detailsConfig = getConf(track, ['displays', '0', 'detailsConfig'])
    const widgetId = 'Variant-' + trackId;
    const featureWidget = session.addWidget(
        'ExtendedVariantWidget',
        widgetId,
        {
          featureData: feature,
          trackId: trackId,
          message: '',
          detailsConfig: detailsConfig
        }
    )

    session.showWidget(featureWidget)
  }

  const actionsCol: GridColDef = {
    field: 'actions',
    headerName: 'Actions',
    width: 50,
    flex: 1,
    headerAlign: 'left',
    renderCell: (params: GridRenderCellParams) => {
      return (
          <>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'flex-start',
                flexDirection: 'column'
              }}
            >
              <Box sx={{lineHeight: '20px'}}><a className={"labkey-text-link"} onClick={() => { showDetailsWidget(params.row.id) }}>Variant Details</a></Box>
              <Box sx={{lineHeight: '20px'}}><a className={"labkey-text-link"} target="_blank" href={getGenotypeURL(params.row.trackId, params.row.chrom, params.row.start, params.row.end)}>View Genotypes</a></Box>
            </Box>
          </>
      )
    }
  }

  const gridElement = (
    <DataGrid
        columns={[...columns, actionsCol]}
        rows={features}
        //rows={features.map((rawFeature, id) => rawFeatureToRow(rawFeature, id, trackId))}
        components={{ Toolbar: GridToolbar }}
        rowsPerPageOptions={[10,25,50,100]}
        pageSize={pageSize}
        onPageSizeChange={(newPageSize) => setPageSize(newPageSize)}
      />
  )

  return (
    <>
      <LoadingIndicator isOpen={!dataLoaded}/>
      {
        [...session.activeWidgets].map((elem) => {
          const widget = elem[1]
          const widgetType = pluginManager.getWidgetType(widget.type)
          const { ReactComponent } = widgetType
          const { visibleWidget } = session

          // Note: this is based on ModalWidget.tsx from JBrowse.
          return (
          <Dialog onClose={() => handleModalClose(widget)} open={true} key={widget.id}>
            <Paper>
              <AppBar position="static">
                <Toolbar>
                    <Typography variant="h6">{widgetType.heading}</Typography>
                </Toolbar>
              </AppBar>
              <ScopedCssBaseline>
                <ReactComponent model={visibleWidget}/>
              </ScopedCssBaseline>
            </Paper>
          </Dialog>
          )
        })
      }

      <div style={{marginBottom: "10px"}}>
        <Grid container spacing={1} justifyContent="flex-start" alignItems="center">
          <Grid key='searchButton' item xs="auto">
            <Search sessionId={sessionId} handleSubmitCallback={(data) => handleSearch(data)}/>
          </Grid>

          {/*<Grid key='filterMenu' item xs="auto">
            <MenuButton disabled={!isValidLocString} id={'filterMenu'} color="primary" variant="contained" text="Filter" anchor={anchorFilterMenu}
              handleClick={(e) => handleMenuClick(e, setAnchorFilterMenu)}
              handleClose={(e) => handleMenuClose(setAnchorFilterMenu)}>
              <MenuItem className="menuItem" onClick={() => { handleMenu("filterSample"); handleMenuClose(setAnchorFilterMenu) }}>Filter By Sample</MenuItem>
              <MenuItem className="menuItem" onClick={() => { handleMenu("filterInfo"); handleMenuClose(setAnchorFilterMenu) }}>Filter By Attributes</MenuItem>
            </MenuButton>
          </Grid>*/}

          <Grid key='genomeViewButton' item xs="auto">
            <Button disabled={!isValidLocString} style={{ marginTop:"8px"}} color="primary" variant="contained" onClick={() => handleMenu("browserRedirect")}>View in Genome Browser</Button>
          </Grid>
        </Grid>
      </div>

      {gridElement}
    </>
  )
})

export default VariantTableWidget
