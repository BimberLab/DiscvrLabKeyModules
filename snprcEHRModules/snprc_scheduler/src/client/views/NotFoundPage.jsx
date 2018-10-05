import React from 'react';

class NotFoundPage extends React.Component {

    constructor(props) {
        super(props);
        console.log(props);
        this.state = { properties: props };
    }

    render() { return <div>404 - not found</div> }
}

export default NotFoundPage;