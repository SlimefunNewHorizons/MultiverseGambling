# Juegos en grupo

[English](Games-Group) · **Español**

Nueve juegos con **rondas automáticas**. Nadie tiene que iniciar nada: un jugador entra con
`/mvgam play <id>` (o pulsando el tablero de bombas en su arena), apuesta durante la ventana
y la ronda se juega sola. Cuando termina, la
siguiente ventana de apuestas se abre por su cuenta para quien quiera entrar otra vez.

El ciclo compartido es: **esperando jugadores → ventana de apuestas → en juego → reparto**.

Cuando una ronda necesita una elección — apostar a un color o a un caballo — la puedes hacer
en el menú **o** con los botones que el lobby manda al chat, así nadie tiene que dejar un menú
abierto mientras mira el espectáculo. La ruleta de colores también acepta
`/mvgam action color red`, `black` y `green`, y se puede cambiar la elección hasta que gire
la ruleta.

| Juego | Id | Reglas | Jugadores | Retorno |
|---|---|---|---|---|
| **Ruleta de Colores** | `color-roulette` | Cada uno apuesta a rojo, negro o verde; el verde es una casilla única, así que paga ~36x | 2-24 | 97.30% |
| **Bote Común** | `jackpot` | Todos ponen dinero y las papeletas son proporcionales a lo apostado; un ganador se lo lleva todo | 2-24 | 100% − comisión |
| **Bomba Caliente** | `hot-bomb` | La TNT pasa de mano en mano con una mecha sorteada; al que le pille queda fuera y su dinero sigue en el bote | 2-12 | 100% − comisión |
| **Tablero de Bombas** | `bomb-board` | Tablero compartido con bombas escondidas; se destapa por turnos; el último en pie cobra | 2-12 | 100% − comisión |
| **Ruleta Rusa** | `russian-roulette` | Por turnos cada uno aprieta el gatillo con 1 bala en 6 recámaras, una bala más cada 3 vueltas a la mesa; las cabezas de los jugadores cuelgan frente a la boca del cañón; el superviviente se lleva el bote | 2-8 | 100% − comisión |
| **Carrera de Caballos** | `race` | 8 caballos con probabilidades publicadas; el favorito paga poco y el tapado mucho | 2-24 | 98.00% por caballo |
| **Duelo 1v1** | `duel` | Retas a alguien por una cantidad; los dos ponen lo mismo y una moneda decide | 2 | 100% − comisión |
| **Rifa** | `raffle` | Boletas a precio fijo y sorteo de tres premios: 70%, 20% y 10% del bote (con dos jugadores los dos premios se reparten el bote entero). Solo se cobran boletos enteros | 2-24 | 100% − comisión |
| **Póker de Dados** | `dice-poker` | Cinco dados cada uno; gana la mejor mano y los empates reparten el bote | 2-16 | 100% − comisión |
| **Póker** | `poker` | Texas hold'em en una mesa con cartas privadas, botones holográficos y botes secundarios; ver [Póker](Poker-es) | 2-8 | 100% − rake |

## Jugar solo contra la casa

Esperar a que se llene una sala es la peor parte de un casino, así que **todos los juegos de
grupo se pueden jugar solo contra la casa**. Con la sala vacía, el selector de apuesta muestra un
segundo botón, **Jugar contra la casa**; además el lobby manda una oferta con botón en el chat en
cuanto estás solo (y otra vez si los demás se van), y `/mvgam action house` hace lo mismo. Al terminar
cada ronda, cada jugador recibe en el chat los botones **Jugar otra vez** y **Contra la casa**
(`/mvgam play <id> house`). Cada duelo tiene el mismo margen de la casa que los juegos en solitario:

- **Ruleta Rusa** — el crupier se sienta enfrente y tú disparas primero. El tambor gira una vez,
  así que el duelo lo decide dónde están las balas: con 1 bala en 6 recámaras ganas la mitad de
  las veces y la victoria paga **1.96x**, el mismo margen que cualquier otra apuesta de aquí. La
  tabla tiene pruebas: ninguna combinación de `chambers` y `bullets` devuelve más que el margen
  de la casa. La bala extra cada 3 vueltas solo se aplica a la ronda de grupo: el duelo mantiene
  las balas con las que se calculó su pago.
