import React from 'react';
import ReactDataGrid from 'react-data-grid';
import ReactDOM from 'react-dom';
import FullCalendar from 'fullcalendar';
import $ from 'jquery'; 
import Glyphicon from 'react-bootstrap/lib/Glyphicon'

class TimelineList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            debugUI: false,
            timelines: [], 
            timelineCols: [],
            selectedTimelines: []
        };
    }
    
    // animal grid methods & handlers
    timelineRowGetter = (index) => {
        return this.state.timelines[index];
    }

    onTimelineRowsSelected = (rows) => {
        this.setState({ selectedTimelines: this.state.selectedTimelines.concat(rows.map(r => r.rowIdx)) });
    }

    onTimelineRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedTimelines: this.state.selectedTimelines.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    handleTimelineSearchChange = (event) => {
        let value = event.target.value;
        this.setState({
            timelineSearchValue: value
        });
        if (value.length > 1) {
            console.log('searching timelines for "' + value + '"');
        } else {
            console.log('timelines search criteria too short.');
        }
    }

    fetchTimelines = () => {
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
                var timelines = [];
                if (results.rows.length > 0) {
                    console.log("Processing " + results.rows.length + " projects...");
                    results.rows.forEach((row) => {
                        /*
                        projects.push({
                            ProjectId: row.ProjectId.value.toString(),
                            Description: row.Description.value.toString() + " , Revision " + row.RevisionNum.value.toString(),
                            StartDate: row.StartDate.value.toString(),
                            EndDate: row.EndDate.value.toString()
                        });
                        */
                    })                    
                }
                this.setState({ timelines: timelines })
            },
            failure: (error) => console.log(error.exception)
        });
    }

    componentDidMount = () => {
        if (this.state.debugUI) console.log('TimelineList didMount()');
        this.fetchTimelines();
    }  

    componentDidUpdate = (prevProps) => {
        if (this.state.debugUI) console.log('TimelineList componentDidUpdate()');
    }

    componentWillUnmount = () => {
        this.ignoreLastFetch = true;
        if (this.state.debugUI) console.log('TimelineList componentWillUnmount()');
    }

    render = () => { return <div>
        <div className="input-group bottom-padding-8">
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
            <input
                id="timelineSearch"
                type="text"
                onChange={this.handleTimelineSearchChange}
                className="form-control search-input"
                name="projectSearch"
                placeholder="Search" />
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="save"/></span>
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="open"/></span>
        </div>
        <div>
            <ReactDataGrid
                rowKey="id3"
                columns={this.state.timelineCols}
                rowGetter={this.timelineRowGetter}
                rowsCount={this.state.timelines.length}
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