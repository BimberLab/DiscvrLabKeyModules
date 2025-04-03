import React from 'react';

import VariantTable from './VariantTable';
import { createRoot } from 'react-dom/client';

window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<VariantTable />)
}, true);
