import React from 'react';

import VariantSearch from './VariantSearch';
import { createRoot } from 'react-dom/client';

window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<VariantSearch />)
});
