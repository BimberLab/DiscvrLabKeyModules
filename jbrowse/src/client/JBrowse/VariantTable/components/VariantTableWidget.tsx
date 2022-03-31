import { observer } from 'mobx-react'
import React, { useEffect, useState } from 'react'
import { getConf } from '@jbrowse/core/configuration'
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache'
import { BaseFeatureDataAdapter } from '@jbrowse/core/data_adapters/BaseAdapter'
import { toArray } from 'rxjs/operators'
import { getSession, SessionWithWidgets, Widget, isSessionModel } from '@jbrowse/core/util'
import { Dialog } from "@material-ui/core"


import DataGrid from 'react-data-grid'
import useFocusRef from '../useFocusRef'
import type { HeaderRendererProps, SortColumn } from 'react-data-grid'
import { exportToXlsx, exportToCsv } from '../exportUtils'

import '../VariantTable.css'
import { ExportButton, FilterButton, JBrowseUIButton } from './Buttons'
import type { Filter, Row } from '../types'
import { defaultFilters, columnsObjRaw } from '../constants'
import { filterFeature, sortFeatures, rawFeatureToRow } from '../dataUtils'

import StandaloneSearch from "../../Search/StandaloneSearch"
import ExtendedVariantAdapter from '../../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter/ExtendedVariantAdapter'

const VariantTableWidget = observer(props => {
  const { assembly, rpcManager, trackId, parsedLocString, sessionId, session, pluginManager } = props
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

  // Manager for the activeWidgetList
  function addActiveWidgetId(id: string) {
    setActiveWidgetList([id, ...activeWidgetList])
  }

  // MaterialUI modal handlers
  function handleClose(widget) {
    session.hideWidget(widget)
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

  // API call to retrieve the requested features. Can handle multiple location strings.
  useEffect(() => {
    async function fetch() {
      const features = await rpcManager.call(sessionId, 'CoreGetFeatures', {
        adapterConfig: getConf(track, 'adapter'),
        sessionId,
        region: {
          start: parsedLocString.start,
          end: parsedLocString.end,
          refName: assembly.getCanonicalRefName(parsedLocString.refName),
        },
      })

      setFeatures(features.map((rawFeature, id) => rawFeatureToRow(rawFeature, id)))
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
      <>
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
          headerRowHeight={90}
        />
      </>
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
            <Dialog onClose={() => handleClose(widget)} open={true}>
              <ReactComponent model={visibleWidget}/>
            </Dialog>
            )
          })
        }

        <div style={{textAlign: 'start', marginBlockEnd: '8px'}}>
          <StandaloneSearch sessionId={sessionId} tableUrl={true} trackId={trackId}></StandaloneSearch>

          <ExportButton onExport={() => exportToCsv(gridElement, 'rows.csv')}>
            Export to CSV
          </ExportButton>

          <ExportButton onExport={() => exportToXlsx(gridElement, 'rows.xlsx')}>
            Export to XSLX
          </ExportButton>

          <JBrowseUIButton session={session} widget={sampleFilterWidget} addActiveWidgetId={addActiveWidgetId}>
            Filter By Sample
          </JBrowseUIButton>

          <JBrowseUIButton session={session} widget={infoFilterWidget} addActiveWidgetId={addActiveWidgetId}>
            Filter By Attributes
          </JBrowseUIButton>

          <FilterButton setFiltersOn={setFiltersOn} filtersOn={filtersOn}></FilterButton>
       </div>
        
        {gridElement}
      </>
    )
  }
})

export default VariantTableWidget