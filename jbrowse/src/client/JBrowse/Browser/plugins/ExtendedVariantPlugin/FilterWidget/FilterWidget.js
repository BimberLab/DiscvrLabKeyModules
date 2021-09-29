import {ActionURL} from "@labkey/api";
import {style as styles} from "./style";
import {filterMap as filters} from "./filters"
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
import { getConf } from '@jbrowse/core/configuration'



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

   const filterOptions = ['Impact = HIGH', 'AF > 0.2', 'None']


   function FilterForm(props){
      const { model } = props
      let track = model.track

      let filterState = {}
      Object.entries(filters).map(([key, val]) => filterState[key] = val.selected)

      const [state, setState] = React.useState(filterState)
      const classes = styles()
      const handleSubmit = (event) => {
         event.preventDefault();
         let filterSubmit = filters
         Object.entries(state).map(([key, val]) => filterSubmit[key].selected = val)
         track.adapter.filters.set(JSON.stringify(filterSubmit))
      }

      const handleChange = (event) => {
         setState({
            ...state,
            [event.target.name]: event.target.checked,
         });
      }

      const labels =  Object.entries(state).map(([key, val]) =>
                       <FormControlLabel className={classes.filterOption} control={<Checkbox checked={val} onChange={handleChange} name={key}/>} label={filters[key].title} />
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