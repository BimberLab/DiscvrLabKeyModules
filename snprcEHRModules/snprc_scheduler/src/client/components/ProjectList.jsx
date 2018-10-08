import React from 'react';
import ReactDataGrid from 'react-data-grid';
import ReactDOM from 'react-dom';
import FullCalendar from 'fullcalendar';
import $ from 'jquery'; 
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

class ProjectList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            debugUI: false,
            projects: [],
            projectCols: [
                { key: 'ProjectId', name: 'ID', width: 40 },
                { key: 'Description', name: 'Description', width: 330 }
            ],
            selectedProjects: []
        };
    }
    

    projectRowGetter = (index) => {
        return this.state.projects[index];
    }

    onProjectRowsSelected = (rows) => {
        //this.setState({ selectedProjects: this.state.selectedProjects.concat(rows.map(r => r.rowIdx)) });
        //console.log(rows.length + " rows selected");
        this.setState({ selectedProjects: rows.map(r => r.rowIdx) });
        console.log("Project " + rows[0].ProjectId + " selected.");
    }

    onProjectRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedProjects: this.state.selectedProjects.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    handleProjectSearchChange = (event) => {
        let value = event.target.value;
        let projects = [];
        this.setState({ projectSearchValue: value });        
        this.state.projectData.forEach((row) => {
            if (row.Description.value.toString().indexOf(this.state.projectSearchValue) > 0 ||
                row.ProjectId.value.toString().indexOf(this.state.projectSearchValue) > 0 ||
                row.RevisionNum.value.toString().indexOf(this.state.projectSearchValue) > 0 || value.length < 2) 
            {
                projects.push({
                                ProjectId: row.ProjectId.value.toString(),
                                Description: row.Description.value.toString() + " , Revision " + row.RevisionNum.value.toString(),
                                StartDate: row.StartDate.value.toString(),
                                EndDate: row.EndDate.value.toString()
                            });
            }
        });
        console.log(projects.length +  " matching projects.");
        this.setState({ projects: projects });
    }

    componentDidMount = () => {
        if (this.state.debugUI) console.log('ProjectList didMount()');
        this.fetchProjects();
    }  

    componentDidUpdate = (prevProps) => {
        if (this.state.debugUI) console.log('ProjectList componentDidUpdate()');
    }

    componentWillUnmount = () => {
        this.ignoreLastFetch = true;
        if (this.state.debugUI) console.log('ProjectList componentWillUnmount()');
    }

    fetchProjects = () => {
        // LABKEY query API call
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'snd',
            queryName: 'ProjectDetails',
            columns: 'ProjectId,RevisionNum,ChargeId,Description,StartDate,EndDate,ProjectType,VsNumber,Active,ObjectId,iacuc,veterinarian',
            filterArray: [], //[{blah: "blah"}],
            sort: 'ProjectId,RevisionNum',
            success: (results) => {
                if (this.ignoreLastFetch) return;
                var projects = [];
                if (results.rows.length > 0) {
                    console.log("Processing " + results.rows.length + " projects...");
                    results.rows.forEach((row) => {
                        projects.push({
                            ProjectId: row.ProjectId.value.toString(),
                            Description: row.Description.value.toString() + " , Revision " + row.RevisionNum.value.toString(),
                            StartDate: row.StartDate.value.toString(),
                            EndDate: row.EndDate.value.toString()
                        });
                    })                    
                }
                this.setState({ 
                    projects: projects,
                    projectData: results.rows 
                })
            },
            failure: (error) => console.log(error.exception)
        });
    }

    render = () => { return <div>
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
                rowKey="id2"
                columns={this.state.projectCols}
                rowGetter={this.projectRowGetter}
                rowsCount={this.state.projects.length}
                minHeight={300}
                rowSelection={{
                    showCheckbox: true,
                    enableShiftSelect: false,
                    onRowsSelected: this.onProjectRowsSelected,
                    onRowsDeselected: this.onProjectRowsDeselected,
                    selectBy: { indexes: this.state.selectedProjects }
                }} />                                    
        </div>
    </div>
    }

  }

  export default ProjectList;