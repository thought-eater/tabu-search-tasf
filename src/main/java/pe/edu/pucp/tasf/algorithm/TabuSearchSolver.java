package pe.edu.pucp.tasf.algorithm;

import pe.edu.pucp.tasf.model.*;
// SLF4J: Descomentar las siguientes líneas para usar logging profesional
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Metaheurística Tabu Search para el problema de ruteo de maletas de Tasf.B2B.
 *
 * ESTRUCTURAS DE VECINDAD SOPORTADAS:
 *   1. REROUTE: cambiar la secuencia de vuelos de un envío individual.
 *   2. (Extensible) SWAP: intercambiar rutas entre dos envíos con orígen/destino
 *                   compatibles.
 *   3. (Extensible) SPLIT: dividir un envío en varias rutas cuando un único vuelo
 *                   no tiene capacidad suficiente.
 *
 * ESTRUCTURAS DE MEMORIA:
 *   - Lista tabú (corto plazo): evita revisitar movimientos recientemente explorados.
 *   - Criterio de aspiración: permite aceptar un movimiento tabú si mejora la
 *     mejor solución global conocida hasta el momento.
 *   - Diversificación: perturba periódicamente la solución para explorar nuevas
 *     regiones del espacio de búsqueda.
 *   - Intensificación: retorna a la mejor solución conocida para explorar su
 *     vecindario con mayor detalle.
 *
 * Referencias metodológicas:
 *   - Taillard et al. (1997) - Tabu search for VRP with soft time windows
 *   - Gmira et al. (2021)    - Time-dependent VRP with time windows
 */
public class TabuSearchSolver {

    // SLF4J: Descomentar la siguiente línea y reemplazar System.out.printf/println por logger.info(...)
    // private static final Logger logger = LoggerFactory.getLogger(TabuSearchSolver.class);

    // ================ Entradas del algoritmo ================

    private final LogisticsNetwork network;
    private final List<ShipmentRequest> requests;
    private final TabuSearchConfig config;
    private final Random random;

    // ================ Memoria tabú ================

    /** Mapa: movimiento -> iteración en la que expira su estado tabú. */
    private final Map<TabuMove, Integer> tabuList;

    // ================ Rutas precomputadas ================

    /**
     * Mapa de rutas candidatas por envío, precomputado al inicio de cada solve().
     *
     * Se construye una sola vez antes del bucle TS usando el {@code creationTime}
     * exacto de cada envío como {@code startTime} del DFS. Esto garantiza que
     * ninguna ruta retornada contiene vuelos que hayan despegado antes de que
     * el envío exista.
     *
     * Durante el bucle TS todas las consultas son O(1) — un simple lookup en este mapa.
     */
    private Map<ShipmentRequest, List<List<Flight>>> candidateRoutes = new HashMap<>();

    // ================ Estado interno de la búsqueda ================

    private Solution currentSolution;
    private Solution bestSolution;
    private double bestFitness;
    private int iteration;

    // ================ Estadísticas ================

    private final List<Double> fitnessHistory;
    private int improvementCount;
    private long startTimeMs;

    public TabuSearchSolver(LogisticsNetwork network, List<ShipmentRequest> requests,
                            TabuSearchConfig config) {
        this.network = network;
        this.requests = requests;
        this.config = config;
        this.random = new Random(config.getSeed());
        this.tabuList = new HashMap<>();
        this.fitnessHistory = new ArrayList<>();
        this.improvementCount = 0;
    }

