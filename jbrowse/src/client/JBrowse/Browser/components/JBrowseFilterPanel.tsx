import { observer } from 'mobx-react';
import { Box, Button, Typography } from '@material-ui/core';
import React from 'react';
import { getConf } from '@jbrowse/core/configuration';
import { generateUserFriendlyLabel } from '../plugins/ExtendedVariantPlugin/FilterWidget/filterUtil.js';

const JBrowseFilterPanel = observer(props => {
    // TODO: the purpose of showTrackName is that for the browser (which is multi-track), track name matters.
    // For the variant table, which is inherently one track, that's irrelevant
    const {session, showTrackName = false} = props
    //const {session} = viewState
    const {view} = session
    if (!view) {
        return
    }

    const {tracks} = view
    if (!tracks) {
        return
    }

    const handleButtonClick = (filterType: string) => {
        console.log('I should open the filter UI!')

    }

    // TODO: this should probably be some kind of react/button class
    const filterDisplayItems = []
    for (const track of tracks) {
        const activeSamples = getConf(track, ['displays', '0', 'renderer', 'activeSamples'])
        if (activeSamples) {
            //TODO: test the number of active samples. If greater than X (maybe 5?) just say '<too many to show>' or something
            const sampleText = activeSamples.split(',').length > 5 ? '<too many to show>' : activeSamples
            const label = getConf(track, ['name']) || getConf(track, ['trackId'])
            //TODO: how to do line break inside a button??
            filterDisplayItems.push(<Button color="primary" style={{marginRight: 10}} variant={'contained'} onClick={() => handleButtonClick('samples')}>{(showTrackName ? label + ': ' : '') + 'Showing sites with a variant in any of:<br>' + sampleText}</Button>)
        }

        const infoFilters = getConf(track, ['displays', '0', 'renderer', 'infoFilters'])
        if (infoFilters?.length) {
            const filterText = infoFilters.map(filter => generateUserFriendlyLabel(filter)).join(', ')
            const label = getConf(track, ['name']) || getConf(track, ['trackId'])
            //TODO: how to do line break inside a button??
            filterDisplayItems.push(<Button color="primary" style={{marginRight: 10}} variant={'contained'} onClick={() => handleButtonClick('info')}>{(showTrackName ? label + ': ' : '') + 'Showing sites where:<br>' + filterText}</Button>)
        }
    }

    return (
        <>
        <Box padding={'5px'}>
            {filterDisplayItems}
        </Box>
        </>
    )
})

export default JBrowseFilterPanel