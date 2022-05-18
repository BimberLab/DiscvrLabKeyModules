import React from 'react';
import ReactDOM from 'react-dom';

import VariantTable from './VariantTable';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<VariantTable/>, document.getElementById('app'))
});
