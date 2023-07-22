import { observer } from 'mobx-react';
import {
    DataGrid,
    getGridNumericColumnOperators,
    GridColDef,
    GridColumns,
    GridRenderCellParams,
    GridToolbarColumnsButton,
    GridToolbarContainer,
    GridToolbarDensitySelector,
    GridToolbarExport
} from '@mui/x-data-grid';
import ScopedCssBaseline from '@material-ui/core/ScopedCssBaseline';
import FilterListIcon from '@material-ui/icons/FilterList';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { AppBar, Box, Button, Dialog, Paper, Popover, Toolbar, Tooltip, Typography } from '@material-ui/core';
import { APIDataToRows } from '../dataUtils';
import ArrowPagination from './ArrowPagination';
import { multiModalOperator, multiValueComparator } from '../constants';
import { FilterFormModal } from './FilterFormModal';
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache';
import { EVAdapterClass } from '../../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import { toArray } from 'rxjs/operators';
import {
    createEncodedFilterString,
    fetchFieldTypeInfo,
    fetchLuceneQuery,
    FieldModel,
    fieldTypeInfoToOperators,
    getBrowserUrlNoFilters,
    getGenotypeURL,
    searchStringToInitialFilters,
    truncateToValidGUID,
} from '../../utils';
import { parseLocString } from '@jbrowse/core/util';


import '../VariantTable.css';
import '../../jbrowse.css';
import LoadingIndicator from './LoadingIndicator';