    /**
     * Ejecuta el algoritmo Tabu Search completo y devuelve la mejor solución
     * encontrada dentro del presupuesto de iteraciones / tiempo configurado.
     */
    public Solution solve() {
        startTimeMs = System.currentTimeMillis();
        System.out.println("=== Tabu Search iniciado ===");
        System.out.printf("Envíos: %d, Aeropuertos: %d, Vuelos: %d%n",
                requests.size(), network.getAirportCount(), network.getFlightCount());

        // Paso 1: precomputar rutas candidatas para todos los envíos
        precomputeRoutes(requests);

        // Paso 2: construir la solución inicial con heurística greedy
        currentSolution = generateInitialSolution();
        bestSolution = currentSolution.copy();
        bestFitness  = bestSolution.getFitness();

        System.out.println("Solución inicial: " + currentSolution);
        fitnessHistory.add(bestFitness);

        // Paso 3: bucle principal de Tabu Search
        for (iteration = 1; iteration <= config.getMaxIterations(); iteration++) {

            // Chequeo del límite de tiempo
            if (isTimeLimitReached()) {
                System.out.println("Límite de tiempo alcanzado en iteración " + iteration);
                break;
            }

            // Generar vecindario y elegir el mejor movimiento no tabú
            MoveCandidateResult bestCandidate = exploreNeighborhood();

            if (bestCandidate != null) {
                // Aplicar el movimiento elegido sobre la solución actual
                applyMove(bestCandidate);

                // Insertar el movimiento en la lista tabú
                addToTabuList(bestCandidate.move);

                // ¿Mejoró el mejor global?
                double currentFitness = currentSolution.getFitness();
                if (currentFitness < bestFitness) {
                    bestSolution = currentSolution.copy();
                    bestFitness  = currentFitness;
                    improvementCount++;
                    System.out.printf("Iter %d: NUEVO MEJOR fitness=%.2f, tardías=%d%n",
                            iteration, bestFitness, bestSolution.getLateCount());
                }
            }

            // Diversificación: cada N iteraciones, perturbar la solución
            if (iteration % config.getDiversificationInterval() == 0) {
                diversify();
            }

            // Intensificación: cada M iteraciones, volver a la mejor conocida
            if (iteration % config.getIntensificationInterval() == 0) {
                intensify();
            }

            // Purgar entradas tabú expiradas
            purgeTabuList();

            // Histórico de aptitud (para gráficas posteriores)
            fitnessHistory.add(currentSolution.getFitness());

            // Log periódico
            if (iteration % 100 == 0) {
                System.out.printf("Iter %d: fitness actual=%.2f, mejor=%.2f, |tabú|=%d%n",
                        iteration, currentSolution.getFitness(), bestFitness, tabuList.size());
            }
        }

        long elapsed = System.currentTimeMillis() - startTimeMs;
        System.out.println("=== Tabu Search finalizado ===");
        System.out.printf("Iteraciones: %d, Mejoras: %d, Tiempo: %d ms%n",
                iteration - 1, improvementCount, elapsed);
        System.out.println("Mejor solución: " + bestSolution);
        System.out.println("Estado del semáforo: " + getSemaphoreStatus(bestSolution));

        return bestSolution;
    }

    // ========================= SOLUCIÓN INICIAL =========================

    /**
     * Genera una solución inicial factible mediante una heurística greedy.
     * Para cada envío (ordenado por urgencia) busca la ruta más corta factible.
     */
    private Solution generateInitialSolution() {
        Solution solution = new Solution();
        network.resetLoads();

        // Ordenar envíos por urgencia (deadline ascendente) y luego por cantidad desc.
        // Los envíos más apretados se asignan primero para asegurarles buena ruta.
        List<ShipmentRequest> sorted = new ArrayList<>(requests);
        sorted.sort(Comparator.comparingDouble(ShipmentRequest::getDeadline)
                .thenComparing(Comparator.comparingInt(ShipmentRequest::getQuantity).reversed()));

        for (ShipmentRequest req : sorted) {
            RouteAssignment bestRoute = findBestGreedyRoute(req);
            if (bestRoute != null) {
                // Asignar la carga a los vuelos escogidos (ocupa capacidad)
                for (Flight f : bestRoute.getFlights()) {
                    f.assign(req.getQuantity());
                }
                solution.addRoute(bestRoute);
            } else {
                // Si no se encontró ruta factible, se agrega una ruta vacía (penalizada)
                solution.addRoute(new RouteAssignment(req));
            }
        }
        return solution;
    }

    /**
     * Busca la mejor ruta greedy para un envío: prueba todas las rutas hasta
     * {@code maxHops} saltos y elige la de menor tiempo de tránsito que
     * respete las capacidades actuales.
     */
    private RouteAssignment findBestGreedyRoute(ShipmentRequest req) {
        List<List<Flight>> allRoutes = getRoutes(req);

        RouteAssignment best = null;
        double bestTime = Double.MAX_VALUE;

        for (List<Flight> path : allRoutes) {
            // Factibilidad: todos los vuelos deben tener capacidad suficiente
            boolean feasible = true;
            for (Flight f : path) {
                if (!f.canAssign(req.getQuantity())) {
                    feasible = false;
                    break;
                }
            }
            if (!feasible) continue;

            RouteAssignment candidate = new RouteAssignment(req, path);
            double transit = candidate.getTotalTransitTime();
            if (transit < bestTime) {
                bestTime = transit;
                best = candidate;
            }
        }
        return best;
    }

