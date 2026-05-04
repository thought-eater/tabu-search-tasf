package pe.edu.pucp.tasf.model;

import java.util.*;

/**
 * Red logística G = (N, A) donde N = aeropuertos y A = vuelos.
 *
 * Esta clase provee:
 *   - Registro de aeropuertos y vuelos.
 *   - Lista de adyacencia por aeropuerto de origen para búsqueda eficiente.
 *   - Búsqueda de rutas factibles con límite de saltos (maxHops) para
 *     alimentar el vecindario del Tabu Search.
 *   - Cancelación dinámica de vuelos (replanificación).
 *   - Reinicio de cargas (para re-evaluar una solución desde cero).
 */
public class LogisticsNetwork {

    /** Mapa código ICAO/IATA → objeto Airport, preservando el orden de inserción. */
    private final Map<String, Airport> airports;

    /** Lista completa de vuelos registrados en la red. */
    private final List<Flight> flights;

    /** Índice: código de aeropuerto → lista de vuelos salientes desde él. */
    private final Map<String, List<Flight>> outgoing;

    /**
     * Caché de vuelos virtuales multi-día (ID como "F5_d1", "F5_d2", etc.).
     * Reutilizar el mismo objeto garantiza que {@code assignedLoad} se acumule
     * correctamente y que {@link #resetLoads()} los limpie junto con los reales.
     */
    private final Map<String, Flight> virtualFlights;

    public LogisticsNetwork() {
        this.airports = new LinkedHashMap<>();
        this.flights = new ArrayList<>();
        this.outgoing = new HashMap<>();
        this.virtualFlights = new LinkedHashMap<>();
    }

    public void addAirport(Airport airport) {
        airports.put(airport.getCode(), airport);
        outgoing.putIfAbsent(airport.getCode(), new ArrayList<>());
    }

    public void addFlight(Flight flight) {
        flights.add(flight);
        outgoing.computeIfAbsent(flight.getOrigin().getCode(), k -> new ArrayList<>())
                .add(flight);
    }

    /**
     * Devuelve todos los vuelos ACTIVOS (no cancelados) salientes desde el
     * aeropuerto {@code airportCode} que pueden despegar a partir de
     * {@code minDepartureTime}.
     *
     * Los vuelos base tienen {@code departureTime ∈ [0, 1)} (fracción del día 0).
     * Para soportar ruteo multi-día se escala el tiempo base al día actual:
     *   {@code scaledDep = f.getDepartureTime() + dayOffset}
     * donde {@code dayOffset = floor(minDepartureTime)}.
     *
     * Si el vuelo ya despegó hoy (scaledDep < minDepartureTime), se crea un
     * vuelo virtual para el día siguiente. El objeto virtual se guarda en caché
     * (id + "_dN") para que su {@code assignedLoad} persista y se limpie en
     * {@link #resetLoads()}.
     *
     * Ejemplo: vuelo base departe a 0.3 (7:12), minDepartureTime = 803.25 (día 803, 6:00).
     *   scaledDep = 0.3 + 803 = 803.3 ≥ 803.25 → disponible hoy como "F5_d803" ✓
     *   (el código anterior computaba dayOffset+1 = 804 y se lo saltaba completamente)
     */
    public List<Flight> getActiveFlightsFrom(String airportCode, double minDepartureTime) {
        List<Flight> available = outgoing.getOrDefault(airportCode, Collections.emptyList());
        List<Flight> result = new ArrayList<>();

        // Día entero en el que cae minDepartureTime (p.ej. 803 para 803.25)
        double dayOffset = Math.floor(minDepartureTime);

        for (Flight f : available) {
            if (f.isCancelled()) continue;

            // Escalar el tiempo base del vuelo al día actual.
            // Los vuelos base tienen departureTime ∈ [0, 1) → el del día N es baseDep + N.
            double scaledDep = f.getDepartureTime() + dayOffset;

            if (scaledDep >= minDepartureTime) {
                // Vuelo disponible hoy
                if (dayOffset == 0) {
                    // Día base: usar el objeto original (sin virtualizar)
                    result.add(f);
                } else {
                    int dayNum = (int) dayOffset;
                    String virtualId = f.getId() + "_d" + dayNum;
                    final double dep = scaledDep;
                    // Preservar el transitTime real del vuelo base (no el valor fijo del enunciado)
                    final double arr = dep + f.getTransitTime();
                    Flight sameDay = virtualFlights.computeIfAbsent(virtualId, k ->
                            new Flight(k, f.getOrigin(), f.getDestination(),
                                    f.getCapacity(), dep, arr));
                    result.add(sameDay);
                }
            } else {
                // Vuelo ya despegó hoy → usar la ocurrencia del día siguiente
                double nextDayDep = f.getDepartureTime() + dayOffset + 1.0;
                int nextDayNum = (int) dayOffset + 1;
                String virtualId = f.getId() + "_d" + nextDayNum;
                final double dep = nextDayDep;
                // Preservar el transitTime real del vuelo base (no el valor fijo del enunciado)
                final double arr = dep + f.getTransitTime();
                Flight nextDay = virtualFlights.computeIfAbsent(virtualId, k ->
                        new Flight(k, f.getOrigin(), f.getDestination(),
                                f.getCapacity(), dep, arr));
                result.add(nextDay);
            }
        }

        result.sort(Comparator.comparingDouble(Flight::getDepartureTime));
        return result;
    }

