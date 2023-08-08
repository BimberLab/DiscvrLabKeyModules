import React from 'react';
import { ErrorInfo } from 'react';
import { Box, Typography, Paper } from '@mui/material';

interface ErrorPageProps {
    error: Error | undefined;
    errorInfo: ErrorInfo | undefined;
    stackTrace: string | undefined;
    devMode: boolean | undefined;
}

const ErrorPage: React.FC<ErrorPageProps> = ({ error, errorInfo, stackTrace, devMode }) => {
    if (!devMode) {
        return (
            <Box p={3}>
                <Typography variant="h3" color="error">
                    Error
                </Typography>
                <Typography variant="body1">
                    An unexpected error caused the application to stop working. Please refresh the page. If the problem persists contact support.
                </Typography>
            </Box>
        );
    }

    return (
        <Box p={3}>
            <Typography variant="h3" color="error">
                Error
            </Typography>
            <Paper variant="outlined" style={{ marginTop: 16, padding: 16 }}>
                <Typography variant="h5">Error Message</Typography>
                <Typography variant="body1">
                    {error ? error.message : 'No error message available'}
                </Typography>
                <Typography variant="h5" style={{ marginTop: 16 }}>Error Info</Typography>
                <Typography variant="body1">
                    {errorInfo ? errorInfo.componentStack : 'No error info available'}
                </Typography>
                <Typography variant="h5" style={{ marginTop: 16 }}>Stack Trace</Typography>
                {stackTrace ? (
                    <pre style={{ overflowX: 'auto' }}>{stackTrace}</pre>
                ) : (
                    <Typography variant="body1">No stack trace available</Typography>
                )}
            </Paper>
        </Box>
    );
};

export default ErrorPage;
