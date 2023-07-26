import { observer } from 'mobx-react';
import { Box, Button, ThemeProvider } from '@mui/material';
import React from 'react';
import HelpDialog from './HelpDialog';
import { styled } from '@mui/material/styles';
import { createJBrowseTheme } from '@jbrowse/core/ui';
import { readConfObject } from '@jbrowse/core/configuration';

const JBrowseFooter = observer(props => {
    const {viewState, bgColor} = props

    const [dialogOpen, setDialogOpen] = React.useState(false);
    const showHelpDialog = () => {
        setDialogOpen(true)
    }

    const SButton = styled(Button)(({ theme }) => ({
        marginRight: '5px'
    }))

    const openTrackSelector = function () {
        viewState.session.view.activateTrackSelector()
    }

    if (!viewState) {
        return (
            <></>
        )
    }

    // This is added to ensure on the first render the buttons use the right color.
    // NOTE: consider pushing this up one level into Browser.tsx
    // @ts-ignore
    const theme = createJBrowseTheme(readConfObject(viewState.config.configuration, 'theme'))

    return (
        <>
        <ThemeProvider theme={theme}>
            <Box padding={'5px'}>
                <SButton onClick={openTrackSelector} variant="contained" color="primary">Open Track Selector</SButton>
                <SButton onClick={showHelpDialog} variant="contained" color="primary">View Help</SButton>
            </Box>
            <HelpDialog isOpen={dialogOpen} setDialogOpen={setDialogOpen} bgColor={bgColor} />
        </ThemeProvider>
        </>
    )
})

export default JBrowseFooter