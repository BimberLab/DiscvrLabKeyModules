import React from 'react';
import ReactDataGrid from 'react-data-grid';
import ReactDOM from 'react-dom';
import FullCalendar from 'fullcalendar';
import $ from 'jquery'; 
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

class AnimalList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            debugUI: false,
            animals: [], 
            animalCols: [],
            selectedAnimals: []
        };
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

    fetchAnimals = () => {
        // LABKEY query API call
        return;
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'snd',
            queryName: 'Projects',
            columns: 'ProjectId,RevisionNum,ReferenceId,StartDate,EndDate,Description,HasEvent',
            filterArray: null,
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
                this.setState({ projects: projects })
            },
            failure: (error) => console.log(error.exception)
        });
    }

    componentDidMount = () => {
        if (this.state.debugUI) console.log('ProjectList didMount()');
        this.fetchAnimals();
    }  

    componentDidUpdate = (prevProps) => {
        if (this.state.debugUI) console.log('ProjectList componentDidUpdate()');
    }

    componentWillUnmount = () => {
        this.ignoreLastFetch = true;
        if (this.state.debugUI) console.log('ProjectList componentWillUnmount()');
    }

    render = () => { return <div>
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
    </div>
    }

  }

  export default AnimalList;