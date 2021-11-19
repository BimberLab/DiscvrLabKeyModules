import {parseSampleCSV} from "./sampleUtil"
import {
  FormControl,
  Select,
  MenuItem,
  InputLabel,
  TextField,
  Button
} from '@material-ui/core'
import { useState } from 'react'
export default jbrowse => {

   const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
   const React = jbrowse.jbrequire('react')
   function SampleIDForm(props){
      //const classes = styles()
      const { model } = props
      let track = model.track

      const [sampleFilter, setSampleFilter] = useState(track.adapter.sampleFilters.value)

      const handleSampleFilterChange = (event) => {
         setSampleFilter(event.target.value)
      }

      const handleSampleFilterSubmit = (event) => {
         track.adapter.sampleFilters.set(sampleFilter)
         console.log("Submitted. Raw value =" + sampleFilter + "\nParsed value = " + parseSampleCSV(sampleFilter))
      }



      return(
         <>
            <FormControl>
              <TextField
                id="outlined-multiline-static"
                label="Enter CSV..."
                multiline
                rows={4}
                defaultValue={sampleFilter}
                variant="outlined"
                onChange={handleSampleFilterChange}
              />
              <Button onClick={handleSampleFilterSubmit} variant="contained" color="primary">
                Apply
              </Button>
            </FormControl>
         </>
      )
   }
   return observer(SampleIDForm)
}