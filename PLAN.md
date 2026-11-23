# Plano — Widgets for Obsidian (Android)

App Android minimalista em Kotlin que oferece widgets de tela inicial para um vault do Obsidian.

## Widgets

1. **Widget de tasks (base)** — uma instância por base de tasks:
   - `UFPB/tasks/tasks.base`
   - `projects/tasks/tasks.base`
2. **Widget de nota** — uma janela editável para qualquer nota do vault.

A captura rápida deixou de ser um widget separado e virou o botão **+** do widget de tasks.

## Stack

- Kotlin, **Jetpack Glance** (widgets), **Jetpack Compose** (telas e popups)
- Acesso ao vault via **SAF** (permissão persistente na pasta do vault)
- Markdown: `commonmark-java` + extensões; YAML: `snakeyaml-engine`
- **Room** como índice do vault; **WorkManager** para reindexar a cada 15 min
- minSdk 26, targetSdk/compileSdk 36, Gradle 9.1, AGP 8.13, Kotlin 2.2

## O que as bases reais usam

As duas `tasks.base` são quase idênticas:

- **Filtros**: `file.inFolder("…")`, `type == "Task"`, `status != "done"`, `file.hasLink("siryus")`
- **Fórmula**: `prio_rank: if(priority == "high", 1, if(priority == "medium", 2, if(priority == "low", 3, 4)))`
- **Ordenação**: por `formula.prio_rank` e `due`, ascendente
- **Views**: quase todas são do plugin **Power Bases** (`powerbases-board`, `-table`,
  `-calendar`, `-chart`, `-timeline`) com chaves próprias: `pbGroup: note.status`,
  `pbRows: note.project`, `cardProps`.
- **Propriedades das tasks**: `type: Task`, `title`, `status` (todo/doing/blocked/done),
  `priority` (high/medium/low), `project` (wikilink), `owner`, `start`, `due`, `depends_on`, `tags`.
  Nome do arquivo = ID com prefixo (`SRY-12`, `BD-3`, `PDI-4`, `VIAGEM-1`).

Por isso o avaliador de expressões só precisa, no início, de: `==`, `!=`, `and`/`or`/`not`,
`file.inFolder()`, `file.hasLink()`, `file.hasTag()`, `if()`, literais de string/número
e acesso a propriedades (`priority`, `note.status`, `formula.x`).

## Widget de tasks

Visual inspirado no widget do TickTick: fundo escuro translúcido, cantos arredondados,
lista agrupada com cabeçalhos.

- **Cabeçalho**: nome da base + nome da view (toque troca a view), botão **+**, botão de refresh.
- **Views suportadas no widget**:
  - `powerbases-board` e `table` → **lista agrupada** pelo `pbGroup` (ex.: status, prioridade).
    `pbRows` vira um subgrupo.
  - `powerbases-table` / `table` → **tabela** compacta (primeiras colunas da `order`).
  - **Cards** → grade com título + 1–2 propriedades.
  - Calendário, gráfico e timeline ficam fora do widget; a view é ignorada ou cai no formato lista.
- **Item**: checkbox colorido pela `priority` (high = vermelho, medium = laranja,
  low = azul), `title` sem o prefixo do ID, `due` à direita (vermelho se atrasado),
  nome do projeto em cinza.
- **Interações**:
  - Toque no checkbox → grava `status: done` (ou volta para `todo`) no frontmatter da nota.
    A edição troca só a linha `status:`, sem reserializar o YAML, para não bagunçar a formatação.
  - Toque no texto → abre a nota no Obsidian (`obsidian://open?vault=…&file=…`).
  - **+** → popup de nova task (ver abaixo).
- **Configuração**: escolher o `.base` e a view inicial ao adicionar o widget.

### Popup de nova task (botão +)

Activity com tema de diálogo translúcido, sem abrir o Obsidian.

- Campos: título, projeto (valores já usados na base), prioridade, `due` opcional.
- Cria `<pasta da base>/<PREFIXO>-<n+1>.md`. O prefixo vem das tasks existentes do mesmo
  projeto (ex.: projeto siryus → `SRY`), e `n` é o maior número já usado com esse prefixo.
- Frontmatter: `type: Task`, `title: "<ID> — <título>"`, `status: todo`, `priority`,
  `project`, `owner`, `start: <hoje>`, `due`, `depends_on: []`, `tags: [task]`.
- A pasta de destino vem do `file.inFolder(...)` do filtro da base.

## Widget de nota (janela editável)

Widgets do Android não aceitam campo de texto, então "editar pelo widget" funciona assim:

