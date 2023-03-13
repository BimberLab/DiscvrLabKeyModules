import React, { useState } from "react";
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
import { fetchLuceneQuery } from "../../utils"

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
  removeButton: {
    marginLeft: theme.spacing(2),
  },
  submitButton: {
    marginTop: theme.spacing(2),
  },
}));

const availableOperators = {
  None: [""],
  Samples: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  CHROM: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  genomicPosition: ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"],
  start: ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"],
  end: ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"],
  contig: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  REF: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  ALT: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  AF: ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"],
  VARIANT_TYPE: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  IMPACT: ["LOW", "MODERATE", "HIGH"],
  OVERLAPPING_GENES: ["contains", "is", "starts with", "ends with", "is empty", "is not empty"],
  CADD_PH: ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"],
};

const FilterForm = ({ open, setOpen, sessionId, handleSubmitCallback }) => {
  const [filters, setFilters] = useState([{ field: "None", operator: "", value: "" }]);
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

  const handleSubmit = (event) => {
    event.preventDefault();
    console.log("handleSubmit")
    console.log("filter form callback", handleSubmitCallback)
    console.log("sessionId", sessionId)
    fetchLuceneQuery(filters, sessionId, 0, (json)=>{console.log(json); handleSubmitCallback(json)});
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
                <MenuItem value="CHROM">Chromosome</MenuItem>
                <MenuItem value="genomicPosition">Position</MenuItem>
                <MenuItem value="Samples">Samples</MenuItem>
                <MenuItem value="start">Start</MenuItem>
                <MenuItem value="end">End</MenuItem>
                <MenuItem value="contig">Contig</MenuItem>
                <MenuItem value="REF">Reference</MenuItem>
                <MenuItem value="ALT">Alternative Allele</MenuItem>
                <MenuItem value="AF">Allele Frequency</MenuItem>
                <MenuItem value="Type">Type</MenuItem>
                <MenuItem value="IMPACT">Impact</MenuItem>
                <MenuItem value="OVERLAPPING_GENES">Overlapping Genes</MenuItem>
                <MenuItem value="CADD_PH">CADD Score</MenuItem>
                <MenuItem value="ADVANCED">Advanced</MenuItem>
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

                {availableOperators[filter.field] ? availableOperators[filter.field].map((operator) => (
                  <MenuItem key={operator} value={operator}>{operator}</MenuItem>
                )) : <MenuItem></MenuItem> }
              </Select>
            </FormControl>
            : null
            }

            <TextField
              label="Value"
              className={classes.removeButton}
              value={filter.value}
              onChange={(event) =>
                handleFilterChange(index, "value", event.target.value)
              }
            />
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
