import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import Counter from './Counter';
import reportWebVitals from './reportWebVitals';

const rootElement = document.getElementById('root') as HTMLElement;

if (rootElement) {
  const root = ReactDOM.createRoot(rootElement);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}

const counterElement = document.getElementById('counter') as HTMLElement;
if (counterElement) {
  const counter = ReactDOM.createRoot(counterElement);
  counter.render(
    <React.StrictMode>
      <Counter />
    </React.StrictMode>
  );
}

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
