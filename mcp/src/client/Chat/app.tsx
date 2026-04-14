import React from 'react';
import { createRoot } from 'react-dom/client';
import { ChatPanel } from './ChatPanel';

// Size the #app host so the chat fills the LabKey content area without a
// fixed height guess. We measure the distance from the host's top to the
// viewport bottom and re-apply on window resize.
function sizeHost(container: HTMLElement) {
    const apply = () => {
        const top = container.getBoundingClientRect().top;
        container.style.height = `calc(100vh - ${Math.max(0, Math.round(top))}px - 16px)`;
    };
    apply();
    window.addEventListener('resize', apply);
}

window.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('app');
    if (container) {
        container.style.display = 'flex';
        container.style.flexDirection = 'column';
        sizeHost(container);
        createRoot(container).render(<ChatPanel />);
    }
});
