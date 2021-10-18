import AddIcon from '@material-ui/icons/Add'
import ClearIcon from '@material-ui/icons/Clear'

import { observer } from 'mobx-react'
import React, { useState } from 'react'
import {filterMap as fields} from './filters'
import { readConfObject } from '@jbrowse/core/configuration'

import { unexpandedFilterStringToObj, operators } from './filterUtil'

import {
  MenuItem,
  FormControl,
  FormLabel,
  Select,
  Input,
  Checkbox,
  ListItemText,
  IconButton,
  List,
  ListItem,
  Tooltip,
  makeStyles,
  TextField
} from '@material-ui/core'

const useStyles = makeStyles(theme => ({
  root: {
    padding: theme.spacing(1, 3, 1, 1),
    background: theme.palette.background.default,
    overflowX: 'hidden',
  },
  formControl: {
    margin: theme.spacing(1),
    minWidth: 50,
  },
}))

const OptionFilterComponent = observer(props => {
    const classes = useStyles()
    const { filterObj, track, index } = props

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
      <List>
        <ListItem>
            <Tooltip title="Remove filter" aria-label="remove" placement="bottom">
              <IconButton aria-label="remove filter" onClick={handleFilterDelete}>
                <ClearIcon />
              </IconButton>
            </Tooltip>
            {filterObj["field"]}
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
                      <MenuItem value={operator} key={operator}>
                        {operator}
                      </MenuItem>
                    )
                  })}
                </Select>
            </FormControl>
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
        </ListItem>
      </List>
    </>
   )
})

const NumericFilterComponent = observer(props => {
    const { filterObj, track, index } = props
    const classes = useStyles()

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
          <List>
            <ListItem>
               <Tooltip title="Remove filter" aria-label="remove" placement="bottom">
                 <IconButton aria-label="remove filter" onClick={handleFilterDelete}>
                   <ClearIcon />
                 </IconButton>
               </Tooltip>
               {filterObj["field"]}
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
                          <MenuItem value={operator} key={operator}>
                            {operator}
                          </MenuItem>
                        )
                      })}
                    </Select>
                </FormControl>
                <FormControl className={classes.formControl}>
                  <TextField
                    id="standard-number"
                    type="number"
                    value={value}
                    onChange={handleValueChange}
                  />
                </FormControl>
            </ListItem>
          </List>
        </>
    )

})

const Filter = observer(props => {
    //const { filter, attribute } = props // unexpanded filter string, AF
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


//export FilterList
export default Filter