import { style as styles } from './style';
import { getSession } from '@jbrowse/core/util';
import { readConfObject } from '@jbrowse/core/configuration';

import { Box, Button, FormControl, TextField } from '@material-ui/core';
import React, { useState } from 'react';
import { SessionWithWidgets } from '@jbrowse/core/util/types';
import { observer } from 'mobx-react';

export default jbrowse => {

    function parseSampleCSV(val) {
        if (!val) {
            return '';
        }

        val = val.trim().replace(/\s+/g, ",")
        val = val.replace(/,+/g, ",")
        val = val.replace(/^,+/g, "")
        val = val.replace(/,+$/g, "")

        return val || ''
    }

    function replaceCommaWithNewline(val) {
        if (val) {
            val = val.split(',').join('\n')
        }

        return val
    }

    function SampleIDForm(props){
        const classes = styles()
        const { model } = props
        const { track } = model

        const displays = readConfObject(track, ['displays']) || []
        const [sampleFilter, setSampleFilter] = useState(replaceCommaWithNewline(displays[0].renderer.activeSamples) || '')

        const handleSampleFilterChange = (event) => {
            setSampleFilter(event.target.value)
        }

        const handleSampleFilterSubmit = (event) => {
            track.displays[0].renderer.activeSamples.set(parseSampleCSV(sampleFilter))
            const m = getSession(model) as SessionWithWidgets
            m.hideWidget(model)
        }

        const clearFilters = (event) => {
            track.displays[0].renderer.activeSamples.set('')
            const m = getSession(model) as SessionWithWidgets
            m.hideWidget(model)
        }

        return(
                <>
                    <FormControl className={classes.formControl} style={{maxWidth: 400}}>
                        Use the box below to enter a list of samples, either one per line or separated by commas.
                        Only sites where at least one of these samples is a variant will be shown.
                        <br/>
                        <TextField
                                id="outlined-multiline-static"
                                label="Enter samples..."
                                multiline
                                minRows={4}
                                defaultValue={sampleFilter}
                                variant="outlined"
                                onChange={handleSampleFilterChange}
                        />
                        <p/>
                        <Box padding={'5px'} mr="5px">
                        <Button className={classes.button} onClick={handleSampleFilterSubmit} variant="contained" color="primary">
                            Apply
                        </Button>
                        <Button className={classes.button} onClick={clearFilters} variant="contained" color="primary">
                            Clear Filters
                        </Button>
                        </Box>
                    </FormControl>
                </>
        )
    }

    return observer(SampleIDForm)
}