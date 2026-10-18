import { createRouter, createWebHistory } from 'vue-router'
import { sessao } from './estado/sessao'
import { quandoPerderSessao } from './api/http'
import AppShell from './componentes/AppShell.vue'

// As rotas públicas ficam fora do AppShell. Toda rota nova do front também precisa entrar em
// SpaController.ROTAS no backend (senão o Spring nega a URL ao recarregar a página).
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, salvo) => salvo ?? { top: 0 },
  routes: [
    { path: '/entrar', component: () => import('./views/EntrarView.vue'), meta: { publica: true, titulo: 'Entrar' } },
    { path: '/criar-conta', component: () => import('./views/CriarContaView.vue'), meta: { publica: true, titulo: 'Criar conta' } },
    { path: '/convite/:token', component: () => import('./views/ConviteView.vue'), meta: { publica: true, livre: true, titulo: 'Convite' } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/ano' },
        { path: 'ano/:ano(\\d{4})?', component: () => import('./views/VisaoAnualView.vue'), meta: { titulo: 'O ano' } },
        { path: 'lancamentos', component: () => import('./views/LancamentosView.vue'), meta: { titulo: 'Lançamentos' } },
        { path: 'recorrencias', component: () => import('./views/RecorrenciasView.vue'), meta: { titulo: 'Recorrências' } },
        { path: 'quanto-pesa', component: () => import('./views/QuantoPesaView.vue'), meta: { titulo: 'Quanto pesa no ano?' } },
        { path: 'familia', component: () => import('./views/FamiliaView.vue'), meta: { titulo: 'Família' } },
        { path: 'categorias', component: () => import('./views/CategoriasView.vue'), meta: { titulo: 'Categorias' } },
        { path: 'investimentos', component: () => import('./views/InvestimentosView.vue'), meta: { titulo: 'Investimentos' } },
        { path: 'conta', component: () => import('./views/ContaView.vue'), meta: { titulo: 'Minha conta' } },
        { path: ':resto(.*)*', component: () => import('./views/NaoEncontradoView.vue'), meta: { titulo: 'Página não encontrada' } },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (!sessao.estado.carregado) {
    try {
      await sessao.carregar()
    } catch {
      // Sem API: deixa a tela pública abrir; as telas logadas mostram o erro ao carregar.
    }
  }
  const logado = sessao.logado.value
  if (!to.meta.publica && !logado) {
    return { path: '/entrar', query: to.fullPath !== '/ano' && to.fullPath !== '/' ? { volta: to.fullPath } : {} }
  }
  if (to.meta.publica && !to.meta.livre && logado) return '/ano'
})

router.afterEach((to) => {
  const titulo = to.meta.titulo as string | undefined
  document.title = titulo ? `${titulo} · Familiança` : 'Familiança'
})

quandoPerderSessao(() => {
  if (!sessao.logado.value) return
  sessao.limpar()
  const atual = router.currentRoute.value
  if (!atual.meta.publica) router.replace({ path: '/entrar', query: { volta: atual.fullPath, expirou: '1' } })
})

export default router
