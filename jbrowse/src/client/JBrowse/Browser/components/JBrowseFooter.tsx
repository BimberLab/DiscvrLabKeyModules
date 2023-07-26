import { observer } from 'mobx-react';
import { Box, Button } from '@mui/material';
import React from 'react';
import HelpDialog from './HelpDialog';
import { styled } from '@mui/material/styles';

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

    return (
        <>
            <Box padding={'5px'}>
                <SButton onClick={openTrackSelector} variant="contained" color="primary">Open Track Selector</SButton>
                <SButton onClick={showHelpDialog} variant="contained" color="primary">View Help</SButton>
            </Box>
            <HelpDialog isOpen={dialogOpen} setDialogOpen={setDialogOpen} bgColor={bgColor} />
        </>
    )
})

export default JBrowseFooter