    // ========================= PRECOMPUTACIÓN DE RUTAS =========================

    /**
     * Construye el mapa de rutas candidatas para todos los envíos dados.
     *
     * Se llama una vez antes del bucle TS (y de nuevo en replanify() para los
     * envíos afectados por cancelaciones). Usa el {@code creationTime} exacto
     * de cada envío como {@code startTime} del DFS, garantizando que ninguna
     * ruta retornada contenga vuelos que hayan despegado antes de que el envío
     * exista.
     *
     * Los virtual flights necesarios se materializan aquí, durante la fase de
     * construcción, y quedan estables durante todo el bucle de búsqueda.
     */
    private void precomputeRoutes(List<ShipmentRequest> reqs) {
        for (ShipmentRequest req : reqs) {
            List<List<Flight>> routes = network.findRoutes(
                    req.getOrigin(), req.getDestination(),
                    req.getCreationTime(), config.getMaxHops(), MAX_RUTAS_POR_CONSULTA);
            candidateRoutes.put(req, routes);
        }
    }

    /**
     * Devuelve las rutas precomputadas para un envío.
     * O(1) — simple lookup en el mapa construido antes del bucle TS.
     */
    private List<List<Flight>> getRoutes(ShipmentRequest req) {
        List<List<Flight>> routes = candidateRoutes.get(req);
        return routes != null ? routes : Collections.emptyList();
    }

    /**
     * Número máximo de rutas a retener por envío en la precomputación.
     * Limita la explosión combinatoria en redes densas con maxHops ≥ 3.
     */
    private static final int MAX_RUTAS_POR_CONSULTA = 25;

    // ========================= EXPLORACIÓN DEL VECINDARIO =========================

    /**
     * Genera un vecindario de la solución actual probando rutas alternativas
     * para una muestra aleatoria de envíos. Devuelve el mejor candidato
     * (considerando el criterio de aspiración).
     *
     * IMPORTANTE: la evaluación de fitness se hace de forma "ligera" sin
     * modificar el estado de carga de los vuelos, para no corromper el estado
     * actual mientras exploramos. Solo se evalúan tardanza e infactibilidad
     * de capacidad respecto a la carga ACTUAL (antes de aplicar el movimiento).
     */
    private MoveCandidateResult exploreNeighborhood() {
        MoveCandidateResult bestCandidate = null;
        double bestCandidateFitness = Double.MAX_VALUE;

        int solutionSize = currentSolution.size();
        if (solutionSize == 0) return null;

        // Seleccionar índices aleatorios sin repetición para los candidatos.
        // Para listas grandes (>3× el tamaño del vecindario) se usa muestreo directo
        // en O(k) en lugar de crear y mezclar la lista completa en O(n), lo que
        // evita asignaciones innecesarias de memoria cuando la solución tiene miles
        // de envíos.
        // Escalar el vecindario al tamaño del problema: al menos neighborhoodSize,
        // pero mínimo n/10 (hasta 500) para garantizar cobertura en instancias grandes.
        int effectiveSize = Math.max(config.getNeighborhoodSize(),
                Math.min(500, solutionSize / 10));
        int candidateCount = Math.min(effectiveSize, solutionSize);
        Set<Integer> selectedSet = new LinkedHashSet<>(candidateCount * 2);
        if (candidateCount * 3 < solutionSize) {
            // Muestreo aleatorio directo: O(k) amortizado
            int intentos = 0;
            while (selectedSet.size() < candidateCount && intentos < candidateCount * 10) {
                selectedSet.add(random.nextInt(solutionSize));
                intentos++;
            }
        } else {
            // Para listas pequeñas el shuffle completo sigue siendo aceptable
            List<Integer> todos = new ArrayList<>(solutionSize);
            for (int i = 0; i < solutionSize; i++) todos.add(i);
            Collections.shuffle(todos, random);
            for (int i = 0; i < candidateCount; i++) selectedSet.add(todos.get(i));
        }
        List<Integer> indices = new ArrayList<>(selectedSet);

        for (int c = 0; c < candidateCount; c++) {
            int reqIndex = indices.get(c);
            RouteAssignment currentRoute = currentSolution.getRoute(reqIndex);
            ShipmentRequest req = currentRoute.getRequest();

            List<List<Flight>> alternatives = getRoutes(req);

            for (List<Flight> altPath : alternatives) {
                // Descartar ruta idéntica a la actual
                if (altPath.size() == currentRoute.getFlights().size()
                        && altPath.equals(currentRoute.getFlights())) continue;

                // Verificar capacidad para la ruta alternativa:
                // liberar la carga de la ruta vieja en los vuelos que cambian,
                // verificar, y luego restaurar. Se hace in-place para evitar
                // crear copias de toda la solución.
                RouteAssignment oldRoute = currentRoute;
                int qty = req.getQuantity();

                // Liberar carga antigua temporalmente
                for (Flight f : oldRoute.getFlights()) f.unassign(qty);

                // Evaluar si la ruta nueva es factible en capacidad
                boolean capOk = altPath.stream().allMatch(f -> f.canAssign(qty));

                // Restaurar carga antigua
                for (Flight f : oldRoute.getFlights()) f.assign(qty);

                if (!capOk) continue; // ruta sin capacidad: saltar

                RouteAssignment altRoute = new RouteAssignment(req, altPath);

                // Fitness delta: diferencia entre la ruta nueva y la vieja
                double oldFitness = routeFitness(oldRoute);
                double newFitness = routeFitness(altRoute);
                double candidateFitness = currentSolution.getFitness() - oldFitness + newFitness;

                int routeHash = altPath.stream().mapToInt(f -> f.getId().hashCode())
                        .reduce(1, (a, b) -> 31 * a + b);
                TabuMove move = new TabuMove(reqIndex, routeHash);
                boolean isTabu = tabuList.containsKey(move) && tabuList.get(move) > iteration;

                if (isTabu && candidateFitness >= bestFitness) continue;

                if (candidateFitness < bestCandidateFitness) {
                    bestCandidateFitness = candidateFitness;
                    bestCandidate = new MoveCandidateResult(move, reqIndex, altRoute, candidateFitness);
                }
            }
        }
        return bestCandidate;
    }

