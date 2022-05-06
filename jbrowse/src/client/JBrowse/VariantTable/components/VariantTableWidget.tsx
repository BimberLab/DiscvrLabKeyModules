import { observer } from 'mobx-react'
import React, { useEffect, useState } from 'react'
import { getConf } from '@jbrowse/core/configuration'
import {  Widget } from '@jbrowse/core/util'
import { Dialog, Grid, MenuItem, Button } from "@material-ui/core"

import { DataGrid, GridToolbar } from '@mui/x-data-grid'

import type { Row } from '../types'
import { columns } from '../constants'
import { rawFeatureToRow, filterFeatures } from '../dataUtils'
import MenuButton from './MenuButton'

import StandaloneSearch from "../../Search/StandaloneSearch"

import '../VariantTable.css'
import '../../jbrowse.css'
import { navigateToBrowser } from '../../utils'

const VariantTableWidget = observer(props => {
  const { assembly, rpcManager, trackId, locString, parsedLocString, sessionId, session, pluginManager } = props
  const { view } = session

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  if (!track) {
    return (<p>Unknown track: {trackId}</p>)
  }

  function handleMenu(item, gridElement) {
    switch(item) {
      case "filterSample":
        session.showWidget(sampleFilterWidget)
        addActiveWidgetId(sampleFilterWidget.id)
        break;
      case "filterInfo":
        session.showWidget(infoFilterWidget)
        addActiveWidgetId(infoFilterWidget.id)
        break;
      case "filterTable":
        setFiltersOn(!filtersOn)
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
  const [features, setFeatures] = useState<Row[]>(null)

  // Flag for whether the filters boxes are being displayed or not
  const [filtersOn, setFiltersOn] = useState<boolean>(false)

  // Active widget ID list to force rerender when a JBrowseUIButton is clicked
  const [activeWidgetList, setActiveWidgetList] = useState<string[]>([])

  // Widget states
  const [sampleFilterWidget, setSampleFilterWidget] = useState<Widget | undefined>()
  const [infoFilterWidget, setInfoFilterWidget] = useState<Widget | undefined>()

  // Menu management
  const [anchorFilterMenu, setAnchorFilterMenu] = useState(null)
  const [validLocString, setValidLocString] = useState(true)


  // API call to retrieve the requested features. Can handle multiple location strings.
  useEffect(() => {
    async function fetch() {
      const rawFeatures = await rpcManager.call(sessionId, 'CoreGetFeatures', {
        adapterConfig: getConf(track, 'adapter'),
        sessionId,
        region: {
          start: parsedLocString.start,
          end: parsedLocString.end,
          refName: assembly.getCanonicalRefName(parsedLocString.refName),
        },
      })

      const filteredFeatures = filterFeatures(rawFeatures, 
        track.configuration.displays[0].renderer.activeSamples.value, 
        track.configuration.displays[0].renderer.infoFilters.valueJSON)

      setFeatures(filteredFeatures.map((rawFeature, id) => rawFeatureToRow(rawFeature, id, trackId)))
    }

    if(pluginManager && parsedLocString && validLocString) {
      setSampleFilterWidget(session.addWidget(
        'SampleFilterWidget',
        'Sample-Variant-' + getConf(track, 'trackId'),
        { track: track.configuration }
      ))

      setInfoFilterWidget(session.addWidget(
        'InfoFilterWidget',
        'Info-Variant-' + getConf(track, 'trackId'),
        { track: track.configuration }
      ))
      
      const regionLength = parsedLocString.end - parsedLocString.start
      const maxRegionSize = 900000
      if (regionLength > maxRegionSize) {
        alert("Location " + locString + " is too large to load.")
        setValidLocString(false)
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

  if (!features && locString && validLocString) {
        return (<p>Loading...</p>)
  } else {
    const gridElement = (
      <DataGrid
          columns={columns}
          rows={features}
          components={{ Toolbar: GridToolbar }}
          autoPageSize
        />
    )

    return (
      <>
        {
          [...session.activeWidgets].map((elem) => {
            const widget = elem[1]
            const widgetType = pluginManager.getWidgetType(widget.type)
            const { ReactComponent } = widgetType
            const { visibleWidget } = session
            return (
            <Dialog onClose={() => handleModalClose(widget)} open={true} key={widget.id}>
              <h3 style={{margin: "15px"}}>Filter Table</h3>
              
              <div style={{margin: "10px"}}>
                <ReactComponent model={visibleWidget}/>
              </div>
            </Dialog>
            )
          })
        }

        <div style={{marginBottom: "10px"}}>
          <Grid container spacing={1} justifyContent="flex-start" alignItems="center">
            <Grid key='search' item xs="auto">
              <StandaloneSearch sessionId={sessionId} tableUrl={true} trackId={trackId} selectedRegion={validLocString ? locString : ""}></StandaloneSearch>
            </Grid>

            <Grid style={locString && validLocString ? {} : {display:"none"}} key='filterMenu' item xs="auto">
              <MenuButton id={'filterMenu'} color="primary" variant="contained" text="Filter" anchor={anchorFilterMenu}
                handleClick={(e) => handleMenuClick(e, setAnchorFilterMenu)}
                handleClose={(e) => handleMenuClose(setAnchorFilterMenu)}>
                <MenuItem className="menuItem" onClick={() => { handleMenu("filterSample", gridElement); handleMenuClose(setAnchorFilterMenu) }}>Filter By Sample</MenuItem>
                <MenuItem className="menuItem" onClick={() => { handleMenu("filterInfo", gridElement); handleMenuClose(setAnchorFilterMenu) }}>Filter By Attributes</MenuItem>
              </MenuButton>
            </Grid>

            <Grid style={locString && validLocString ? {} : {display:"none"}} key='genomeViewButton' item xs="auto">
              <Button style={{ marginTop:"8px"}} color="primary" variant="contained" onClick={() => handleMenu("browserRedirect", gridElement)}>View in Genome Browser</Button>
            </Grid>
          </Grid>
        </div>
        
        {gridElement}
      </>
    )
  }
})

export default VariantTableWidget