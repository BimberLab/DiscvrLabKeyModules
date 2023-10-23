import React, { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import Button from '@mui/material/Button';
import Tooltip from '@mui/material/Tooltip';
import HelpIcon from '@mui/icons-material/Help';
import CloseIcon from '@mui/icons-material/Close';

export default function VideoModal({ videoURL, hoverText = '' }) {
  const [open, setOpen] = useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const buttonContent = (
    <Button 
      onClick={handleOpen} 
      disableRipple
      style={{
        padding: 0,
        minWidth: 0,
        backgroundColor: 'transparent',
      }}
    >
      <HelpIcon />
    </Button>
  );

  return (
    <div>
      {hoverText ? (
        <Tooltip title={hoverText}>
          {buttonContent}
        </Tooltip>
      ) : (
        buttonContent
      )}
      <Dialog
        open={open}
        onClose={handleClose}
        PaperProps={{
          style: {
            backgroundColor: 'transparent',
            boxShadow: 'none',
            position: 'relative',
          },
        }}
      >
        <CloseIcon 
          style={{ 
            position: 'absolute',
            top: 10,
            right: 10,
            zIndex: 1,
            cursor: 'pointer',
            color: 'white',
            backgroundColor: 'rgba(0, 0, 0, 0.4)',
            borderRadius: '50%'
          }} 
          onClick={handleClose} 
        />
        <video
          width="100%"
          controls
        >
          <source src={videoURL} type="video/mp4" />
          Your browser does not support the video tag.
        </video>
      </Dialog>
    </div>
  );
}