    /**
     * Calcula el fitness parcial de una sola RouteAssignment (sin depender del
     * estado global de cargas). Usado para evaluación delta en el vecindario.
     *
     * La lógica espeja exactamente la de {@link Solution#getFitness()} para que
     * el cálculo delta sea correcto:
     *   candidateFitness = currentFitness - routeFitness(old) + routeFitness(new)
     */
    private double routeFitness(RouteAssignment ra) {
        final double PENALTY_LATE       = 1000.0;
        final double PENALTY_DELAY      = 100.0;
        final double PENALTY_INFEASIBLE = 5000.0;
        double f = 0;
        int qty = ra.getRequest().getQuantity();
        if (!ra.isOnTime()) {
            f += PENALTY_LATE * qty;
            f += PENALTY_DELAY * ra.getDelay() * qty;
        }
        if (!ra.isFeasible()) {
            f += PENALTY_INFEASIBLE * qty;
        }
        return f;
    }

    /** Construye una solución candidata sustituyendo sólo una ruta. */
    private Solution buildCandidateSolution(int index, RouteAssignment newRoute) {
        Solution candidate = currentSolution.copy();
        candidate.setRoute(index, newRoute);
        return candidate;
    }

    // ========================= APLICACIÓN DE UN MOVIMIENTO =========================

    /**
     * Aplica el movimiento {@code candidate} sobre la solución actual:
     *  1. libera la carga de los vuelos de la ruta antigua
     *  2. asigna la carga a los vuelos de la ruta nueva
     *  3. reemplaza la ruta en la solución
     */
    private void applyMove(MoveCandidateResult candidate) {
        RouteAssignment oldRoute = currentSolution.getRoute(candidate.requestIndex);
        for (Flight f : oldRoute.getFlights()) {
            f.unassign(oldRoute.getRequest().getQuantity());
        }

        for (Flight f : candidate.newRoute.getFlights()) {
            f.assign(candidate.newRoute.getRequest().getQuantity());
        }

        currentSolution.setRoute(candidate.requestIndex, candidate.newRoute);
    }

    // ========================= GESTIÓN DE LA LISTA TABÚ =========================

    /** Añade un movimiento a la lista tabú con vencimiento = iter actual + tenencia. */
    private void addToTabuList(TabuMove move) {
        tabuList.put(move, iteration + config.getTabuTenure());
    }

