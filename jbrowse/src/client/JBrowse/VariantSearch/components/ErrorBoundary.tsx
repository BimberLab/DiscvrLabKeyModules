import React, { ErrorInfo } from 'react';
import { getServerContext } from '@labkey/api';

import ErrorPage from './ErrorPage';

interface ErrorBoundaryState {
    error: Error | undefined;
    errorInfo: ErrorInfo | undefined;
    stackTrace: string | undefined;
    devMode: boolean | undefined,
}

export class ErrorBoundary extends React.PureComponent<{}, ErrorBoundaryState> {
    constructor(props) {
        super(props);

        const { devMode } = getServerContext(); 

        this.state = {
            error: undefined,
            errorInfo: undefined,
            stackTrace: undefined,
            devMode: devMode,
        };
    }

    componentDidCatch(error, errorInfo) {
        const { Mothership } = getServerContext();

        this.setState(() => ({
            error,
            errorInfo,
            stackTrace: error?.stack ? error.stack : undefined,
        }));

        if (Mothership) {
            // process stack trace against available source maps
            Mothership.processStackTrace(error, stackTrace => {
                this.setState(state => ({
                    stackTrace: stackTrace || state.stackTrace,
                }));
            });

            // log error as this error was caught by React
            Mothership.logError(error);
        }
    }

    render() {
        if (this.state.error !== undefined) {
            const { error, errorInfo, stackTrace, devMode } = this.state;

            return <ErrorPage error={error} errorInfo={errorInfo} stackTrace={stackTrace} devMode={devMode} />;
        }

        return this.props.children;
    }
}