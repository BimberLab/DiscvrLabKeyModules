import { observer } from 'mobx-react';

import React from 'react';
import { FormControl, IconButton, MenuItem, Select, TableCell, TableRow, TextField, Tooltip } from '@mui/material';
import { filterMap } from './filters';
import ClearIcon from '@mui/icons-material/Clear';
import { styled } from '@mui/material/styles';

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
    const fieldDef = filterMap[filter.field]
    if (fieldDef.dataType === 'number') {
        // Ensure values like ".5" become 0.5. Remember we have integers, string, and double
        if (filter.value && filter.value.startsWith(".")) {
            filter.value = "0" + filter.value
        }
    }

    return [filter.field, filter.operator || '', filter.value || ''].join(':')
}

const InfoFilterRow = observer(props => {
    const { rowIdx, filterStr, filterChangeHandler, deleteHandler, hasSubmitted } = props
    const filterObj = convertFilterStringToObj(filterStr)

    const handleOperatorChange = (event) => {
        filterObj.operator = event.target.value
        filterChangeHandler(rowIdx, convertFilterObjToString(filterObj))
    };

    const handleValueChange = (event) => {
        filterObj.value = event.target.value
        filterChangeHandler(rowIdx, convertFilterObjToString(filterObj))
    }

    const handleFilterDelete = () => {
        deleteHandler(rowIdx)
    }

    const FormControlNumValue = styled(FormControl)(({ theme }) => ({
        margin: theme.spacing(1),
        minWidth: 100
    }))

    const FormControlS = styled(FormControl)(({ theme }) => ({
        margin: theme.spacing(1),
        padding: theme.spacing(2),
        minWidth: 400,
        display: 'flex'
    }))

    const TableCellS = styled(TableCell)(({ theme }) => ({
        textAlign: 'center',
        padding: theme.spacing(0.75, 0, 0.75, 1),
    }))

    const getValueComponent = ((filterObj) => {
        const fieldDef = filterMap[filterObj.field]
        if (fieldDef.dataType === 'number') {
            return (
                <FormControlNumValue>
                    <TextField
                        id="standard-number"
                        type="number"
                        inputProps={{
                            inputMode: "numeric",
                            min: fieldDef.minValue ? Number(fieldDef.minValue) : undefined,
                            max: fieldDef.maxValue ? Number(fieldDef.maxValue) : undefined
                        }}
                        value={filterObj.value}
                        required={true}
                        error={hasSubmitted && !filterObj.value}
                        onChange={handleValueChange}
                    />
                </FormControlNumValue>
            )
        } else if (filterObj) {
            return (
                <FormControlS>
                    <div>
                        <Select
                            labelId="category-select-label"
                            id="category-select"
                            value={filterObj.value}
                            required={true}
                            error={hasSubmitted && !filterObj.value}
                            onChange={handleValueChange}
                        >
                            {filterMap[filterObj["field"]].options.map((option) => (
                                <MenuItem key={option} value={option}>
                                    {option}
                                </MenuItem>
                            ))}
                        </Select>
                    </div>
                </FormControlS>
            )
        }
    })

    return (
        <>
            <TableRow>
                <TableCellS>
                    {filterMap[filterObj["field"]].title || filterObj["field"]}
                </TableCellS>
                <TableCellS>
                    <FormControlS>
                        <Select
                            labelId="category-select-label"
                            id="category-select"
                            value={filterObj.operator || ''}
                            error={hasSubmitted && !filterObj.operator}
                            onChange={handleOperatorChange}
                            required={true}
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
                    </FormControlS>
                </TableCellS>
                <TableCellS>
                    {getValueComponent(filterObj)}
                </TableCellS>
                <TableCellS>
                    <Tooltip title="Remove filter" aria-label="remove" placement="bottom">
                        <IconButton aria-label="remove filter" onClick={handleFilterDelete}>
                            <ClearIcon />
                        </IconButton>
                    </Tooltip>
                </TableCellS>
            </TableRow>
        </>
    )
})

export default InfoFilterRow