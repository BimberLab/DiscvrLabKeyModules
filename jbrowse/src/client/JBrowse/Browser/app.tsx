import View from './Browser';
import { createRoot } from 'react-dom/client';
import React from 'react';

window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<View />)
});