    /** Conveniencia: todos los vuelos salientes desde el tiempo 0. */
    public List<Flight> getActiveFlightsFrom(String airportCode) {
        return getActiveFlightsFrom(airportCode, 0.0);
    }

    /**
     * Enumera rutas posibles (hasta {@code maxHops} saltos) entre origen y destino,
     * devolviendo como máximo {@code maxResults} caminos.
     *
     * Se explotan primero los vuelos con salida más temprana (el DFS visita los vuelos
     * en orden de hora de salida), por lo que los primeros resultados tienden a ser
     * los de menor tiempo de tránsito.
     *
     * Advertencia: el costo es exponencial en maxHops; se recomienda
     * maxHops ≤ 3 para redes densas.
     *
     * @param origin     aeropuerto de origen
     * @param destination aeropuerto de destino
     * @param startTime  tiempo mínimo de salida del primer vuelo
     * @param maxHops    profundidad máxima del DFS (número de vuelos en la ruta)
     * @param maxResults límite de rutas a devolver; usar {@link Integer#MAX_VALUE} para sin límite
     * @return lista de caminos (cada camino es una lista ordenada de vuelos)
     */
    public List<List<Flight>> findRoutes(Airport origin, Airport destination,
                                         double startTime, int maxHops, int maxResults) {
        List<List<Flight>> result = new ArrayList<>();
        findRoutesRecursive(origin.getCode(), destination.getCode(), startTime,
                maxHops, new ArrayList<>(), new HashSet<>(), result, maxResults);
        return result;
    }

    /**
     * Sobrecarga sin límite de resultados (compatibilidad con código existente).
     * Equivalente a llamar con maxResults = {@link Integer#MAX_VALUE}.
     */
    public List<List<Flight>> findRoutes(Airport origin, Airport destination,
                                         double startTime, int maxHops) {
        return findRoutes(origin, destination, startTime, maxHops, Integer.MAX_VALUE);
    }

    /**
     * DFS acotado en profundidad para enumerar rutas simples (sin ciclos).
     * Se detiene anticipadamente cuando ya se han encontrado {@code maxResults} rutas.
     */
    private void findRoutesRecursive(String current, String target, double currentTime,
                                     int hopsLeft, List<Flight> path,
                                     Set<String> visited, List<List<Flight>> result,
                                     int maxResults) {
        // Corte anticipado: ya alcanzamos el límite de rutas solicitado
        if (result.size() >= maxResults) return;

        if (current.equals(target) && !path.isEmpty()) {
            result.add(new ArrayList<>(path));
            return;
        }
        if (hopsLeft == 0) return;

        visited.add(current);
        for (Flight f : getActiveFlightsFrom(current, currentTime)) {
            // Corte anticipado dentro del bucle para no seguir expandiendo nodos
            if (result.size() >= maxResults) break;
            String nextCode = f.getDestination().getCode();
            if (!visited.contains(nextCode)) {
                path.add(f);
                findRoutesRecursive(nextCode, target, f.getArrivalTime(),
                        hopsLeft - 1, path, visited, result, maxResults);
                path.remove(path.size() - 1);
            }
        }
        visited.remove(current);
    }

    /** Marca un vuelo como cancelado (usado en replanificación dinámica). */
    public void cancelFlight(String flightId) {
        for (Flight f : flights) {
            if (f.getId().equals(flightId)) {
                f.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Pone a cero las cargas asignadas de todos los vuelos (reales y virtuales)
     * y el stock de los aeropuertos. Se invoca antes de reconstruir una solución
     * para evitar contabilidad errónea.
     */
    public void resetLoads() {
        for (Flight f : flights) {
            f.setAssignedLoad(0);
        }
        for (Flight f : virtualFlights.values()) {
            f.setAssignedLoad(0);
        }
        for (Airport a : airports.values()) {
            a.setCurrentStock(0);
        }
    }

    // ----------------- Getters -----------------
    public Airport getAirport(String code) { return airports.get(code); }
    public Collection<Airport> getAirports() { return airports.values(); }
    public List<Flight> getFlights() { return Collections.unmodifiableList(flights); }
    public int getAirportCount() { return airports.size(); }
    public int getFlightCount() { return flights.size(); }
}
