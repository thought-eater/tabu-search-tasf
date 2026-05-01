package pe.edu.pucp.tasf.algorithm;

/**
 * Parámetros de configuración del algoritmo Tabu Search.
 *
 * Todos los parámetros son ajustables para experimentación numérica (requisito
 * no funcional (b) del enunciado: los dos algoritmos deben ser del tipo
 * metaheurístico y evaluados por experimentación).
 *
 * La clase usa el patrón builder encadenable para una configuración fluida:
 *
 *   new TabuSearchConfig()
 *       .maxIterations(1000)
 *       .tabuTenure(20)
 *       .timeLimitMs(30 * 60 * 1000);
 */
public class TabuSearchConfig {

    // ================= Parámetros de la búsqueda =================

    /** Iteraciones máximas del bucle principal. */
    private int maxIterations = 1000;

    /** Permanencia de un movimiento en la lista tabú (en iteraciones). */
    private int tabuTenure = 15;

    /** Cantidad de candidatos evaluados por iteración en el vecindario. */
    private int neighborhoodSize = 20;

    /** Máximo número de escalas (vuelos) por ruta construida. */
    private int maxHops = 3;

    /** Cada cuántas iteraciones se dispara la diversificación. */
    private int diversificationInterval = 100;

    /** Cada cuántas iteraciones se dispara la intensificación. */
    private int intensificationInterval = 50;

    /** Umbral del criterio de aspiración (0.0 = aspirar si mejora el mejor global). */
    private double aspirationThreshold = 0.0;

    /** Tiempo máximo de ejecución (por defecto 90 min, para escenario E1). */
    private long timeLimitMs = 90 * 60 * 1000;

    /** Semilla del generador pseudoaleatorio (para reproducibilidad). */
    private long seed = 42;

    // ================= Pesos de penalización (soft constraints) =================

    /** Penalización por cada maleta entregada tarde. */
    private double penaltyLate = 1000.0;

    /** Penalización por violación de capacidad (ruta infactible). */
    private double penaltyCapacity = 5000.0;

    /** Penalización por cada día-maleta de retraso acumulado. */
    private double penaltyDelay = 100.0;

    // ================= Umbrales del semáforo =================
    // Colores solicitados por el requisito no funcional (c) del enunciado

    /** Si el % de maletas tardías ≤ 10%, el semáforo está en VERDE. */
    private double greenThreshold = 0.1;

    /** Si el % ≤ 30%, ÁMBAR; si > 30%, ROJO. */
    private double amberThreshold = 0.3;

    // ================= Builder (setters encadenables) =================

    public TabuSearchConfig maxIterations(int val)            { this.maxIterations = val; return this; }
    public TabuSearchConfig tabuTenure(int val)               { this.tabuTenure = val; return this; }
    public TabuSearchConfig neighborhoodSize(int val)         { this.neighborhoodSize = val; return this; }
    public TabuSearchConfig maxHops(int val)                  { this.maxHops = val; return this; }
    public TabuSearchConfig diversificationInterval(int val)  { this.diversificationInterval = val; return this; }
    public TabuSearchConfig intensificationInterval(int val)  { this.intensificationInterval = val; return this; }
    public TabuSearchConfig timeLimitMs(long val)             { this.timeLimitMs = val; return this; }
    public TabuSearchConfig seed(long val)                    { this.seed = val; return this; }
    public TabuSearchConfig penaltyLate(double val)           { this.penaltyLate = val; return this; }
    public TabuSearchConfig penaltyCapacity(double val)       { this.penaltyCapacity = val; return this; }
    public TabuSearchConfig greenThreshold(double val)        { this.greenThreshold = val; return this; }
    public TabuSearchConfig amberThreshold(double val)        { this.amberThreshold = val; return this; }

    // ================= Getters =================

    public int getMaxIterations()           { return maxIterations; }
    public int getTabuTenure()              { return tabuTenure; }
    public int getNeighborhoodSize()        { return neighborhoodSize; }
    public int getMaxHops()                 { return maxHops; }
    public int getDiversificationInterval() { return diversificationInterval; }
    public int getIntensificationInterval() { return intensificationInterval; }
    public double getAspirationThreshold()  { return aspirationThreshold; }
    public long getTimeLimitMs()            { return timeLimitMs; }
    public long getSeed()                   { return seed; }
    public double getPenaltyLate()          { return penaltyLate; }
    public double getPenaltyCapacity()      { return penaltyCapacity; }
    public double getPenaltyDelay()         { return penaltyDelay; }
    public double getGreenThreshold()       { return greenThreshold; }
    public double getAmberThreshold()       { return amberThreshold; }
}
