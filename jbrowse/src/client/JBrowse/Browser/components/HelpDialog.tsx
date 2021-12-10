import { observer } from 'mobx-react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@material-ui/core';
import React, { useState } from 'react';
import { buildURL } from '@labkey/components';


const HelpDialog = observer(props => {
    const [open, setOpen] = useState(props.isOpen)

    const handleClose = () => {
        setOpen(false)
    }

    const doPrevious = () => {
        if (activeImage > 0) {
            setActiveImage(activeImage - 1)
        }
    }

    const doNext = () => {
        if (activeImage < images.length -1) {
            setActiveImage(activeImage + 1)
        }
    }

    const images = [0,1,2]
    const [activeImage, setActiveImage] = useState(0);

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            <DialogTitle id="alert-dialog-title">Genome Browser Tutorial</DialogTitle>
            <DialogContent>
                <img alt="Genome Browser Help" src={buildURL('jbrowse', 'foo', {idx: activeImage})} />
            </DialogContent>
            <DialogActions>
                <Box mr="5px">
                    <Button onClick={doPrevious} disabled={activeImage === 0}>Previous</Button>
                    <Button onClick={doNext} disabled={activeImage === images.length - 1}>Next</Button>
                    <Button onClick={handleClose}>Close</Button>
                </Box>
            </DialogActions>
        </Dialog>

    )
})


export default HelpDialog