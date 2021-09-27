import {ActionURL} from "@labkey/api";
import {style as styles} from "./style";
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
      const [state, setState] = React.useState({
       af02: false,
       impactHigh: false,
      });
      const classes = styles()
      const handleSubmit = (event) => {
         event.preventDefault();
         track.adapter.filters.set(JSON.stringify(state))
      }

      const handleChange = (event) => {
         setState({
            ...state,
            [event.target.name]: event.target.checked,
         });
      }

      const { af02, impactHigh } = state;
      return(
         <Paper>
            <form className={classes.filterGroup} onSubmit={handleSubmit}>
               <FormGroup >
                 <FormControlLabel className={classes.filterOption} control={<Checkbox checked={af02} onChange={handleChange} name="af02"/>} label="AF > 0.2" />
                 <FormControlLabel className={classes.filterOption} control={<Checkbox checked={impactHigh} onChange={handleChange} name="impactHigh"/>} label="Impact = HIGH" />
               </FormGroup>
               <input className={classes.button} type="submit" value="Apply" />
            </form>
         </Paper>
      )
   }

    return observer(FilterForm)
}