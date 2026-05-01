package pe.edu.pucp.tasf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;

/**
 * Representa una solución completa del problema: un conjunto de
 * {@link RouteAssignment} (una por cada {@link ShipmentRequest}).
 *
 * La función objetivo principal es minimizar la cantidad de maletas que
 * se entregan fuera del plazo. Como desempate se usa el retraso acumulado
 * y la penalización por infactibilidades (capacidades excedidas o vuelos
 * cancelados).
 */
public class Solution {

    /** Conjunto ordenado de asignaciones de ruta. */
    private final List<RouteAssignment> routes;

    public Solution() {
        this.routes = new ArrayList<>();
    }

    public Solution(List<RouteAssignment> routes) {
        this.routes = new ArrayList<>(routes);
    }

    public void addRoute(RouteAssignment route) {
        this.routes.add(route);
    }

    /** Objetivo principal: número total de maletas entregadas tarde. */
    public int getLateCount() {
        int late = 0;
        for (RouteAssignment ra : routes) {
            if (!ra.isOnTime()) {
                late += ra.getRequest().getQuantity();
            }
        }
        return late;
    }

    /** Métrica secundaria: retraso total acumulado (ponderado por cantidad). */
    public double getTotalDelay() {
        double total = 0;
        for (RouteAssignment ra : routes) {
            total += ra.getDelay() * ra.getRequest().getQuantity();
        }
        return total;
    }

    /** Suma de maletas en todas las solicitudes (indicador del tamaño del problema). */
    public int getTotalSuitcases() {
        return routes.stream().mapToInt(r -> r.getRequest().getQuantity()).sum();
    }

    /** Maletas entregadas (en ruta factible y a tiempo). */
    public int getDeliveredCount() {
        int delivered = 0;
        for (RouteAssignment ra : routes) {
            if (ra.isFeasible() && ra.isOnTime()) {
                delivered += ra.getRequest().getQuantity();
            }
        }
        return delivered;
    }

    /** Maletas no entregadas (ruta vacía o infactible). */
    public int getUndeliveredCount() {
        int undelivered = 0;
        for (RouteAssignment ra : routes) {
            if (!ra.isFeasible() || ra.getFlights().isEmpty()) {
                undelivered += ra.getRequest().getQuantity();
            }
        }
        return undelivered;
    }

    /** Exceso de capacidad en vuelos (suma de unidades sobre el límite). */
    public int getCapacityOverflow() {
        // Para no contar el mismo vuelo varias veces (un vuelo puede aparecer
        // en varias rutas si distintos envíos lo comparten), agrupamos primero
        // todos los vuelos únicos referenciados por la solución.
        HashSet<Flight> uniqueFlights = new HashSet<>();
        for (RouteAssignment ra : routes) {
            uniqueFlights.addAll(ra.getFlights());
        }
        int overflow = 0;
        for (Flight f : uniqueFlights) {
            int over = f.getAssignedLoad() - f.getCapacity();
            if (over > 0) overflow += over;
        }
        return overflow;
    }

    /** Exceso de capacidad en almacenes (actualmente modelado como 0; extensible). */
    public int getWarehouseOverflow() {
        // La red actual no modela almacenes de aeropuerto individualmente.
        // Este método devuelve 0 como placeholder. Cuando se integre el
        // modelo de almacenes, calcular aquí el exceso por aeropuerto.
        return 0;
    }

    /** Número total de solicitudes (pedidos). */
    public int getTotalRequests() {
        return routes.size();
    }

    /**
     * Fitness (menor es mejor). Combina la cantidad de maletas tardías
     * (principal) con el retraso acumulado (desempate) y agrega una
     * penalización fuerte si la ruta es infactible.
     *
     * Valores por defecto (pueden reconfigurarse desde TabuSearchConfig):
     *   - PENALTY_LATE       = 1000  por maleta tardía
     *   - PENALTY_DELAY      = 100   por día de retraso por maleta
     *   - PENALTY_INFEASIBLE = 5000  por maleta en ruta infactible
     */
    public double getFitness() {
        double fitness = 0;
        double PENALTY_LATE = 1000.0;
        double PENALTY_DELAY = 100.0;
        double PENALTY_INFEASIBLE = 5000.0;

        for (RouteAssignment ra : routes) {
            int qty = ra.getRequest().getQuantity();
            if (!ra.isOnTime()) {
                fitness += PENALTY_LATE * qty;
                fitness += PENALTY_DELAY * ra.getDelay() * qty;
            }
            if (!ra.isFeasible()) {
                fitness += PENALTY_INFEASIBLE * qty;
            }
        }
        return fitness;
    }

    /** Copia profunda: duplica cada RouteAssignment para aislar estados. */
    public Solution copy() {
        List<RouteAssignment> copied = new ArrayList<>();
        for (RouteAssignment ra : routes) {
            copied.add(ra.copy());
        }
        return new Solution(copied);
    }

    // ----------------- Getters -----------------
    public List<RouteAssignment> getRoutes() { return Collections.unmodifiableList(routes); }
    public int size() { return routes.size(); }
    public RouteAssignment getRoute(int index) { return routes.get(index); }

    public void setRoute(int index, RouteAssignment route) {
        routes.set(index, route);
    }

    @Override
    public String toString() {
        return String.format("Solución [solicitudes=%d, maletas=%d, tarde=%d, fitness=%.2f]",
                routes.size(), getTotalSuitcases(), getLateCount(), getFitness());
    }
}
