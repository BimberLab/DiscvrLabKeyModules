import React from 'react';
import CalendarView from './CalendarView';
//import 'fullcalendar-reactwrapper/dist/css/fullcalendar.min.css';
import 'fullcalendar/dist/fullcalendar.css';

let calendar_style = {
    width: '80%',
    height: '80%',
    marginLeft: '150px',
    marginTop: '30px'
}

const CalendarPage = () => (
    <div style={calendar_style}>
        <CalendarView />
    </div>
);

export default CalendarPage;