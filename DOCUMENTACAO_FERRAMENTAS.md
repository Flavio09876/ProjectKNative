# Documentação de ferramentas e dependências

Este registro existe para deixar claro tudo o que foi adicionado ao projeto Project K Native
para a construção do APK, por que cada item existe e qual foi o raciocínio usado.
Toda a descrição está em português brasileiro, conforme solicitado.

## 1. Nenhuma ferramenta de sistema foi instalada no celular

Até o momento não foi instalado nenhum programa novo dentro do Termux ou do Android
fora do próprio APK gerado. O fluxo de instalação continua usando `termux-share`,
porque o `pm install` direto é bloqueado pelo contexto/SELinux do dispositivo.
O APK é baixado da release `apk-latest`, aberto com `termux-share` e confirmado
pela interface do Android.

## 2. Dependência Gradle `androidx.core:core-ktx:1.13.1`

### O que é

É a biblioteca oficial AndroidX Core, adicionada em
`app/build.gradle.kts`. Ela dá acesso a APIs compatíveis com versões antigas
do Android, mantendo o `minSdk` em 23.

### Por que existe

As fontes Montserrat foram portadas do APK do Project Z para
`app/src/main/res/font/`. Para carregar uma fonte de `res/font` em qualquer
versão a partir da 23, precisamos de `ResourcesCompat.getFont(...)`.
O método nativo `Resources.getFont(...)` só existe a partir da API 26 e
quebraria o Galaxy A03 em alguns caminhos. Por isso a dependência foi
declarada explicitamente, em vez de depender de uma versão transitiva
acidental trazida por outra biblioteca.

### Raciocínio

- O app precisa das fontes do Z para manter a identidade visual das telas.
- O suporte precisa cobrir Android 6.0 (API 23) em diante.
- Declarar a dependência diretamente evita erro de compilação
  (`Unresolved reference 'ResourcesCompat'`) e torna o build reproduzível.

## 3. Fontes Montserrat portadas do Project Z

### O que são

Arquivos copiados de `z-patch/res/font/`:

- `montserrat_regular.ttf`
- `montserrat_medium.ttf`
- `montserrat_semi_bold.ttf`
- `montserrat_bold.ttf`

### Por que existem

São as fontes usadas pelo Project Z. Elas foram trazidas para o Project K
Native para que textos, botões, cabeçalhos e navegação tenham o mesmo acabamento
tipográfico do aplicativo de referência.

### Raciocínio

- O usuário autorizou explicitamente usar código e assets do Project Z.
- Não queremos WebView de tela; tipografia nativa é necessária para telas próprias.
- `KUi.init()` carrega as fontes uma única vez e aplica via `KUi.bodyTypeface()`
  e `KUi.displayTypeface()`.

## 4. Assets visuais portados do Project Z

### O que são

Imagens copiadas de `z-patch/res/drawable-xxhdpi/` para
`app/src/main/res/drawable-xxhdpi/`:

- `splash_icon.png` e `splash_text.png` — usados na tela de carregamento.
- `ic_tab_discover.png`, `ic_tab_discover_selected.png`,
  `ic_tab_circle.png`, `ic_tab_circle_selected.png`,
  `ic_tab_chat.png`, `ic_tab_chat_selected.png` — usados na barra inferior.
- `null_user_icon.png` e `null_user_icon_big.png` — fallback de avatar.
- `bg_home_header.png`, `bg_home_section.png`, `z_card_bg.png`,
  `purple_card_bg.9.png` — reservados para composição de cards e cabeçalhos.

### Por que existem

São elementos do Project Z que ajudam a manter continuidade visual entre o
aplicativo antigo e o novo, sem recorrer a WebView nem a elementos genéricos
inventados.

### Raciocínio

- `splash_*` dá identidade imediata ao abrir o app.
- Os ícones de aba trazem o padrão visual do Z para a navegação nativa.
- `null_user_icon_big` substitui a letra inicial quando o usuário não tem avatar.
- Os fundos ficam disponíveis para uso gradual nos cards, sem alterar a estrutura
  das telas de uma vez.

## 5. Ajuste no GitHub Actions

### O que mudou

Em `.github/workflows/build-apk.yml`:

- `concurrency` com `cancel-in-progress: true` para não rodar builds duplicados.
- Espera pela remoção da tag `apk-latest` antes de recriá-la.
- Tentativas de publicação da release.

### Por que existe

O mesmo commit dispara o workflow por `push` e por `workflow_dispatch`.
Isso gerou duas execuções ao mesmo tempo e uma falha `422: Release.tag_name
already exists`. A espera pela tag e as tentativas eliminam essa corrida.

### Raciocínio

- A URL de download precisa continuar estável (`apk-latest`).
- Não podemos trocar o nome da tag sem invalidar o fluxo do usuário.
- Publicar de forma confiável é parte da entrega do APK.

## 6. Scripts locais de automação

### O que são

`run_level2_ssh.py` e os scripts `.sh` temporários em
`C:\Users\Marrie\AppData\Local\Temp\opencode\` servem para:

- enviar arquivos por SCP para o repositório no aparelho;
- commitar e dar push no GitHub a partir do Termux;
- disparar e acompanhar o workflow;
- baixar e abrir o APK usando `termux-share`;
- ler os arquivos de diagnóstico `projectk_*.txt` em `/sdcard/Download`.

### Por que existem

Não há Gradle instalado no Windows e o `pm install` é bloqueado no aparelho.
O caminho viável é: editar localmente → SCP → CI no GitHub → release →
`termux-share` no Galaxy A03.

### Raciocínio

- Reduz erro humano em uma sequência longa de passos.
- Mantém o histórico de builds e hashes para comparação.
- Permite diagnóstico sem depender de permissões de `dumpsys`/`logcat` de outros apps.

---

Se uma nova ferramenta ou dependência for adicionada, este arquivo deve ser
atualizado no mesmo commit, explicando o que é, por que existe e o raciocínio
em português brasileiro.
