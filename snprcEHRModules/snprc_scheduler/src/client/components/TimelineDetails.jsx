import React from 'react';

const FORCE_RENDER = true;

class TimelineDetails extends React.Component {

    constructor(props) {
        super(props);
        this.state = { 
            value: '',
            selectedTimeline: (this.props.store.getState().project.selectedTimeline || null)
        };
        this.disconnect = this.props.store.subscribe(this.handleStoreUpdate);
    }

    componentWillUnmount = () => this.disconnect();

    handleStoreUpdate = () => {
        let selectedTimeline = this.props.store.getState().project.selectedTimeline || null;
        this.setState({ selectedTimeline: selectedTimeline });
    }

    render() {
        if (this.state.selectedTimeline != null || FORCE_RENDER) {
            return (
            <div className='container' style={{textAlign: 'left'}}>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Project</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
                    <div className='col-sm-2'><label>Study Notes</label></div>
                    <div className='col-sm-4'><input type='textarea' className='input-wide study-notes' readOnly /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Research Coordinator</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
                </div>
                <div className='row input-row'>
                    <div className='col-sm-2'><label>Lead Technitian</label></div>
                    <div className='col-sm-4'><input type='text' className='input-wide' readOnly /></div>
                </div>

                <div className='row input-row'>
                    <div className='col-sm-2'><label>Date Created</label></div>
                    <div className='col-sm-2'><input type='text' className='input-wide' readOnly /></div>
                    <div className='col-sm-2'><label>Date Modified</label></div>
                    <div className='col-sm-2'><input type='text' className='input-wide' readOnly /></div>
                </div>

            </div>)
        } else {
            return <div>Please select a timeline to view it's details</div>
        }

    }
}

export default TimelineDetails;