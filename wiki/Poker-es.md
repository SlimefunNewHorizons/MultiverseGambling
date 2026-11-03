# Póker

[English](Poker) · **Español**

**Texas hold'em para hasta 8 jugadores** (`/mvgam play poker`), en una gran mesa ovalada
tumbada en el centro de su pabellón, con un crupier de pie tras el sabot.

- **Sentarse**: acércate a una silla libre y pulsa su holograma **✚ SENTARSE**, elige tu
  entrada (en ciegas grandes) y quedas sentado en la silla. Agáchate o pulsa **⏏ Levantarse**
  para irte.
- **Cartas privadas**: tus dos cartas están boca abajo para toda la sala; solo tú las ves
  boca arriba, además de una copia más grande flotando frente a ti. Nadie puede leer tu mano
  dando vueltas a la mesa.
- **Tus propios botones**: en tu turno aparecen botones flotantes frente a tu silla, solo
  para ti: **Retirarse**, **Pasar / Igualar**, **All in**, y **◀ Subir a ▶** para elegir la
  subida (mínimo, un tercio del bote, la mitad, tres cuartos, el bote...). Las mismas jugadas
  llegan como botones del chat. Tienes `action-seconds` (30) para actuar; después la mesa pasa
  o se retira por ti.
- **El crupier** baraja con el generador verificable, reparte las cartas volando desde el
  sabot, quema antes de cada calle, mueve el botón de dealer y empuja el bote al ganador.
- **Reglas reales**: ciegas, mano a mano con el botón en la ciega pequeña, subidas mínimas,
  all-in que no reabren las apuestas, apuestas no igualadas devueltas, **botes secundarios**,
  botes repartidos con la ficha impar a la izquierda del botón.
- **Contra la casa**: solo en la mesa, pulsa **⚑ Jugar contra la casa** y la casa se sienta
  con tantas fichas como tú. Juega estimando sus probabilidades frente al bote y se queda
  `house-rake` (5%) de cada bote tras el flop mientras juega.

## Fichas por ítems

El póker se juega con dinero, pero puedes **comprar fichas con ítems**: vanilla, de
**Slimefun** y de **MultiverseCreatures** y de **todos los addons de Slimefun**, mezclados en un mismo cofre. Cada uno vale lo que
dice [item-values.yml](https://github.com/SlimefunNewHorizons/MultiverseGambling/blob/main/src/main/resources/item-values.yml), y al levantarte recuperas primero
tus ítems (los más valiosos primero, mientras tus fichas los cubran) y el resto en dinero.
Cómo se fijaron los valores:

| Origen | Cómo se valora |
|---|---|
| Vanilla | Frente al ancla **1 diamante = 100 monedas**: lo difícil que es conseguirlo |
| Slimefun (514 ítems) | Generado desde las recetas de Slimefun: sus ingredientes, por lo que añade la máquina o el ritual (mesa de crafteo mejorada ×1.05, fundición ×1.08, mesa mágica ×1.15, cámara de presión caliente ×1.15, altar antiguo ×1.40), más 0,5 monedas por nivel de investigación, dividido entre lo que produce la receta. Los recursos del mundo (mineral tamizado, uranio, petróleo) tienen valor fijo |
| MultiverseCreatures (59 ítems) | Un drop vale lo difícil que es su criatura dividido entre su probabilidad (Orbe del Caos: 150 / 60% = 250); los crafteados valen sus ingredientes ×1.10, las armas, armaduras y reliquias legendarias ×1.65; los del mercader valen su intercambio (Excalibur: 16 Núcleos Estelares + 32 netherite) |
| Addons de Slimefun (todos los instalados) | Se valoran en cada arranque desde las recetas que Slimefun registró de verdad, con la misma regla: DynaTech, Supreme, Galactifun, ExoticGarden, Networks, LiteXpansion, FluffyMachines y cualquier otro addon. Los ítems que se encuentran (drops, recursos GEO) y los que no tienen receta parten de 25 monedas. El resultado se escribe en `plugins/MultiverseGambling/item-values-addons.yml`, agrupado por addon; copia una línea bajo `addons:` en item-values.yml para cambiarla |

Un ítem se reconoce por el ID que su plugin guarda en él, nunca por su nombre, así que un
ítem renombrado no pasa por uno custom; un ítem vanilla o de MultiverseCreatures que no está en el archivo se rechaza.
Edita cualquier valor, o `scale` para cambiarlos todos, y `/mvgam reload`.

## Configuración

```yaml
games:
  poker:
    min-bet: 200            # entrada mínima
    max-bet: 1000000        # pila máxima en la mesa
    small-blind: 5
    big-blind: 10
    action-seconds: 30      # tiempo para actuar antes de que la mesa pase o se retire por ti
    next-hand-seconds: 6
    rake: 0.0               # parte de cada bote tras el flop, jugador contra jugador
    rake-cap: 0
    house-rake: 0.05        # mientras juega la casa
    house-players: 1        # cuántos jugadores de la casa se sientan
    item-buy-in: true
```

## Seguridad

- El dinero sale del saldo solo al confirmar la entrada; cerrar un menú no cuesta nada.
- Cada cambio de asientos se escribe en `poker-table.yml`: tras un fallo del servidor, cada
  jugador recupera la pila que tenía al empezar la mano, en ítems y dinero.
- Quien se desconecta o se levanta a mitad de mano se retira (o pasa) en sus turnos y cobra al
  terminar la mano. Dos veces sin tiempo seguidas levantan al jugador tras la mano.
