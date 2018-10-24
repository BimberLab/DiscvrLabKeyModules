import React from 'react';

class ProjectDetails extends React.Component {

    constructor(props) {
        super(props);
        let selectedProject = this.props.store.getState().project.selectedProject || null;
        this.state = { 
            value: '',
            selectedProject: selectedProject
        };
        this.disconnect = this.props.store.subscribe(this.handleStoreUpdate);
    }

    componentWillUnmount = () => this.disconnect();
    
    handleStoreUpdate = () => {
        let selectedProject = this.props.store.getState().project.selectedProject || null;
        this.setState({ selectedProject: selectedProject });
    }

    render() {
        if (this.state.selectedProject != null) {
            return (<div className='container' style={{textAlign: 'left'}}>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Cost Account</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Charge ID</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.ChargeId.value || ''} /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>IACUC</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.Iacuc.value || ''} /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Primary Vet</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.veterinarian.value || ''}/></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Secondary Vet</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>VS Number</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.VsNumber.value || ''}/></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Start Date</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.StartDate.value || ''} /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>End Date</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly value={this.state.selectedProject.EndDate.value || ''}/></div>
                </div>
            </div>)
        } else {
            return <div>Please select a project to view it's details</div>
        }

    }
}

export default ProjectDetails;