import { Backdrop, CircularProgress } from '@mui/material';
import React from 'react';
import { styled } from '@mui/material/styles';

const SBackdrop = styled(Backdrop)(({ theme }) => ({
    zIndex: theme.zIndex.drawer + 1,
    color: '#fff',
}))

export default function LoadingIndicator(props: {isOpen: boolean}) {
    return(<SBackdrop open={props.isOpen}>
            <CircularProgress color="inherit" />
        </SBackdrop>
    )
}