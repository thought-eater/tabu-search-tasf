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

    public LogisticsNetwork() {
        this.airports = new LinkedHashMap<>();
        this.flights = new ArrayList<>();
        this.outgoing = new HashMap<>();
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
     * Para soportar ruteo multi-día: si {@code minDepartureTime} ya superó
     * el día 1, los vuelos de horario temprano "se repiten" al día siguiente.
     * Se genera un vuelo virtual (mismo id + "_dN") para representarlo.
     */
    public List<Flight> getActiveFlightsFrom(String airportCode, double minDepartureTime) {
        List<Flight> available = outgoing.getOrDefault(airportCode, Collections.emptyList());
        List<Flight> result = new ArrayList<>();

        for (Flight f : available) {
            if (f.isCancelled()) continue;

            // Coincidencia directa: el vuelo despega después del tiempo actual
            if (f.getDepartureTime() >= minDepartureTime) {
                result.add(f);
            }
            // Multi-día: si ya pasamos del día 1, los vuelos se repiten cada día.
            // Un vuelo a las 0.1 hoy es también a las 1.1 mañana, 2.1 pasado...
            else if (minDepartureTime > 0.5) {
                double dayOffset = Math.floor(minDepartureTime);
                double adjustedDep = f.getDepartureTime() + dayOffset + 1.0;
                if (adjustedDep >= minDepartureTime) {
                    // Vuelo virtual para el día siguiente
                    Flight nextDay = new Flight(f.getId() + "_d" + (int)(dayOffset + 1),
                            f.getOrigin(), f.getDestination(), f.getCapacity(), adjustedDep);
                    result.add(nextDay);
                }
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
     * Enumera TODAS las rutas posibles (hasta {@code maxHops} saltos) entre
     * origen y destino. Se usa tanto en la heurística greedy inicial como en
     * la exploración del vecindario del Tabu Search.
     *
     * Advertencia: el costo es exponencial en maxHops; se recomienda
     * maxHops ≤ 3 para redes densas.
     */
    public List<List<Flight>> findRoutes(Airport origin, Airport destination,
                                         double startTime, int maxHops) {
        List<List<Flight>> result = new ArrayList<>();
        findRoutesRecursive(origin.getCode(), destination.getCode(), startTime,
                maxHops, new ArrayList<>(), new HashSet<>(), result);
        return result;
    }

    /** DFS acotado en profundidad para enumerar rutas simples (sin ciclos). */
    private void findRoutesRecursive(String current, String target, double currentTime,
                                     int hopsLeft, List<Flight> path,
                                     Set<String> visited, List<List<Flight>> result) {
        if (current.equals(target) && !path.isEmpty()) {
            result.add(new ArrayList<>(path));
            return;
        }
        if (hopsLeft == 0) return;

        visited.add(current);
        for (Flight f : getActiveFlightsFrom(current, currentTime)) {
            String nextCode = f.getDestination().getCode();
            if (!visited.contains(nextCode)) {
                path.add(f);
                findRoutesRecursive(nextCode, target, f.getArrivalTime(),
                        hopsLeft - 1, path, visited, result);
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
     * Pone a cero las cargas asignadas de todos los vuelos y el stock de los
     * aeropuertos. Se invoca antes de reconstruir una solución para evitar
     * contabilidad errónea.
     */
    public void resetLoads() {
        for (Flight f : flights) {
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
