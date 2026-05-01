package pe.edu.pucp.tasf.algorithm;

import java.util.Objects;

/**
 * Representa un movimiento en la búsqueda tabú.
 *
 * Un movimiento se identifica por el par:
 *   - índice de la solicitud cuya ruta fue modificada,
 *   - hash de la nueva ruta (secuencia de vuelos).
 *
 * Esta identificación permite que la lista tabú evite revisitar asignaciones
 * de ruta recientemente exploradas. Si la misma solicitud vuelve a recibir
 * exactamente la misma ruta dentro de la ventana tabú, el movimiento es
 * considerado tabú.
 */
public class TabuMove {

    /** Índice de la solicitud de envío que se está reruteando. */
    private final int requestIndex;

    /** Hash de la nueva ruta propuesta. */
    private final int routeHash;

    public TabuMove(int requestIndex, int routeHash) {
        this.requestIndex = requestIndex;
        this.routeHash = routeHash;
    }

    public int getRequestIndex() { return requestIndex; }
    public int getRouteHash() { return routeHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TabuMove other)) return false;
        return requestIndex == other.requestIndex && routeHash == other.routeHash;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestIndex, routeHash);
    }

    @Override
    public String toString() {
        return "Mov[req=" + requestIndex + ", ruta=" + routeHash + "]";
    }
}
