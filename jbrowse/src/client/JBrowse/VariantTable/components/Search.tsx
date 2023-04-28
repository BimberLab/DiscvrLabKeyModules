import React, { useState } from "react";
import Button from "@material-ui/core/Button";
import FilterForm from "./FilterForm";


export default function Search(props: {sessionId: string, trackId: string, handleSubmitCallback: any, handleFailureCallback: any}) {
  const [open, setOpen] = useState(false);

  // The code expects a proper GUID, yet the trackId is a string containing the GUID + filename
  var trackGUID = props.trackId
  if (props.trackId && props.trackId.length > 36) {
      trackGUID = props.trackId.substring(0, 36)
  }

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <>
      <Button style={{ marginTop:"8px"}} variant="contained" color="primary" onClick={handleOpen}>
        Search
      </Button>
      <FilterForm open={open} setOpen={setOpen} sessionId={props.sessionId} trackGUID={trackGUID} handleSubmitCallback={props.handleSubmitCallback} handleFailureCallback={props.handleFailureCallback}/>
    </>
  );
};