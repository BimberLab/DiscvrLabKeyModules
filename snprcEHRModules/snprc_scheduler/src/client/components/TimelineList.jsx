import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

class TimelineList extends React.Component {
    
    constructor(props) {
        super(props);
        let redux_state = this.props.store.getState().project;
        this.state = {
            debugUI: false,
            timelines: (redux_state.timelines || null), 
            timelineCols: [
                { key: 'Description', name: 'Description', width: 374 }
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
        if (redux_state.timelines && redux_state.timelines.length > 0) {
            console.log(redux_state.timelines[0]);
        }
        
    }

    timelineRowGetter = (index) => this.state.timelines[index];
    
    onTimelineRowsSelected = (rows) => {
        this.setState({ selectedTimelines: this.state.selectedTimelines.concat(rows.map(r => r.rowIdx)) });
    }

    onTimelineRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedTimelines: this.state.selectedTimelines.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    render = () => { 
        let projectCount = this.state.timelines ? this.state.timelines.length : 0;
        return <div>
        <div className="input-group bottom-padding-8">
            <button title="Create new timeline" className="smooth-border"><Glyphicon glyph="plus"/></button>
            <button title="Clone selected timeline" className="input-group-left-margin smooth-border"><Glyphicon glyph="copy"/></button>
            <button title="Remove selected timeline" className="input-group-left-margin smooth-border"><Glyphicon glyph="trash"/></button>
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