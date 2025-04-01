import React from 'react';

import VariantTable from './VariantTable';
import { createRoot } from 'react-dom/client';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<VariantTable />)
}, true);
