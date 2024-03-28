import { observer } from 'mobx-react';
import {
    DataGrid,
    GridColDef,
    GridColumnVisibilityModel,
    GridPaginationModel,
    GridRenderCellParams,
    GridSortDirection,
    GridSortModel,
    GridToolbarColumnsButton,
    GridToolbarContainer,
    GridToolbarDensitySelector,
    GridToolbarExport
} from '@mui/x-data-grid';
import SearchIcon from '@mui/icons-material/Search';
import LinkIcon from '@mui/icons-material/Link';
import React, { useEffect, useState } from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { AppBar, Box, Button, Dialog, Paper, Popover, Toolbar, Tooltip, Typography } from '@mui/material';
import { FilterFormModal } from './FilterFormModal';
import { getAdapter } from '@jbrowse/core/data_adapters/dataAdapterCache';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import { toArray } from 'rxjs/operators';
import {
    createEncodedFilterString,
    fetchFieldTypeInfo,
    fetchLuceneQuery,
    FieldModel,
    Filter,
    getBrowserUrlNoFilters,
    getGenotypeURL,
    searchStringToInitialFilters,
    truncateToValidGUID,
} from '../../utils';
import { parseLocString } from '@jbrowse/core/util';
import '../../jbrowse.css';
import LoadingIndicator from './LoadingIndicator';
import { BaseFeatureDataAdapter } from '@jbrowse/core/data_adapters/BaseAdapter';
import { lastValueFrom } from 'rxjs';

