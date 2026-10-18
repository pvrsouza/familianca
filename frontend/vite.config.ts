import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Em desenvolvimento a API roda no mesmo container (8080) e o Vite repassa /api: front e API na mesma origem,
// então o cookie de sessão (SameSite=Strict) e o CSRF funcionam sem CORS.
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.API_URL ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    // Sem source maps em produção: o código do front não vai legível para o navegador.
    sourcemap: false,
  },
})
