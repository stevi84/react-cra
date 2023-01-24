import { useCallback, useState } from 'react';

export default function Counter() {
  const [count, setCount] = useState(1);
  const decrease = useCallback(() => setCount(prevCount => prevCount-1), []);
  const increase = useCallback(() => setCount(prevCount => prevCount+1), []);

  return(
    <>
      <button onClick={decrease}>-</button>
      <span style={{paddingLeft: 12, paddingRight: 12}}>{count}</span>
      <button onClick={increase}>+</button>
    </>
  );
}
