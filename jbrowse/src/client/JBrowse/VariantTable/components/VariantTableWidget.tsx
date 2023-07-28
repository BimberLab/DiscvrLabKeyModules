import { observer } from 'mobx-react';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { ParsedLocString, parseLocString } from '@jbrowse/core/util';
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
import '../../jbrowse.css';
import {
  FieldModel,
  getGenotypeURL,
  isVariant,
  navigateToBrowser,
  navigateToSearch,
  navigateToTable,
  parsedLocStringToUrl,
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
  const { assembly, assemblyName, trackId, locString, sessionId, session, pluginManager } = props
  const { view, assemblyManager } = session

  const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
    return assemblyManager.isValidRefName(refName, props.assemblyName)
  }

  const [isValidLocString, setIsValidLocString] = useState(true)

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  if (!track) {
    return (<p>Unknown track: {trackId}</p>)
  }

  function handleMenu(item) {
    switch(item) {
      case "filterSample":
        const sampleFilterWidget = session.addWidget(
            'SampleFilterWidget',
            // @ts-ignore
            'Sample-Variant-' + getConf(track, ['trackId']),
            {track: track.configuration}
        )
        session.showWidget(sampleFilterWidget)
        break;
      case "filterInfo":
        const infoFilterWidget = session.addWidget(
            'InfoFilterWidget',
            // @ts-ignore
            'Info-Variant-' + getConf(track, ['trackId']),
            {track: track.configuration}
        )
        session.showWidget(infoFilterWidget)
        break;
      case "browserRedirect":
        navigateToBrowser(sessionId, locString, trackId, track)
        break;
      case "luceneRedirect":
        navigateToSearch(sessionId, locString, trackId, track)
        break;
    }
  }

  async function fetchData(parsedLocString: ParsedLocString, adapter: BaseFeatureDataAdapter) {
    const featObservable = adapter.getFeatures({
      refName: assembly.getCanonicalRefName(parsedLocString.refName),
      start: parsedLocString.start,
      end: parsedLocString.end
    } as NoAssemblyRegion).pipe(toArray())
    
    // TODO: do we actually want to cache the in-memory filtered features, or should we
    // cache the full set and react to changes in track.configuration and filter on-demand?
    const rawFeatures = await lastValueFrom(featObservable)

    const filteredFeatures = filterFeatures(rawFeatures,
        getConf(track, ['displays', '0', 'renderer', 'activeSamples']),
        getConf(track, ['displays', '0', 'renderer', 'infoFilters'])
    )

    // Maintain a cached list of all non-WT samples at this position:
    filteredFeatures.forEach(variant => {
      if (!variant.get('INFO')['variableSamples'] && variant.get('SAMPLES')) {
        variant.get('INFO')['variableSamples'] = []
        Object.keys(variant.get('SAMPLES')).forEach(function(sampleId) {
          const gt = variant.get('SAMPLES')[sampleId]["GT"][0]
          if (isVariant(gt)) {
            variant.get('INFO')['variableSamples'].push(sampleId)
          }
        })
      }
    })

    setFeatures(filteredFeatures)
  }
  
  async function getSessionMetadata(adapter: BaseFeatureDataAdapter) {
    const metadataPromise = adapter.getMetadata()
    const metadata: any = await metadataPromise
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
        params: {infoKeys: Object.keys(metadata.INFO), includeDefaultFields: true}
      });
    }
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

    // The VcfFeature uses 0-based coordinates
    if (ret['start']) {
      ret['start0'] = ret['start']
      ret['start'] = ret['start'] + 1
    }

    return(ret)
  }

  function filterFeatures(features, activeSamples, filters) {
    let ret = []

    let processedActiveSamples = activeSamples === "" ? [] : activeSamples.split(",")
    let processedFilters = deserializeFilters(filters)

    features.forEach((feature) => {
      if (passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
        ret.push(feature)
      }
    })

    return ret
  }

  // Contains all features from the API call once the useEffect finished
  const [features, setFeatures] = useState<VcfFeature[]>([])

  // Menu management
  const [anchorFilterMenu, setAnchorFilterMenu] = useState(null)

  // False until initial data load or an error. If we load w/o a query string, also treat as loaded:
  const [sessionInfoLoaded, setSessionInfoLoaded] = useState(false)
  
  const [awaitingData, setAwaitingData] = useState(false)
  const [adapter, setAdapter] = useState<BaseFeatureDataAdapter>(null)
  
  const [pageSizeModel, setPageSizeModel] = React.useState<GridPaginationModel>({ page: 0, pageSize: 25 });
  const [columnVisibilityModel, setColumnVisibilityModel] = useState<GridColumnVisibilityModel>({});
  const [gridColumns, setGridColumns] = useState<GridColDef[]>([])

  useEffect(() => {
    // First grab session info.
    if (!sessionInfoLoaded) {
      let adapterConfig = getConf(track, ['adapter'])
        getAdapter(
            pluginManager,
            sessionId,
            adapterConfig,
      ).then(adapterConfig => { 
          setAdapter(adapterConfig.dataAdapter as BaseFeatureDataAdapter)
          getSessionMetadata(adapterConfig.dataAdapter as BaseFeatureDataAdapter).then(x => {
            setSessionInfoLoaded(true)
          })
      })
    }
  }, [])

  useEffect(() => {
    let parsedLocString = null

    // Ensure we only hit this once:
    if (isValidLocString !== false) {
      try {
        parsedLocString = locString ? parseLocString(locString, isValidRefNameForAssembly) : null
      }
      catch (e) {
        alert('Error: ' + e.message)
        setIsValidLocString(false)
      }
    }

    if (parsedLocString && isValidLocString) {
      const regionLength = parsedLocString.end - parsedLocString.start
      const maxRegionSize = 900000
      if (isNaN(regionLength)) {
        alert("Must include start/stop in location: " + locString)
        setAwaitingData(false)
        setIsValidLocString(false)
      } else if (regionLength > maxRegionSize) {
        alert("Location " + locString + " is too large to load.")
        setAwaitingData(false)
        setIsValidLocString(false)
      }
    }

    if (sessionInfoLoaded && parsedLocString != null && !awaitingData) {
      setAwaitingData(true)
      fetchData(parsedLocString, adapter).then(x => setAwaitingData(false))
    }
  }, [sessionInfoLoaded, locString, session.visibleWidget])

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

  const handleSearch = (queryString: string, locString: ParsedLocString, errorMessages?: string[]) => {
    if (locString) {
      let start = locString.start ?? -1
      let end = locString.end ?? -1
      if (start === -1 || end === -1) {
        alert('Location lacks a start or end, cannot use: ' + locString.refName)
        return
      }

      navigateToTable(sessionId, parsedLocStringToUrl(locString), trackId)
    }
    else if (errorMessages?.length) {
      alert(errorMessages.join("<br>"))
      console.error('Error with search component: ' + errorMessages.join(", "))
    }
  }

  return (
    <>
      <LoadingIndicator isOpen={awaitingData || !sessionInfoLoaded }/>
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
        <Grid container spacing={1} justifyContent="flex-start" alignItems="center" >
          <Grid key='search' item xs="auto">
            <StandaloneSearchComponent session={session} onSelect={handleSearch} forVariantTable={true} assemblyName={assemblyName} selectedRegion={isValidLocString ? locString : ""} fieldMinWidth={225}/>
          </Grid>

          <Grid key='filterMenu' item xs="auto">
            <MenuButton disabled={!isValidLocString} id={'filterMenu'} color="primary" variant="contained" text="Filter" anchor={anchorFilterMenu}
              handleClick={(e) => handleMenuClick(e, setAnchorFilterMenu)}
              handleClose={(e) => handleMenuClose(setAnchorFilterMenu)} >
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