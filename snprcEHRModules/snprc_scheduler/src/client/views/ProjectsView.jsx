import React from 'react';
import { Accordion, AccordionItem, AccordionItemTitle, AccordionItemBody } from 'react-accessible-accordion';
import '../styles/Accordion.style.css'
import ProjectList from '../components/ProjectList';
import AnimalList from '../components/AnimalList';
import ProjectDetails from '../components/ProjectDetails';
import TimelineList from '../components/TimelineList';
import TimelineDetails from '../components/TimelineDetails';

const TAB_PROJECTS = 0x0;
const TAB_TIMELINES = 0x1;
const TAB_ANIMALS = 0x2;
const TAB_CALENDAR = 0x3;

class ProjectsView extends React.Component {
        
    constructor(props) {
        super(props);
        this.state = { selectedTab: TAB_PROJECTS };
    }

    handleAccordionSelectionChange = (tabIndex) => this.setState({ selectedTab: tabIndex });
    
    getDetailComponent = (tabIndex) => {
        let projectDetails = (<ProjectDetails store={this.props.store} project={this.selectedProject} />);
        let timelineDetails = (<TimelineDetails store={this.props.store} project={this.selectedProject} />);
        let animalDetails = (<div> animal details </div>);
        let calendarDetails = (<div> calendar details </div>);        
        switch (tabIndex) {
            case TAB_PROJECTS: return projectDetails
            case TAB_ANIMALS: return animalDetails;
            case TAB_TIMELINES: return timelineDetails;
            case TAB_CALENDAR: return calendarDetails;
        }
    }

    render() {

        let detailView = this.getDetailComponent(this.state.selectedTab);
        let accordionComponent = (
        <Accordion className="accordion__style__primary" onChange={this.handleAccordionSelectionChange}>
            <AccordionItem expanded={true}>
                <AccordionItemTitle><label className="accordion__title__text">Projects</label></AccordionItemTitle>
                <AccordionItemBody><ProjectList store={this.props.store} /></AccordionItemBody>
            </AccordionItem>
            <AccordionItem>
                <AccordionItemTitle><label className="accordion__title__text">Timelines</label></AccordionItemTitle>
                <AccordionItemBody><TimelineList store={this.props.store} /></AccordionItemBody>
            </AccordionItem>
            <AccordionItem>
                <AccordionItemTitle><label className="accordion__title__text">Animals</label></AccordionItemTitle>
                <AccordionItemBody><AnimalList store={this.props.store} /></AccordionItemBody>
            </AccordionItem>
            <AccordionItem>
                <AccordionItemTitle><label className="accordion__title__text">Calendar / Schedule</label></AccordionItemTitle>
                <AccordionItemBody><p>Body content</p></AccordionItemBody>
            </AccordionItem>
        </Accordion> );
        return <div>
            <div className='row spacer-row'></div>
            <div className='row'>
                <div className='col-sm-4'>{accordionComponent}</div>
                <div className='col-sm-8'>{detailView}</div>
            </div>
        </div>
    }

  }

  export default ProjectsView;