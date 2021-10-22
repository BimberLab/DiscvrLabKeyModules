import {style as styles} from "./style";
import {filterMap as filters} from "./filters"

import {
  MenuItem,
  Select,
  FormControl,
  Table,
  TableHead,
  TableBody,
} from '@material-ui/core'
import { getContainingTrack, getSession, getContainingView, getContainingDisplay } from '@jbrowse/core/util'
import { readConfObject } from '@jbrowse/core/configuration'
import Filter from './FilterComponent'


export default jbrowse => {
    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const React = jbrowse.jbrequire('react')

   function FilterForm(props){
      const classes = styles()
      const { model } = props
      let track = model.track

      const configFilters = readConfObject(track, ['adapter', 'filters'])

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

      return(
      <>
         <Table className={classes.table}>
            <TableBody>
            {Object.entries(configFilters).map(([key, val]) =>
                <Filter filterString={val} track={track} index={Number(key)}/>
            )}
            </TableBody>
         </Table>
         <FormControl className={classes.addNewControl}>
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