const VariantTableWidget = observer(props => {
  const { assembly, trackId, parsedLocString, sessionId, session, pluginManager } = props
  const { assemblyNames, assemblyManager } = session
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
    setDataLoaded(true)
  }

  function handleModalClose(widget) {
    session.hideWidget(widget)
  } 

  function handleQuery(passedFilters) {
    const encodedSearchString = createEncodedFilterString(passedFilters, false);
    const currentUrl = new URL(window.location.href);
    currentUrl.searchParams.set("searchString", encodedSearchString);
    window.history.pushState(null, "", currentUrl.toString());

    setFilters(passedFilters);
    setDataLoaded(false)
    fetchLuceneQuery(passedFilters, sessionId, trackGUID, currentOffset, (json)=>{handleSearch(json)}, (error) => {setDataLoaded(true);setError(error)});
  }

  const TableCellWithPopover = (props: { value: any }) => {
    const { value } = props;
    const fullDisplayValue = value ? (Array.isArray(value) ? value.join(', ') : value) : ''
    const [displayValue, setDisplayValue] = useState(fullDisplayValue)

    const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null)
    const open = Boolean(anchorEl);

    const [hoverTimeout, setHoverTimeout] = React.useState<NodeJS.Timeout | null>(null);

    const handlePopoverOpen = (event: React.MouseEvent<HTMLElement>) => {
      clearHoverTimeout()
      const currentTarget = event.currentTarget;
      const timeoutId = setTimeout(() => {
        setAnchorEl(currentTarget);
      }, 1000)
      setHoverTimeout(timeoutId);
    };

    const handlePopoverClose = () => {
      setAnchorEl(null);
    };

    const clearHoverTimeout = () => {
      if (hoverTimeout) {
        clearTimeout(hoverTimeout);
        setHoverTimeout(null);
      }
    };

    const renderPopover = displayValue && Array.isArray(value)

    // Get screen width and set the maximum number of characters for displayValue
    useEffect(() => {
      const truncateDisplayValue = () => {
        const screenWidth = window.innerWidth;
        const maxCharacters = Math.floor(screenWidth / 12 / 10);

        setDisplayValue(
          fullDisplayValue && fullDisplayValue.length > maxCharacters
            ? `${fullDisplayValue.substring(0, maxCharacters - 3)}...`
            : fullDisplayValue
        );
      };

      window.addEventListener('resize', truncateDisplayValue)
      truncateDisplayValue()

      return () => {
        window.removeEventListener('resize', truncateDisplayValue)
      };
    }, [fullDisplayValue]);

    return (
      <div>
        <Typography
          aria-owns={open ? 'mouse-over-popover' : undefined}
          aria-haspopup="true"
          onMouseEnter={handlePopoverOpen}
          onMouseLeave={clearHoverTimeout}
        >
          <span className='table-cell-truncate'>{displayValue}</span>
        </Typography>
        {renderPopover && 
          <Popover
            id="mouse-over-popover"
            open={open}
            anchorEl={anchorEl}
            onClose={handlePopoverClose}
            anchorOrigin={{
              vertical: 'bottom',
              horizontal: 'left',
            }}
            transformOrigin={{
              vertical: 'top',
              horizontal: 'left',
            }}
            PaperProps={{
              style: { maxWidth: '80%', wordWrap: 'break-word' },
            }}
            onMouseEnter={clearHoverTimeout}
            onMouseLeave={handlePopoverClose}
          >
            <Typography style={{ padding: '16px', marginTop: '16px', marginBottom: '16px', marginLeft: '16px' }}>
              {fullDisplayValue}
            </Typography>
          </Popover>
        }
      </div>
    );
  }

  function CustomToolbar({ setFilterModalOpen }) {
    return (
      <GridToolbarContainer>
        <GridToolbarColumnsButton />
        <Button
          startIcon={<FilterListIcon />}
          size="small"
          color="primary"
          onClick={() => setFilterModalOpen(true)}
        >
          Filter
        </Button>
        <GridToolbarDensitySelector />
        <GridToolbarExport />
      </GridToolbarContainer>
    );
  }

  const ToolbarWithProps = () => (
    <CustomToolbar setFilterModalOpen={setFilterModalOpen} />
  );

  const handleOffsetChange = (newOffset: number) => {
    const url = new URL(window.location.href);
    const urlSearchParams = url.searchParams;

    urlSearchParams.set('offset', newOffset.toString());
    url.search = urlSearchParams.toString();

    window.location.href = url.toString();
  };

  // Manager for the activeWidgetList
  function addActiveWidgetId(id: string) {
    setActiveWidgetList([id, ...activeWidgetList])
  }

  // Contains all features from the API call once the useEffect finished
  //const [features, setFeatures] = useState<ExtendedVcfFeature[]>([])
  const [features, setFeatures] = useState<any[]>([])
  const [columns, setColumns] = useState<GridColumns>([])

  const [error, setError] = useState<any>(undefined)

  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [filters, setFilters] = useState([]);
  const [fieldTypeInfo, setFieldTypeInfo] = useState([]);

  const [adapter, setAdapter] = useState<EVAdapterClass | undefined>(undefined)

  const [availableOperators, setAvailableOperators] = useState([]);

  // Active widget ID list to force rerender when a JBrowseUIButton is clicked
  const [activeWidgetList, setActiveWidgetList] = useState<string[]>([])

  const [isValidLocString, setIsValidLocString] = useState(true)

  // False until initial data load or an error:
  const [dataLoaded, setDataLoaded] = useState(!parsedLocString)

  const [pageSize, setPageSize] = React.useState<number>(25);

  // API call to retrieve the requested features.
  useEffect(() => {
    async function fetch() {
      const queryParam = new URLSearchParams(window.location.search)

      await fetchFieldTypeInfo(sessionId, trackGUID,
        (res: FieldModel[]) => {
          let columns: any = [];

          for (const fieldObj of res) {
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

            let column: any = { field: field, renderCell: (params: any) =>  { return <TableCellWithPopover value={params.value} /> }, description: fieldObj.description , headerName: fieldObj.label ?? field, minWidth: 25, width: fieldObj.colWidth ?? 50, maxWidth: 100, type: muiFieldType, flex: 1, headerAlign: 'left', align: "left", hide: fieldObj.isHidden }

            if (fieldObj.isMultiValued || field == "af") {
              column.sortComparator = multiValueComparator
              column.filterOperators = getGridNumericColumnOperators().map(op => multiModalOperator(op))
            }

            column.orderKey = fieldObj.orderKey;

            columns.push(column)
          }

          columns.sort((a, b) => a.orderKey - b.orderKey);
          const columnsWithoutOrderKey = columns.map(({ orderKey, ...rest }) => rest);

          setColumns(columnsWithoutOrderKey);
          const operators = fieldTypeInfoToOperators(res)
          setAvailableOperators(operators)
          setFieldTypeInfo(res)
          handleQuery(searchStringToInitialFilters(operators))
        },
        (error) => {
          setError(error)
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

  if (error) {
    throw new Error(error)
  }

  const getAdapterInstance = async () => {
    let adapterConfig = getConf(track, 'adapter')
    
    let a = (await getAdapter(
        pluginManager,
        sessionId,
        adapterConfig,
    )).dataAdapter as EVAdapterClass

    setAdapter(a)

    return a;
  }

  const showDetailsWidget = (rowIdx: number, params: any) => {
    (async () => {
        /*let a = adapter;

        if (!a) {
            a = await getAdapterInstance();
        }*/
        let a = await getAdapterInstance();

        const row = features[rowIdx] as any

        const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
            return assemblyManager.isValidRefName(refName, assemblyNames[0])
        }

        const parsedLocString = parseLocString(row.contig + ":" + row.start + ".." + row.end, isValidRefNameForAssembly)
        const refName = assembly.getCanonicalRefName(parsedLocString.refName)

        const ret = a.getFeatures({
          refName: refName,
          start: row.start - 1,
          end: row.end
        } as NoAssemblyRegion)

        const extendedFeatures = await ret.pipe(toArray()).toPromise()
        const feature = extendedFeatures[0];

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
    })();
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
              <Box sx={{lineHeight: '20px'}}><a className={"labkey-text-link"} onClick={() => { showDetailsWidget(params.row.id, params) }}>Variant Details</a></Box>
              <Box sx={{lineHeight: '20px'}}><a className={"labkey-text-link"} target="_blank" href={getGenotypeURL(params.row.trackId, params.row.contig, params.row.start, params.row.end)}>View Genotypes</a></Box>
              <Box sx={{lineHeight: '20px'}}><a className={"labkey-text-link"} target="_blank" href={getBrowserUrlNoFilters(sessionId, params.row.contig + ":" + params.row.start + ".." + params.row.end, trackId, track)}>View in Genome Browser</a></Box>
            </Box>
          </>
      )
    }
  }

  const gridElement = (
    <DataGrid
        columns={[...columns, actionsCol]}
        rows={features}
        components={{ Toolbar: ToolbarWithProps }}
        rowsPerPageOptions={[10,25,50,100]}
        pageSize={pageSize}
        density="comfortable"
        onPageSizeChange={(newPageSize) => setPageSize(newPageSize)}
      />
  )

  const renderHeaderCell = (params) => {
    return (
      <Tooltip title={params.colDef.description}>
        <div>{params.colDef.headerName}</div>
      </Tooltip>
    );
  };

  const filterModal = (
    <FilterFormModal open={filterModalOpen} handleClose={() => setFilterModalOpen(false)} 
                   sessionId={sessionId}
                   trackGUID={trackGUID}
                   handleQuery={(filters) => handleQuery(filters)}
                   setFilters={setFilters}
                   handleFailureCallback={() => {}}
                   availableOperators={availableOperators}
                   fieldTypeInfo={fieldTypeInfo}
                   components={{
                     headerCell: renderHeaderCell,
                    }}
  />
  );


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


      <div style={{ marginBottom: "10px", display: "flex", alignItems: "center" }}>

      <div style={{ flex: 1 }}>
        {filters.map((filter, index) => {
          if ((filter as any).field == "" || (filter as any).operator == "" || (filter as any).value == "" ) {
            return null;
          }
          return (
            <Button
              key={index}
              onClick={() => setFilterModalOpen(true)}
              style={{ border: "1px solid gray", margin: "5px" }}
            >
              {`${(filter as any).field} ${(filter as any).operator} ${(filter as any).value}`}
            </Button>
          );
        })}
      </div>

        <div style={{ marginLeft: "auto" }}>
          <ArrowPagination
            offset={currentOffset}
            onOffsetChange={handleOffsetChange}
          />
        </div>
      </div>

      {filterModal}
      {gridElement}
    </>
  )
})

export default VariantTableWidget
