import { observer } from 'mobx-react';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { Widget } from '@jbrowse/core/util';
import { toArray } from 'rxjs/operators';
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache';
import { AppBar, Box, Button, Dialog, Grid, MenuItem, Toolbar, Typography, Paper } from '@material-ui/core';
import ScopedCssBaseline from '@material-ui/core/ScopedCssBaseline';
import { DataGrid, GridColDef, GridRenderCellParams, GridToolbar } from '@mui/x-data-grid';
import { columns } from '../constants';
import { filterFeatures, rawFeatureToRow } from '../dataUtils';
import MenuButton from './MenuButton';

import '../VariantTable.css';
import '../../jbrowse.css';
import { getGenotypeURL, navigateToBrowser } from '../../utils';
import LoadingIndicator from './LoadingIndicator';
import ExtendedVcfFeature from '../../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter/ExtendedVcfFeature';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import StandaloneSearchComponent from '../../Search/components/StandaloneSearchComponent';
import { EVAdapterClass } from '../../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter';

const VariantTableWidget = observer(props => {
  const { assembly, assemblyName, trackId, locString, parsedLocString, sessionId, session, pluginManager } = props
  const { view } = session

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  if (!track) {
    return (<p>Unknown track: {trackId}</p>)
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
  const [features, setFeatures] = useState<ExtendedVcfFeature[]>([])

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
      let adapterConfig = getConf(track, ['adapter'])

      let adapter = (await getAdapter(
          pluginManager,
          sessionId,
          adapterConfig,
      )).dataAdapter as EVAdapterClass

      console.log('calling getFeatures')
      const ret = adapter.getFeatures({
        refName: assembly.getCanonicalRefName(parsedLocString.refName),
        start: parsedLocString.start,
        end: parsedLocString.end
      } as NoAssemblyRegion)

      // TODO: do we actually want to cache the in-memory filtered features, or should we
      // cache the full set and react to changes in track.configuration and filter on-demand?
      // I suspect the fact this is responsive to session.visibleWidget is accidentally getting the reload correct, since that forces
      // this to be re-called when the filter widget is removed.
      const rawFeatures = await ret.pipe(toArray()).toPromise()
      const filteredFeatures = filterFeatures(rawFeatures,
        track.configuration.displays[0].renderer.activeSamples.value, 
        track.configuration.displays[0].renderer.infoFilters.valueJSON)

      setFeatures(filteredFeatures)
      setDataLoaded(true)
    }

    if (pluginManager && parsedLocString && isValidLocString) {
      setSampleFilterWidget(session.addWidget(
        'SampleFilterWidget',
        'Sample-Variant-' + getConf(track, ['trackId']),
        { track: track.configuration }
      ))

      setInfoFilterWidget(session.addWidget(
        'InfoFilterWidget',
        'Info-Variant-' + getConf(track, ['trackId']),
        { track: track.configuration }
      ))

      const regionLength = parsedLocString.end - parsedLocString.start
      const maxRegionSize = 900000
      if (!parsedLocString.start) {
        alert("Must include start/stop in location to avoid loading too many sites: " + locString)
        setDataLoaded(true)
        setIsValidLocString(false)
      }
      else if (regionLength > maxRegionSize) {
        alert("Location " + locString + " is too large to load.")
        setDataLoaded(true)
        setIsValidLocString(false)
      } else {
        fetch()
      }
    }
  }, [pluginManager, parsedLocString, session.visibleWidget])

  if (!view) {
      return
  }

  if (!track) {
      return(<p>Unable to find track: {trackId}</p>)
  }

  const showDetailsWidget = (rowIdx: number) => {
    const feature = features[rowIdx]
    const trackId = getConf(track, ['trackId'])
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
        rows={features.map((rawFeature, id) => rawFeatureToRow(rawFeature, id, trackId))}
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
          <Grid key='search' item xs="auto">
            <StandaloneSearchComponent session={session} tableUrl={true} assemblyName={assemblyName} trackId={trackId} selectedRegion={isValidLocString ? locString : ""}/>
          </Grid>

          <Grid key='filterMenu' item xs="auto">
            <MenuButton disabled={!isValidLocString} id={'filterMenu'} color="primary" variant="contained" text="Filter" anchor={anchorFilterMenu}
              handleClick={(e) => handleMenuClick(e, setAnchorFilterMenu)}
              handleClose={(e) => handleMenuClose(setAnchorFilterMenu)}>
              <MenuItem className="menuItem" onClick={() => { handleMenu("filterSample"); handleMenuClose(setAnchorFilterMenu) }}>Filter By Sample</MenuItem>
              <MenuItem className="menuItem" onClick={() => { handleMenu("filterInfo"); handleMenuClose(setAnchorFilterMenu) }}>Filter By Attributes</MenuItem>
            </MenuButton>
          </Grid>

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