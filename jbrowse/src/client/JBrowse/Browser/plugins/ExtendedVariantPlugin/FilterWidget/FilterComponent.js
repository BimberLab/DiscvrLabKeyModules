import AddIcon from '@material-ui/icons/Add'
import ClearIcon from '@material-ui/icons/Clear'
import { observer } from 'mobx-react'
import React, { useState } from 'react'
import {filterMap as fields} from './filters'
import { readConfObject } from '@jbrowse/core/configuration'
import { unexpandedFilterStringToObj, operators as utilOperators} from './filterUtil'
import {style as styles} from "./style";

import {
  MenuItem,
  FormControl,
  Select,
  IconButton,
  List,
  ListItem,
  Tooltip,
  makeStyles,
  TextField,
  TableCell,
  TableRow
} from '@material-ui/core'

function eqConvert(eqString){
   if(eqString == "=="){
      eqString = "="
   }
   return eqString
}

const OptionFilterComponent = observer(props => {
    const classes = styles()
    const { filterObj, track, index } = props
    let operators = JSON.parse(JSON.stringify(utilOperators))
    operators["eq"] = "="
    const baseOp = operators[filterObj["operator"]] ?? ""
    const baseVal = filterObj["value"].replaceAll("'", "") ?? ""

    const [operator, setOperator] = useState(baseOp)
    const handleOperatorChange = (event) => {
        setOperator(event.target.value);
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        filterObj["operator"] = Object.keys(operators).find(key => operators[key] === event.target.value)
        tempFilters[index] = filterObj["field"] + ":" + filterObj["operator"] + ":" + filterObj["value"]
        track.adapter.filters.set(tempFilters)
    };

    const [value, setValue] = useState(baseVal)
    const handleValueChange = (event) => {
        setValue(event.target.value);
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        filterObj["value"] = event.target.value
        tempFilters[index] = filterObj["field"] + ":" + filterObj["operator"] + ":'" + filterObj["value"] + "'"
        track.adapter.filters.set(tempFilters)
    }

    const handleFilterDelete = (event) => {
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        tempFilters.splice(index, 1)
        track.adapter.filters.set(tempFilters)
    }

   if(!Object.is(operator, baseOp)){
      setOperator(baseOp)
   }

   if(!Object.is(value, baseVal)){
      setValue(baseVal)
   }

    return (
    <>
      <TableRow>
         <TableCell className={classes.tableCell}>
            {filterObj["field"]}
         </TableCell>
         <TableCell className={classes.tableCell}>
            <FormControl className={classes.formControl}>
                <Select
                  labelId="category-select-label"
                  id="category-select"
                  value={operator}
                  onChange={handleOperatorChange}
                  displayEmpty
                >
                  <MenuItem disabled value="">
                    <em>Operator</em>
                  </MenuItem>
                  {fields[filterObj["field"]].operators.map(operator => {
                    return (
                      <MenuItem value={eqConvert(operator)} key={eqConvert(operator)}>
                        {eqConvert(operator)}
                      </MenuItem>
                    )
                  })}
                </Select>
            </FormControl>
         </TableCell>
         <TableCell className={classes.tableCell}>
            <FormControl className={classes.formControl}>
              <div>
                <Select
                  labelId="category-select-label"
                  id="category-select"
                  value={value}
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

const NumericFilterComponent = observer(props => {
    const classes = styles()
    const { filterObj, track, index } = props
    let operators = JSON.parse(JSON.stringify(utilOperators))
    operators["eq"] = "="
    const baseOp = operators[filterObj["operator"]] ?? ""
    const baseVal = filterObj["value"] ?? ""

    const [operator, setOperator] = React.useState(baseOp)
    const handleOperatorChange = (event) => {
        setOperator(event.target.value);
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        filterObj["operator"] = Object.keys(operators).find(key => operators[key] === event.target.value)
        tempFilters[index] = filterObj["field"] + ":" + filterObj["operator"] + ":" + filterObj["value"]
        track.adapter.filters.set(tempFilters)
    };

    const [value, setValue] = React.useState(baseVal)
    const handleValueChange = (event) => {
        setValue(event.target.value);
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        filterObj["value"] = event.target.value
        tempFilters[index] = filterObj["field"] + ":" + filterObj["operator"] + ":" + filterObj["value"]
        track.adapter.filters.set(tempFilters)
    };

    const handleFilterDelete = (event) => {
        let filters = readConfObject(track, ['adapter', 'filters'])
        let tempFilters = [...filters]
        tempFilters.splice(index, 1)
        track.adapter.filters.set(tempFilters)
    }

   if(!Object.is(operator, baseOp)){
      setOperator(baseOp)
   }

   if(!Object.is(value, baseVal)){
      setValue(baseVal)
   }
    return (
        <>
          <TableRow>
            <TableCell className={classes.tableCell}>
               {filterObj["field"]}
            </TableCell>
            <TableCell className={classes.tableCell}>
                <FormControl className={classes.formControl}>
                    <Select
                      labelId="category-select-label"
                      id="category-select"
                      value={operator}
                      onChange={handleOperatorChange}
                      displayEmpty
                    >
                      <MenuItem disabled value="">
                        <em>Operator</em>
                      </MenuItem>
                      {fields["AF"].operators.map(operator => {
                        return (
                          <MenuItem value={eqConvert(operator)} key={eqConvert(operator)}>
                            {eqConvert(operator)}
                          </MenuItem>
                        )
                      })}
                    </Select>
                </FormControl>
            </TableCell>
            <TableCell className={classes.tableCell}>
                <FormControl className={classes.numValueControl}>
                  <TextField
                    id="standard-number"
                    type="number"
                    value={value}
                    onChange={handleValueChange}
                  />
                </FormControl>
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

const Filter = observer(props => {
    const { filterString, track, index } = props
    const filterObj = unexpandedFilterStringToObj(filterString)
    const field = filterObj.field
    if(fields[field].dataType == "number"){
        return (<NumericFilterComponent filterObj={filterObj} track={track} index={index}/>)
    }
    if(fields[field].dataType == "string"){
        return (<OptionFilterComponent filterObj={filterObj} track={track} index={index}/>)
    }
})


export default Filter