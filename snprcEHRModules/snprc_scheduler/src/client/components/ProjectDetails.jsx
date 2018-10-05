import React from 'react';

class ProjectDetails extends React.Component {

    constructor(props) {
        super(props);
        this.state = { value: '' };
    }

    render() {
        let rowStyle = {
            paddingBottom: '10px'
        }

        let textLeft = {
            textAlign: 'left'
        }

        return <div className='container' style={textLeft}>
            <div className='row' style={rowStyle}>
                <div className='col-sm-2'><label>Animal Account</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row' style={rowStyle}>
                <div className='col-sm-2'><label>Charge ID</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row' style={rowStyle}>
                <div className='col-sm-2'><label>IACUC</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row' style={rowStyle}>
                <div className='col-sm-2'><label>Vetrinarian</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
        </div>
    }
}

export default ProjectDetails;