const VariantTableWidget = observer(props => {
    const { assembly, trackId, parsedLocString, sessionId, session, pluginManager } = props;
    const { assemblyNames = [], assemblyManager } = session ?? {};
    const { view } = session ?? {};

    var track = undefined;
    var trackGUID = undefined;
    if(view && trackId) {
        // The code expects a proper GUID, yet the trackId is a string containing the GUID + filename
        // NOTE: since the trackId is GUID+filename, allow exact string matching, or a match on the GUID portion alone.
        // Upstream code might only have access to the GUID and translating to the trackId isnt always easy
        trackGUID = truncateToValidGUID(props.trackId)
        track = view.tracks.find(
            t => t.configuration.trackId === trackId || truncateToValidGUID(t.configuration.trackId).toUpperCase() === trackGUID.toUpperCase()
        )
    }

    function handleSearch(data) {
        setFeatures(data.data.map((obj, idx) => {
            obj["id"] = idx
            obj["trackId"] = trackId

            return obj
        }))
        setTotalHits(data.totalHits)
        setDataLoaded(true)
    }

    function handleModalClose(widget) {
        session.hideWidget(widget)
    }

    function handleQuery(passedFilters, pushToHistory, pageQueryModel = pageSizeModel, sortQueryModel = sortModel) {
        const { page = pageSizeModel.page, pageSize = pageSizeModel.pageSize } = pageQueryModel;
        const { field = "genomicPosition", sort = false } = sortQueryModel[0] ?? {};

        const encodedSearchString = createEncodedFilterString(passedFilters, false);
        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.set("searchString", encodedSearchString);
        currentUrl.searchParams.set("page", page.toString());
        currentUrl.searchParams.set("pageSize", pageSize.toString());
        currentUrl.searchParams.set("sortField", field.toString());
        currentUrl.searchParams.set("sortDirection", sort.toString());

        if (pushToHistory) {
          window.history.pushState(null, "", currentUrl.toString());
        }

        setFilters(passedFilters);
        setDataLoaded(false)
        fetchLuceneQuery(passedFilters, sessionId, trackGUID, page, pageSize, field, sort, (json)=>{handleSearch(json)}, (error) => {setDataLoaded(true); setError(error)});
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
                    startIcon={<SearchIcon />}
                    size="small"
                    color="primary"
                    onClick={() => setFilterModalOpen(true)}
                >
                    Search
                </Button>
                <GridToolbarDensitySelector />
                <GridToolbarExport csvOptions={{
                    delimiter: ';',
                }} />

                <Button
                    startIcon={<LinkIcon />}
                    size="small"
                    color="primary"
                    onClick={() => {
                        navigator.clipboard.writeText(window.location.href)
                        .then(() => {
                            // Popup message for successful copy
                            alert('URL copied to clipboard.');
                        })
                        .catch(err => {
                            // Error handling
                            console.error('Failed to copy the URL: ', err);
                            alert('Failed to copy the URL.');
                        });
                    }}
                >
                Share
                </Button>
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
    const [features, setFeatures] = useState<any[]>([])
    const [columns, setColumns] = useState<GridColDef[]>([])

    const [error, setError] = useState<any>(undefined)

    const [filterModalOpen, setFilterModalOpen] = useState(false);
    const [filters, setFilters] = useState([]);
    const [totalHits, setTotalHits] = useState(0);
    const [fieldTypeInfo, setFieldTypeInfo] = useState<FieldModel[]>([]);
    const [allowedGroupNames, setAllowedGroupNames] = useState<string[]>([]);
    const [promotedFilters, setPromotedFilters] = useState<Map<string, Filter[]>>(null);
    const [adapter, setAdapter] = useState<BaseFeatureDataAdapter>(null)

    // Active widget ID list to force rerender when a JBrowseUIButton is clicked
    const [activeWidgetList, setActiveWidgetList] = useState<string[]>([])

    // False until initial data load or an error:
    const [dataLoaded, setDataLoaded] = useState(false)

    const urlParams = new URLSearchParams(window.location.search)
    const page = parseInt(urlParams.get('page') || '0')
    const pageSize = parseInt(urlParams.get('pageSize') || '50')
    const [pageSizeModel, setPageSizeModel] = React.useState<GridPaginationModel>({ page, pageSize });

    const sortField = urlParams.get('sortField') || 'genomicPosition'
    const sortDirection = urlParams.get('sortDirection') || 'desc'
    const [sortModel, setSortModel] = React.useState<GridSortModel>([{ field: sortField, sort: sortDirection as GridSortDirection }])

    const colVisURLComponent = urlParams.get("colVisModel") || "{}"
    const colVisModel = JSON.parse(decodeURIComponent(colVisURLComponent))
    const [columnVisibilityModel, setColumnVisibilityModel] = useState<GridColumnVisibilityModel>(colVisModel);

    // API call to retrieve the requested features.
    useEffect(() => {
        const handlePopState = () => {
          window.location.reload();
        };
        window.addEventListener('popstate', handlePopState);

        async function fetch() {
            await fetchFieldTypeInfo(sessionId, trackGUID,
                (fields: FieldModel[], groups: string[], promotedFilters: Map<string, Filter[]>) => {
                    fields.sort((a, b) => a.orderKey - b.orderKey || a.getLabel().toLowerCase().localeCompare(b.getLabel().toLowerCase()))

                    let columns: GridColDef[] = fields.filter((x) => !x.isHidden).map((x) => {
                        return {...x.toGridColDef(),
                            renderCell: (params: any) =>  { return <TableCellWithPopover value={params.value} /> }
                        }
                    })

                    setColumns(columns)

                    if(JSON.stringify(columnVisibilityModel) === '{}') {
                        const defaultModel = {};
                        fields.filter((x) => !x.isHidden).forEach((x) => {
                            defaultModel[x.name] = !!x.isInDefaultColumns;
                        });
                        setColumnVisibilityModel(defaultModel);
                    } else {
                        const updatedModel = fields.reduce((acc, field) => {
                            acc[field.name] = columnVisibilityModel[field.name] === true;
                            return acc;
                        }, {});
                        setColumnVisibilityModel(updatedModel);
                    }

                    setFieldTypeInfo(fields)
                    setAllowedGroupNames(groups)
                    setPromotedFilters(promotedFilters)

                    handleQuery(searchStringToInitialFilters(fields.map((x) => x.name)), false)
                },
                (error) => {
                    setError(error)
                })
        }

        if(sessionId && trackGUID) {
            fetch()
        }

        return () => {
          window.removeEventListener('popstate', handlePopState);
        };

    }, [pluginManager, parsedLocString, session?.visibleWidget, sessionId, trackGUID])

    if (error) {
        throw new Error(error)
    }

    const getAdapterInstance = async () => {
        // @ts-ignore
        let adapterConfig = getConf(track, 'adapter')

        let a = (await getAdapter(
            pluginManager,
            sessionId,
            adapterConfig,
        )).dataAdapter as BaseFeatureDataAdapter

        setAdapter(a)

        return a;
    }

    const showDetailsWidget = (rowIdx: number, params: any) => {
        (async () => {
            let a = await getAdapterInstance();

            const row = features[rowIdx] as any

            const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
                return assemblyManager.isValidRefName(refName, assemblyNames[0])
            }

            const parsedLocString = parseLocString(row.contig + ":" + row.start + ".." + row.end, isValidRefNameForAssembly)
            const refName = assembly.getCanonicalRefName(parsedLocString.refName)

            const featuresObservable = a.getFeatures({
                refName: refName,
                start: row.start - 1,
                end: row.end
            } as NoAssemblyRegion).pipe(toArray())

            const extendedFeatures = await lastValueFrom(featuresObservable)

            // TODO: sanity check this to ensure we have the right feature, especially if there are different alleles
            if (extendedFeatures.length > 1) {

            }

            const feature = extendedFeatures[0]
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
            density="comfortable"
            slots={{
                toolbar: ToolbarWithProps
            }}
            columnVisibilityModel={columnVisibilityModel}
            pageSizeOptions={[10,25,50,100]}
            paginationModel={ pageSizeModel }
            rowCount={ totalHits }
            paginationMode="server"
            onPaginationModelChange = {(newModel) => {
                setPageSizeModel(newModel)
                handleQuery(filters, true, newModel)
            }}
            onColumnVisibilityModelChange={(model) => {
                setColumnVisibilityModel(model)

                const trueValuesModel = Object.keys(model).reduce((acc, key) => {
                    if (model[key] === true) {
                        acc[key] = true;
                    }
                    return acc;
                }, {});

                const currentUrl = new URL(window.location.href);
                currentUrl.searchParams.set("colVisModel", encodeURIComponent(JSON.stringify(trueValuesModel)));
                window.history.pushState(null, "", currentUrl.toString());
            }}
            sortingMode="server"
            onSortModelChange={(newModel) => {
                setSortModel(newModel)
                handleQuery(filters, true, { page: 0, pageSize: pageSizeModel.pageSize }, newModel);
            }}
        />
    )

    const renderHeaderCell = (params) => {
        return (
            <Tooltip title={params.colDef.description}>
                <div style={{fontSize: 16}}>{params.colDef.headerName}</div>
            </Tooltip>
        );
    };


    const filterModal = (
        <FilterFormModal
            open={filterModalOpen}
            handleClose={() => setFilterModalOpen(false)}
            filterProps={{
                setFilters: setFilters,
                fieldTypeInfo: fieldTypeInfo,
                allowedGroupNames: allowedGroupNames,
                promotedFilters: promotedFilters,
                handleQuery: (filters) => handleQuery(filters, true, { page: 0, pageSize: pageSizeModel.pageSize}, sortModel)
            }}
        />
    );


    return (
        <>
            <LoadingIndicator isOpen={!dataLoaded}/>

            {
                [...(session?.activeWidgets ?? [])].map((elem) => {
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

                                <Box sx={{ margin: '12px' }}>
                                    <ReactComponent model={visibleWidget} style={{ margin: '12px' }}/>
                                </Box>
                            </Paper>
                        </Dialog>
                    )
                })
            }

            <div style={{ marginBottom: "10px", display: "flex", alignItems: "center" }}>
                <div style={{ flex: 1 }}>
                    {filters.map((filter, index) => {
                        if ((filter as any).field && ((filter as any).operator === "is empty" || (filter as any).operator === "is not empty") && !(filter as any).value) {
                            return (
                                <Button
                                    key={index}
                                    onClick={() => setFilterModalOpen(true)}
                                    style={{ border: "1px solid gray", margin: "5px" }}
                                >
                                    {`${(filter as any).field} ${(filter as any).operator}`}
                                </Button>
                            );
                        }
                        if ((filter as any).field == "" || (filter as any).operator == "" || (filter as any).value == "" ) {
                            return (<Button
                                key={index}
                                onClick={() => setFilterModalOpen(true)}
                                style={{ border: "1px solid gray", margin: "5px" }}
                                >
                                No filters
                            </Button>)
                        }
                        return (
                            <Button
                                key={index}
                                onClick={() => setFilterModalOpen(true)}
                                style={{ border: "1px solid gray", margin: "5px" }}                            >
                                {`${(filter as any).field} ${(filter as any).operator} ${(filter as any).value}`}
                            </Button>
                        );
                    })}
                </div>
            </div>

            {filterModal}
            {gridElement}
        </>
    )
})

export default VariantTableWidget
