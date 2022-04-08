import React, { useState } from 'react'
import { Menu, Button } from "@material-ui/core"

export default function MenuButton(props) {
  return (
    <>
    <Button
          style={{backgroundColor: "#116596", marginTop:"8px"}}
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