    /** Elimina de la lista tabú las entradas cuyo vencimiento ya pasó. */
    private void purgeTabuList() {
        tabuList.entrySet().removeIf(entry -> entry.getValue() <= iteration);
    }

    // ========================= DIVERSIFICACIÓN & INTENSIFICACIÓN =========================

    /**
     * Diversificación: perturba aleatoriamente un 20% de las rutas de la
     * solución actual, asignando una ruta aleatoria (entre las factibles)
     * a cada uno. Objetivo: escapar de óptimos locales.
     */
    private void diversify() {
        int perturbCount = Math.max(1, currentSolution.size() / 5);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < currentSolution.size(); i++) indices.add(i);
        Collections.shuffle(indices, random);

        network.resetLoads();

        for (int i = 0; i < Math.min(perturbCount, indices.size()); i++) {
            int idx = indices.get(i);
            ShipmentRequest req = currentSolution.getRoute(idx).getRequest();
            // Usar rutas precomputadas (reutiliza los resultados DFS ya calculados durante la perturbación)
            List<List<Flight>> routes = getRoutes(req);

            if (!routes.isEmpty()) {
                List<Flight> randomPath = routes.get(random.nextInt(routes.size()));
                currentSolution.setRoute(idx, new RouteAssignment(req, randomPath));
            }
        }

