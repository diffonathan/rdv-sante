import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';

import { routes } from './app.routes';

// Sans cet enregistrement, Angular formate en anglais quelle que soit la
// valeur de LOCALE_ID : les données de locale ne sont pas embarquées par
// défaut. Une application francophone affichait « Wednesday 30 September ».
registerLocaleData(localeFr, 'fr');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    { provide: LOCALE_ID, useValue: 'fr' },
    // withFetch : l'API Fetch plutôt que XHR. Elle gère l'annulation
    // proprement — utile quand on change de praticien pendant qu'une requête
    // de créneaux est encore en vol.
    provideHttpClient(withFetch()),
  ],
};
