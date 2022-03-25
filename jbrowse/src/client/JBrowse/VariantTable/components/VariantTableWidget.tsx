import { observer } from 'mobx-react'
import React, { useEffect, useState } from 'react'
import { getConf } from '@jbrowse/core/configuration'
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache'
import { BaseFeatureDataAdapter } from '@jbrowse/core/data_adapters/BaseAdapter'
import { toArray } from 'rxjs/operators'

import DataGrid from 'react-data-grid'
import useFocusRef from '../useFocusRef'
import type { HeaderRendererProps, SortColumn } from 'react-data-grid'
import { exportToXlsx, exportToPdf, exportToCsv } from '../exportUtils'

import '../VariantTable.css'
import { ExportButton, FilterButton } from './Buttons'
import type { Filter, Row } from '../types'
import { defaultFilters, columnsObjRaw } from '../constants'
import { filterFeature, sortFeatures, rawFeatureToRow } from '../dataUtils'

import StandaloneSearch from "../../Search/StandaloneSearch"

const VariantTableWidget = observer(props => {
  const { view, trackId, parsedLocString, sessionId, pluginManager } = props
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


  // Contains all features from the API call once the useEffect finished
  const [features, setFeatures] = useState(null)

  // Flag for whether the filters boxes are being displayed or not
  const [filtersOn, setFiltersOn] = useState(false)

  // Contains filter state
  const [filters, setFilters] = useState<Filter>(defaultFilters)

  // Contains column sort state
  const [sortColumns, setSortColumns] = useState<readonly SortColumn[]>([])


  // API call to retrieve the requested features. Can handle multiple location strings.
  useEffect(() => {
    async function fetch() {
      const adapterConfig = getConf(track, 'adapter')
      const dataAdapter = (
        await getAdapter(pluginManager, sessionId, adapterConfig)
      ).dataAdapter as BaseFeatureDataAdapter

      const featureObservable = dataAdapter.getFeatures(parsedLocString)
      let features = await featureObservable.pipe(toArray()).toPromise()
      setFeatures(features.map((rawFeature, id) => rawFeatureToRow(rawFeature, id)))
    }

    if(pluginManager && parsedLocString) {
      console.log(pluginManager)
      fetch()
    }
  }, [pluginManager, parsedLocString])

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

  if (!features || !pluginManager.widgetTypes.registeredTypes.SampleFilterWidget.stateModel) {
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
      </>)

    return (
      <>
        <div style={{textAlign: 'start', marginBlockEnd: '8px'}}>


          <StandaloneSearch sessionId={sessionId} tableUrl={true}></StandaloneSearch>

          <ExportButton onExport={() => exportToCsv(gridElement, 'rows.csv')}>
            Export to CSV
          </ExportButton>
          <ExportButton onExport={() => exportToXlsx(gridElement, 'rows.xlsx')}>
            Export to XSLX
          </ExportButton>

          <FilterButton setFiltersOn={setFiltersOn} filtersOn={filtersOn}></FilterButton>
       </div>
        
        {gridElement}
      </>
    )
  }
})

export default VariantTableWidget