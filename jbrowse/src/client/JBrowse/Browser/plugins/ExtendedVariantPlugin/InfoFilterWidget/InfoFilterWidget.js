import {style as styles} from "./style";
import {filterMap} from "./filters"
import React, {useState} from 'react'
import {readConfObject} from '@jbrowse/core/configuration'
import {getSession} from '@jbrowse/core/util'

import {Button, FormControl, MenuItem, Select, Table, TableBody} from '@material-ui/core'
import {removeInvalidUnexpandedFilters} from './filterUtil'
import InfoFilterRow from "./InfoFilterRow";


export default jbrowse => {
    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const React = jbrowse.jbrequire('react')

    function FilterForm(props){
        const classes = styles()
        const { model } = props
        let track = model.track

        const displays = readConfObject(track, ['displays']) || []
        const initialFilters = displays[0].renderer.infoFilters || []

        console.log('initial filters')
        console.log(initialFilters)
        const [infoFilters, setInfoFilters] = useState(initialFilters)

        const addNewFilterRow = (event) => {
            console.log('adding new row: ' + event.target.value)
            infoFilters.push(event.target.value + "::")
            setInfoFilters(infoFilters)
        }

        const onRowDelete = (rowIdx) => {
            console.log('row delete: ' + rowIdx)
            infoFilters.splice(rowIdx, 1);
        }

        const handleFilterSubmit = (event) => {
            //TODO: user feedback if invalid?
            let infoFiltersToAdd = removeInvalidUnexpandedFilters(infoFilters)
            console.log(infoFiltersToAdd)

            track.displays[0].renderer.infoFilters.set(infoFiltersToAdd)
            getSession(model).hideWidget(model)
        }

        const filterChangeHandler = (rowIdx, filterStr) => {
            console.log('setting: ' + filterStr)
            infoFilters[rowIdx] = filterStr
        }

        const menuItems =
                Object.entries(filterMap).map(([key, val]) =>
                        <MenuItem value={key} key={key}>
                            {val.title || key}
                        </MenuItem>
                )

        return(
                <>
                    <Table className={classes.table}>
                        <TableBody>
                            {infoFilters.map((filterStr, key) =>
                                    <InfoFilterRow key={key} infoFilters={infoFilters} filterChangeHandler={filterChangeHandler} deleteHandler={onRowDelete} rowIdx={Number(key)}/>
                            )}
                        </TableBody>
                    </Table>
                    <FormControl className={classes.addNewControl} style={{maxWidth: 400}}>
                        <Select
                                labelId="category-select-label"
                                id="category-select"
                                onChange={addNewFilterRow}
                                value=""
                                displayEmpty
                        >
                            <MenuItem disabled value="">
                                <em>Add New Filter</em>
                            </MenuItem>
                            {menuItems}
                        </Select>
                        <p/>
                        <Button className={classes.applyButton} onClick={handleFilterSubmit} variant="contained" color="primary">
                            Apply
                        </Button>
                    </FormControl>
                </>
        )
    }

    return observer(FilterForm)
}