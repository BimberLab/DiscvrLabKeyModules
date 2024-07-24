import React from 'react';
import ReactDOM from 'react-dom';

import View from './Browser';

const render = () => {
    ReactDOM.render(<View />, document.getElementById('app'));
};

render();