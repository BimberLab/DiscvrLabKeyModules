import { filterMap } from './filters';
import { readConfObject } from '@jbrowse/core/configuration';
import { getSession } from '@jbrowse/core/util';
import React from 'react';
import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControl,
    Menu,
    MenuItem,
    Table,
    TableBody
} from '@mui/material';

import InfoFilterRow from './InfoFilterRow';
import { SessionWithWidgets } from '@jbrowse/core/util/types';
import { observer } from 'mobx-react';
import { styled } from '@mui/material/styles';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';

export default jbrowse => {
    const FormControlButtonBar = styled(FormControl)(({ theme }) => ({
        margin: theme.spacing(1),
        padding: theme.spacing(2),
        minWidth: 400,
        display: 'flex'
    }))

    const ButtonS = styled(Button)(({ theme }) => ({
        maxWidth: 150,
        marginRight: theme.spacing(2)
    }))

    const TableNoPaddingBlock = styled(Table)(({ theme }) => ({
        padding: 0,
        display: 'block'
    }))

    function FilterForm(props){
        const { model } = props
        let track = model.track

        // @ts-ignore
        const displays = readConfObject(track, 'displays')
        const initialFilters = displays[0].renderer.infoFilters || []

        const [infoFilters, setInfoFilters] = React.useState(initialFilters)
        const [hasSubmitted, setHasSubmitted] = React.useState(false)

        const onRowDelete = (rowIdx) => {
            infoFilters.splice(rowIdx, 1)
            setInfoFilters([...infoFilters])
        }

        const hasInvalidFilters = (filters) => {
            if (!filters){
                return false
            }

            // TODO: data-driven
            for (const filter of filters){
                const tokens = filter.split(':')
                if (tokens.length !== 3) {
                    return true
                }

                if (!tokens[0] || !tokens[1] || !tokens[2]) {
                    return true
                }
            }

            return false
        }

        const handleFilterSubmit = (event) => {
            setHasSubmitted(true)

            if (hasInvalidFilters(infoFilters)) {
                setAlertOpen(true)
                return
            }

            track.displays[0].renderer.infoFilters.set([...infoFilters])
            const m = getSession(model) as SessionWithWidgets
            m.hideWidget(model)
        }

        const clearFilters = (event) => {
            track.displays[0].renderer.infoFilters.set([])
            const m = getSession(model) as SessionWithWidgets
            m.hideWidget(model)
        }

        const filterChangeHandler = (rowIdx, filterStr) => {
            infoFilters[rowIdx] = filterStr
            setInfoFilters([...infoFilters])
        }

        const [alertOpen, setAlertOpen] = React.useState(false);

        // Based on: https://mui.com/components/menus/#menulist-composition
        const [open, setOpen] = React.useState(false);
        const buttonRef = React.useRef(null);

        const handleAlertClose = () => {
            setAlertOpen(false);
        };

        const handleMenuChange = (event) => {
            setOpen(false)

            const { fieldName } = event.currentTarget.dataset;
            const op = (filterMap[fieldName].operators.length === 1 ? filterMap[fieldName].operators[0] : '')
            infoFilters.push(fieldName + ":" + op + ":")
            setInfoFilters([...infoFilters])
        }

        const handleMenuClose = () => {
            setOpen(false)
        }

        return(
            <>
                <div style={{padding: '5px' }}>
                    Only show variants where:
                    <TableNoPaddingBlock>
                        <TableBody>
                            {infoFilters.map((filterStr, key) =>
                                <InfoFilterRow key={key} filterStr={filterStr} filterChangeHandler={filterChangeHandler} deleteHandler={onRowDelete} rowIdx={Number(key)} hasSubmitted={hasSubmitted}/>
                            )}
                        </TableBody>
                    </TableNoPaddingBlock>
                    <FormControlButtonBar>
                        <Box padding={'5px'}>
                            <Button
                                ref={buttonRef}
                                sx={{maxWidth: 150, marginRight: 2}}
                                variant="contained"
                                onClick={() => setOpen(!open)}
                                endIcon={<KeyboardArrowDownIcon />}
                                color="primary">
                                Add Filter
                            </Button>
                            <Menu open={open} onClose={handleMenuClose} anchorEl={buttonRef.current}>
                                {Object.entries(filterMap).map(([key, val]) =>
                                    <MenuItem value={key} key={key} onClick={handleMenuChange} data-field-name={key}>
                                        {val.title || key}
                                    </MenuItem>
                                )}
                            </Menu>
                            <ButtonS onClick={handleFilterSubmit} variant="contained" color="primary">
                                Apply
                            </ButtonS>
                            <ButtonS onClick={clearFilters} variant="contained" color="primary">
                                Clear Filters
                            </ButtonS>
                        </Box>
                    </FormControlButtonBar>
                </div>
                <Dialog
                    open={alertOpen}
                    onClose={handleAlertClose}
                    aria-labelledby="alert-dialog-title"
                    aria-describedby="alert-dialog-description"
                >
                    <DialogTitle id="alert-dialog-title">Invalid Filters</DialogTitle>
                    <DialogContent>
                        <DialogContentText id="alert-dialog-description">
                            One or more filters is not complete. Either fill out all fields or use the 'x' buttons to remove invalid filters
                        </DialogContentText>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={handleAlertClose}>OK</Button>
                    </DialogActions>
                </Dialog>
            </>
        )
    }

    return observer(FilterForm)
}