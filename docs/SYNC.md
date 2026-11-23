# Sync do vault: Syncthing num VPS + backup no Storage Box

Guia para trocar o Remotely Save por um sync que funciona com o Obsidian fechado e que
envia as mudanças para os outros dispositivos em segundos.

## Por que trocar

- **Plugins só rodam com o Obsidian aberto.** No Android, quando o app vai para segundo plano,
  o sistema congela o Obsidian e o Remotely Save para. Nenhuma configuração muda isso.
- **Seu Remotely Save está sem sync periódico.** `autoRunEveryMilliseconds` é `-1`: ele só
  sincroniza ao abrir o app e depois de salvar. Por isso o que muda no PC não chega no celular.
- **O Storage Box não avisa ninguém.** Ele só guarda arquivos (WebDAV/SFTP) e não roda
  programas, então não pode ser um nó do Syncthing nem empurrar mudanças.

## Arquitetura

```
 celular (Syncthing-Fork) ─┐
                           ├──▶ VPS Hetzner (Syncthing, sempre ligado) 
 PC (Syncthing) ───────────┘
```

- O **VPS** é o ponto de encontro sempre ligado. Celular e PC sincronizam com ele mesmo quando
  o outro está desligado, e também direto entre si quando estão na mesma rede.
- O **Syncthing** observa a pasta do vault: uma mudança chega nos outros dispositivos em segundos,
  nos dois sentidos, sem o Obsidian aberto.



## 1. Instalar o Syncthing no VPS

```bash
ssh root@IP_DO_VPS
```

Dentro do VPS:

```bash
apt update && apt install -y syncthing unattended-upgrades
```

```bash
adduser --disabled-password --gecos "" sync
```

```bash
systemctl enable --now syncthing@sync
```

Para abrir a interface, crie um túnel a partir do PC e acesse `http://127.0.0.1:8385`:

```bash
ssh -L 8385:127.0.0.1:8384 root@IP_DO_VPS
```

Na interface: *Ações → Configurações → GUI*, defina usuário e senha.

## 3. Instalar no PC e no celular

**PC:**

```bash
sudo apt install syncthing
```

```bash
systemctl --user enable --now syncthing
```

Interface em `http://127.0.0.1:8384`.

**Celular:** instale o **Syncthing-Fork** (F-Droid ou Play Store, desenvolvedor *Catfriend1*).
O app original "Syncthing" para Android foi descontinuado. Depois:

- Conceda **acesso a todos os arquivos**.
- **Desative a otimização de bateria** do Syncthing-Fork (Configurações do Android → Apps →
  Syncthing-Fork → Bateria → Sem restrições). Sem isso o Android pode pausar o serviço.
- *Configurações → Condições de execução*: "Iniciar no boot" e, se quiser economizar dados,
  "Executar apenas em Wi‑Fi".

## 4. Migrar sem conflitos

A ordem importa, para os dois syncs não brigarem:

1. **Sincronize uma última vez pelo Remotely Save** no PC e no celular, para os dois vaults
   ficarem iguais.
2. **Desative o Remotely Save** no PC e no celular (Configurações → Plugins da comunidade).
3. Crie o `~/obsidian/.stignore` no PC (conteúdo abaixo) **antes** de compartilhar a pasta.
4. Troque os **IDs de dispositivo** (*Ações → Mostrar ID*; no celular há QR code):
   - no VPS, adicione o PC e o celular;
   - no PC e no celular, adicione o VPS. Em *Avançado → Endereços*, coloque
     `tcp://IP_DO_VPS:22000` para a conexão ir direto, sem depender de descoberta.
5. No **PC**, adicione a pasta `~/obsidian`:
   - ID da pasta: `obsidian` (o mesmo em todos)
   - Tipo: *Enviar e receber*
   - Compartilhar com: VPS e celular
   - *Observar mudanças* ligado (padrão)
6. Aceite a pasta no **VPS** (caminho `/home/sync/obsidian`) e espere sincronizar tudo.
   Ligue *Versionamento escalonado* no VPS, para guardar versões antigas.
7. Aceite a pasta no **celular** apontando para o vault que já existe
   (normalmente `/storage/emulated/0/Documents/<nome do vault>`). Como os arquivos são iguais,
   o Syncthing só confirma; nada é duplicado.
8. Repita o `.stignore` no VPS e no celular (pelo editor de ignores da interface), porque ele
   não é sincronizado.

### `.stignore`

```
// Estado da interface, diferente em cada dispositivo
.obsidian/workspace.json
.obsidian/workspace-mobile.json
// Configuração do Remotely Save (tem credenciais do Storage Box)
.obsidian/plugins/remotely-save
// Lixeira local do Obsidian
.trash
(?d).DS_Store
```

O resto de `.obsidian` (plugins, temas, `app.json`) passa a ser sincronizado. Assim
configurações como esconder o frontmatter valem nos dois dispositivos.

## Os widgets com Syncthing

O app de widgets não precisa de configuração extra. Quando o Syncthing grava um arquivo no
vault, o Android avisa o app (pelo MediaStore, mesmo com o app fechado) e os widgets
atualizam em poucos segundos. Testado no emulador: arquivo criado, editado e apagado por fora
aparece no widget em ~5 s, inclusive com o processo do app morto.

Limitação: mudanças só dentro de `.obsidian` (trocar o tema, abrir outra nota no modo
"Seguir o Obsidian") não geram esse aviso, porque o Android não indexa pastas ocultas. Elas
aparecem na próxima mudança de qualquer nota, no botão ↻ ou em até 15 minutos.

## Aviso de "desatualizado"

O Syncthing-Fork mantém uma notificação com o estado (sincronizado, sincronizando, pausado por
condição de execução, sem conexão). Fora do Wi‑Fi com "apenas Wi‑Fi" ligado, ela mostra que está
pausado: esse é o aviso de vault desatualizado. Como o VPS tem IP público, no 4G o celular
também alcança o VPS se você permitir dados móveis.

## Conflitos

Se a mesma nota for editada em dois dispositivos antes de sincronizar, o Syncthing mantém as
duas versões: a mais nova fica com o nome normal e a outra vira
`nota.sync-conflict-AAAAMMDD-HHMMSS-XXXXXXX.md`, ao lado. Com o sync em segundos isso é raro.

## Outras melhorias da sua nota

