import React, { useState, useEffect } from "react";
import { makeStyles } from "@material-ui/core/styles";
import TextField from "@material-ui/core/TextField";
import Button from "@material-ui/core/Button";
import Select from "@material-ui/core/Select";
import MenuItem from "@material-ui/core/MenuItem";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";
import Dialog from "@material-ui/core/Dialog";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContent from "@material-ui/core/DialogContent";
import DialogTitle from "@material-ui/core/DialogTitle";
import { fetchLuceneQuery, fetchFieldTypeInfo } from "../../utils"

const useStyles = makeStyles((theme) => ({
  formControl: {
    minWidth: 120,
    marginRight: theme.spacing(2),
  },
  filterRow: {
    display: "flex",
    alignItems: "center",
    marginTop: theme.spacing(2),
  },
  addButton: {
    marginTop: theme.spacing(2),
  },
  textField: {
    marginLeft: theme.spacing(2),
    marginRight: theme.spacing(2),
  },
  submitButton: {
    marginTop: theme.spacing(2),
  },
  valueInput: {
    width: 120,
    marginLeft: theme.spacing(2),
  },
}));

const stringType = ["equals", "contains", "in", "starts with", "ends with", "is empty", "is not empty"];
const variableSamplesType = ["in set", "contains", "in", "starts with", "ends with", "is empty", "is not empty"];
const numericType = ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"];
const noneType = [];
const impactType = ["LOW", "MODERATE", "HIGH"];

const FilterForm = ({ open, setOpen, sessionId, trackGUID, handleSubmitCallback, handleFailureCallback }) => {
  const [filters, setFilters] = useState([{ field: "", operator: "", value: "" }]);

  const [availableOperators, setAvailableOperators] = useState<any>({
    variableSamples: { type: variableSamplesType },
    ref: { type: stringType },
    alt: { type: stringType },
    start: { type: numericType },
    end: { type: numericType },
    genomicPosition: { type: numericType },
    contig: { type: stringType },
  });

  const [dataLoaded, setDataLoaded] = useState(false)

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

  // API call to retrieve the fields and build the form
  useEffect(() => {
    async function fetch() {
      const queryParam = new URLSearchParams(window.location.search)

      fetchFieldTypeInfo(sessionId, trackGUID,
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
                fieldType = impactType;
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
          }, {}); 

          setAvailableOperators(availableOperators)
          setDataLoaded(true)
        })
    }

    fetch()

  }, [])

  const handleSubmit = (event) => {
    event.preventDefault();
    fetchLuceneQuery(filters, sessionId, trackGUID, 0, (json)=>{console.log(json); handleSubmitCallback(json)}, () => {handleFailureCallback()});
    setOpen(false);
  };

  return (
    <form>

    <Dialog open={open} onClose={handleClose}>
      <DialogTitle>Search</DialogTitle>
      <DialogContent>
        <Button
          variant="contained"
          color="primary"
          className={classes.addButton}
          onClick={handleAddFilter}
        >
          Add Filter
        </Button>

        {filters.map((filter, index) => (
          <div key={index} className={classes.filterRow}>
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

      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          Close
        </Button>

        <Button
          onClick={handleSubmit}
          type="submit"
          variant="contained"
          color="primary"
        >
          Submit
        </Button>
      </DialogActions>
    </Dialog>
    </form>
  );
};

export default FilterForm;
