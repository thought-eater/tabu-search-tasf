package pe.edu.pucp.tasf.model;

/**
 * Representa un vuelo (arista dirigida) entre dos aeropuertos.
 *
 * Cada vuelo tiene una capacidad máxima de maletas y un tiempo de tránsito
 * que depende de si origen y destino están en el mismo continente:
 *   - Mismo continente   → 0.5 días de tránsito, capacidad [150, 250].
 *   - Distinto continente → 1.0 día  de tránsito, capacidad [150, 400].
 *
 * El tiempo de salida {@code departureTime} se expresa como fracción de día
 * (0.0 = 00:00, 0.5 = 12:00, 1.0 = 24:00). Así, un valor de 0.25 equivale
 * a las 06:00 AM.
 *
 * Un vuelo también puede estar cancelado dinámicamente durante la simulación.
 * Esto lo maneja el flag {@link #cancelled}, que afecta la factibilidad de
 * las rutas que lo incluyan.
 */
public class Flight {

    /** Identificador único del vuelo, p. ej. "F123". */
    private final String id;

    /** Aeropuerto de origen. */
    private final Airport origin;

    /** Aeropuerto de destino. */
    private final Airport destination;

    /** Capacidad máxima en maletas. */
    private final int capacity;

    /** Tiempo de tránsito en DÍAS (0.5 intracontinental, 1.0 intercontinental). */
    private final double transitTime;

    /** Hora de salida como fracción del día (0.0 = 00:00, 1.0 = 24:00). */
    private final double departureTime;

    /** Maletas actualmente asignadas al vuelo. Se actualiza durante la búsqueda. */
    private int assignedLoad;

    /** Flag de cancelación dinámica (para replanificación). */
    private boolean cancelled;

    public Flight(String id, Airport origin, Airport destination,
                  int capacity, double departureTime) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.capacity = capacity;
        this.departureTime = departureTime;
        this.assignedLoad = 0;
        this.cancelled = false;

        // El tiempo de tránsito se fija según los continentes, conforme al enunciado
        boolean sameCont = origin.getContinent() == destination.getContinent();
        this.transitTime = sameCont ? 0.5 : 1.0;
    }

    /**
     * Constructor con hora de llegada real (leída de planes_vuelo.txt).
     * El transitTime se calcula como arrivalTime - departureTime.
     * Si el resultado es ≤ 0 (lo cual no debería ocurrir tras ajustar por día siguiente),
     * se usa el valor por defecto del enunciado.
     */
    public Flight(String id, Airport origin, Airport destination,
                  int capacity, double departureTime, double arrivalTime) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.capacity = capacity;
        this.departureTime = departureTime;
        this.assignedLoad = 0;
        this.cancelled = false;

        double computed = arrivalTime - departureTime;
        if (computed > 0) {
            this.transitTime = computed;
        } else {
            // Fallback al valor del enunciado
            boolean sameCont = origin.getContinent() == destination.getContinent();
            this.transitTime = sameCont ? 0.5 : 1.0;
        }
    }

    /** Indica si el vuelo es intracontinental (origen y destino mismo continente). */
    public boolean isSameContinent() {
        return origin.getContinent() == destination.getContinent();
    }

    /** Capacidad restante (maletas) del vuelo tras considerar lo ya asignado. */
    public int remainingCapacity() {
        return capacity - assignedLoad;
    }

    /**
     * Comprueba si es factible asignar {@code quantity} maletas: el vuelo no
     * debe estar cancelado y debe haber capacidad suficiente.
     */
    public boolean canAssign(int quantity) {
        return !cancelled && assignedLoad + quantity <= capacity;
    }

    /** Asigna {@code quantity} maletas a este vuelo (suma al load actual). */
    public void assign(int quantity) {
        this.assignedLoad += quantity;
    }

    /** Retira {@code quantity} maletas, sin permitir que el load baje de 0. */
    public void unassign(int quantity) {
        this.assignedLoad = Math.max(0, this.assignedLoad - quantity);
    }

    /** Hora de llegada = hora de salida + tiempo de tránsito (en días). */
    public double getArrivalTime() {
        return departureTime + transitTime;
    }

    // ----------------- Getters y setters -----------------
    public String getId() { return id; }
    public Airport getOrigin() { return origin; }
    public Airport getDestination() { return destination; }
    public int getCapacity() { return capacity; }
    public double getTransitTime() { return transitTime; }
    public double getDepartureTime() { return departureTime; }
    public int getAssignedLoad() { return assignedLoad; }
    public void setAssignedLoad(int load) { this.assignedLoad = load; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public String toString() {
        return id + ": " + origin.getCode() + " -> " + destination.getCode()
                + " [cap=" + capacity + ", load=" + assignedLoad
                + ", dep=" + String.format("%.2f", departureTime) + "]"
                + (cancelled ? " CANCELADO" : "");
    }
}
