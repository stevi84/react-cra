import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    debug: false,
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
    resources: {
      de: { translation: { yes: 'Ja', no: 'Nein', dashboard: { welcome: 'Willkommen' }}},
      en: { translation: { yes: 'Yes', no: 'No', dashboard: { welcome: 'Welcome' }}},
    }}
  );
