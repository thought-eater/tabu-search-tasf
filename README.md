# Tasf.B2B - Tabu Search Planner

**Equipo 8H — PUCP 2026-1 — Ingeniería Informática**

## Descripción

Implementación del algoritmo **Tabu Search** para el problema de optimización de
rutas de traslado de equipaje de la empresa Tasf.B2B. El objetivo es minimizar
el número de maletas que exceden su plazo de entrega.

Esta versión **ya puede procesar los archivos reales** de envíos entregados
por la empresa (`_envios_XXXX_.txt`) además de generar datos sintéticos para
las pruebas iniciales.

## Estructura del Proyecto

```
tabu-search-tasf/
├── pom.xml                          # Configuración Maven (Java 17+)
├── README.md
└── src/main/java/pe/edu/pucp/tasf/
    ├── Main.java                    # Punto de entrada (4 escenarios)
    ├── model/
    │   ├── Airport.java             # Nodo del grafo (aeropuerto)
    │   ├── Continent.java           # Enum con helper fromIcao(...)
    │   ├── Flight.java              # Arista del grafo (vuelo)
    │   ├── LogisticsNetwork.java    # Grafo G = (N, A)
    │   ├── RouteAssignment.java     # Ruta asignada a un envío
    │   ├── ShipmentRequest.java     # Demanda dk = (ok, sk, qk, TWk)
    │   └── Solution.java            # Solución completa
    ├── algorithm/
    │   ├── TabuSearchSolver.java    # Algoritmo Tabu Search
    │   ├── TabuSearchConfig.java    # Parámetros configurables
    │   └── TabuMove.java            # Representación de un movimiento
    ├── io/
    │   └── EnviosDataLoader.java    # ★ Parser de los _envios_XXXX_.txt
    └── util/
        ├── NetworkGenerator.java    # Generador SINTÉTICO (escenarios E1/E2/E3)
        └── RealNetworkBuilder.java  # ★ Constructor a partir de ICAOs reales
```

★ = componentes nuevos añadidos para soportar los datos reales.

## Compilación y Ejecución

### Con Maven
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="pe.edu.pucp.tasf.Main" -Dexec.args="E1 5 50"
```

### Con javac (sin Maven)
```bash
mkdir -p target/classes
find src/main/java -name "*.java" > sources.txt
javac -d target/classes @sources.txt
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1 5 50
```

## Escenarios

| Escenario | Comando ejemplo | Descripción |
|-----------|-----------------|-------------|
| **E1**  | `java ... Main E1  5 50` | Simulación de periodo SINTÉTICA (5 días, 50 req/día) |
| **E2**  | `java ... Main E2  0 50` | Operación en tiempo real con replanificación |
| **E3**  | `java ... Main E3  0 30` | Simulación hasta el colapso |
| **E1R** | `java ... Main E1R /ruta/_envios_preliminar_ 0 5` | **Simulación con DATOS REALES** |

### Modo de datos reales (E1R)

```
java -cp target/classes pe.edu.pucp.tasf.Main E1R <carpeta> [díaInicio] [numDías]
```

**Argumentos:**
- `<carpeta>` : ruta a la carpeta que contiene los archivos `_envios_XXXX_.txt`
- `[díaInicio]` : día 0-based (default 0). El día 0 es la fecha mínima detectada en los datos.
- `[numDías]` : cantidad de días consecutivos a simular (default 5).

**Ejemplo:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
     E1R ./_envios_preliminar_ 0 3
```

Lee los 30 archivos de la carpeta, detecta como fecha base 2026-01-02
(la primera en los datos), filtra los envíos que caen entre el 2026-01-02
y el 2026-01-05 y corre el Tabu Search sobre esa demanda.

### Formato de los archivos `_envios_XXXX_.txt`

- El nombre del archivo codifica el aeropuerto **origen** (ICAO 4 letras).
- Cada línea tiene el formato:

  ```
  ID-FECHA-HH-MM-DESTINO-CANTIDAD-CLIENTE
  ```

  Ejemplo:
  ```
  000000001-20260102-00-47-SUAA-002-0032535
  ```
  Envío #1, creado el 02/ene/2026 a las 00:47, desde el origen del archivo
  hacia **SUAA** (Montevideo), **2 maletas**, cliente 32535.

### Clasificación por continente (ICAO → Continent)

La clase `Continent.fromIcao(String)` mapea la primera letra del código ICAO
al continente al que pertenece el aeropuerto:

| Prefijo | Continente | Ejemplos |
|---------|------------|----------|
| `S`, `K`, `C`, `M`, `T` | AMERICA | SPIM, SKBO, KJFK, CYYZ, MMMX |
| `E`, `L`, `B`           | EUROPE  | EDDI, LOWW, BIKF |
| `O`, `U`, `V`, `R`, `Z`, `W`, `Y` | ASIA | OMDB, UBBB, VIDP, RJTT |

## Componentes del algoritmo

### Solución inicial
- Heurística **greedy**: asigna la ruta más corta factible a cada solicitud,
  priorizando las de menor deadline y mayor cantidad de maletas.

### Estructura de vecindad
- **REROUTE**: cambia la secuencia de vuelos de un envío.

### Memoria adaptativa
- **Lista tabú**: previene revisitar movimientos recientes (*tenure* configurable).
- **Criterio de aspiración**: acepta un movimiento tabú si mejora la mejor
  solución global conocida.

### Estrategias de búsqueda
- **Diversificación**: perturba aleatoriamente rutas para escapar de óptimos
  locales (cada `diversificationInterval` iteraciones).
- **Intensificación**: retorna a la mejor solución conocida para explorar su
  vecindario con más detalle.

### Replanificación dinámica
- Ante cancelación de vuelos, identifica envíos afectados, los rerutea con
  greedy y ejecuta un Tabu Search focalizado (≤100 iteraciones).

### Semáforo (verde / ámbar / rojo)
- **Verde**: ≤10% maletas retrasadas
- **Ámbar**: ≤30% maletas retrasadas
- **Rojo**: >30% maletas retrasadas
- Umbrales configurables vía `TabuSearchConfig`.

## Parámetros configurables

| Parámetro          | Default | Descripción |
|--------------------|---------|-------------|
| `maxIterations`    | 1000    | Iteraciones máximas |
| `tabuTenure`       | 15      | Duración de un movimiento en la lista tabú |
| `neighborhoodSize` | 20      | Candidatos evaluados por iteración |
| `maxHops`          | 3       | Máximo de vuelos por ruta |
| `timeLimitMs`      | 90 min  | Límite de tiempo (E1) |
| `penaltyLate`      | 1000    | Penalización por maleta retrasada |
| `penaltyCapacity`  | 5000    | Penalización por violación de capacidad |
| `seed`             | 42      | Semilla para reproducibilidad |

## Función objetivo

Minimizar el número de maletas que exceden su plazo de entrega:
- **1 día** máximo para traslados dentro del mismo continente
- **2 días** máximo para traslados entre continentes distintos

Con penalizaciones por:
- Maletas retrasadas (× cantidad × `penaltyLate`)
- Delay acumulado (× cantidad × `penaltyDelay`)
- Rutas infactibles (× cantidad × `penaltyCapacity`)
