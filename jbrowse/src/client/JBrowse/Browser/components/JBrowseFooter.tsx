import { observer } from 'mobx-react';
import { Box, Button } from '@material-ui/core';
import React from 'react';
import HelpDialog from './HelpDialog';
import { makeStyles } from '@material-ui/core/styles';


const useStyles = makeStyles({
    button: {
        marginRight: '5px'
    }
})

const JBrowseFooter = observer(props => {
    const {viewState, bgColor} = props
    const styles = useStyles()

    const [dialogOpen, setDialogOpen] = React.useState(false);
    const showHelpDialog = () => {
        setDialogOpen(true)
    }

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
            <Button className={styles.button} onClick={openTrackSelector} variant="contained" color="primary">Open Track Selector</Button>
            <Button className={styles.button} onClick={showHelpDialog} variant="contained" color="primary">View Help</Button>
        </Box>
        <HelpDialog isOpen={dialogOpen} setDialogOpen={setDialogOpen} bgColor={bgColor} />
        </>
    )
})

export default JBrowseFooter