        reassignAllLoads();
    }

    /**
     * Intensificación: descarta la solución actual y vuelve a partir del mejor
     * óptimo global conocido, para explorar su vecindario con nuevas iteraciones.
     */
    private void intensify() {
        currentSolution = bestSolution.copy();
        reassignAllLoads();
    }

    /**
     * Recalcula la ocupación de todos los vuelos y almacenes a partir de la
     * solución actual. Necesario tras diversificar/intensificar para que las
     * restricciones de capacidad queden coherentes.
     */
    private void reassignAllLoads() {
        network.resetLoads();
        for (RouteAssignment ra : currentSolution.getRoutes()) {
            for (Flight f : ra.getFlights()) {
                f.assign(ra.getRequest().getQuantity());
            }
        }
    }

    // ========================= REPLANIFICACIÓN DINÁMICA =========================

    /**
     * Replanifica la solución tras la cancelación de un vuelo.
     *
     * Pasos:
     *  1. Marca el vuelo como cancelado en la red.
     *  2. Detecta todos los envíos afectados (los que pasaban por ese vuelo).
     *  3. Reasigna una ruta greedy a cada afectado.
     *  4. Ejecuta un Tabu Search focalizado (menos iteraciones) para afinar.
     */
    public Solution replanify(Solution currentSol, String cancelledFlightId) {
        System.out.println("Replanificación disparada por cancelación de: " + cancelledFlightId);
        network.cancelFlight(cancelledFlightId);

        // Identificar los envíos afectados
        List<Integer> affected = new ArrayList<>();
        for (int i = 0; i < currentSol.size(); i++) {
            for (Flight f : currentSol.getRoute(i).getFlights()) {
                if (f.getId().equals(cancelledFlightId)) {
                    affected.add(i);
                    break;
                }
            }
        }

        System.out.println("Envíos afectados: " + affected.size());

        // Recomputar rutas para los envíos afectados (el vuelo cancelado ya no aparecerá)
        List<ShipmentRequest> affectedReqs = new ArrayList<>();
        for (int idx : affected) affectedReqs.add(currentSol.getRoute(idx).getRequest());
        precomputeRoutes(affectedReqs);

        // Reruteo greedy de cada afectado
        for (int idx : affected) {
            ShipmentRequest req = currentSol.getRoute(idx).getRequest();
            RouteAssignment newRoute = findBestGreedyRoute(req);
            if (newRoute != null) {
                currentSol.setRoute(idx, newRoute);
            }
        }

        // Tabu Search focalizado (100 iteraciones o menos) para afinar
        this.currentSolution = currentSol;
        this.bestSolution = currentSol.copy();
        this.bestFitness  = bestSolution.getFitness();

        int replanIter = Math.min(100, config.getMaxIterations());

        for (iteration = 1; iteration <= replanIter; iteration++) {
            MoveCandidateResult best = exploreNeighborhood();
            if (best != null) {
                applyMove(best);
                addToTabuList(best.move);
                double fitness = currentSolution.getFitness();
                if (fitness < bestFitness) {
                    bestSolution = currentSolution.copy();
                    bestFitness  = fitness;
                }
            }
        }

        System.out.println("Replanificación completada. Nueva solución: " + bestSolution);
        return bestSolution;
    }

    // ========================= SEMÁFORO =========================

    /**
     * Devuelve el estado del semáforo (GREEN/AMBER/RED) según el porcentaje
     * de maletas entregadas fuera de plazo. Los umbrales son configurables.
     */
    public String getSemaphoreStatus(Solution solution) {
        if (solution.getTotalSuitcases() == 0) return "GREEN";
        double lateRatio = (double) solution.getLateCount() / solution.getTotalSuitcases();
        if (lateRatio <= config.getGreenThreshold()) return "GREEN";
        if (lateRatio <= config.getAmberThreshold()) return "AMBER";
        return "RED";
    }

    // ========================= REPORTES =========================

    public List<Double> getFitnessHistory() {
        return Collections.unmodifiableList(fitnessHistory);
    }

    public int getImprovementCount() { return improvementCount; }
    public int getIterationsRun()    { return iteration - 1; }

    private boolean isTimeLimitReached() {
        return (System.currentTimeMillis() - startTimeMs) >= config.getTimeLimitMs();
    }

    /** Imprime un reporte detallado de la mejor solución encontrada. */
    public void printReport() {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        System.out.println("\n========== REPORTE TABU SEARCH ==========");
        System.out.println("Iteraciones ejecutadas: " + getIterationsRun());
        System.out.println("Mejoras registradas:    " + improvementCount);
        System.out.println("Total solicitudes:      " + sol.getTotalRequests());
        System.out.println("Total maletas:          " + sol.getTotalSuitcases());
        System.out.println("Maletas entregadas:     " + sol.getDeliveredCount());
        System.out.println("Maletas no entregadas:  " + sol.getUndeliveredCount());
        System.out.println("Maletas tardías:        " + sol.getLateCount());
        System.out.printf ("Retraso total:          %.2f días%n", sol.getTotalDelay());
        System.out.println("Overflow capacidad:     " + sol.getCapacityOverflow());
        System.out.println("Overflow almacén:       " + sol.getWarehouseOverflow());
        System.out.printf ("Fitness:                %.2f%n", sol.getFitness());
        System.out.println("Semáforo:               " + getSemaphoreStatus(sol));
        System.out.printf ("Tiempo de ejecución:    %d ms%n", elapsed);
        System.out.println();

        // Para instancias grandes (miles de envíos) conviene silenciar el detalle
        // de rutas para que el log no sea inmanejable. Se imprime sólo si
        // hay <= 100 rutas.
        if (sol.size() <= 100) {
            System.out.println("--- Detalle de rutas ---");
            for (RouteAssignment ra : sol.getRoutes()) {
                System.out.println("  " + ra);
            }
        } else {
            System.out.println("(Detalle de rutas omitido: " + sol.size() + " rutas)");
        }
        System.out.println("=========================================\n");
    }

    /**
     * Devuelve una fila CSV con todas las métricas del experimento.
     * Cabecera: seed,TotalRequests,TotalSuitcases,Delivered,Undelivered,
     *           LateSuitcases,TotalDelay,CapacityOverflow,WarehouseOverflow,
     *           Fitness,Semaphore,IterationsRun,Improvements,ExecutionTime
     *
     * @param seed semilla usada en esta réplica
     */
    public String getCsvRow(long seed) {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return String.format("%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%.2f,%s,%d,%d,%d",
                seed,
                sol.getTotalRequests(),
                sol.getTotalSuitcases(),
                sol.getDeliveredCount(),
                sol.getUndeliveredCount(),
                sol.getLateCount(),
                sol.getTotalDelay(),
                sol.getCapacityOverflow(),
                sol.getWarehouseOverflow(),
                sol.getFitness(),
                getSemaphoreStatus(sol),
                getIterationsRun(),
                improvementCount,
                elapsed);
    }

    // ========================= CLASES INTERNAS =========================

    /** Contenedor inmutable con la información de un movimiento candidato. */
    private static class MoveCandidateResult {
        final TabuMove move;
        final int requestIndex;
        final RouteAssignment newRoute;
        final double fitness;

        MoveCandidateResult(TabuMove move, int requestIndex, RouteAssignment newRoute, double fitness) {
            this.move = move;
            this.requestIndex = requestIndex;
            this.newRoute = newRoute;
            this.fitness = fitness;
        }
    }
}
