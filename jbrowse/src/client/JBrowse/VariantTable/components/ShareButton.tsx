import React from 'react';
import { Button } from '@mui/material';
import LinkIcon from '@mui/icons-material/Link';

export const ShareButton = () => {
  return (
      <Button
            startIcon={<LinkIcon />}
            size="small"
            color="primary"
            onClick={() => {
                navigator.clipboard.writeText(window.location.href)
                .then(() => {
                    alert('URL copied to clipboard.');
                })
                .catch(err => {
                    console.error('Failed to copy the URL: ', err);
                    alert('Failed to copy the URL.');
                });
            }}
        >
        Share
      </Button>
  );
};
