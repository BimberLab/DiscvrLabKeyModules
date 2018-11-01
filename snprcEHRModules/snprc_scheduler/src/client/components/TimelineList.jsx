/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 15 2018      
    ==================================================================================
*/
import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

const verboseOutput = true;

class TimelineList extends React.Component {
    
    constructor(props) {
        super(props);
        let redux_state = this.props.store.getState().project;
        this.state = {
            debugUI: false,
            timelines: (redux_state.timelines || null), 
            timelineCols: [
                { key: 'Description', name: 'Description', width: 373 }
            ],
            selectedTimelines: [],
            selectedTimeline: (redux_state.selectedTimeline || null)
        };
        this.disconnect = this.props.store.subscribe(this.handleStoreUpdate); 
    }

    componentWillUnmount = () => this.disconnect();

    handleStoreUpdate = () => {
        let redux_state = this.props.store.getState().project;
        this.setState({ timelines: redux_state.timelines });
        /*
        if (redux_state.timelines && redux_state.timelines.length > 0) {
            console.log(redux_state.timelines[0]);
        }
        */
    }

    handleTimelineCreate = () => {
        console.log('handleTimelineCreate()');
    }

    handleTimelineDuplicate = () => {
        console.log('handleTimelineDuplicate()');
    }

    handleTimelineDestroy = () => {
        console.log('handleTimelineDestroy()');
    }

    timelineRowGetter = (index) => this.state.timelines[index];
    
    onTimelineRowsSelected = (rows) => {
        console.log(rows);
        let selectedTimeline = rows.length > 0 ? rows[0].row : null;
        if (rows.length > 0) rows = [rows[0]];
        else rows = [];
        this.setState({ selectedTimelines: rows.map(r => r.rowIdx) });
        if (verboseOutput) {
            console.log('selectedTimeline:');
            console.log(selectedTimeline); 
        }

        
    }

    onTimelineRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedTimelines: this.state.selectedTimelines.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    render = () => { 
        let projectCount = this.state.timelines ? this.state.timelines.length : 0;
        return <div>
        <div className="input-group bottom-padding-8">
            <button title="Create new timeline" className="smooth-border" onClick={this.handleTimelineCreate}><Glyphicon glyph="plus"/></button>
            <button title="Clone selected timeline" className="input-group-left-margin smooth-border" onClick={this.handleTimelineDuplicate}><Glyphicon glyph="copy"/></button>
            <button title="Remove selected timeline" className="input-group-left-margin smooth-border" onClick={this.handleTimelineDestroy}><Glyphicon glyph="trash"/></button>
        </div>
        <div>
            <ReactDataGrid
                rowKey="id3"
                columns={this.state.timelineCols}
                rowGetter={this.timelineRowGetter}
                rowsCount={projectCount}
                minHeight={300}
                rowSelection={{
                    showCheckbox: true,
                    enableShiftSelect: true,
                    onRowsSelected: this.onTimelineRowsSelected,
                    onRowsDeselected: this.onTimelineRowsDeselected,
                    selectBy: { indexes: this.state.selectedTimelines }
                }} />                                    
        </div>
    </div>
    }

  }

  export default TimelineList;