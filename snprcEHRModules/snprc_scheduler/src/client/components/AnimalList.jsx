import React from 'react';
import ReactDataGrid from 'react-data-grid';
import ReactDOM from 'react-dom';
import FullCalendar from 'fullcalendar';
import $ from 'jquery'; 
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import {connect} from "react-redux";

const verboseOutput = false;

class EmptyAnimalRowsView extends React.Component { render() {return (<div> No animals available.</div>);} }

class AnimalList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            animals: [], 
            animalCols: [
                { key: 'Id', name: 'ID', width: 60 },
                { key: 'Gender', name: 'Gender', width: 80 },
                { key: 'Weight', name: 'Weight', width: 90 },
                { key: 'Age', name: 'Age', width: 140 },
            ],
            selectedAnimals: []
        };
        this.props.store.subscribe(this.handleStoreUpdate); 
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

    }

    handleStoreUpdate = () => {
        let animals = this.props.store.getState().project.animals || null;
        let formattedAnimals = [];
        if (animals != null) {
            animals.forEach((a) => {               
                formattedAnimals.push({
                    Id: a.Id.value,
                    Gender: a.Gender.displayValue,
                    Weight: a.Weight.value + ' kg',
                    Age: a.Age.value
                })
            });
            this.setState({animals: formattedAnimals});
        } else this.setState({animals: []});
    }

    componentDidMount = () => {
        if (verboseOutput) console.log('AnimalList didMount()');
    }  

    componentDidUpdate = (prevProps) => {
        if (verboseOutput) console.log('AnimalList componentDidUpdate()');
    }

    componentWillUnmount = () => {
        if (verboseOutput) console.log('AnimalList componentWillUnmount()');
    }

    render = () => { 
        let animalCount = this.state.animals ? this.state.animals.length : 0;
        return (<div>
        <div className="input-group bottom-padding-8">
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
            <input
                id="animalSearch"
                type="text"
                onChange={this.handleAnimalSearchChange}
                className="form-control search-input"
                name="animalSearch"
                placeholder="Search" />
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="save"/></span>
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="open"/></span>
        </div>
        <div>
            <ReactDataGrid
                rowKey="id1"
                columns={this.state.animalCols}
                rowGetter={this.animalRowGetter}
                rowsCount={animalCount}
                minHeight={300}
                rowSelection={{
                    showCheckbox: true,
                    enableShiftSelect: true,
                    onRowsSelected: this.onAnimalRowsSelected,
                    onRowsDeselected: this.onAnimalRowsDeselected,
                    selectBy: { indexes: this.state.selectedAnimals }
                }}
                emptyRowsView={EmptyAnimalRowsView} />                                    
        </div>
    </div>
    )};

  }

  export default AnimalList;