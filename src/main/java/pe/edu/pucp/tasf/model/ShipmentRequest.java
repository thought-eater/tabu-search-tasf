package pe.edu.pucp.tasf.model;

/**
 * Representa una solicitud de envío de maletas (demanda) del cliente.
 *
 * En la formulación del problema, cada demanda se modela como la tupla:
 *     dk = (ok, sk, qk, TWk)
 * donde:
 *   - ok  = aeropuerto origen
 *   - sk  = aeropuerto destino
 *   - qk  = cantidad de maletas del envío
 *   - TWk = ventana / plazo de entrega (deadline en días)
 *
 * El plazo de entrega se deriva automáticamente según el enunciado:
 *   - 1 día  si origen y destino están en el mismo continente
 *   - 2 días si están en continentes distintos
 *
 * El tiempo de creación {@code creationTime} se expresa como fracción de día
 * (0.0 = inicio del día 0). Si el problema se simula durante varios días,
 * este valor puede ser mayor que 1 (p. ej. 2.25 = día 2 a las 06:00).
 */
public class ShipmentRequest {

    /** Identificador único del envío (p. ej. "E000000001"). */
    private final String id;

    /** Aeropuerto de origen (ok). */
    private final Airport origin;

    /** Aeropuerto de destino (sk). */
    private final Airport destination;

    /** Cantidad de maletas solicitadas (qk). */
    private final int quantity;

    /** Plazo máximo de entrega en días (TWk): 1.0 o 2.0 según continentes. */
    private final double deadline;

    /** Instante de creación del envío, expresado en días desde la fecha base. */
    private final double creationTime;

    /**
     * Crea una solicitud de envío. El plazo se calcula automáticamente a partir
     * de los continentes de origen y destino.
     */
    public ShipmentRequest(String id, Airport origin, Airport destination,
                           int quantity, double creationTime) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.quantity = quantity;
        this.creationTime = creationTime;

        // Deadline según el enunciado: 1 día mismo continente, 2 días distinto.
        boolean sameCont = origin.getContinent() == destination.getContinent();
        this.deadline = sameCont ? 1.0 : 2.0;
    }

    /** Indica si, a la hora {@code currentTime}, el envío ya está vencido. */
    public boolean isOverdue(double currentTime) {
        return (currentTime - creationTime) > deadline;
    }

    /** Tiempo restante (en días) antes de vencer el plazo. */
    public double timeRemaining(double currentTime) {
        return deadline - (currentTime - creationTime);
    }

    /** Indica si origen y destino están en el mismo continente. */
    public boolean isSameContinent() {
        return origin.getContinent() == destination.getContinent();
    }

    // ----------------- Getters -----------------
    public String getId() { return id; }
    public Airport getOrigin() { return origin; }
    public Airport getDestination() { return destination; }
    public int getQuantity() { return quantity; }
    public double getDeadline() { return deadline; }
    public double getCreationTime() { return creationTime; }

    @Override
    public String toString() {
        return id + ": " + origin.getCode() + " -> " + destination.getCode()
                + " [qty=" + quantity + ", deadline=" + deadline + "d]";
    }
}
