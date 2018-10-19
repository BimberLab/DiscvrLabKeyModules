/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 1 2018      
    ==================================================================================
*/
import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import {connect} from "react-redux";
import { selectProject, filterProjects } from '../actions/dataActions';

const verboseOutput = false;

class EmptyRowsView extends React.Component { render() {return (<div> No projects to show</div>);} }

class ProjectList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            projectCols: [
                { key: 'ProjectId', name: 'ID', width: 40 },
                { key: 'Description', name: 'Description', width: 330 }
            ],
            selectedProjects: [],
        };
        // handle store changes
        this.props.store.subscribe(this.handleStoreUpdate); 
    }
    
    projectRowGetter = (index) => this.state.projects[index];
    
    onProjectRowsSelected = (rows) => {;
        let selectedProject = rows[0].row;
        this.setState({ 
            selectedProjects: rows.map(r => r.rowIdx),
            selectedProject: selectedProject
        });
        if (verboseOutput) console.log("Project [" + selectedProject.ProjectId + "] selected.");
        this.props.store.dispatch(selectProject(selectedProject.ProjectId));
    }

    onProjectRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedProjects: this.state.selectedProjects.filter(i => rowIndexes.indexOf(i) === -1) });       
    }
    
    handleProjectSearchChange = (event) => this.props.store.dispatch(filterProjects(event.target.value));

    handleStoreUpdate = () => {
        if (verboseOutput) console.log('handeling store update...');
        let projects = this.props.store.getState().project.projects || [];
        let projectCount = projects.length;
        var newProjects = [];
        projects.forEach((p) => {
            let data = {
                ProjectId: p.ProjectId.value,
                Description: p.Description.value.toString(),
                ChargeId: p.ChargeId.value,
                StartDate: p.StartDate.value,
                EndDate: p.EndDate.value,
                Iacuc: p.Iacuc.value,
                RevisionNum: p.RevisionNum.value,
                vet1: p.veterinarian.value,
                vet2: null
            }
            newProjects.push(data);
        })
        this.setState({
            projects: newProjects,
            projectCount: projectCount
        });    
    }

    componentDidMount = () => { }  

    componentDidUpdate = (prevProps) => { };

    componentWillUnmount = () => { };

    render = () => { 
        return (<div>
            <div className="input-group bottom-padding-8">
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
            <input 
                id="projectSearch" 
                type="text" 
                onChange={this.handleProjectSearchChange}
                className="form-control search-input" 
                name="projectSearch" 
                placeholder="Search" />
            </div>
            <div className="bottom-padding-8">
                <ReactDataGrid
                    rowKey="ProjectId"
                    columns={this.state.projectCols}
                    rowGetter={this.projectRowGetter}
                    rowsCount={this.state.projectCount}
                    minHeight={284}
                    rowSelection={{
                        showCheckbox: true,
                        enableShiftSelect: false,
                        onRowsSelected: this.onProjectRowsSelected,
                        onRowsDeselected: this.onProjectRowsDeselected,
                        selectBy: { indexes: this.state.selectedProjects}
                    }}
                    emptyRowsView={EmptyRowsView}  
                />               
            </div>
        </div>)             
    };

}

export default connect()(ProjectList);