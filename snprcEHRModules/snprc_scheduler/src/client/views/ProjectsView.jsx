import React from 'react';
import ReactDataGrid from 'react-data-grid';
import ReactDataGridPlugins from 'react-data-grid-addons';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import { Accordion, AccordionItem, AccordionItemTitle, AccordionItemBody } from 'react-accessible-accordion';
import ProjectDetails from '../components/ProjectDetails';
import '../styles/Accordion.style.css'

class ProjectsView extends React.Component {
        constructor(props) {
        super(props);
        this.state = { 
            animals: [], 
            animalCols: [],
            selectedAnimals: [],
            projects: [],
            projectCols: [
                { key: 'ProjectId', name: 'ID', width: 40 },
                { key: 'Description', name: 'Description', width: 330 }
            ],
            selectedProjects: []
        };
    }

    // project grid methods & handlers
    projectRowGetter = (index) => {
        return this.state.projects[index];
    }

    onProjectRowsSelected = (rows) => {
        this.setState({ selectedProjects: this.state.selectedProjects.concat(rows.map(r => r.rowIdx)) });
    }

    onProjectRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedProjects: this.state.selectedProjects.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    handleProjectSearchChange = (event) => {
        let value = event.target.value;
        this.setState({
            projectSearchValue: value
        });
        if (value.length > 2) {
            console.log('searching projects for "' + value + '"');
        } else {
            console.log('project search criteria too short.');
        }
    }

    // animal grid methods & handlers
    animalRowGetter = (index) => {
        return this.state.animals[index];
    }

    onAnimalRowsSelected = (rows) => {
        this.setState({ selectedAnimals: this.state.selectedAnimals.concat(rows.map(r => r.rowIdx)) });
    }

    onAnimalRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedAnimals: this.state.selectedAnimals.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    handleAnimalSearchChange = (event) => {
        let value = event.target.value;
        this.setState({
            animalSearchValue: value
        });
        if (value.length > 1) {
            console.log('searching animals for "' + value + '"');
        } else {
            console.log('animal search criteria too short.');
        }
    }

    fetchProjects = () => {
        // LABKEY query API call
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'snd',
            queryName: 'Projects',
            columns: 'ProjectId,RevisionNum,ReferenceId,StartDate,EndDate,Description,HasEvent',
            filterArray: null,
            sort: 'ProjectId,RevisionNum',
            success: (results) => {
                if (this.ignoreLastFetch) return;
                console.log(results.rows[0]);

                var projects = [];
                results.rows.forEach((row) => {
                    projects.push({
                        ProjectId: row.ProjectId.value.toString(),
                        Description: row.Description.value.toString(),
                        StartDate: row.StartDate.value.toString(),
                        EndDate: row.EndDate.value.toString()
                    });
                })
                //console.log(projects.length + ' projects received.');
                console.log(projects[0]);
                this.setState({ 
                    projects: projects
                })
            },
            failure: (error) => {
                alert(errorInfo.exception);
            }
        });
    }

    handleTimelineSearchChange = (event) => {
        let value = event.target.value;
        this.setState({
            animalSearchValue: value
        });
        if (value.length > 1) {
            console.log('searching animals for "' + value + '"');
        } else {
            console.log('animal search criteria too short.');
        }
    }

    // component methods & handlers
    componentDidMount = () => {
        console.log('ProjectsView didMount()');
        this.fetchProjects();
    }  

    componentDidUpdate = (prevProps) => { 
        console.log('componentDidUpdate()');
    }

    componentWillUnmount = () => {
        console.log('componentWillUnmount()');        
        this.ignoreLastFetch = true;
    }

    fetchAnimals = () => {
        return;
        // LABKEY query API call
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'snd',
            queryName: 'Projects',
            columns: 'ProjectId,RevisionNum,ReferenceId,StartDate,EndDate,Description,HasEvent',
            filterArray: null,
            sort: 'ProjectId,RevisionNum',
            success: (results) => {
                if (this.ignoreLastFetch) return;
                //console.log(results.rows.length + ' projects received.');
                //this.setState({ rows: results.rows })
            },
            failure: (error) => {
                alert(errorInfo.exception);
            }
        });
    }

    render() {
        return <div>
            <div className='row spacer-row'></div>
            <div className='row'>
                <div className='col-sm-4'>
                    <Accordion className="accordion__style__primary">

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Projects</label></AccordionItemTitle>
                            <AccordionItemBody>
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
                            </AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Timelines</label></AccordionItemTitle>
                            <AccordionItemBody>
                                <div className="input-group bottom-padding-8">
                                    <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="plus"/></span>
                                    <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="trash"/></span>
                                </div>
                                <div>
                                  
                                </div>
                            </AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Animals</label></AccordionItemTitle>
                            <AccordionItemBody>
                                <div className="input-group bottom-padding-8">
                                    <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
                                    <input
                                        id="projectSearch"
                                        type="text"
                                        onChange={this.handleAnimalSearchChange}
                                        className="form-control search-input"
                                        name="projectSearch"
                                        placeholder="Search" />
                                    <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="save"/></span>
                                    <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="open"/></span>
                                </div>
                                <div>
                                    <ReactDataGrid
                                        rowKey="id1"
                                        columns={this.state.animalCols}
                                        rowGetter={this.animalRowGetter}
                                        rowsCount={this.state.animals.length}
                                        minHeight={300}
                                        rowSelection={{
                                            showCheckbox: true,
                                            enableShiftSelect: true,
                                            onRowsSelected: this.onAnimalRowsSelected,
                                            onRowsDeselected: this.onAnimalRowsDeselected,
                                            selectBy: { indexes: this.state.selectedAnimals }
                                        }} />                                    
                                </div>
                            </AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Calendar / Schedule</label></AccordionItemTitle>
                            <AccordionItemBody>
                                <p>Body content</p>
                            </AccordionItemBody>
                        </AccordionItem>

                    </Accordion>                
                </div>
                <div className='col-sm-8'>
                    <ProjectDetails project={this.selectedProjects} />
                </div>
            </div>
        </div>
    }

  }

  export default ProjectsView;