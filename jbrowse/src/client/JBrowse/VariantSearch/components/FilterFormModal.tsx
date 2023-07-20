import { Modal, Paper, Typography } from "@material-ui/core";
import React from "react";
import FilterForm from "./FilterForm";

export const FilterFormModal = ({ open, handleClose, ...props }) => {
  const body = (
    <Paper style={{position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', padding: '1em'}}>
      <Typography variant="h6">Filters</Typography>
      <FilterForm {...props} handleClose={handleClose} />
    </Paper>
  );

  return (
    <Modal
      open={open}
      onClose={handleClose}
      aria-labelledby="simple-modal-title"
      aria-describedby="simple-modal-description"
    >
      {body}
    </Modal>
  );
};