- **Ruleta de Colores** — la ruleta paga tu color directamente (2x rojo o negro, 36x verde) en
  vez de pagarte de un bote en el que solo estás tú. El duelo empieza en cuanto eliges color.
- **Bote Común** y **Rifa** — la casa iguala tu apuesta (o compra tantos boletos como tú) y un
  único sorteo decide: ganas la mitad de las veces y la victoria paga **1.96x**.
- **Bomba Caliente** — la bomba pasa entre tú y el crupier; quien la tenga cuando explote pierde.
  Un juego parejo, pagado **1.96x**.
- **Tablero de Bombas** — tú y el crupier destapáis por turnos, tú primero; quien encuentre antes
  una bomba pierde. Es el revólver de la ruleta rusa con casillas en vez de recámaras, así que lo
  paga la misma tabla con pruebas.
- **Póker de Dados** — el crupier también tira una mano: la mejor mano gana 1.96x y el empate
  devuelve la apuesta.
- **Carrera de Caballos** — la carrera ya paga cuotas fijas, así que solo corres contra la banca:
  el caballo que elijas paga su propia cuota. Las cuotas se sortean al abrir la sala y se ven en
  los botones, así que eliges sabiéndolas.
- **Duelo 1v1** — el menú de rivales tiene un botón **Duelo contra la casa**: la misma moneda, tu
  cabeza contra la casa, un lanzamiento parejo pagado 1.96x.

Los demás pueden entrar a la ronda normal: el duelo solo se ofrece mientras estás solo.

## Jugador contra jugador significa sin ventaja de la casa

Estos juegos mueven dinero entre jugadores, así que por defecto la casa no se queda **nada**
(`group.house-commission: 0.0`). Ponle un valor pequeño, por ejemplo `0.02`, si quieres que el
casino se lleve una comisión del bote, la rifa y el póker de dados.

## Rondas y bote

- La ventana de apuestas dura `group.betting-seconds` (20 por defecto). El lobby anuncia la cuenta
  atrás y después la ronda empieza sola.
- El dinero sale de los monederos **cuando apuestas**, no cuando empieza la ronda: la apuesta
  queda guardada en un `Pot` que mantiene un wager liquidable-una-sola-vez por jugador.
- Una ronda necesita el mínimo de jugadores con apuesta; si no, se cancela y se devuelve todo.
- Si entras con una ronda en marcha, quedas apuntado a la siguiente y se te avisa.
- **Desconectarse a mitad de ronda no devuelve tu dinero.** Tu apuesta sigue en el bote y
  cualquier otro la puede ganar, que es lo que hace inútil el rage quit. Irse **antes** de que
  empiece la ronda sí te devuelve todo.
- El límite de asientos es `group.max-players`; cuando la mesa está llena se te dice que pruebes
  la siguiente ronda.

## Juegos por turnos y botones del chat

Bomba Caliente, Tablero de Bombas y Ruleta Rusa funcionan por turnos con un reloj
(`group.bomb-board.seconds-per-turn`, y el rango de la mecha para la bomba caliente). Anuncian
al jugador actual y, si no actúa a tiempo, el turno sigue solo, así que un jugador distraído no
puede congelar la mesa.

Algunas acciones también están en el chat: los botones clicables mandan
`/mvgam action <accion>` (`reveal`, `horse 3`, `shoot`, `accept`, `decline`...), que es lo que
hace que los juegos se puedan jugar desde el móvil o desde el chat sin abrir un menú.

## Azar verificable

Las rondas sacan su azar de la misma fuente verificable que los juegos en solitario, atribuida a
la identidad fija del casino (UUID cero). Eso significa que una carrera o una ronda de bomba
caliente se puede auditar después con `/mvgam verify`. Ver [Azar verificable](Justicia-es).
