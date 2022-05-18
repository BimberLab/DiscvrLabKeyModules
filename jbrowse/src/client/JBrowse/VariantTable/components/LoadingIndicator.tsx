import { Backdrop, CircularProgress } from '@material-ui/core';
import React from 'react';
import { makeStyles } from '@material-ui/core/styles';

export default function LoadingIndicator(props: {isOpen: boolean}) {
    const classes = makeStyles((theme) => ({
        backdrop: {
            zIndex: theme.zIndex.drawer + 1,
            color: '#fff',
        },
    }))();

    return(<Backdrop className={classes.backdrop} open={props.isOpen}>
            <CircularProgress color="inherit" />
        </Backdrop>
    )
}