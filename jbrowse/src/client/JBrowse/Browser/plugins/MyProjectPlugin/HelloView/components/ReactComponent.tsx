import React, { useState } from 'react'

export default function ReactComponent() {
  const [pushed, setPushed] = useState('')
  return (
    <div style={{ padding: 50 }}>
      <h1>Hello plugin developers!</h1>
      <button
        onClick={() => {
          setPushed('Woah! You pushed the button! Great job!')
        }}
      >
        Push the button
      </button>
      <p>{pushed}</p>
    </div>
  )
}
