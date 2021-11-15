import {colorSchemes} from "./colorSchemes"
import {generateSchemeJexl} from "./colorUtil"
import {style as styles} from "./style";

import {
  FormControl,
  Select,
  MenuItem,
  InputLabel
} from '@material-ui/core'
import { useState } from 'react'
import SchemeTable from './SchemeComponent'
export default jbrowse => {

   const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
   const React = jbrowse.jbrequire('react')
   function ColorSchemePicker(props){
      const classes = styles()
      const { model } = props
      let track = model.track
      const [colorTable, setColorTable] = useState(<SchemeTable colorScheme={colorSchemes[track.displays[0].renderer.palette.value]}/>)

      const [scheme, setScheme] = useState(track.displays[0].renderer.palette.value)
      const handleSchemeChange = (event) => {
         setScheme(event.target.value)
         let activeColorScheme = colorSchemes[event.target.value]
         track.displays[0].renderer.colorJexl.set(generateSchemeJexl(activeColorScheme))
         track.displays[0].renderer.palette.set(event.target.value)
         setColorTable(<SchemeTable colorScheme={activeColorScheme}/>)
      }

      const menuItems = (
       Object.entries(colorSchemes).map(([key, val]) =>
           <MenuItem value={key}>
            {key}
           </MenuItem>
       ))


      return(
         <>
            <FormControl className={classes.schemeControl}>
               <InputLabel id="category-select-label">Color using</InputLabel>
                <Select
                  labelId="category-select-label"
                  id="category-select"
                  value={scheme}
                  onChange={handleSchemeChange}
                >
                    {menuItems}
                </Select>
            </FormControl>
            {colorTable}
         </>
      )
   }
   return observer(ColorSchemePicker)
}