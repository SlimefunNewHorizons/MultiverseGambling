# Azar verificable

[English](Fairness) · **Español**

Cada tirada que decide dinero sale de `FairnessService`, y cualquiera puede recalcularla
después.

## La idea

```
tirada = HMAC-SHA256(secretoDelServidor, semillaDelJugador:nonce:cursor)
```

- El **secreto del servidor** se genera al arrancar y **su hash se publica** antes de que juegues.
  Mientras el secreto está activo no puedes conocer las tiradas, pero el hash publicado
  compromete al servidor con ellas.
- La **semilla de cliente** es tuya. Puedes ponerle cualquier texto con
  `/mvgam verify <texto>`, así el servidor no puede elegir un resultado después de ver tu
  apuesta.
- El **nonce** cuenta las tiradas emitidas, y el **cursor** separa los varios valores aleatorios
  que un mismo juego puede necesitar.

Cuando el secreto rota, se revela el anterior. El hash que te mostraron antes coincide ahora con
el secreto que puedes leer: cualquiera puede rehacer las cuentas y comprobar que no se cambió
nada.

## Auditar

```
/mvgam verify
```

Muestra el hash del secreto actual, el secreto anterior (cuando ya rotó), el hash que dejó ese
secreto anterior, tu semilla de cliente y cuántas tiradas se han emitido en esta sesión.

```
/mvgam verify mi-texto-de-suerte
```

Cambia tu semilla de cliente. Hazlo cuando quieras; el cambio se aplica a las tiradas
posteriores.

## Reglas que cumple el servicio

- **La tirada es uniforme.** 100.000 tiradas repartidas en 10 cubos caen al 10% cada uno, y la
  suite comprueba exactamente eso.
- **Es determinista.** El mismo secreto, semilla, nonce y cursor dan siempre la misma tirada, que
  es lo que hace posible la auditoría.
- **El dinero nunca usa el azar de las animaciones.** El `Rng` normal solo pinta ruletas y
  rodillos; el resultado que paga sale siempre del servicio verificable.
- **Las rondas en grupo también son auditables.** Se atribuyen a la identidad fija del casino
  (UUID cero), así que una bomba caliente o una carrera se pueden recalcular paso a paso.
- **Se puede desactivar, pero no es recomendable.** `fairness.provably-fair: false` hace que el
  plugin use el generador normal; la auditoría entonces no puede demostrar nada.

## Qué no hace

Verificable no significa que la casa no pueda ganar: la ventaja está en las tablas de premios y
está documentada (ver [Retornos reales](https://github.com/SlimefunNewHorizons/MultiverseGambling/blob/main/README.es.md#retornos-reales)).
Lo que demuestra es que **los resultados son los que implica la tabla publicada**, y que a un
jugador no se le puede dar una tirada peor que a otro.
