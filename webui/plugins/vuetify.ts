// import this after install `@mdi/font` package
import '@mdi/font/css/materialdesignicons.css'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import * as components from "vuetify/components";
import * as directives from "vuetify/directives";

export default defineNuxtPlugin((app) => {
  const vuetify = createVuetify({
    components,
    directives,
    theme: {
        defaultTheme: 'dark',
        themes: {
          dark: {
            dark: true,
            variables: {},
            colors: {
              indigonish: '#3949AB',
              greenish: '#03DAC5',
              background: '#263238',
              surface: '#212121',
              button: '#212121',
              'button-text': '#ADADAD',
              'primary-darken-1': '#3700B3',
              secondary: '#03DAC5',
              'secondary-darken-1': '#03DAC5',
              error: '#CF6679',
              info: '#2196F3',
              success: '#4CAF50',
              warning: '#FB8C00',
              anchor: '#8c9eff',
            }
          }
        }
    }
  })
  app.vueApp.use(vuetify)
})