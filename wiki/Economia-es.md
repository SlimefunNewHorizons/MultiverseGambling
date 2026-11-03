# Economía

[English](Economy) · **Español**

El plugin puede usar la economía de tu servidor a través de **Vault**, o su propio monedero,
según `economy.provider`.

| Valor | Comportamiento |
|---|---|
| `auto` (por defecto) | Prueba los motores de `economy.auto-order`, en orden |
| `sbank` | Las **cuentas bancarias** del plugin sBank |
| `vault` | La economía que el servidor registre a través de Vault |
| `internal` | El `balances.json` del propio plugin, ignorando a cualquier otro |

Elijas lo que elijas, el plugin acaba con un monedero usable: si el motor nombrado falta o no
responde, avisa por consola y cae al siguiente, y el monedero interno siempre va al final. El
casino nunca puede fallar al arrancar por culpa del dinero.

## Qué motor, en qué orden

```yaml
economy:
  provider: auto
  auto-order: [sbank, vault, internal]
```

`auto` recorre esa lista. El orden que viene de fábrica pone el **banco primero**, porque un
servidor que instala sBank guarda el dinero de sus jugadores en cuentas bancarias; un servidor
que prefiera jugar con el monedero que reparte con Vault solo tiene que intercambiarlo:

```yaml
  auto-order: [vault, sbank, internal]
```

Elegir un motor directamente es la otra opción: `provider: sbank` sigue cayendo al monedero
interno si sBank no está, y `provider: internal` ignora a cualquier otro plugin.

## Cuentas bancarias de sBank

El puente con [sBank](https://github.com/SlimefunNewHorizons) usa su API pública, así que el
casino mueve el mismo número que muestra el banco:

- **Las lecturas** salen del banco en memoria de los jugadores conectados y de la base de
datos para quien esté desconectado, que es lo que permite pagar una ronda de grupo a alguien
que se fue.
- **Las escrituras** siguen las reglas del propio banco: el saldo se redondea a dos decimales
como hace el banco, y cada movimiento se persiste al instante con `SBank.persistBank`, así que
una caída del servidor no puede resucitar un saldo viejo. Si el banco no puede guardar el movimiento, el
casino rechaza el pago en lugar de repartir dinero que nadie puede tener.
- **Auditoría**: cada apuesta y cada pago se escriben en el registro del banco como
`CASINO_BET`, `CASINO_PAYOUT` o `CASINO_ADMIN`, así que un administrador puede cuadrar el
casino contra el banco después.
- **Las cuentas son cosa del banco.** sBank abre una para cada jugador que entra, con su
propio dinero inicial, así que el casino nunca crea ni financia una.

El puente se carga por reflexión: el casino sigue arrancando en servidores sin sBank, y si una
versión futura de sBank cambia su API el plugin avisa por consola y usa el siguiente motor.

## Monedero interno

`balances.json` guarda `uuid → saldo`. El saldo de bienvenida sale de
`economy.starting-balance` (1000 por defecto) y se abona la primera vez que se ve a un jugador,
con su mensaje. Los saldos se guardan cada `data.save-every-minutes` minutos y al apagar.

Comandos de administración:

```
/mvgam give <jugador> <cantidad>
/mvgam take <jugador> <cantidad>
/mvgam set  <jugador> <cantidad>
```

Funcionan con cualquiera de los dos proveedores. Cuando Vault está activo el plugin llama a
Vault, así que tu plugin de economía mantiene sus límites, sus registros y sus hooks.

## Cantidades y formato

`economy.format` decide cómo se imprime el dinero en todas partes
(`&6{amount} &7{currency}` por defecto): mensajes, menús, carteles y comandos de saldo. Las
cantidades escritas en los comandos aceptan coma o punto, así que funcionan `1000.50` y
`1000,50`.

## Archivos de datos

| Archivo | Contenido | ¿Se puede borrar? |
|---|---|---|
| `balances.json` | Monedero interno; se ignora con sBank o Vault | Solo para reiniciar la economía interna |
| `stats.json` | Partidas, victorias, apostado, mayor premio y los rankings | Sí, los jugadores pierden sus estadísticas |
| `fairness.json` | Secreto actual, secreto anterior y semillas de cliente por jugador | Sí, pero cambia la semilla de todos |
| `languages.json` | El idioma que eligió cada jugador | Sí, todos caen a `language.default` |
| `pending-items.yml` | Premios en ítems de quien salió a mitad de ronda (ver [Apuestas con ítems](Apuestas-Items-es)) | No: esos jugadores pierden sus ítems |

## Cómo protege el dinero el plugin

- **Una liquidación por apuesta.** Un `Wager` se crea cuando se cobra la apuesta y se puede
  liquidar una sola vez: un segundo `pay()`, `refund()` o `lose()` no hace nada. Pagar dos veces
  la misma ronda no es posible, no solo improbable.
- **El bote es dueño del dinero.** En los juegos en grupo las apuestas viven en un `Pot` desde la
  ventana de apuestas hasta el reparto, así que una desconexión no puede dejar dinero a medias:
  o vuelve con la devolución o sigue siendo ganable por los demás.
- **Un solo reloj.** `SessionManager` planifica todo y `shutdownAll()` liquida cada partida
  abierta al apagar, así que un reinicio no se come la apuesta de nadie.
- **La ventaja es dato, no código.** `game.house-edge` lo aplican las tablas del `engine` y la
  suite se niega a compilar una tabla que devuelva más de lo que cobra.
