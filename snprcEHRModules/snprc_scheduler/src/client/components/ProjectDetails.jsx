import React from 'react';

class ProjectDetails extends React.Component {

    constructor(props) {
        super(props);
        this.state = { 
            value: '',
            selectedProject: {
                ProjectId: 0,
                StartDate: '1/1/2019',
                EndDate: '1/1/2019'
            } 
        };
    }

    render() {

        let textLeft = {
            textAlign: 'left'
        }

        return <div className='container' style={textLeft}>
            <div className='row input-row'>
                <div className='col-sm-2'><label>Animal Account</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>Charge ID</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.ProjectId} /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>IACUC</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>Vetrinarian</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>VS Number</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>Start Date</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.StartDate} /></div>
            </div>
            <div className='row input-row'>
                <div className='col-sm-2'><label>End Date</label></div>
                <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.EndDate}/></div>
            </div>
        </div>
    }
}

export default ProjectDetails;