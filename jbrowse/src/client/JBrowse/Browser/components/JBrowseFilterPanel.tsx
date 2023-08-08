import { observer } from 'mobx-react';
import { Box, Button } from '@mui/material';
import React from 'react';
import { getConf } from '@jbrowse/core/configuration';
import {
    deserializeFilters,
    isSerializedFilterStringValid,
    operators
} from '../plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil';

const JBrowseFilterPanel = observer(props => {
    const {session} = props
    const {view} = session
    if (!view) {
        return
    }

    const {tracks} = view
    if (!tracks) {
        return
    }

    const handleButtonClick = (widgetType: string, track: any, label: string) => {
        const trackId = getConf(track, ['trackId'])
        const widgetId = widgetType + '-' + trackId;
        const filterWidget = session.addWidget(
            widgetType,
            widgetId,
            { track: track.configuration }
        )
        session.showWidget(filterWidget)
    }

    const generateUserFriendlyLabel = (filter) => {
        if (!isSerializedFilterStringValid(filter)) {
            return null
        }

        const filterObjs = deserializeFilters([filter])
        if (!filterObjs.length) {
            return null
        }

        return(filterObjs[0].field + ' ' + operators[filterObjs[0].operator] + ' ' + filterObjs[0].value)
    }

    const filterDisplayItems = []
    for (const track of tracks) {
        // @ts-ignore
        const activeSamples = getConf(track, ['displays', '0', 'renderer', 'activeSamples'])
        const trackId = getConf(track, ['trackId'])
        if (activeSamples) {
            const sampleText = activeSamples.split(',').length > 5 ? '<too many to show>' : activeSamples
            const label = getConf(track, ['name']) || trackId

            filterDisplayItems.push(<Button color="primary" key={"sampleFilterButton"} style={{marginRight: 10, textTransform: 'initial'}} variant={'contained'} onClick={() => handleButtonClick('SampleFilterWidget', track, label)}>{label + ': ' + 'Showing sites with a variant in any of:'}<br />{sampleText}</Button>)
        }

        const infoFilters = getConf(track, ['displays', '0', 'renderer', 'infoFilters'])
        if (infoFilters?.length) {
            const filterText = infoFilters.map(filter => generateUserFriendlyLabel(filter)).join(', ')
            const label = getConf(track, ['name']) || getConf(track, ['trackId'])

            filterDisplayItems.push(<Button color="primary" key={"infoFilterButton"} style={{marginRight: 10, textTransform: 'initial'}} variant={'contained'} onClick={() => handleButtonClick('InfoFilterWidget', track, label)}>{label + ': ' + 'Showing sites where:'}<br />{filterText}</Button>)
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