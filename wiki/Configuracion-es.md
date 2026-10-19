# Configuración

[English](Configuration) · **Español**

Todo está en `plugins/MultiverseGambling/config.yml`. El archivo se escribe en inglés y
comentado; esta página es la referencia. Después de editar ejecuta `/mvgam reload`: las
partidas en curso no se tocan y los valores nuevos se aplican en la siguiente ronda.

## `economy`

| Clave | Por defecto | Significado |
|---|---|---|
| `provider` | `auto` | Con qué dinero juega el casino: `auto` (prueba los motores de abajo en orden), `sbank` (cuentas bancarias), `vault` (la economía Vault) o `internal` (solo el monedero del plugin). Si un motor falta, cae al siguiente |
| `auto-order` | `[sbank, vault, internal]` | Motores que se prueban cuando `provider` es `auto`. Cámbialo a `[vault, sbank, internal]` para jugar con el monedero en vez de con el banco |
| `currency` | `coins` | Nombre que muestra el placeholder `{currency}` |
| `format` | `&6{amount} &7{currency}` | Cómo se imprime cualquier cantidad |
| `starting-balance` | `1000` | Saldo de bienvenida del monedero interno (se ignora con sBank o Vault) |

## `game`

| Clave | Por defecto | Significado |
|---|---|---|
| `house-edge` | `0.02` | Ventaja media (2%). Los juegos cuya ventaja la fija la propia rueda, como la ruleta, la ignoran |
| `min-bet` | `10` | Apuesta mínima permitida |
| `max-bet` | `100000` | Apuesta máxima permitida |
| `announce-wins` | `true` | Anunciar los premios grandes al servidor |
| `announce-threshold` | `50000` | Solo se anuncian los premios que pagan al menos esta cantidad |

Por juego puedes sobrescribir los límites con `games.<id>.min-bet` y `games.<id>.max-bet`, y
desactivar un juego con `games.<id>.enabled: false`. Un juego desactivado desaparece de los
menús y del catálogo, y el mundo casino se construye sin su arena.

## `fairness`

| Clave | Por defecto | Significado |
|---|---|---|
| `provably-fair` | `true` | Publica el hash del secreto y deja que cualquiera audite cada tirada. Muy recomendable dejarlo activo |

## `data`

| Clave | Por defecto | Significado |
|---|---|---|
| `save-every-minutes` | `5` | Cada cuánto se guardan saldos, estadísticas y semillas |

## `language`

| Clave | Por defecto | Significado |
|---|---|---|
| `default` | `en` | Idioma de la consola, de los jugadores sin preferencia y de los carteles del mundo |
| `follow-client` | `true` | Quien nunca eligió idioma sigue el de su cliente de Minecraft cuando el plugin lo tiene |

Ver [Idiomas](Idiomas-es).

## `item-bets`

| Clave | Por defecto | Significado |
|---|---|---|
| `enabled` | `true` | Permite que los seis juegos compatibles acepten ítems como apuesta |
| `max-items` | `1728` | Máximo de ítems en una sola apuesta |
| `blocked` | `*SHULKER_BOX`, `*BUNDLE` | Ítems que nunca se pueden apostar: nombre exacto, `*SUFIJO` o `PREFIJO*` |

Ver [Apuestas con ítems](Apuestas-Items-es).

## `world`

| Clave | Por defecto | Significado |
|---|---|---|
| `enabled` | `true` | Crea (o carga) el mundo casino al arrancar |
| `name` | `mvgam_casino` | Nombre de la carpeta del mundo. Apúntalo a un mundo existente para reutilizarlo |
| `size` | `500` | Lado del mundo cuadrado, en bloques (200-2000). Se agranda solo si la distribución no cabe |
| `build-structures` | `true` | Construye la plaza, las carreteras y las arenas la primera vez que se usa el mundo |
| `teleport-on-join` | `false` | Manda a los jugadores al mundo casino cuando entran al servidor |

Ver [Mundo](Mundo-Casino-es).

## `group`

Compartido por los nueve juegos en grupo.

| Clave | Por defecto | Significado |
|---|---|---|
| `betting-seconds` | `20` | Cuánto queda abierta la ventana de apuestas |
| `max-players` | `24` | Límite de asientos por ronda |
| `countdown` | `5` | Cuenta atrás antes de empezar la ronda |
| `house-commission` | `0.0` | Lo que se queda la casa del bote, la rifa y el póker de dados |

Por juego:

| Clave | Por defecto | Significado |
|---|---|---|
| `group.bomb-board.tiles` | `36` | Casillas del tablero |
| `group.bomb-board.bombs` | `4` | Bombas escondidas |
| `group.bomb-board.seconds-per-turn` | `10` | Tiempo para destapar |
| `group.hot-bomb.min-seconds` / `max-seconds` | `5` / `30` | Rango de la mecha sorteada |
| `group.russian-roulette.chambers` / `bullets` | `6` / `1` | Configuración del revólver |
| `group.russian-roulette.extra-bullet-every-laps` | `3` | Una bala más cada tantas vueltas a la mesa (`0` = nunca) |
| `group.race.horses` / `steps` | `8` / `60` | Caballos de la carrera y pasos por caballo |
| `group.raffle.ticket-price` / `max-tickets` | `100` / `20` | Precio de la boleta y tope por jugador |
| `group.duel.accept-seconds` | `30` | Tiempo para aceptar un reto antes de que caduque |

## `games`

Un bloque por juego, todos con `enabled` y los límites de apuesta compartidos.

| Juego | Claves extra |
|---|---|
| `roulette` | `american` (añade el 00 y baja el retorno), `spin-ticks` (duración de la animación) |
| `slots` | `spin-ticks` |
| `crash` | `double-every-seconds`, `max-multiplier` |
| `mines` | `tiles`, `default-mines`, `max-mines` |
| `towers` | `levels`, `tiles`, `bombs` |
| `blackjack` | `decks`, `dealer-hits-soft-17` |
| `high-low` | `max-steps` |
| `plinko` | `rows`, `max-multiplier` (tope de seguridad) |
| `scratch` | `picks` |
| `lucky-wheel` | `segments` (multiplicador por segmento, todos igual de probables) |
| `dice`, `coin-flip`, `color-roulette`, `jackpot`, `hot-bomb`, `bomb-board`, `russian-roulette`, `race`, `duel`, `raffle`, `dice-poker` | solo `enabled` y los límites de apuesta |

## Cambiar una tabla de premios

El retorno de tragaperras, plinko, rasca y ruleta de la suerte está fijado por tests
automáticos. Si cambias pesos, pagos, segmentos o filas, ejecuta la suite:

```bash
mvn test
```

Si una tabla ahora devuelve más de lo que cobra, la compilación falla y te dice cuál. Es
deliberado: un retorno del 120% por descuido en un servidor público es dinero impreso de la
nada.
