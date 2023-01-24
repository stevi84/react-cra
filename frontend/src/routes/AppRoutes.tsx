import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import { Dashboard } from './Dashboard';

const AppRoutes: React.FC<unknown> = () => (
  <Router basename="/frontend/">
    <Routes>
      <Route path="/" element={<Dashboard/>}/>
    </Routes>
  </Router>
);
export default AppRoutes;
