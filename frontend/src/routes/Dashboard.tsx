import React from 'react';
import { useTranslation } from 'react-i18next';

export function Dashboard() {
  const { t, i18n } = useTranslation();
  function german() {
    i18n.changeLanguage('de');
  }
  function english() {
    i18n.changeLanguage('en');
  }
  return (
    <React.Fragment>
      <h1>{t('dashboard.welcome')}</h1>
      <p>{i18n.language}</p>
      <button onClick={english}>English</button>
      <button onClick={german}>Deutsch</button>
    </React.Fragment>
  );
}
