import View from './Browser';
import { createRoot } from 'react-dom/client';
import React from 'react';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<View />)
}, true);
