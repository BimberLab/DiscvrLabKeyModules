import React from 'react';
import { Accordion, AccordionItem, AccordionItemTitle, AccordionItemBody } from 'react-accessible-accordion';
import '../styles/Accordion.style.css'
import ProjectList from '../components/ProjectList';
import AnimalList from '../components/AnimalList';
import ProjectDetails from '../components/ProjectDetails';
import TimelineList from '../components/TimelineList';

const verboseOutput = false;

class ProjectsView extends React.Component {
        constructor(props) {
        super(props);
        this.state = { };
    }

    componentDidMount = () => {
        if (verboseOutput) console.log('ProjectsView didMount()');
    }  

    componentDidUpdate = (prevProps) => { 
        if (verboseOutput) console.log('ProjectsView componentDidUpdate()');
    }

    componentWillUnmount = () => {
        if (verboseOutput) console.log('ProjectsView componentWillUnmount()');        
        this.ignoreLastFetch = true;
    }

    render() {
        return <div>
            <div className='row spacer-row'></div>
            <div className='row'>
                <div className='col-sm-4'>
                    <Accordion className="accordion__style__primary">
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
                    </Accordion>                
                </div>
                <div className='col-sm-8'>
                    <ProjectDetails store={this.props.store} project={this.selectedProject} />
                </div>
            </div>
        </div>
    }

  }

  export default ProjectsView;