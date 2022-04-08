import { observer } from 'mobx-react'
import React, { useEffect, useState } from 'react'
import { getConf } from '@jbrowse/core/configuration'
import {  Widget } from '@jbrowse/core/util'
import { ActionURL } from '@labkey/api';
import { Dialog, Grid, MenuItem, Button } from "@material-ui/core"

import DataGrid from 'react-data-grid'
import type { HeaderRendererProps, SortColumn } from 'react-data-grid'

import useFocusRef from '../useFocusRef'
import { exportToXlsx, exportToCsv } from '../exportUtils'
import type { Filter, Row } from '../types'
import { defaultFilters, columnsObjRaw } from '../constants'
import { filterFeature, sortFeatures, rawFeatureToRow, filterFeatures } from '../dataUtils'
import MenuButton from './MenuButton'

import StandaloneSearch from "../../Search/StandaloneSearch"

import '../VariantTable.css'
import '../../jbrowse.css'

const VariantTableWidget = observer(props => {
  const { assembly, rpcManager, trackId, locString, parsedLocString, sessionId, session, pluginManager } = props
  const { view } = session

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  // Render function for the custom components that make up the header cells of the table
  function FilterRenderer<R, SR, T extends HTMLOrSVGElement>({
    isCellSelected,
    column,
    children
  }: HeaderRendererProps<R, SR> & {
    children: (args: {
      ref: React.RefObject<T>;
      tabIndex: number;
      filters: Filter;
    }) => React.ReactElement;
  }) {
    const { ref, tabIndex } = useFocusRef<T>(isCellSelected)
    
    return (
      <>
        <div>{column.name}</div>
        {<div>{children({ ref, tabIndex, filters })}</div>}
      </>
    )
  }

  // Ensure you can't arrow-navigate out of a filter input box when you're typing into it
  function inputStopPropagation(event: React.KeyboardEvent<HTMLInputElement>) {
    if (['ArrowLeft', 'ArrowRight'].includes(event.key)) {
      event.stopPropagation();
    }
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
      case "exportCSV": 
        exportToCsv(gridElement, 'rows.csv')
        break;
      case "exportXLSX":
        exportToXlsx(gridElement, 'rows.xlsx')
        break;
      case "browserRedirect":
        window.location.href = ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: locString, trackId: trackId})
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

  // Contains filter state
  const [filters, setFilters] = useState<Filter>(defaultFilters)

  // Contains column sort state
  const [sortColumns, setSortColumns] = useState<readonly SortColumn[]>([])

  // Active widget ID list to force rerender when a JBrowseUIButton is clicked
  const [activeWidgetList, setActiveWidgetList] = useState<string[]>([])

  // Widget states
  const [sampleFilterWidget, setSampleFilterWidget] = useState<Widget | undefined>()
  const [infoFilterWidget, setInfoFilterWidget] = useState<Widget | undefined>()

  // Menu management
  const [anchorFilterMenu, setAnchorFilterMenu] = useState(null)
  const [anchorExportMenu, setAnchorExportMenu] = useState(null)


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

      setFeatures(filteredFeatures.map((rawFeature, id) => rawFeatureToRow(rawFeature, id)))
    }

    if(pluginManager && parsedLocString) {
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

      fetch()
    }
  }, [pluginManager, parsedLocString, session.visibleWidget])

  // Sort the base feature list using the requested sort columns. Then, filter using the requested filters.
  // filteredFeatures goes on to become the rows of the datagrid.
  const sortedFeatures = features ? sortFeatures(features, sortColumns) : []
  const filteredFeatures = sortedFeatures?.filter((r) => filterFeature(r, filters)) ?? []

  // List of columns, which can contain arbitrary render components or a simple key-name object.
  // Based on whether the filterOn flag is enabled or not, the components will be shown or hidden.
  const columnsObj = columnsObjRaw.map(obj => filtersOn && obj.type == "string" ? ({
      key: obj.key,
      name: obj.name,
      headerCellClass: "variantDataHeader",
      sortable: true,
      headerRenderer: (p) => (
        <FilterRenderer<Row, unknown, HTMLInputElement> {...p}>
          {({filters, ...rest}) => (
            <input
              {...rest}
              id={"searchbox-" + obj.key}
              style={{inlineSize: '100%', fontSize: '14px'}}
              value={filters[obj.key]}
              onChange={(e) => {
                setFilters({
                  ...filters,
                  [obj.key]: e.target.value
                })}
              }
              onKeyDown={inputStopPropagation}
            />
          )}
        </FilterRenderer>
      )
    }): {key: obj.key, name: obj.name})

  const columns = [
    ...columnsObj
  ]

  if (!view) {
      return
  }

  if (!track) {
      return(<p>Unable to find track: {trackId}</p>)
  }

  if (!features) {
        return (<p>Loading...</p>)
  } else {
    const gridElement = (
      <DataGrid
          columns={columns}
          rows={filteredFeatures}
          defaultColumnOptions={{
            sortable: true,
            resizable: true
          }}
          sortColumns={sortColumns}
          onSortColumnsChange={setSortColumns}
          className="rdg-light dataGrid"
          headerRowHeight={filtersOn ? 90 : 45}
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
              <StandaloneSearch sessionId={sessionId} tableUrl={true} trackId={trackId}></StandaloneSearch>
            </Grid>

            <Grid key='filterMenu' item xs="auto">
              <MenuButton id={'filterMenu'} text="Filter" anchor={anchorFilterMenu}
                handleClick={(e) => handleMenuClick(e, setAnchorFilterMenu)}
                handleClose={(e) => handleMenuClose(setAnchorFilterMenu)}>
                <MenuItem className="menuItem" onClick={() => { handleMenu("filterSample", gridElement); handleMenuClose(setAnchorFilterMenu) }}>Filter By Sample</MenuItem>
                <MenuItem className="menuItem" onClick={() => { handleMenu("filterInfo", gridElement); handleMenuClose(setAnchorFilterMenu) }}>Filter By Attributes</MenuItem>
                <MenuItem className="menuItem" onClick={() => { handleMenu("filterTable", gridElement); handleMenuClose(setAnchorFilterMenu) }}>{filtersOn ? "Hide Table Filters" : "Show Table Filters"}</MenuItem>
              </MenuButton>
            </Grid>

            <Grid key='exportMenu' item xs="auto">
              <MenuButton id={'exportMenu'} text="Export" anchor={anchorExportMenu}
                handleClick={(e) => handleMenuClick(e, setAnchorExportMenu)}
                handleClose={() => handleMenuClose(setAnchorExportMenu)}>
                <MenuItem className="menuItem" onClick={() => { handleMenu("exportCSV", gridElement); handleMenuClose(setAnchorExportMenu) }}>Export to CSV</MenuItem>
                <MenuItem className="menuItem" onClick={() => { handleMenu("exportXLSX", gridElement); handleMenuClose(setAnchorExportMenu) }}>Export to XLSX</MenuItem>
              </MenuButton>
            </Grid>

            <Grid key='genomeViewButton' item xs="auto">
              <Button style={{backgroundColor: "#116596", marginTop:"8px"}} color="primary" variant="contained" onClick={() => handleMenu("browserRedirect", gridElement)}>View in Genome Browser </Button>
            </Grid>
          </Grid>
        </div>
        
        {gridElement}
      </>
    )
  }
})

export default VariantTableWidget