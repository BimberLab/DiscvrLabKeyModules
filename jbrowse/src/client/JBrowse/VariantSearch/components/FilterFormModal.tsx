import { Modal, Paper, Typography } from '@mui/material';
import React from 'react';
import FilterForm, { FilterFormProps } from './FilterForm';

export const FilterFormModal = ({ open, handleClose, filterProps }: {open: boolean, handleClose: any, filterProps: FilterFormProps }) => {
  const body = (
    <Paper style={{position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', padding: '1em'}}>
      <Typography variant="h6">Filters</Typography>
      <FilterForm {...filterProps} handleClose={handleClose} />
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
