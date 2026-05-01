package pe.edu.pucp.tasf.model;


public class Airport {

    /** Código ICAO (4 letras) o IATA (3 letras) del aeropuerto. Inmutable. */
    private final String code;           // p. ej. "SPIM", "EDDI", "OMDB"

    /** Nombre legible de la ciudad. */
    private final String city;

    /** Continente al que pertenece el aeropuerto. */
    private final Continent continent;

    /** Capacidad máxima del almacén (en maletas). Rango típico: [500, 800]. */
    private final int warehouseCapacity;

    /** Cantidad de maletas actualmente almacenadas en el aeropuerto. */
    private int currentStock;

    /**
     * Crea un aeropuerto con código, ciudad, continente y capacidad dados.
     * El stock inicial es 0.
     */
    public Airport(String code, String city, Continent continent, int warehouseCapacity) {
        this.code = code;
        this.city = city;
        this.continent = continent;
        this.warehouseCapacity = warehouseCapacity;
        this.currentStock = 0;
    }

    /**
     * Indica si el almacén puede aceptar {@code quantity} maletas más sin
     * superar su capacidad máxima.
     */
    public boolean canStore(int quantity) {
        return currentStock + quantity <= warehouseCapacity;
    }

    /** Espacio libre disponible (maletas) en el almacén. */
    public int availableSpace() {
        return warehouseCapacity - currentStock;
    }

    /** Agrega maletas al stock actual. No verifica capacidad. */
    public void addStock(int quantity) {
        this.currentStock += quantity;
    }

    /** Retira maletas del stock actual, sin permitir que baje de 0. */
    public void removeStock(int quantity) {
        this.currentStock = Math.max(0, this.currentStock - quantity);
    }

    // ----------------- Getters y setters -----------------
    public String getCode() { return code; }
    public String getCity() { return city; }
    public Continent getContinent() { return continent; }
    public int getWarehouseCapacity() { return warehouseCapacity; }
    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int stock) { this.currentStock = stock; }

    @Override
    public String toString() {
        return code + " (" + city + ", " + continent + ")";
    }

    /**
     * Dos aeropuertos son iguales si y sólo si comparten el mismo código.
     * Permite usar {@code Airport} como clave en Sets / Maps.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Airport other)) return false;
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}
