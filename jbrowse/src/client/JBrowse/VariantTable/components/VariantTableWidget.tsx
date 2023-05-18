import { observer } from 'mobx-react';
import { GridColumns, getGridNumericColumnOperators } from '@mui/x-data-grid';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { Widget } from '@jbrowse/core/util';
import { AppBar, Box, Button, Dialog, Grid, Paper, Toolbar, Typography } from '@material-ui/core';
import ScopedCssBaseline from '@material-ui/core/ScopedCssBaseline';
import { DataGrid, GridColDef, GridRenderCellParams, GridToolbar } from '@mui/x-data-grid';
import { APIDataToRows } from '../dataUtils';
import ArrowPagination from './ArrowPagination';
import { fieldToReadableName, multiValueComparator, multiModalOperator } from '../constants';
import FilterForm from "./FilterForm"


import '../VariantTable.css';
import '../../jbrowse.css';
<<<<<<< HEAD
import { getGenotypeURL, navigateToBrowser, fetchLuceneQuery, fetchFieldTypeInfo } from '../../utils';
=======
import { fetchLuceneQuery, getGenotypeURL, navigateToBrowser, truncateToValidGUID } from '../../utils';
>>>>>>> 38da686e (Update table widget to always pass a proper GUID)
import LoadingIndicator from './LoadingIndicator';
import { Row } from '../types';
import Search from './Search';

const VariantTableWidget = observer(props => {
  const { assembly, assemblyName, trackId, locString, parsedLocString, sessionId, session, pluginManager } = props
  const { view } = session

  const currentOffset = parseInt(new URLSearchParams(window.location.search).get('offset') || '0');

  // The code expects a proper GUID, yet the trackId is a string containing the GUID + filename
  const trackGUID = truncateToValidGUID(props.trackId)

  const track = view.tracks.find(
      t => t.configuration.trackId === trackId,
  )

  if (!track) {
    return (<p>Unknown track: {trackId}</p>)
  }

  function handleSearch(data) {
    setFeatures(APIDataToRows(data.data, trackId))
  }

  const handleOffsetChange = (newOffset: number) => {
    const url = new URL(window.location.href);
    const urlSearchParams = url.searchParams;

    urlSearchParams.set('offset', newOffset.toString());
    url.search = urlSearchParams.toString();

    window.location.href = url.toString();
  };

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
  const [columns, setColumns] = useState<GridColumns>([])

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
      const searchString = queryParam.get('searchString');

      await fetchFieldTypeInfo(sessionId, trackGUID,
        (res) => {
          let columns: GridColumns = [];

          for(const fieldObj of res.fields) {
            const field = fieldObj.name;
            const type = fieldObj.type;
            let muiFieldType;

            switch (type) {
              case 'Flag':
              case 'String':
              case 'Character':
              case 'Impact':
                muiFieldType = "string";
                break;
              case 'Float':
              case 'Integer':
                muiFieldType = "number";
                break;
            }

            let column: any = { field: field, headerName: fieldToReadableName[field] ?? field, width: muiFieldType == "string" ? 150 : 50, type: type as string, flex: 1, headerAlign: 'left', hide: fieldToReadableName[field] ? false : true }

            if (field == "af") {
              column = { 
                field: 'af', 
                headerName: 'Allele Frequency',
                width: 50,
                type: "number",
                flex: 1,
                headerAlign: 'left',
                sortComparator: multiValueComparator,
                filterOperators: getGridNumericColumnOperators().map(op => multiModalOperator(op))
              }
            }

            columns.push(column)
          }
          setColumns(columns)
        })

<<<<<<< HEAD
      if (searchString) {
        await fetchLuceneQuery(queryParam.get('searchString'), sessionId, trackGUID, queryParam.get('offset'),
=======
        /*await fetchLuceneQuery(queryParam.get('searchString'), sessionId, trackGUID, queryParam.get('offset'),
>>>>>>> 801442b9 (Add "all" query and pos sorting)
          (res) => {
            console.log("AYYYYY")
            setFeatures(APIDataToRows(res.data, trackId))
            setDataLoaded(true)
          },
          () => {
            setDataLoaded(true)
<<<<<<< HEAD
          })
      }
=======
          })*/
>>>>>>> 801442b9 (Add "all" query and pos sorting)
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

      <div style={{ marginBottom: "10px" }}>
        <Grid container spacing={1} justifyContent="center" alignItems="center">
          <Grid item xs={12} md={12}>
            <FilterForm
              open
              setOpen={() => {}}
              sessionId={sessionId}
              trackGUID={trackGUID}
              handleSubmitCallback={(data) => handleSearch(data)}
              handleFailureCallback={() => {}}
              externalActionComponent={
                <Button
                  disabled={!isValidLocString}
                  color="primary"
                  variant="contained"
                  onClick={() => handleMenu("browserRedirect")}
                >
                  View in Genome Browser
                </Button>
              }
              arrowPagination={
                <ArrowPagination
                  offset={currentOffset}
                  onOffsetChange={handleOffsetChange}
                />
              }
            />
          </Grid>
        </Grid>
      </div>

      {gridElement}
    </>
  )
})

export default VariantTableWidget
