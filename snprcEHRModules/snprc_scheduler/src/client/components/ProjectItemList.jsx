/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 30 2018      
    ==================================================================================
*/
import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

class ProjectItemList extends React.Component {
    
    constructor(props) {
        super(props);
        this.state = { selectedProject: this.props.store.getState().project.selectedProject || null };
        // wire into redux store updates
        this.disconnect = this.props.store.subscribe(this.handleStoreUpdate); 
    }

    componentWillUnmount = () => this.disconnect();

    handleStoreUpdate = () => {
        let selectedProject = this.props.store.getState().project.selectedProject || null;
        this.setState({ selectedProject: selectedProject });
        console.log('found ' + selectedProject.ProjectItems.length + ' project items');
    }

    render = () => {
        
        
        let tJSX = (
        <div>

        </div>);
        return (
            <div>
                <table>

                </table>
            </div>
        );
    }
}

export default ProjectItemList;