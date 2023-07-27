import { colorSchemes } from './colorSchemes';
import { getSession } from '@jbrowse/core/util';
import { readConfObject } from '@jbrowse/core/configuration';
import { generateSchemeJexl } from './colorUtil';

import { Button, FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import React, { useState } from 'react';
import SchemeTable from './SchemeComponent';
import { SessionWithWidgets } from '@jbrowse/core/util/types';
import { observer } from 'mobx-react';
import { styled } from '@mui/material/styles';

export default jbrowse => {
    const FormControlS = styled(FormControl)(({ theme }) => ({
        margin: theme.spacing(2),
        padding: theme.spacing(1, 3, 1, 1),
        minWidth: 100,
    }))

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
                    <div style={{padding: '5px' }}>
                    <FormControlS>
                        <InputLabel id="category-select-label">Color Using</InputLabel>
                        <Select
                                labelId="category-select-label"
                                id="category-select"
                                value={palette}
                                onChange={handleSchemeChange}
                        >
                            {menuItems}
                        </Select>
                    </FormControlS>
                    <SchemeTable colorScheme={colorSchemes[palette]}/>
                    <p/>
                    <Button onClick={onApply} variant="contained" color="primary">
                        Apply
                    </Button>
                    </div>
                </>
        )
    }
    return observer(ColorSchemePicker)
}