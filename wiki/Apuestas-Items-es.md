# Apuestas con ítems

[English](Item-Bets) · **Español**

Además del dinero, seis juegos en solitario aceptan **ítems** como apuesta. El premio vuelve
en el mismo ítem: una apuesta de diamantes se paga en diamantes y una apuesta con una espada
custom se paga en copias de esa misma espada.

| Juego | Id | Resultados que muestra el menú de ítems |
|---|---|---|
| Ruleta clásica | `roulette` | Apuestas sencillas, una docena o columna, un número pleno |
| Tragaperras | `slots` | Cada trío y cada pareja que paga |
| Dados | `dice` | Cinco objetivos de ejemplo, del 75% de probabilidad al 1% |
| Plinko | `plinko` | Cada cubeta del tablero |
| Ruleta de la suerte | `lucky-wheel` | Cada multiplicador de la rueda y cuántos segmentos lo llevan |
| Cara o cruz | `coin-flip` | Acertar la cara |

Los juegos con decisiones durante la partida (crash, minas, torre, blackjack, mayor o menor,
rasca) y los juegos en grupo siguen siendo solo con dinero: su premio depende de decisiones
tomadas después de apostar, que no se pueden mostrar antes como un número fijo de ítems.

## Hacer una apuesta con ítems

1. Abre un juego (`/mvgam play coin-flip`, el menú principal o su pabellón) y elige
   **❖ Apostar items** en el menú de apuesta.
2. Se abre un cofre de seis filas. Coloca los ítems en las 21 casillas del centro: **un solo
   tipo de ítem, la cantidad que quieras**. Funciona el shift + clic desde tu inventario.
3. El resumen muestra lo que apuestas y el panel **Lo que recibes** enumera cada resultado
   con su multiplicador y la cantidad de ítems que devuelve:

   ```
   Acertar la cara » 1.96x = 196 × Diamante
   Cualquier otro resultado » pierdes los 100 Diamante
   ```

4. Pulsa **Jugar con estos ítems**. El juego sigue igual que con dinero: eliges la cara, el
   objetivo, la apuesta de la mesa...

**Cancelar**, **Apostar dinero** o simplemente cerrar el cofre devuelve todos los ítems. Un
juego que se cierra antes de decidirse nada también los devuelve.

## Ítems vanilla y custom

La apuesta guarda **una copia exacta** del ítem con todo lo que lleva: nombre, lore,
encantamientos, custom model data y los datos persistentes que otros plugins usan para sus
IDs custom. Dos ítems solo cuentan como el mismo tipo cuando el servidor los considera
similares, así que no puedes mezclar una espada de diamante normal con una encantada.

## Cuántos ítems recibes

El premio es `apostado × multiplicador`, la misma fórmula que con dinero. Cuando no da un
número entero, la fracción se paga **por probabilidad**:

- 100 diamantes a 1.96x son exactamente 196 diamantes;
- 10 diamantes a 1.96x son 19,6: recibes 19, más un **60%** de probabilidad del 20.º.

De media paga exactamente 19,6, así que las apuestas con ítems tienen el mismo retorno que
las de dinero y ningún redondeo recorta la parte del jugador. La probabilidad del ítem extra
sale del generador verificable, como cualquier otra tirada.

## Entrega

| Situación | Qué pasa |
|---|---|
| Los ítems caben en el inventario | Entran directamente |
| El inventario está lleno | El resto cae a tus pies y se te avisa |
| Saliste antes de que acabara la ronda | El resultado es el que ya se sorteó; los ítems esperan en `pending-items.yml` y llegan un segundo después de volver a entrar |

Las rondas con ítems no cuentan en las estadísticas de dinero, los rankings ni los anuncios
de grandes premios: esos solo comparan monedas.

## Configuración

```yaml
item-bets:
  enabled: true
  max-items: 1728        # máximo de ítems por apuesta (27 stacks de 64)
  blocked:               # nombre exacto, que termine (*SUFIJO) o que empiece (PREFIJO*)
    - '*SHULKER_BOX'
    - '*BUNDLE'
```

| Clave | Por defecto | Significado |
|---|---|---|
| `enabled` | `true` | Muestra el botón **❖ Apostar items** en el menú de apuesta |
| `max-items` | `1728` | Máximo de ítems en una sola apuesta |
| `blocked` | shulker boxes, bundles | Materiales que nunca se pueden apostar. Los contenedores se bloquean para que nadie multiplique su contenido |

Entradas habituales: `NETHERITE_*` para dejar fuera el equipo de final de juego, `ELYTRA`,
`DRAGON_EGG` o el ítem de moneda del servidor que no quieras multiplicar.

## Quien pone el precio y el mordisco de la casa

El plugin siempre valora los items (`item-values.yml`, luego las recetas de Slimefun); el jugador nunca fija un precio.

- Un item sin precio no se puede apostar. Techos: `item-bets.max-item-value` por item y `item-bets.max-bet-value` por apuesta.
- Un stack con datos fuera de `item-bets.allowed-data` (por defecto `slimefun`, `multiversecreatures`) se rechaza: el pago es una copia del item.
- `item-bets.deposit-haircut` (0.10): ese porcentaje del valor se ignora al entrar (las fichas de poker se acreditan por debajo; el item solo se recompra a precio completo).
- `item-bets.house-cut` (0.05): comision extra de cada pago. Una ronda anulada devuelve todo.
- Ambos son sumideros reales: esos items nunca se crean ni se reparten.
- Botes de un ganador: `group.house-commission` (0.03); poker: `games.poker.rake` (0.03, tope 50000).

`/mvgam help` (o `guia`) abre el manual dentro del juego, tambien desde el menu principal; cada pabellon muestra un holograma con su guia.
