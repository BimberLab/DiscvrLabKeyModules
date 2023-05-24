import React, { useState, useEffect } from "react";
import { makeStyles } from "@material-ui/core/styles";
import TextField from "@material-ui/core/TextField";
import Button from "@material-ui/core/Button";
import Select from "@material-ui/core/Select";
import MenuItem from "@material-ui/core/MenuItem";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";
import CardActions from "@material-ui/core/CardActions";
import Card from "@material-ui/core/Card";
import CardContent from "@material-ui/core/CardContent";
import { fetchLuceneQuery, createEncodedFilterString } from "../../utils"

const useStyles = makeStyles((theme) => ({
  formControl: {
    minWidth: 200, 
    marginRight: theme.spacing(2),
  },
  actionWrapper: {
    display: 'flex',
    gap: theme.spacing(4)
  },
  filterContainer: {
    display: 'flex',
    justifyContent: 'center',
    width: '100%',
    marginTop: theme.spacing(2),
  },
  filterRow: {
    display: "flex",
    alignItems: "center",
    marginTop: theme.spacing(2),
    justifyContent: "center", // Add this line to center the contents of the filter map call
  },
  textField: {
    marginLeft: theme.spacing(2),
    marginRight: theme.spacing(2),
  },
  submitButton: {
    marginTop: theme.spacing(2),
  },
  valueInput: {
    width: 200,
    marginLeft: theme.spacing(2),
  },
  submitAndExternal: {
    display: "flex",
    gap: theme.spacing(2),
  },
  centeredContent: {
    display: "flex",
    flexDirection: "column",
    justifyContent: "flex-start",
    alignItems: "center",
    "& > :not(:first-child)": {
      textAlign: "center",
    },
  },
  arrowPaginationWrapper: {
    maxWidth: "50%",
    display:'flex'
  },
  cardActions: {
    display: 'flex',
    justifyContent: 'flex-end',
  },
  formScroll: {
    width: '100%',
    margin: '0 auto',
    maxHeight: 'calc(100vh - 200px)',
    overflowY: 'auto',
  },
  addFilterExternalWrapper: {
    display: 'flex',
    justifyContent: 'space-between',
    width: '100%',
  },
  card: {
    backgroundColor: 'transparent',
    border: '1px solid rgba(0, 0, 0, 0.12)'
  },
}));

const stringType = ["equals", "contains", "starts with", "ends with", "is empty", "is not empty"];
const variableSamplesType = ["in set", "variable in", "not variable in", "variable in all of", "variable in any of", "not variable in any of", "not variable in one of", "is empty", "is not empty"];
const numericType = ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"];
const noneType = [];
const impactType = ["LOW", "MODERATE", "HIGH"];

