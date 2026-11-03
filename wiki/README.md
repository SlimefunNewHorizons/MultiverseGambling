# Wiki source · Fuente de la wiki

**English** · [Español](#español)

Every page of the project wiki lives here as plain Markdown, in both languages, so it is
versioned next to the code and read straight from the repository:

| Language | Pages |
|---|---|
| English | `Home.md`, `Installation.md`, `Commands.md`, `Permissions.md`, `Configuration.md`, `Casino-World.md`, `Languages.md`, `Games-Solo.md`, `Games-Group.md`, `Item-Bets.md`, `Poker.md`, `Fairness.md`, `Economy.md`, `Troubleshooting.md`, `Development.md`, `_Sidebar.md` |
| Español | `Home-es.md`, `Instalacion-es.md`, `Comandos-es.md`, `Permisos-es.md`, `Configuracion-es.md`, `Mundo-Casino-es.md`, `Idiomas-es.md`, `Juegos-Solo-es.md`, `Juegos-Grupo-es.md`, `Apuestas-Items-es.md`, `Poker-es.md`, `Justicia-es.md`, `Economia-es.md`, `Problemas-es.md`, `Desarrollo-es.md`, `_Sidebar-es.md` |

The filenames are exactly the page names GitHub wants: pushing this folder to the wiki
repository creates one page per file, and `_Sidebar.md` / `_Sidebar-es.md` become the
navigation sidebar. After a push, GitHub needs a wiki page rebuilt to refresh the sidebar:
open any page, edit it and save, or run `/wiki` equivalents if your tooling supports it.

## Making the GitHub wiki tab appear

The wiki of a repository is a separate git repository (`MultiverseGambling.wiki.git`) which
GitHub only creates once the feature is on **and** the first page exists:

1. Open the repository on GitHub → **Settings** → **Features** → tick **Wikis**.
2. Open the **Wiki** tab and click **Create the first page** (write anything, for example
   `Home`, and save). GitHub creates the wiki git repository.
3. Publish these files:

```bash
bash wiki/publish.sh
```

The script clones the wiki repository, copies every page (skipping this file), commits and
pushes. Run it again whenever you edit a page here.

## Where the links point

The README and the wiki pages link to each other by relative path (`wiki/Casino-World.md`),
so everything works from the repository view even before the wiki tab is enabled. Once the
wiki tab exists, the same pages are also reachable as
`https://github.com/SlimefunNewHorizons/MultiverseGambling/wiki/<Page>`.

## Language switch

Every page starts with a link to its counterpart (`English · Español`), and the sidebar lists
both language groups, so readers can jump between them from anywhere.

---

# Español

Cada página de la wiki del proyecto vive aquí como Markdown plano, en los dos idiomas, así
que queda versionada junto al código y se lee directamente desde el repositorio (ver la tabla
de arriba).

Los nombres de archivo son exactamente los nombres de página que quiere GitHub: subir esta
carpeta al repositorio de la wiki crea una página por archivo, y `_Sidebar.md` /
`_Sidebar-es.md` se convierten en la barra de navegación.

## Hacer que aparezca la pestaña Wiki de GitHub

La wiki de un repositorio es un repositorio git aparte (`MultiverseGambling.wiki.git`) que
GitHub solo crea cuando la función está activada **y** existe la primera página:

1. Abre el repositorio en GitHub → **Settings** → **Features** → marca **Wikis**.
2. Abre la pestaña **Wiki** y pulsa **Create the first page** (escribe cualquier cosa, por
   ejemplo `Home`, y guarda). GitHub crea el repositorio de la wiki.
3. Publica estos archivos:

```bash
bash wiki/publish.sh
```

El script clona el repositorio de la wiki, copia todas las páginas (excepto este archivo),
hace commit y las sube. Vuelve a ejecutarlo cada vez que edites una página aquí.

## A dónde apuntan los enlaces

El README y las páginas de la wiki se enlazan entre sí por ruta relativa
(`wiki/Mundo-Casino-es.md`), así que todo funciona desde la vista del repositorio incluso
antes de activar la pestaña. Cuando la pestaña exista, las mismas páginas también están en
`https://github.com/SlimefunNewHorizons/MultiverseGambling/wiki/<Pagina>`.

## Cambio de idioma

Cada página empieza con un enlace a su pareja (`English · Español`), y la barra lateral lista
los dos grupos de idioma, así que el lector salta de uno a otro desde cualquier sitio.
