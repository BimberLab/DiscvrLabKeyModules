import React from 'react';
import { Button, Menu } from '@mui/material';

export default function MenuButton(props) {
  return (
    <>
    <Button
          style={{ marginTop:"8px"}}
          aria-owns={props.anchor ? props.id : undefined}
          aria-haspopup="true"
          onClick={props.handleClick}
          color="primary" variant="contained"
        >
        {props.text}
      </Button>
      <Menu
        id={props.id}
        anchorEl={props.anchor}
        open={Boolean(props.anchor)}
        onClose={props.handleClose}
      >
        {props.children}
      </Menu>
    </>
  )
}