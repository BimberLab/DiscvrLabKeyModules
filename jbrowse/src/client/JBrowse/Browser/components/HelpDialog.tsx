import { observer } from 'mobx-react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import React, { useState } from 'react';
import { getServerContext } from '@labkey/api';

const HelpDialog = observer(props => {
    const setDialogOpen = props.setDialogOpen
    const { isOpen, bgColor }  = props

    const handleClose = () => {
        setActiveImage(0)
        setDialogOpen(false)
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

    const images = ['JB-1.jpg', 'JB-2.jpg', 'JB-3.jpg', 'JB-4.jpg', 'JB-5.jpg']
    const [activeImage, setActiveImage] = useState(0);

    return (
        <Dialog
            open={isOpen}
            onClose={handleClose}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
            maxWidth={false}
        >
            <DialogTitle style={{background: bgColor, color: 'white'}} id="alert-dialog-title">Genome Browser Tutorial</DialogTitle>
            <DialogContent>
                <img alt="Genome Browser Help" src={getServerContext().contextPath + '/jbrowse/img/' + images[activeImage]} />
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