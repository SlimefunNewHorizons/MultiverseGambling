<p align="center"><img src="https://raw.githubusercontent.com/SlimefunNewHorizons/MultiverseGambling/main/assets/banner-es.svg" alt="MultiverseGambling" width="100%"/></p>

# Wiki de MultiverseGambling

[English](Home) · **Español**

Motor de azar y apuestas para **Paper 1.21.11, 26.1 y 26.2** con **22 minijuegos**, su propio **mundo
casino** y **traducción dentro del juego** al inglés o al español.

| | |
|---|---|
| Versión | 1.0.4 |
| Servidor | Paper 1.21.11, 26.1 y 26.2 (también carga en cualquier 1.21.x) |
| Java | 21 |
| Dependencia blanda | Vault (opcional) |
| Autor | **Chagui68** |
| Organización | **Drakes Labs** |
| Repositorio | [SlimefunNewHorizons/MultiverseGambling](https://github.com/SlimefunNewHorizons/MultiverseGambling) |

## Empieza por aquí

| Página | Qué responde |
|---|---|
| [Instalación](Instalacion-es) | Cómo instalar el jar y qué archivos crea |
| [Comandos](Comandos-es) | Todos los subcomandos de `/mvgam`, con ejemplos |
| [Permisos](Permisos-es) | Los tres permisos y qué abre cada uno |
| [Configuración](Configuracion-es) | Todas las claves de `config.yml` |
| [Mundo](Mundo-Casino-es) | El mundo aparte, la plaza, las arenas y las carreteras |
| [Idiomas](Idiomas-es) | La autotraducción, `/mvgam language` y añadir un idioma |
| [Juegos en solitario](Juegos-Solo-es) | Los 12 juegos contra la casa |
| [Juegos en grupo](Juegos-Grupo-es) | Los 10 juegos con rondas automáticas, también contra la casa |
| [Póker](Poker-es) | La mesa de Texas hold'em, fichas por ítems y cómo se valoran |
| [Apuestas con ítems](Apuestas-Items-es) | Apostar ítems vanilla o custom y cuántos vuelven |
| [Azar verificable](Justicia-es) | Tiradas verificables y `/mvgam verify` |
| [Economía](Economia-es) | sBank, Vault o el monedero interno, y los archivos de datos |
| [Problemas](Problemas-es) | Los sospechosos habituales |
| [Desarrollo](Desarrollo-es) | Compilar, testear y estructura del proyecto |

## Las tres ideas detrás del plugin

1. **La matemática vive fuera de Bukkit.** Todo lo que decide dinero es Java puro en
   `com.chagui68.multiversegambling.engine`, así que se testea en milisegundos y el plugin
   solo puede usar esas fórmulas.
2. **El dinero se liquida una sola vez.** Cada apuesta va envuelta en un `Wager` que rechaza
   un segundo pago; los juegos en grupo usan un `Pot` con un wager por jugador.
3. **El azar que decide dinero es verificable.** Un secreto del servidor más tu propia
   semilla producen cada tirada, y `/mvgam verify` deja que cualquiera las recalcule.

## Recorrido rápido

```
/mvgam                      → menú principal (pestañas solo / grupo)
/mvgam play roulette        → jugar por su id exacto
/mvgam play plinko          → y luego "❖ Apostar items" para apostar ítems en vez de dinero
/mvgam world                → viajar al mundo casino
/mvgam language es          → leer todo en español desde ahora
/mvgam verify               → auditar la justicia de cada tirada
/mvgam world build          → reconstruir plaza, carreteras y arenas (admin)
/mvgam world info           → qué le falta al mundo casino (admin)
```