const FilterForm = ({ open, setOpen, sessionId, trackGUID, handleSubmitCallback, handleFailureCallback, fieldTypeInfo, externalActionComponent, arrowPagination }) => {
  const availableOperators = fieldTypeInfoToOperators(fieldTypeInfo);

  const [filters, setFilters] = useState(searchStringToInitialFilters(availableOperators) ?? [{ field: "", operator: "", value: "" }]);

  const classes = useStyles();

  const handleClose = () => {
    setOpen(false);
  };

  const handleAddFilter = () => {
    setFilters([...filters, { field: "", operator: "", value: "" }]);
  };

  const handleRemoveFilter = (index) => {
    setFilters(
      filters.filter((filter, i) => {
        return i !== index;
      })
    );
  };

  const handleFilterChange = (index, key, value) => {
    setFilters(
      filters.map((filter, i) => {
        if (i === index) {
          return { ...filter, [key]: value };
        }
        return filter;
      })
    );
  };

<<<<<<< HEAD
  // API call to retrieve the fields and build the form
  useEffect(() => {
    async function fetch() {
      const queryParam = new URLSearchParams(window.location.search)
      const searchString = queryParam.get("searchString");

      await fetchFieldTypeInfo(sessionId, trackGUID,
        (res) => {
          const availableOperators = Object.keys(res.fields).reduce((acc, idx) => {
            const fieldObj = res.fields[idx];
            const field = fieldObj.name;
            const type = fieldObj.type;

            let fieldType;

            switch (type) {
              case 'Flag':
              case 'String':
              case 'Character':
                fieldType = stringType;
                break;
              case 'Float':
              case 'Integer':
                fieldType = numericType;
                break;
              case 'Impact':
                fieldType = stringType;
                break;
              case 'None':
              default:
                fieldType = noneType;
                break;
            }

            acc[field] = { type: fieldType };

<<<<<<< HEAD
            if(field == "variableSamples") {
              acc[field] = { type: variableSamplesType };
=======
            if (field == "variableSamples") {
              acc[field] = variableSamplesType;
>>>>>>> 38da686e (Update table widget to always pass a proper GUID)
            }

            return acc;
          }, {}); 

          setAvailableOperators(availableOperators)
          setDataLoaded(true)
        })
=======
  function fieldTypeInfoToOperators(fieldTypeInfo) {
    const operators = Object.keys(fieldTypeInfo).reduce((acc, idx) => {
      const fieldObj = fieldTypeInfo[idx];
      const field = fieldObj.name;
          const type = fieldObj.type;

          let fieldType;

          switch (type) {
            case 'Flag':
            case 'String':
            case 'Character':
              fieldType = stringType;
              break;
            case 'Float':
            case 'Integer':
              fieldType = numericType;
              break;
            case 'Impact':
              fieldType = stringType;
              break;
            case 'None':
            default:
              fieldType = noneType;
              break;
          }

          acc[field] = { type: fieldType };

          if(field == "variableSamples") {
            acc[field] = { type: variableSamplesType };
          }

          return acc;
        }, {}) ?? [];

    return operators
  }
>>>>>>> d9ba7905 (Server-driven column model)

  function searchStringToInitialFilters(operators) {
    const queryParam = new URLSearchParams(window.location.search)
    const searchString = queryParam.get("searchString");

    let initialFilters: any[] | undefined = undefined;

    if (searchString) {
      const decodedSearchString = decodeURIComponent(searchString);
      const searchStringsArray = decodedSearchString.split("&");
      console.log("search strings array: ", searchStringsArray)
      initialFilters = searchStringsArray
        .map((item) => {
        const [field, operator, value] = item.split(",");
        return { field, operator, value };
        })
        .filter(({ field }) => operators.hasOwnProperty(field));
    }

    return initialFilters
  }

  useEffect(() => {
    async function fetch() {
      handleQuery(filters)
    }

    fetch()
  }, [filters])

  function handleQuery(passedFilters) {
    if(passedFilters.length != 0) {
      const encodedSearchString = createEncodedFilterString(passedFilters, false);
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.set("searchString", encodedSearchString);
      window.history.pushState(null, "", currentUrl.toString());
    }

    fetchLuceneQuery(passedFilters, sessionId, trackGUID, 0, (json)=>{console.log(json); handleSubmitCallback(json)}, () => {handleFailureCallback()});
    setOpen(false);
  }

  const handleSubmit = (event) => {
    event.preventDefault();
    handleQuery(filters);
  };

  return (
   <Card className={classes.card} elevation={0}>
     <form>
      <CardContent className={classes.centeredContent}>
        <div className={classes.addFilterExternalWrapper}>
          <Button
            variant="contained"
            color="primary"
            onClick={handleAddFilter}
          >
            Add Search Filter
          </Button>
           <div className={classes.actionWrapper}>
            {externalActionComponent && externalActionComponent}
            <div className={classes.arrowPaginationWrapper}>
              {arrowPagination && arrowPagination}
            </div>
          </div>
        </div>

        <div className={classes.formScroll}>
          {filters.map((filter, index) => (
            <div key={index} className={`${classes.filterRow}`}>
              <FormControl className={classes.formControl}>
                <InputLabel id="field-label">Field</InputLabel>
                <Select
                  labelId="field-label"
                  value={filter.field}
                  onChange={(event) =>
                    handleFilterChange(index, "field", event.target.value)
                  }
                >
                  <MenuItem value="None">
                    <em>None</em>
                  </MenuItem>
                  {Object.keys(availableOperators).map((field) => (
                    <MenuItem key={field} value={field}>
                      {field}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              {filter.field != "ADVANCED" ?
              <FormControl className={classes.formControl}>
                <InputLabel id="operator-label">Operator</InputLabel>
                <Select
                  labelId="operator-label"
                  value={filter.operator}
                  onChange={(event) =>
                    handleFilterChange(index, "operator", event.target.value)
                  }
                >
                  <MenuItem value="None">
                    <em>None</em>
                  </MenuItem>

                  {availableOperators[filter.field] && availableOperators[filter.field].type ? (
                    availableOperators[filter.field].type.map((operator) => (
                      <MenuItem key={operator} value={operator}>
                        {operator}
                      </MenuItem>
                    ))
                  ) : (
                    <MenuItem></MenuItem>
                  )}

                </Select>
              </FormControl>
              : null
              }

              {filter.operator === "in set" ? (
                <FormControl className={`${classes.formControl} ${classes.valueInput}`}>
                  <InputLabel id="value-select-label">Value</InputLabel>
                  <Select
                    labelId="value-select-label"
                    value={filter.value}
                    onChange={(event) =>
                      handleFilterChange(index, "value", event.target.value)
                    }
                  >
                    <MenuItem value="ONPRC">ONPRC</MenuItem>
                  </Select>
                </FormControl>
              ) : (
                <TextField
                  label="Value"
                  className={`${classes.textField} ${classes.valueInput}`}
                  value={filter.value}
                  onChange={(event) =>
                    handleFilterChange(index, "value", event.target.value)
                  }
                />
              )}

              <Button
                variant="contained"
                color="primary"
                onClick={() => handleRemoveFilter(index)}
              >
                Remove Filter
              </Button>
            </div>
          ))}
        </div>
      </CardContent>

      <CardActions className={classes.cardActions}>
        <div className={classes.submitAndExternal}>
          <Button
            onClick={handleSubmit}
            type="submit"
            variant="contained"
            color="primary"
          >
            Search
          </Button>
        </div>
      </CardActions>
    </form>
  </Card>
)}


export default FilterForm;
