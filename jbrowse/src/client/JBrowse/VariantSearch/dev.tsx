import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';

import VariantTable from './VariantTable';

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <VariantTable />
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();