import {colorSchemes} from "./colorSchemes"
import {style as styles} from "./style";
import {getSession} from '@jbrowse/core/util'
import {readConfObject} from '@jbrowse/core/configuration'

import {Button, FormControl, InputLabel, MenuItem, Select} from '@material-ui/core'
import {useState} from 'react'
import SchemeTable from './SchemeComponent'

export default jbrowse => {

    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const React = jbrowse.jbrequire('react')
    function ColorSchemePicker(props){
        const classes = styles()
        const { model } = props
        const { track } = model

        const displays = readConfObject(track, ['displays']) || []
        let paletteName = displays[0].renderer.palette
        paletteName = paletteName || 'IMPACT'

        const [palette, setPalette] = useState(paletteName)

        const handleSchemeChange = (event) => {
            setPalette(event.target.value)
        }

        const onApply = (event) => {
            // NOTE: preProcessSnapshot in the renderer schema should set color1
            track.displays[0].renderer.palette.set(palette)
            getSession(model).hideWidget(model)
        }

        const menuItems = (
                Object.entries(colorSchemes).map(([key, val]) =>
                        <MenuItem key={key} value={key}>
                            {val.title || key}
                        </MenuItem>
                ))

        return(
                <>
                    <FormControl className={classes.schemeControl}>
                        <InputLabel id="category-select-label">Color Using</InputLabel>
                        <Select
                                labelId="category-select-label"
                                id="category-select"
                                value={palette}
                                onChange={handleSchemeChange}
                        >
                            {menuItems}
                        </Select>
                    </FormControl>
                    <SchemeTable colorScheme={colorSchemes[palette]}/>
                    <p/>
                    <Button className={classes.applyButton} onClick={onApply} variant="contained" color="primary">
                        Apply
                    </Button>
                </>
        )
    }
    return observer(ColorSchemePicker)
}