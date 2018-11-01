/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 4 2018      
    ==================================================================================
*/
import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import { filterAnimals } from '../actions/dataActions';

class AnimalList extends React.Component {
    
    constructor(props) {
        super(props);
        this.state = {
            animals: [], 
            animalCols: [
                { key: 'Id', name: 'ID', width: 70 },
                { key: 'Gender', name: 'Gender', width: 82 },
                { key: 'Weight', name: 'Weight', width: 90 },
                { key: 'Age', name: 'Age', width: 130 },
            ],
            selectedAnimals: []
        };
        this.props.store.subscribe(this.handleStoreUpdate); 
    }
    
    componentWillUnmount = () => this.disconnect();
    
    animalRowGetter = (index) => this.state.animals[index];
    
    onAnimalRowsSelected = (rows) => {
        this.setState({ selectedAnimals: this.state.selectedAnimals.concat(rows.map(r => r.rowIdx)) });
    }

    onAnimalRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedAnimals: this.state.selectedAnimals.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    handleAnimalSearchChange = (event) => this.props.store.dispatch(filterAnimals(event.target.value));

    handleStoreUpdate = () => {
        let animals = this.props.store.getState().project.animals || null;
        let formattedAnimals = [];
        if (animals != null) {
            animals.forEach((a) => {               
                formattedAnimals.push({
                    Id: a.Id.value,
                    Gender: a.Gender.displayValue,
                    Weight: a.Weight.value ? a.Weight.value + ' kg' : 'unknown',
                    Age: a.Age.value
                })
            });
        }
        this.setState({animals: formattedAnimals});
    }

    render = () => { 
        let animalCount = this.state.animals ? this.state.animals.length : 0;
        let searchJSX = (                    
            <div className="input-group bottom-padding-8">
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
            <input
                id="animalSearch"
                type="text"
                onChange={this.handleAnimalSearchChange}
                className="form-control search-input"
                name="animalSearch"
                placeholder="Search animals" />
            <span className="input-group-addon input-group-addon-buffer" title="Import animal list"><Glyphicon glyph="save"/></span>
            <span className="input-group-addon input-group-addon-buffer" title="Export animal list"><Glyphicon glyph="open"/></span>
            </div>
        );
        if (animalCount > 0) {
            return (
                <div>
                    {searchJSX}
                    <div>
                        <ReactDataGrid
                            rowKey="Id4"
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
                        />                                    
                    </div>
                </div>
            )
        } else return <div style={{ minHeight: 346 }}>{searchJSX}<div> No assignable animals found. </div></div>
    }

  }

  export default AnimalList;