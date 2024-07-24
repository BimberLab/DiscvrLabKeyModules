import React from 'react';
import ReactDOM from 'react-dom';

import VariantTable from './VariantTable';

const render = () => {
    ReactDOM.render(<VariantTable />, document.getElementById('app'));
};

render();