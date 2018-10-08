import React from 'react';
import ReactDataGrid from 'react-data-grid';
//import ReactDataGridPlugins from 'react-data-grid-addons';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import { Accordion, AccordionItem, AccordionItemTitle, AccordionItemBody } from 'react-accessible-accordion';
import '../styles/Accordion.style.css'
import ProjectList from '../components/ProjectList';
import AnimalList from '../components/AnimalList';
import ProjectDetails from '../components/ProjectDetails';
import TimelineList from '../components/TimelineList';


class ProjectsView extends React.Component {
        constructor(props) {
        super(props);
        this.state = { 
            animals: [], 
            animalCols: [],
            selectedAnimals: []
        };
    }

    // component methods & handlers
    componentDidMount = () => {
        console.log('ProjectsView didMount()');
    }  

    componentDidUpdate = (prevProps) => { 
        console.log('ProjectsView componentDidUpdate()');
    }

    componentWillUnmount = () => {
        console.log('ProjectsView componentWillUnmount()');        
        this.ignoreLastFetch = true;
    }

    render() {
        return <div>
            <div className='row spacer-row'></div>
            <div className='row'>
                <div className='col-sm-4'>
                    <Accordion className="accordion__style__primary">

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Projects</label></AccordionItemTitle>
                            <AccordionItemBody><ProjectList /></AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Timelines</label></AccordionItemTitle>
                            <AccordionItemBody><TimelineList /></AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Animals</label></AccordionItemTitle>
                            <AccordionItemBody><AnimalList /></AccordionItemBody>
                        </AccordionItem>

                        <AccordionItem>
                            <AccordionItemTitle><label className="accordion__title__text">Calendar / Schedule</label></AccordionItemTitle>
                            <AccordionItemBody><p>Body content</p></AccordionItemBody>
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