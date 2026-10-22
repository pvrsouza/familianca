import { createApp } from 'vue'
import '@fontsource-variable/figtree/wght.css'
import '@fontsource/fraunces/latin-500.css'
import '@fontsource/fraunces/latin-600.css'
import './estilos/base.css'
import App from './App.vue'
import router from './router'
import { tema } from './estado/tema'

// Antes de montar, para a primeira tela já sair no tema escolhido (a CSP não permite script inline no index.html).
tema.aplicar()

createApp(App).use(router).mount('#app')
