import {ActionURL} from "@labkey/api";
import {style as styles} from "./style";
import {filterMap as filters} from "./filters"
import {expandFilters, expandedFilterListToObj, expandedFilterObjToList} from "./filterUtil"
import {
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
  FormLabel
} from '@material-ui/core'
import { getContainingTrack, getSession, getContainingView, getContainingDisplay } from '@jbrowse/core/util'
import { readConfObject } from '@jbrowse/core/configuration'



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


   function FilterForm(props){
      const { model } = props
      let track = model.track

      let filterState = {}

      const configFilters = readConfObject(track, ['adapter', 'filters'])
      try {
          let trackFilters = track.adapter.filters
          if(trackFilters && (trackFilters != configFilters)){
            configFilters = trackFilters
          }
      } catch {

      }
      const expandedFilterList = expandFilters(configFilters)
      const expandedFilters = expandedFilterListToObj(expandedFilterList)

      //Object.entries(filters).map(([key, val]) => filterState[key] = val.selected)
      Object.entries(expandedFilters).map((key, val) => filterState[key[0]] = strToBool(key[1].selected))

      const [state, setState] = React.useState(filterState)
      const classes = styles()
      const handleSubmit = (event) => {
         event.preventDefault();
         let filterSubmit = filters
         //Object.entries(state).map(([key, val]) => filterSubmit[key].selected = val)
         Object.entries(state).map(([key, val]) => expandedFilters[key].selected = val.toString())
         try {
             track.adapter.filters.set(expandedFilterObjToList(expandedFilters)) // pass it back as a list of strings
         } catch(e){
             console.error("Error setting adapter filters.")
         }
      }

      const handleChange = (event) => {
         setState({
            ...state,
            [event.target.name]: event.target.checked,
         });
      }

      const labels =  Object.entries(state).map(([key, val]) =>
                       <FormControlLabel className={classes.filterOption} control={<Checkbox checked={val} onChange={handleChange} name={key}/>} label={expandedFilters[key].label} />
                      )
      return(
         <Paper>
            <form className={classes.filterGroup} onSubmit={handleSubmit}>
               <FormGroup >
                 {labels}
               </FormGroup>
               <input className={classes.button} type="submit" value="Apply" />
            </form>
         </Paper>
      )
   }

    return observer(FilterForm)
}