import React, { useState } from "react";
import Button from "@material-ui/core/Button";
import FilterForm from "./FilterForm";


export default function Search(props: {sessionId: number, handleSubmitCallback: any}) {
  const [open, setOpen] = useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  console.log("search form callback", props.handleSubmitCallback)

  return (
    <>
      <Button style={{ marginTop:"8px"}} variant="contained" color="primary" onClick={handleOpen}>
        Search
      </Button>
      <FilterForm open={open} setOpen={setOpen} sessionId={props.sessionId} handleSubmitCallback={props.handleSubmitCallback}/>
    </>
  );
};