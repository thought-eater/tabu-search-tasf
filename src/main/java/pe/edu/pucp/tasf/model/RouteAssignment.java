package pe.edu.pucp.tasf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa la ruta planificada para un envío: una secuencia ordenada de
 * vuelos que lleva las maletas desde el aeropuerto origen hasta el destino.
 *
 * Una ruta puede ser:
 *   - Directa:   un solo vuelo (origen → destino).
 *   - Multi-hop: varios vuelos encadenados pasando por aeropuertos intermedios.
 *
 * La clase calcula automáticamente el tiempo total de tránsito (incluyendo
 * tiempos de espera entre vuelos) y la hora estimada de llegada, comparándolos
 * con el plazo definido en la {@link ShipmentRequest}.
 */
public class RouteAssignment {

    /** Solicitud de envío asociada a esta ruta. */
    private final ShipmentRequest request;

    /** Secuencia ordenada de vuelos (primer vuelo sale del origen). */
    private final List<Flight> flights;

    public RouteAssignment(ShipmentRequest request) {
        this.request = request;
        this.flights = new ArrayList<>();
    }

    public RouteAssignment(ShipmentRequest request, List<Flight> flights) {
        this.request = request;
        this.flights = new ArrayList<>(flights);
    }

    /** Agrega un vuelo al final de la ruta. */
    public void addFlight(Flight flight) {
        this.flights.add(flight);
    }

    /**
     * Calcula el tiempo total de tránsito (en días) considerando:
     *   - tiempos de espera entre el momento actual y la hora de salida
     *     del siguiente vuelo,
     *   - tiempo efectivo de vuelo de cada tramo.
     *
     * Devuelve un valor alto (999.0) si la ruta está vacía, para penalizarla
     * en la función objetivo sin necesidad de casos especiales.
     */
    public double getTotalTransitTime() {
        if (flights.isEmpty()) return 999.0; // penalización grande pero finita

        double totalTime = 0;
        double currentTime = request.getCreationTime();

        for (Flight f : flights) {
            // Espera hasta la hora de salida del vuelo
            double waitTime = Math.max(0, f.getDepartureTime() - currentTime);
            totalTime += waitTime + f.getTransitTime();
            currentTime = f.getDepartureTime() + f.getTransitTime();
        }
        return totalTime;
    }

    /** Hora estimada de llegada al destino final (en días absolutos desde día 0). */
    public double getArrivalTime() {
        if (flights.isEmpty()) return 999.0;

        double currentTime = request.getCreationTime();
        for (Flight f : flights) {
            double waitTime = Math.max(0, f.getDepartureTime() - currentTime);
            currentTime += waitTime + f.getTransitTime();
        }
        return currentTime;
    }

    /**
     * Indica si la ruta entrega dentro del plazo comprometido.
     * El plazo absoluto es: creationTime + deadline.
     * Ejemplo: creationTime=0.167 (04:00), deadline=1.0 → entrega antes de las 04:00 del día siguiente.
     */
    public boolean isOnTime() {
        double absoluteDeadline = request.getCreationTime() + request.getDeadline();
        return getArrivalTime() <= absoluteDeadline;
    }

    /** Retraso (en días) respecto al plazo absoluto. 0 si llega a tiempo. */
    public double getDelay() {
        double absoluteDeadline = request.getCreationTime() + request.getDeadline();
        double arrival = getArrivalTime();
        return Math.max(0, arrival - absoluteDeadline);
    }

    /**
     * Verifica la factibilidad: la ruta no puede estar vacía, ningún vuelo
     * debe estar cancelado y todos deben tener capacidad restante suficiente.
     *
     * Una ruta vacía se considera infactible (el envío no tiene ningún vuelo
     * asignado) para mantener coherencia con la penalización PENALTY_INFEASIBLE
     * de la función objetivo.
     */
    public boolean isFeasible() {
        if (flights.isEmpty()) return false; // sin vuelos = infactible
        for (Flight f : flights) {
            if (f.isCancelled() || f.getAssignedLoad() > f.getCapacity()) {
                return false;
            }
        }
        return true;
    }

    /** Copia profunda de la asignación (para snapshots durante la búsqueda). */
    public RouteAssignment copy() {
        return new RouteAssignment(request, new ArrayList<>(flights));
    }

    // ----------------- Getters -----------------
    public ShipmentRequest getRequest() { return request; }
    public List<Flight> getFlights() { return Collections.unmodifiableList(flights); }
    public int getHopCount() { return flights.size(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getId()).append(": ");
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            if (i == 0) sb.append(f.getOrigin().getCode());
            sb.append(" --(").append(f.getId()).append(")--> ").append(f.getDestination().getCode());
        }
        sb.append(String.format(" [tránsito=%.2f, plazo=%.1f, %s]",
                getTotalTransitTime(), request.getDeadline(),
                isOnTime() ? "A_TIEMPO" : "TARDE"));
        return sb.toString();
    }
}
