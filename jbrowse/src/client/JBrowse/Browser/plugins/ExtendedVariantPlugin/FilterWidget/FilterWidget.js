import {ActionURL} from "@labkey/api";
import {style as styles} from "./style";
import {filterMap as FILTERS } from "./filters"

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
import { getConf, readConfObject } from '@jbrowse/core/configuration'



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

    function FilterForm(props){
        const { model } = props
        const { trackConfig } = model

        //Load the defaults from the track's config. This should let us provide them at load time in the session JSON
        const existingFilters = readConfObject(trackConfig, ['adapter', 'filters']) || [];

        // TODO: we can leave this alone for now, but once we support filters with user-supplied thresholds, this logic doesnt work anymore
        // We should instead make this UI has 'Add' and 'Remove' buttons. The user could hit add, and then pick a filter type (based on register types defined in FILTERS).
        let filterState = {}
        Object.entries(FILTERS).map(([key, val]) => filterState[key] = existingFilters.indexOf(key) > -1)

        const [state, setState] = React.useState(filterState)
        const classes = styles()
        const handleSubmit = (event) => {
            event.preventDefault();
            let activeFilters = []
            Object.entries(state).map(([key, val]) => {
                if (val)
                    activeFilters.push(key)
            })

            //TODO: unsure if this could ever happen since we query it above?
            if (!trackConfig.adapter || !trackConfig.adapter.filters) {
                console.error("trackConfig.adapter.filters was null in FilterWidget!")
            }

            // TODO: updating this seems to automatically destroy all the rendered variants
            // Maybe there should be a check here to change over prior filters?
            // Maybe this should always trigger re-rendering?
            trackConfig.adapter.filters.set(activeFilters)

            //TODO: should this close the widget?
        }

        const handleChange = (event) => {
            setState({
                ...state,
                [event.target.name]: event.target.checked,
            });
        }

        const labels =  Object.entries(FILTERS).map(([key, val]) =>
                <FormControlLabel key={key} className={classes.filterOption} control={<Checkbox checked={!!state[key]} onChange={handleChange} name={key}/>} label={FILTERS[key].title} />
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