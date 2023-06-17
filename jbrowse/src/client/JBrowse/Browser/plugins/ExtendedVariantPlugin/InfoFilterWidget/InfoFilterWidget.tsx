import { style as styles } from './style';
import { filterMap } from './filters';
import { readConfObject } from '@jbrowse/core/configuration';
import { getSession } from '@jbrowse/core/util';
import React from 'react';
import {
    Box,
    Button,
    ClickAwayListener,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControl,
    Grow,
    MenuItem,
    MenuList,
    Paper,
    Popper,
    Table,
    TableBody
} from '@material-ui/core';

import InfoFilterRow from './InfoFilterRow';
import { SessionWithWidgets } from '@jbrowse/core/util/types';
import { observer } from 'mobx-react';

export default jbrowse => {

    function FilterForm(props){
        const classes = styles()
        const { model } = props
        let track = model.track

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
        const anchorRef = React.useRef(null);

        const handleToggle = () => {
            setOpen((prevOpen) => !prevOpen);
        };

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

        const handleClose = (event) => {
            if (anchorRef.current && anchorRef.current.contains(event.target)) {
                return;
            }

            setOpen(false)
        };

        // return focus to the button when we transitioned from !open -> open
        const prevOpen = React.useRef(open);
        React.useEffect(() => {
            if (prevOpen.current === true && open === false) {
                anchorRef.current.focus();
            }

            prevOpen.current = open;
        }, [open])

        return(
                <>
                    <div style={{padding: '5px' }}>
                    Only show variants where:
                    <Table className={classes.table}>
                        <TableBody>
                            {infoFilters.map((filterStr, key) =>
                                    <InfoFilterRow key={key} filterStr={filterStr} filterChangeHandler={filterChangeHandler} deleteHandler={onRowDelete} rowIdx={Number(key)} hasSubmitted={hasSubmitted}/>
                            )}
                        </TableBody>
                    </Table>
                    <FormControl className={classes.addNewControl} >
                        <Box padding={'5px'} mr="5px">
                        <Button
                                className={classes.button}
                                ref={anchorRef}
                                id="composition-button"
                                aria-controls={open ? 'composition-menu' : undefined}
                                aria-expanded={open ? 'true' : undefined}
                                aria-haspopup="true"
                                onClick={handleToggle}
                                variant="contained"
                                color="primary">
                            Add Filter
                        </Button>
                        <Popper
                                open={open}
                                anchorEl={anchorRef.current}
                                role={undefined}
                                placement="bottom-start"
                                transition
                                disablePortal
                        >
                            {({ TransitionProps, placement }) => (
                                    <Grow
                                            {...TransitionProps}
                                            style={{
                                                transformOrigin:
                                                        placement === 'bottom-start' ? 'left top' : 'left bottom',
                                            }}
                                    >
                                        <Paper>
                                            <ClickAwayListener onClickAway={handleClose}>
                                                <MenuList
                                                        autoFocusItem={open}
                                                        id="composition-menu"
                                                        aria-labelledby="composition-button"
                                                >
                                                        {
                                                            Object.entries(filterMap).map(([key, val]) =>
                                                                    <MenuItem value={key} key={key} onClick={handleMenuChange} data-field-name={key}>
                                                                        {val.title || key}
                                                                    </MenuItem>
                                                            )
                                                        }
                                                </MenuList>
                                            </ClickAwayListener>
                                        </Paper>
                                    </Grow>
                            )}
                        </Popper>
                        <Button className={classes.button} onClick={handleFilterSubmit} variant="contained" color="primary">
                            Apply
                        </Button>
                        <Button className={classes.button} onClick={clearFilters} variant="contained" color="primary">
                            Clear Filters
                        </Button>
                        </Box>
                    </FormControl>
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