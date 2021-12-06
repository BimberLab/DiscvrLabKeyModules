import {observer} from "mobx-react";
import {style as styles} from "./style";

import React, {useState} from "react";
import {FormControl, IconButton, MenuItem, Select, TableCell, TableRow, TextField, Tooltip} from "@material-ui/core";
import {filterMap} from "./filters";
import ClearIcon from "@material-ui/icons/Clear";

export const OPERATOR_TO_DISPLAY = {
    lt: "<",
    gt: ">",
    eq: "="
}

function convertFilterStringToObj(filter){
    const splitFilter = filter.split(":")

    return {
        field: splitFilter[0],
        operator: splitFilter[1],
        value: splitFilter[2]
    }
}

function convertFilterObjToString(filter) {
    return [filter.field, filter.operator || '', filter.value || ''].join(':')
}

const InfoFilterRow = observer(props => {
    const classes = styles()
    const { rowIdx, infoFilters, filterChangeHandler, deleteHandler } = props

    console.log('row:')
    console.log(infoFilters)

    const filterObj = convertFilterStringToObj(infoFilters[rowIdx])

    const handleOperatorChange = (event) => {
        filterObj.operator = event.target.value
        console.log(filterObj)
        filterChangeHandler(rowIdx, convertFilterObjToString(filterObj))
    };

    const handleValueChange = (event) => {
        filterObj.value = event.target.value
        console.log(filterObj)
        filterChangeHandler(rowIdx, convertFilterObjToString(filterObj))
    }

    const handleFilterDelete = () => {
        deleteHandler(rowIdx)
    }

    const getValueComponent = ((filterObj) => {
        if (filterMap[filterObj.field].dataType === 'number') {
            return (
                    <FormControl className={classes.numValueControl}>
                        <TextField
                                id="standard-number"
                                type="number"
                                value={filterObj.value}
                                onChange={handleValueChange}
                        />
                    </FormControl>
            )
        } else if (filterObj) {
            return (
                    <FormControl className={classes.formControl}>
                        <div>
                            <Select
                                    labelId="category-select-label"
                                    id="category-select"
                                    value={filterObj.value}
                                    onChange={handleValueChange}
                                    displayEmpty
                            >
                                <MenuItem disabled value="">
                                    <em>Value</em>
                                </MenuItem>
                                {fields[filterObj["field"]].options.map((option) => (
                                        <MenuItem key={option} value={option}>
                                            {option}
                                        </MenuItem>
                                ))}
                            </Select>
                        </div>
                    </FormControl>
            )
        }
    })

    return (
            <>
                <TableRow>
                    <TableCell className={classes.tableCell}>
                        {filterMap[filterObj["field"]].title || filterObj["field"]}
                    </TableCell>
                    <TableCell className={classes.tableCell}>
                        <FormControl className={classes.formControl}>
                            <Select
                                    labelId="category-select-label"
                                    id="category-select"
                                    value={filterObj["operator"] || ''}
                                    onChange={handleOperatorChange}
                                    displayEmpty
                            >
                                <MenuItem disabled value="">
                                    <em>Operator</em>
                                </MenuItem>
                                {filterMap[filterObj["field"]].operators.map(operatorStr => {
                                    return (
                                            <MenuItem value={operatorStr} key={operatorStr}>
                                                {OPERATOR_TO_DISPLAY[operatorStr]}
                                            </MenuItem>
                                    )
                                })}
                            </Select>
                        </FormControl>
                    </TableCell>
                    <TableCell className={classes.tableCell}>
                        {getValueComponent(filterObj)}
                    </TableCell>
                    <TableCell className={classes.tableCell}>
                        <Tooltip title="Remove filter" aria-label="remove" placement="bottom">
                            <IconButton aria-label="remove filter" onClick={handleFilterDelete}>
                                <ClearIcon />
                            </IconButton>
                        </Tooltip>
                    </TableCell>
                </TableRow>
            </>
    )
})

export default InfoFilterRow