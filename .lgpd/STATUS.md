# LGPD Audit Status

**Projeto**: Familiança
**Cenário**: A — Greenfield (ainda sem deploy nem famílias de fora)
**Início**: 2026-10-25
**Última atualização**: 2026-10-25
**Controlador**: CSS Consultoria em Tecnologia da Informação Ltda. (PVRS Consultoria em TI), CNPJ
42.694.582/0001-01, ME no Simples Nacional — decidido em 2026-10-25 no lugar da pessoa física (não expõe CPF e
endereço residencial, emite nota da assinatura, separa o patrimônio; em troca, guarda o registro de acesso por 6
meses — Marco Civil Art. 15).
**Encarregado**: agente de tratamento de pequeno porte sem alto risco (Res. CD/ANPD 2/2022, Art. 11): sem
designação formal, com canal privacidade@familianca.com.br (caixa a criar — G04).

## Pipeline atual
- [x] F0 — Setup
- [x] F1 — Legal basis (`legal-basis.md`)
- [x] F2 — Data mapping (`data-map.md`)
- [x] F3 — Consent schema — **não se aplica**: nenhuma atividade usa consentimento. Só registrar a ciência da
      política (versão + data) no cadastro (G01).
- [ ] F4 — Privacy policy — draft pronto ⏸ **CHECKPOINT: revisão do conteúdo e jurídica**
- [ ] F5 — DSAR (exportar e excluir — G02)
- [ ] F6 — Audit logging (junto da pendência de auditoria de alterações — G11)
- [ ] F7 — Encryption keys (hoje: MFA AES-GCM, backup cifrado; disco/banco sem TDE)
- [~] F8 — Retention/erasure — backup 30 dias, convites 30 dias, registro de acesso 6 meses (2026-10-25); falta a
      rotação por tempo dos logs do Docker (G07)
- [ ] F9 — Incident response (G09)
- [ ] F10 — ROPA ⏸
- [ ] F11 — Encarregado (G04)
- [ ] F12 — RIPD — provavelmente N/A (sem alto risco)
- [ ] F13 — ECA Digital — provavelmente N/A (G12)
- [ ] F14 — Vendors + DPA + transferência (G05)
- [ ] F15 — Relatório final

## F1 — Legal basis ✓
- 8 atividades mapeadas
- 0 usam consentimento
- 2 usam legítimo interesse (A02 convite pendente, A04 segurança/logs) — LIA resumido no próprio arquivo
- 7 usam execução de contrato (A01–A08, exceto o convite pendente)

## F2 — Data mapping ✓
- 8 atividades inventariadas
- 0 com dado sensível por finalidade (A03 pode revelar por inferência no texto livre)
- 0 voltadas a menores (menores só citados em lançamentos — G12)
- 0 de alto risco → sem RIPD

## Artefatos gerados
- `.lgpd/legal-basis.md` — v1, 2026-10-25
- `.lgpd/data-map.md` — v1, 2026-10-25
- `.lgpd/gaps.md` — v1, 2026-10-25 (14 itens)
- `.lgpd/policies/privacy-policy-v1-draft.md` — draft, 2026-10-25

## Gaps abertos
Ver `.lgpd/gaps.md`.

## Próximo passo
Política v1 só com pendências de fato (vigência, logradouro, cláusulas da Cloudflare) — revisão jurídica.
Correções pequenas feitas em 2026-10-25 (G03, G06, G08; G07 e G14 em parte). Próximo: F5 (DSAR — exportar e
excluir conta/família).
