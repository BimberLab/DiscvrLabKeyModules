import { observer } from 'mobx-react';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { Widget } from '@jbrowse/core/util';
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache';
import { AppBar, Box, Button, Dialog, Grid, MenuItem, Paper, Toolbar, Typography } from '@mui/material';
import ScopedCssBaseline from '@mui/material/ScopedCssBaseline';
import {
  DataGrid,
  GridColDef,
  GridColumnVisibilityModel,
  GridPaginationModel,
  GridRenderCellParams,
  GridToolbar
} from '@mui/x-data-grid';
import MenuButton from './MenuButton';

import '../VariantTable.css';
import '../../jbrowse.css';
import {
  FieldModel,
  getGenotypeURL,
  navigateToBrowser,
  navigateToSearch,
  navigateToTable,
  passesInfoFilters,
  passesSampleFilters
} from '../../utils';
import LoadingIndicator from './LoadingIndicator';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import StandaloneSearchComponent from '../../Search/components/StandaloneSearchComponent';
import { VcfFeature } from '@jbrowse/plugin-variants';
import { BaseFeatureDataAdapter } from '@jbrowse/core/data_adapters/BaseAdapter';
import { lastValueFrom } from 'rxjs';
import { toArray } from 'rxjs/operators';
import { ActionURL, Ajax } from '@labkey/api';
import { deserializeFilters } from '../../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil';

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
      case "luceneRedirect":
        navigateToSearch(sessionId, locString, trackId, track)
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

  function resolveValue(key: string, feature: VcfFeature) {
    let val = feature.get(key) ?? feature.get("INFO")[key] ?? null
    if (Array.isArray(val)) {
      val = val.filter(x => x !== null && x !== '').join(", ") ?? ""
    }

    return val
  }

  function rawFeatureToRow(feature: VcfFeature, id: number, columns: GridColDef[], trackId: string) {
    const ret = {
      id: id,
      trackId: trackId
    }

    columns.forEach(col => {
      // NOTE: upperCase() might be needed for CHROM, START, etc.
      ret[col.field] = resolveValue(col.field, feature) ?? resolveValue(col.field.toUpperCase(), feature)
    })

    return(ret)
  }

  function filterFeatures(features, activeSamples, filters) {
    let ret = []

    let processedActiveSamples = activeSamples === "" ? [] : activeSamples.split(",")
    let processedFilters = deserializeFilters(JSON.parse(filters))

    features.forEach((feature) => {
      if (passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
        ret.push(feature)
      }
    })

    return ret
  }

  // Contains all features from the API call once the useEffect finished
  const [features, setFeatures] = useState<VcfFeature[]>([])

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

  const [pageSizeModel, setPageSizeModel] = React.useState<GridPaginationModel>({ page: 0, pageSize: 25 });
  const [columnVisibilityModel, setColumnVisibilityModel] = useState<GridColumnVisibilityModel>({});
  const [infoFields, setInfoFields] = useState<Map<string, FieldModel>>(null)
  const [gridColumns, setGridColumns] = useState<GridColDef[]>([])

  // API call to retrieve the requested features.
  useEffect(() => {
    async function fetch() {
      let adapterConfig = getConf(track, ['adapter'])

      let adapter = (await getAdapter(
          pluginManager,
          sessionId,
          adapterConfig,
      )).dataAdapter as BaseFeatureDataAdapter

      const featObservable = adapter.getFeatures({
        refName: assembly.getCanonicalRefName(parsedLocString.refName),
        start: parsedLocString.start,
        end: parsedLocString.end
      } as NoAssemblyRegion).pipe(toArray())

      const metadataPromise = adapter.getMetadata()

      // TODO: do we actually want to cache the in-memory filtered features, or should we
      // cache the full set and react to changes in track.configuration and filter on-demand?
      // I suspect the fact this is responsive to session.visibleWidget is accidentally getting the reload correct, since that forces
      // this to be re-called when the filter widget is removed.
      const rawFeatures = await lastValueFrom(featObservable)
      const metadata: any = await metadataPromise

      const filteredFeatures = filterFeatures(rawFeatures,
        track.configuration.displays[0].renderer.activeSamples.value, 
        track.configuration.displays[0].renderer.infoFilters.valueJSON
      )

      setFeatures(filteredFeatures)

      if (metadata?.INFO) {
        await Ajax.request({
          url: ActionURL.buildURL('jbrowse', 'resolveVcfFields.api'),
          method: 'POST',
          success: async function (res) {
            const fields: Map<string, FieldModel> = JSON.parse(res.response);

            Object.keys(fields).forEach((key) => {
              const f = fields[key];

              // This is a bit of a hack. The lucene search indexes chrom/contig as contig, but the JB feature uses chrom
              if (fields[key].name === 'contig') {
                fields[key].name = 'chrom';
              }

              if (metadata.INFO[f.name]) {

                if (!f.type) {
                  fields[key].type = metadata.INFO[f.name].Type;
                }

                if (!f.description) {
                  fields[key].type = metadata.INFO[f.name].Description;
                }

                if (metadata.INFO[f.name].Number == 'R' || metadata.INFO[f.name].Number == 'A' || metadata.INFO[f.name].Number == '.') {
                  fields[key].isMultiValued = true;
                } else if (!isNaN(metadata.INFO[f.name].Number) && parseInt(metadata.INFO[f.name].Number) > 1) {
                  fields[key].isMultiValued = true;
                }
              }
            });

            // NOTE: for now these are only available in free-text search
            delete fields['genomicPosition']
            delete fields['variableSamples']

            setInfoFields(fields);

            const columns = [];
            const fieldToShow: FieldModel[] = Object.values(fields).map((x) => Object.assign(new FieldModel(), x)).sort((a, b) => a.orderKey - b.orderKey || a.getLabel().toLowerCase().localeCompare(b.getLabel().toLowerCase()));
            fieldToShow.filter((x) => !x.isHidden).map((x) => x.toGridColDef()).forEach((x) => columns.push(x));

            const columnVisibilityModel = {actions: true};
            fieldToShow.filter((x) => !x.isHidden).forEach((x) => columnVisibilityModel[x.name] = !!x.isInDefaultColumns);

            setColumnVisibilityModel(columnVisibilityModel);
            setGridColumns(columns);
          },
          failure: function (res) {
            console.error('There was an error while fetching field types: ' + res.status + '\n Status Body: ' + res.statusText);
          },
          params: {infoKeys: Object.keys(metadata.INFO), includeDefaultFields: true},
        });
      }

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
      if (isNaN(regionLength)) {
        alert("Must include start/stop in location: " + locString)
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

  // @ts-ignore
  const supportsLuceneIndex = getConf(track, ['displays', '0', 'renderer', 'supportsLuceneIndex'])
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
    hideable: false,
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
        columns={[...gridColumns, actionsCol]}
        rows={features.map((rawFeature, id) => rawFeatureToRow(rawFeature, id, gridColumns, trackId))}
        slots={{ toolbar: GridToolbar }}
        pageSizeOptions={[10,25,50,100]}
        paginationModel={ pageSizeModel }
        onPaginationModelChange= {(newModel) => setPageSizeModel(newModel)}
        columnVisibilityModel={columnVisibilityModel}
        onColumnVisibilityModelChange={(model) => {
          setColumnVisibilityModel(model)
        }}
      />
  )

  const handleSearch = (locString) => {
    if (locString) {
      navigateToTable(sessionId, locString, trackId)
    }
  }

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
            <StandaloneSearchComponent session={session} onSelect={handleSearch} forVariantTable={true} assemblyName={assemblyName} selectedRegion={isValidLocString ? locString : ""}/>
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

          {supportsLuceneIndex ? <Grid key='luceneViewButton' item xs="auto">
            <Button hidden={!supportsLuceneIndex} style={{ marginTop:"8px"}} color="primary" variant="contained" onClick={() => handleMenu("luceneRedirect")}>Switch to Free-text Search</Button>
          </Grid> : null}
        </Grid>
      </div>

      {gridElement}
    </>
  )
})

export default VariantTableWidget