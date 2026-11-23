# Installation guide

## Requirements

- Android 8.0 or newer.
- An Obsidian vault stored as a folder on the device, for example
  `Documents/My Vault`. Vaults created in Obsidian with *"On this device"* already are.

## 1. Install the app

1. On your phone, download the APK:
   **[widgets-for-obsidian.apk](https://github.com/j4marcos/widgets-for-obsidian-mobile/releases/latest/download/widgets-for-obsidian.apk)**.
2. Open the downloaded file. The app is installed from outside the Play Store, so Android asks you to
   allow it: tap **Settings**, turn on **Allow from this source** for your browser or file manager,
   then go back and tap **Install**.
3. If Google Play Protect warns that the app is unknown, tap **More details → Install anyway**.

To update later, install the new APK over the old one. Your settings and widgets are kept.

## 2. Choose your vault

1. Open **Obsidian Widgets**.
2. Tap **Choose vault folder**.
3. Browse to your vault's folder (the one that contains `.obsidian`) and tap **Use this folder**,
   then **Allow**.

The app lists your notes and bases. It reads files only from the folder you picked.

## 3. Add widgets

Long-press an empty spot on the home screen, tap **Widgets**, find **Obsidian Widgets** and add one:

| Widget | Setup |
|---|---|
| **Tasks (base)** | Pick a `.base` file, then a view, then the options: group by project, hide completed tasks, and layout (list, table or cards). |
| **Note** | Pick a note, or **Follow Obsidian** to always show the note currently open in Obsidian. |
| **New note** | No setup. Notes go to the `inbox/` folder, which is created if missing. |

You can resize every widget. To change a widget's settings later, long-press it and tap the pencil
icon, or tap the note widget's title.

## 4. Using the widgets

**Tasks (base)**

- Tap a **checkbox** to complete a task. The app sets `status: done` in the note's frontmatter and
  leaves the rest of the file untouched. Tap it again to reopen the task.
- Tap a **task** to open it in Obsidian.
- Tap a **section header** to collapse or expand it.
- Tap the **widget title** to switch to the base's next view.
- Tap **+** to add a task. Type the title, then optionally set a due date, priority and project.
  Press Enter or tap the send button. The **↗** button creates the task and opens it in Obsidian.

New tasks follow the format of your existing ones: a `PREFIX-n` file name taken from the project
(for example `APP-7`) and the same frontmatter fields.

**Note**

- Tap a **checkbox** to tick it in the file.
- Tap any **text** to edit the note in Obsidian.

## Supported bases

The tasks widget reads standard Obsidian Bases files. It supports:

- **Views:** table, list and cards, plus the *board*, *table* and *gallery* views of the Power Bases
  plugin. Calendar, chart and timeline views are not available in the widget.
- **Filters:** `and` / `or` / `not`, comparisons, `file.inFolder()`, `file.hasTag()`,
  `file.hasLink()`, `if()` formulas, sorting and grouping.

Tasks are expected to have the properties `title`, `status` (`todo`, `doing`, `blocked`, `done`),
`priority` (`high`, `medium`, `low`), `project` and `due`. Missing properties are simply left out.

## Keeping widgets up to date

Widgets refresh automatically a few seconds after a file in the vault changes, including changes
made by Obsidian or by a sync app, and even when Obsidian Widgets is closed. You can also tap
**↻** on any widget.

Changes that only touch the hidden `.obsidian` folder, such as switching the theme or opening
another note in *Follow Obsidian* mode, show up on the next note change, on **↻**, or within
15 minutes. Android does not report changes inside hidden folders.

**Sync:** Obsidian plugins (such as Remotely Save) only run while Obsidian is open. If you edit the
vault on several devices, use a sync app that runs in the background, such as
[Syncthing](https://syncthing.net), so that the files and the widgets stay current.

## Troubleshooting

| Problem | Fix |
|---|---|
| A widget says *"Open the app and pick your vault"* | Open the app and choose the vault folder. |
| The app says access to the vault was lost | Tap **Change vault** and pick the same folder again. |
| No bases appear when adding a tasks widget | Open the app and tap **Rescan now**. |
| Tapping a task does nothing | Make sure Obsidian is installed and the vault is open in it. The vault name must match the folder name. |
| The update fails with *"package conflicts"* | The new APK was signed with a different key. Uninstall the old version first, then re-add the widgets. |

## Uninstall

Long-press the app icon → **App info** → **Uninstall**. Your vault is not changed.
