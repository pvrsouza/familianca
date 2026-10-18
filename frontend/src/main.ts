import { createApp } from 'vue'
import '@fontsource-variable/figtree/wght.css'
import '@fontsource/fraunces/latin-500.css'
import '@fontsource/fraunces/latin-600.css'
import './estilos/base.css'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
