# Comandos

[English](Commands) · **Español**

El comando es `/mvgam` y **no tiene alias**: cada subcomando tiene una sola forma. Todo tiene
autocompletado, y cada respuesta se escribe en el idioma de quien ejecutó el comando.

Todos los identificadores que el plugin añade al servidor llevan el prefijo `mvgam_`, así que
nada puede chocar con otro plugin: los permisos `mvgam_play`, `mvgam_top` y `mvgam_admin`, y
el mundo casino `mvgam_casino`.

## Jugar

| Comando | Qué hace |
|---|---|
| `/mvgam` | Abre el menú principal: pestaña de solitario y pestaña de grupo |
| `/mvgam menu` | Igual que el anterior |
| `/mvgam games [solo\|group]` | Lista el catálogo con los límites de apuesta de cada juego |
| `/mvgam play <id>` | Juega por su id exacto (`roulette`, `slots`, `lucky-wheel`...) |
| `/mvgam play <id> house` | Juega un juego de grupo directamente contra la casa (lo que hace el botón del chat) |
| `/mvgam action <accion>` | Punto de entrada de los **botones del chat** (`shoot`, `reveal`, `horse 3`, `accept`, `decline`) |

Dentro de cada juego hay menús propios: el selector de apuesta permite mitad, doblar o
apostarlo todo, y los paneles de juego tienen retirarse, plantarse, doblar o disparar.

## Tu cuenta

| Comando | Qué hace |
|---|---|
| `/mvgam balance [jugador]` | Muestra tu saldo, o el de otro |
| `/mvgam stats [jugador]` | Partidas, victorias, apostado, retorno real y juego favorito |
| `/mvgam top [profit\|wagered\|prize]` | Ranking por beneficio neto, volumen o mayor premio |

`/mvgam top` necesita `mvgam_top`, que todos tienen por defecto.

## Azar verificable

| Comando | Qué hace |
|---|---|
| `/mvgam verify` | Muestra el hash del secreto actual, el secreto anterior y tu semilla |
| `/mvgam verify <texto>` | Cambia tu semilla de cliente a ese texto |

La explicación completa está en [Azar verificable](Justicia-es).

## Mundo casino

| Comando | Qué hace |
|---|---|
| `/mvgam world` | Te lleva al mundo casino (avisa con claridad si no está listo) |
| `/mvgam world build` | Reconstruye la plaza, las carreteras y todas las arenas (**`mvgam_admin`**) |
| `/mvgam world info` | Informa del estado del mundo: si existe, terreno, tamaño, suelo, arenas construidas y qué falta (**`mvgam_admin`**) |

## Idiomas

| Comando | Qué hace |
|---|---|
| `/mvgam language` | Muestra tu idioma y los disponibles |
| `/mvgam language <codigo>` | Cambia a ese idioma (`en`, `es`, `ES`, `es-AR`, `spanish`, `español`...) |
| `/mvgam language reset` | Olvida tu elección y vuelve a seguir tu cliente de Minecraft |


## Administración

| Comando | Qué hace |
|---|---|
| `/mvgam info` | Economía activa, proveedor, juegos registrados y secreto actual |
| `/mvgam reload` | Recarga `config.yml` y todos los `lang/*.yml` sin tocar las partidas |
| `/mvgam give <jugador> <cantidad>` | Añade saldo |
| `/mvgam take <jugador> <cantidad>` | Quita saldo |
| `/mvgam set <jugador> <cantidad>` | Fija el saldo a un valor exacto |
| `/mvgam cancel <jugador>` | Cierra la partida de alguien y devuelve su dinero |

Las cantidades aceptan coma o punto: `1000`, `1000.50`, `1000,50`.

Todos necesitan `mvgam_admin`, que los operadores tienen. Ver [Permisos](Permisos-es).

## Argumentos, no alias

Algunos subcomandos aceptan una palabra para elegir el modo. Son argumentos, no alias, así que
cada comando se escribe de una sola forma:

| Se escribe | Significa |
|---|---|
| `/mvgam games solo` / `/mvgam games group` | Filtrar el catálogo por categoría |
| `/mvgam top profit` | Ranking por beneficio neto (por defecto) |
| `/mvgam top wagered` | Ranking por volumen |
| `/mvgam top prize` | Ranking por mayor premio |
| `/mvgam verify <texto>` | Fija tu semilla de cliente a ese texto |
| `/mvgam language es` / `<codigo>` / `reset` | Elige idioma, o vuelve a automático |
| `/mvgam world build` | Reconstruye el mundo casino (admin) |
| `/mvgam world info` | Informa de qué le falta al mundo casino (admin) |
| `/mvgam action house` | Se enfrenta a la casa cuando estás solo en una sala de grupo |

## Ejemplos

```
/mvgam play crash            → abrir crash y elegir apuesta
/mvgam play race             → entrar en la siguiente ronda de carrera
/mvgam action shoot          → lo usa el botón de la ruleta rusa
/mvgam top prize             → ranking de mayores premios
/mvgam language es           → todo lo que leas pasa a español
/mvgam world build           → reconstruir el mundo casino desde cero
/mvgam world info            → revisar qué le falta al mundo casino (funciona en consola)
```

`play` solo acepta los ids exactos que lista `/mvgam games`: el id de la carrera es `race`, no
`horse-race`.
