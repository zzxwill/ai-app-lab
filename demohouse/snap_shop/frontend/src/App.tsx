import { useState } from 'react';


import './index.css';

import Recognition from './routes/recognition';


function App() {



  const [route] = useState('recognition');
  

  const renderRoute = () => {
    switch (route) {
      case 'recognition': {
        return <Recognition />;
      }
      default: {
        return <div>404</div>
      }
    }
  };

  


  return (
  <div>
     {renderRoute()}
  </div>
  );
}

export default App;
