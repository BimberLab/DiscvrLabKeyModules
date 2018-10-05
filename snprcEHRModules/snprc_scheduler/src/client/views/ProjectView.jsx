import React from 'react';
import ReactDOM from 'react-dom';
import FullCalendar from 'fullcalendar';
import $ from 'jquery'; 

class ProjectView extends React.Component {
        constructor(props) {
        super(props);
        this.data = props;
        this.state = { }
    }
    
    render() { return <div id="project_">project view</div>; }
    
    componentDidMount() {
        console.log(' ProjectView didMount()')
        this.fetchProject();
    }  

    componentDidUpdate (prevProps) {
        //let oldId = prevProps.params.projectId
        //let newId = this.props.params.projectId
        //if (newId !== oldId)
        this.data = this.props;
        this.fetchProject();
    }

    componentWillUnmount () {
        this.ignoreLastFetch = true
    }

    fetchProject () {
        // LABKEY API call
        /*
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'snd',
            queryName: 'Projects',
            columns: 'ProjectId,RevisionNum,ReferenceId,StartDate,EndDate,Description,HasEvent',
            filterArray: null,
            sort: 'ProjectId,RevisionNum',
            success: this.onSuccess,
            failure: this.onError
        });
        */
    }


    /*
    onSuccess(results) {
        var data = '';
        var length = Math.min(10, results.rows.length);
    
        // Display first 10 rows in a popup dialog
        for (var idxRow = 0; idxRow < length; idxRow++) {
            var row = results.rows[idxRow];
    
            for (var col in row) {
                data = data + row[col].value + ' ';
            }
    
            data = data + '\n';
        }
    
        alert(data);
    }
    
    onError(errorInfo) {
        alert(errorInfo.exception);
    }
    */


  }

  export default ProjectView;