import {ActionURL} from "@labkey/api";
import {style as styles} from "./style";
import {filterMap as filters} from "./filters"
import {expandFilters, expandedFilterListToObj, expandedFilterObjToList} from "./filterUtil"
import {
  MenuItem,
  Select,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  Button,
  List,
  ListItem,
  DialogActions,
  Checkbox,
  FormGroup,
  FormControlLabel,
  Tooltip,
  IconButton,
  AddIcon,
  FormLabel,
  FormControl,
} from '@material-ui/core'
import { getContainingTrack, getSession, getContainingView, getContainingDisplay } from '@jbrowse/core/util'
import { readConfObject } from '@jbrowse/core/configuration'
import Filter from './FilterComponent'


export default jbrowse => {
    const {
        Paper,
        Table,
        TableBody,
        TableCell,
        TableHead,
        TableRow
    } = jbrowse.jbrequire('@material-ui/core')
    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const React = jbrowse.jbrequire('react')
    const { useState, useEffect } = React
    const { FeatureDetails, BaseCard } = jbrowse.jbrequire(
            '@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail',
    )

    function strToBool(str){
        if(str === "true"){
            return true
        } else {
            return false
        }
    }

   /*const useStyles = makeStyles(theme => ({
     root: {
       padding: theme.spacing(1, 3, 1, 1),
       background: theme.palette.background.default,
       overflowX: 'hidden',
     },
     formControl: {
       margin: theme.spacing(1),
       minWidth: 150,
     },
   }))*/

   function FilterForm(props){
      const classes = styles()
      const { model } = props
      let track = model.track

      const configFilters = readConfObject(track, ['adapter', 'filters'])
      const expandedFilterList = expandFilters(configFilters)
      const expandedFilters = expandedFilterListToObj(expandedFilterList)

      const [newField, setNewField] = React.useState('')

      const handleFieldChange = (event) => {
         let tempFilters = [...readConfObject(track, ['adapter', 'filters'])]
         tempFilters.push(event.target.value + "::")
         track.adapter.filters.set(tempFilters)
      }

      const menuItems =
          Object.entries(filters).map(([key, val]) =>
              <MenuItem value={key} key={key}>
                  {key}
              </MenuItem>
          )

      // TODO - ERROR PREVENTION WHEN INVALID FILTER PASSED
      /*const filterComponents = Object.entries(configFilters).map(([key, val]) =>
         <Filter filterString={val} track={track} index={Number(key)}/>
      )*/
      return(
      <>
         {Object.entries(configFilters).map(([key, val]) =>
             <Filter filterString={val} track={track} index={Number(key)}/>
         )}
         <FormControl className={classes.formControl}>
             <Select
               labelId="category-select-label"
               id="category-select"
               value={newField}
               onChange={handleFieldChange}
               displayEmpty
             >
                 <MenuItem disabled value="">
                     <em>Add New Filter...</em>
                 </MenuItem>
                 {menuItems}
             </Select>
         </FormControl>
      </>
      )
   }

    return observer(FilterForm)
}

         /*<Paper>
            <form className={classes.filterGroup} onSubmit={handleSubmit}>
               <FormGroup >
                 {labels}
               </FormGroup>
               <input className={classes.button} type="submit" value="Apply" />
            </form>
         </Paper>*/