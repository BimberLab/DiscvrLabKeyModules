import React from 'react';
import { HashRouter, Route, Switch, Redirect } from 'react-router-dom';
import CalendarPage from '../views/CalendarPage';
//import ProjectView from '../views/ProjectView';
import ProjectsView from '../views/ProjectsView';
import NotFoundPage from '../views/NotFoundPage';

const baseFilters = ['?','%2F','/','=','&','#']

class UIRouter extends React.Component {
        constructor(props) {
        super(props);
        this.state = { }
    }
    
    render() {
        this.filterPath();
        return <HashRouter>
            <div>
                <Switch>
                    <Route path='/projects' exact render={(p) => (<ProjectsView store={this.props.store} />)} />
                    <Route path='/project/:id' exact render={(p) => (<ProjectsView store={this.props.store} />)} />
                    <Route path="/calendar" component={CalendarPage} exact />
                    <Redirect from="/" to="/projects" />
                    <Route component={NotFoundPage} />
                </Switch>
            </div>
        </HashRouter> 
    }
    
    filterPath = () => {
        let path = window.location.toString();
        var bypass = true;
        if (path.indexOf(baseFilters[0]) > -1) {
            let segments = path.split(baseFilters[0]);
            // inspect the hash path relative to the root path.
            switch(segments[1]) {
                case '#/':

                    break;
                case '#/projects':
                    
                    break;
                default:

                    break;
            }
            // if we change anything with the path, flag bypass=false
            // so that the browsers location is updated.
        }
        if (!bypass) { window.location.replace(path) }
    }
    
  }

  export default UIRouter;