- **Widget** mostra a nota renderizada (títulos, listas, checkboxes, callouts, wikilinks,
  tags) numa lista com rolagem.
  - Toque num checkbox `- [ ]` → alterna direto no arquivo, sem abrir nada.
  - Toque no corpo → abre o **editor em popup**: janela flutuante sobre a tela inicial,
    com o markdown cru editável e salvamento automático no vault. Ao fechar, o widget atualiza.
  - Toque no título → trocar de nota (busca no índice).
- **Origem da nota** (escolhida na configuração):
  - **Nota fixa** — qualquer nota do vault.
  - **Seguir o Obsidian** — mostra a última nota aberta no Obsidian, lida de
    `lastOpenFiles` em `.obsidian/workspace-mobile.json` (ou `workspace.json`).
- Tema: cores do tema Minimal (que o vault usa), claro/escuro.

## Fases

1. ~~Fundação: projeto Gradle, seleção do vault (SAF), Indexer + Room, tela de configurações~~ ✅
2. ~~Widget de tasks (lista agrupada), parser de `.base`, avaliador, QueryEngine, concluir tarefa~~ ✅
3. ~~Popup de nova task (botão +), com próximo ID por projeto~~ ✅
4. ~~Widget de nota: renderização, checkboxes, editor em popup, modo "seguir o Obsidian"~~ ✅
5. ~~Tabela e cards no widget de tasks, tamanhos responsivos, tema claro/escuro~~ ✅
6. ~~Melhorias da nota "melhorias pro obsidian"~~ ✅
   - Popup de criação no estilo do TickTick (prazo, prioridade, destino, voz, ↗ abrir no Obsidian)
   - Widget "Nova nota" (captura rápida em `/inbox`)
   - Seções por projeto colapsáveis, esconder concluídas, tema do Obsidian (`appearance.json`)
   - Widget de nota abre o Obsidian para editar (o editor em popup foi removido)
   - Sem reindexação manual (ver abaixo)
   - Guia de sync com Syncthing: [docs/SYNC.md](docs/SYNC.md)

Tudo testado num emulador Android 15 com o Obsidian 1.15.1 e um vault demo.

## Notas de implementação

- **Widget de tasks** usa Jetpack Glance.
- **Widget de nota** usa RemoteViews tradicional (`AppWidgetProvider` + `ListView`), não Glance:
  - Texto formatado (negrito, links, destaques) precisa de `TextView` com spans, que só entra no
    Glance via `AndroidRemoteViews`.
  - Itens de `LazyColumn` com `AndroidRemoteViews` perdem os cliques.
  - Uma `ListView` dentro de `AndroidRemoteViews` é recusada pelo launcher
    ("setRemoteAdapter can only be used for AppWidgets").
  - Cliques na lista vão por fill-in intents para `NoteActionActivity`, uma activity invisível.
- **Grid de cards** do Glance dimensiona cada linha por uma célula só, então os cards têm altura fixa.
- **Leitura sem reindexar**:
  - Antes de cada render, o widget de tasks reescaneia (incrementalmente, por mtime) só a pasta
    da base e a pasta do `file.inFolder(...)` do filtro.
  - `VaultWatchJob` (JobScheduler com *content URI trigger* no MediaStore) acorda o app quando
    qualquer arquivo do armazenamento muda, mesmo com o processo morto, e redesenha os widgets
    (~5 s). É um `JobService` próprio porque o WorkManager, ao recriar o processo, reagenda os
    jobs e perde o disparo que o acordou.
  - O MediaStore não indexa pastas ocultas: mudanças só em `.obsidian` (tema, nota aberta no
    modo "Seguir o Obsidian") aparecem na próxima mudança de nota, no ↻ ou em até 15 min.

## Ainda não feito

- Imagens embutidas (`![[img.png]]`) aparecem como link, sem a imagem.
- Fórmulas além de `if()` e comparações simples; views de calendário, gráfico e timeline.
- Desmarcar uma task no widget de tasks volta para `todo`, não para o status anterior.

## Riscos

- **Sincronização**: com o Remotely Save, edições feitas pelo widget só sincronizam quando o
  Obsidian rodar de novo. Com o Syncthing ([docs/SYNC.md](docs/SYNC.md)) elas sincronizam na hora;
  editar a mesma nota em dois lugares ao mesmo tempo gera um arquivo `.sync-conflict`.
- **Power Bases**: as chaves `pb*` são de um plugin de terceiros e podem mudar; o widget
  lê só `pbGroup`, `pbRows` e `order`, e ignora o resto.
- SAF lento em vaults grandes → indexação incremental por mtime (já feita na fase 1).
- Glance/RemoteViews limitam a fidelidade visual (sem CSS nem temas da comunidade).
