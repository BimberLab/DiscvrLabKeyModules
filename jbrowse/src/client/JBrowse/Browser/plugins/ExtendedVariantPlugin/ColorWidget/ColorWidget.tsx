import { colorSchemes } from './colorSchemes';
import { classes, Root as RootStyle } from './style';
import { getSession } from '@jbrowse/core/util';
import { readConfObject } from '@jbrowse/core/configuration';
import { generateSchemeJexl } from './colorUtil';

import { Button, FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import React, { useState } from 'react';
import SchemeTable from './SchemeComponent';
import { SessionWithWidgets } from '@jbrowse/core/util/types';
import { observer } from 'mobx-react';

export default jbrowse => {
    function ColorSchemePicker(props){
        const { model } = props
        const { track } = model

        // @ts-ignore
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
            track.displays[0].renderer.color1.set(generateSchemeJexl(palette))

            const m = getSession(model) as SessionWithWidgets
            m.hideWidget(model)
        }

        const menuItems = (
                Object.entries(colorSchemes).map(([key, val]) =>
                        <MenuItem key={key} value={key}>
                            {val.title || key}
                        </MenuItem>
                ))

        return(
                <>
                    <RootStyle className={classes.root}>
                        <div style={{padding: '5px' }}>
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
                        <Button onClick={onApply} variant="contained" color="primary">
                            Apply
                        </Button>
                        </div>
                    </RootStyle>
                </>
        )
    }
    return observer(ColorSchemePicker)
}