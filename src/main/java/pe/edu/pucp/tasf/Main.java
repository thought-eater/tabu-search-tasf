package pe.edu.pucp.tasf;

import pe.edu.pucp.tasf.algorithm.TabuSearchConfig;
import pe.edu.pucp.tasf.algorithm.TabuSearchSolver;
import pe.edu.pucp.tasf.io.EnviosDataLoader;
import pe.edu.pucp.tasf.model.*;
import pe.edu.pucp.tasf.util.NetworkGenerator;
import pe.edu.pucp.tasf.util.RealNetworkBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Punto de entrada principal del planificador Tabu Search para Tasf.B2B.
 *
 * Soporta cuatro modos:
 *
 *   E1  - Simulación de periodo con DATOS SINTÉTICOS (3, 5 o 7 días)
 *         Ejecuta en 30-90 minutos. Usa {@link NetworkGenerator}.
 *
 *   E2  - Operación en tiempo real con replanificación (datos sintéticos)
 *
 *   E3  - Simulación hasta el colapso (datos sintéticos)
 *
 *   E1R - Simulación de periodo con DATOS REALES leídos desde los archivos
 *         _envios_XXXX_.txt. Permite fijar el día de inicio y la cantidad
 *         de días a simular.
 *
 * Uso:
 *   java pe.edu.pucp.tasf.Main E1  [días] [solicitudes/día]
 *   java pe.edu.pucp.tasf.Main E2  [_]    [solicitudes]
 *   java pe.edu.pucp.tasf.Main E3  [_]    [solicitudes base]
 *   java pe.edu.pucp.tasf.Main E1R <carpeta_envios> [díaInicio] [numDías]
 *   java pe.edu.pucp.tasf.Main EXP <carpeta_envios> [réplicas] [salida.csv]
 *
 * Ejemplo experimento numérico (30 réplicas, seeds 1-30):
 *   java pe.edu.pucp.tasf.Main EXP ./_envios_preliminar_ 30 E1_30_runs_TS.csv
 */
public class Main {

    public static void main(String[] args) {
        // Forzar salida estándar en UTF-8 para que los acentos, emojis del
        // semáforo y caracteres de caja (╔══╗) se vean correctamente en
        // cualquier terminal (Windows, Linux, macOS).
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // Si la JVM no soporta UTF-8 (caso muy raro), seguimos con el
            // encoding por defecto sin abortar la ejecución.
        }

        // Primer argumento: escenario (default = E1)
        String scenario = args.length > 0 ? args[0].toUpperCase() : "E1";

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Tasf.B2B - Tabu Search Planner      ║");
        System.out.println("║         Equipo 8H - PUCP 2026-1         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        // Despacho al escenario correspondiente
        switch (scenario) {
            case "E1" -> {
                // Escenario sintético: días + solicitudes/día
                int days = args.length > 1 ? Integer.parseInt(args[1]) : 5;
                int requestsPerDay = args.length > 2 ? Integer.parseInt(args[2]) : 50;
                runPeriodSimulation(days, requestsPerDay);
            }
            case "E2" -> {
                int requestsPerDay = args.length > 2 ? Integer.parseInt(args[2]) : 50;
                runRealTimeSimulation(requestsPerDay);
            }
            case "E3" -> {
                int requestsPerDay = args.length > 2 ? Integer.parseInt(args[2]) : 30;
                runCollapseSimulation(requestsPerDay);
            }
            case "E1R" -> {
                // Escenario con datos REALES
                // Argumentos: <carpeta_envios> [díaInicio] [numDías] [planes_vuelo.txt]
                if (args.length < 2) {
                    System.out.println("Uso: Main E1R <carpeta_envios> [díaInicio] [numDías] [planes_vuelo.txt]");
                    System.out.println("  carpeta_envios  : carpeta con archivos _envios_XXXX_.txt");
                    System.out.println("  díaInicio       : día 0-based de inicio (default 0)");
                    System.out.println("  numDías         : cantidad de días a simular (default 1)");
                    System.out.println("  planes_vuelo.txt: tabla de vuelos reales (opcional, busca en CWD)");
                    return;
                }
                String folder    = args[1];
                int startDay     = args.length > 2 ? Integer.parseInt(args[2]) : 0;
                int numDays      = args.length > 3 ? Integer.parseInt(args[3]) : 1;
                String flightsF  = args.length > 4 ? args[4] : null;
                Path flightsPath = resolveFlightsFile(flightsF);
                runPeriodSimulationReal(Paths.get(folder), startDay, numDays, flightsPath);
            }
            case "EXP" -> {
                // Modo experimentación numérica: N réplicas con seeds 1..N
                // Argumentos: <carpeta_envios> [réplicas=30] [salida.csv] [planes_vuelo.txt]
                if (args.length < 2) {
                    System.out.println("Uso: Main EXP <carpeta_envios> [réplicas] [salida.csv] [planes_vuelo.txt]");
                    System.out.println("  Si no se pasa planes_vuelo.txt, busca planes_vuelo.txt en el directorio actual.");
                    return;
                }
                String folder    = args[1];
                int    replicas  = args.length > 2 ? Integer.parseInt(args[2]) : 30;
                String outputCsv = args.length > 3 ? args[3] : "E1_30_runs_TS.csv";
                String flightsF  = args.length > 4 ? args[4] : null;
                Path flightsPath = resolveFlightsFile(flightsF);
                runExperiment(Paths.get(folder), replicas, outputCsv, null,
                        flightsPath != null ? flightsPath.toString() : null);
            }
            default -> {
                System.out.println("Uso: java -jar tabu-search-tasf.jar <E1|E2|E3|E1R|EXP> [...]");
                System.out.println("  E1  - Simulación sintética de periodo (default 5 días, 50 req/día)");
                System.out.println("  E2  - Operación en tiempo real con replanificación");
                System.out.println("  E3  - Simulación hasta el colapso");
                System.out.println("  E1R - Simulación de periodo con datos reales de _envios_XXXX_.txt");
                System.out.println("  EXP - Experimentación numérica: N réplicas con seeds 1..N → CSV");
            }
        }
    }

    /**
     * Resuelve la ruta al archivo de vuelos: usa la indicada explícitamente,
     * o busca "planes_vuelo.txt" en el directorio de trabajo actual como fallback.
     * Devuelve null si no se encuentra ninguno (se usará red sintética).
     */
    private static Path resolveFlightsFile(String explicit) {
        if (explicit != null) {
            Path p = Paths.get(explicit);
            if (java.nio.file.Files.isRegularFile(p)) return p;
            System.err.println("Advertencia: planes_vuelo.txt indicado no existe: " + explicit);
        }
        // Buscar en el directorio actual
        Path cwd = Paths.get("planes_vuelo.txt");
        if (java.nio.file.Files.isRegularFile(cwd)) {
            System.out.println("[Main] Usando planes_vuelo.txt del directorio actual: "
                    + cwd.toAbsolutePath());
            return cwd;
        }
        System.out.println("[Main] planes_vuelo.txt no encontrado — se usará red sintética.");
        return null;
    }

    // =====================================================================
    //  MODO EXP - EXPERIMENTACIÓN NUMÉRICA (30 réplicas con seeds 1..N)
    // =====================================================================

    /**
     * Ejecuta N réplicas independientes del Tabu Search sobre el escenario E1R
     * (datos reales, día 0, 1 día) cambiando únicamente la semilla aleatoria.
     * Genera un CSV con todas las métricas del diseño experimental.
     *
     * Cabecera CSV:
     *   Rep,Seed,TotalRequests,TotalSuitcases,Delivered,Undelivered,
     *   LateSuitcases,TotalDelay,CapacityOverflow,WarehouseOverflow,
     *   Fitness,Semaphore,IterationsRun,Improvements,ExecutionTime(ms)
     *
     * Si {@code airportsFile} y {@code flightsFile} son no nulos, se usa la red
     * REAL leída de esos archivos (formato del curso). En caso contrario, se
     * recurre al {@link RealNetworkBuilder} sintético (compatible con la
     * versión anterior del programa).
     *
     * Comando de ejecución:
     *   java -cp target/classes pe.edu.pucp.tasf.Main EXP \
     *        ./_envios_preliminar_ 30 E1_30_runs_TS.csv \
     *        aeropuertos.txt planes_vuelo.txt
     */
    private static void runExperiment(Path folder, int replicas, String outputCsv,
                                      String airportsFile, String flightsFile) {
        System.out.println("=== MODO EXP: Experimentación numérica ===");
        System.out.printf("Réplicas: %d | Salida: %s%n", replicas, outputCsv);
        if (airportsFile != null && flightsFile != null) {
            System.out.printf("Red real: aeropuertos=%s | vuelos=%s%n", airportsFile, flightsFile);
        } else if (flightsFile != null) {
            System.out.printf("Red real: vuelos=%s%n", flightsFile);
        } else {
            System.out.println("Red SINTÉTICA (RealNetworkBuilder con seed=42)");
        }
        System.out.println();

        // Cargar datos de envíos una sola vez (todas las réplicas usan el mismo dataset)
        EnviosDataLoader enviosLoader = new EnviosDataLoader();
        try {
            enviosLoader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf("Fecha base: %s | ICAO en envíos: %d | Envíos totales: %d%n%n",
                enviosLoader.getBaseDate(),
                enviosLoader.getIcaoCodes().size(),
                enviosLoader.getShipments().size());

        // Construir la red UNA SOLA VEZ y reutilizarla en cada réplica.
        // (Los Flight tienen estado mutable -- carga asignada -- pero el solver
        // hace network.resetLoads() al inicio de cada solve(), así que es seguro.)
        LogisticsNetwork network;
        try {
            network = (flightsFile != null)
                    ? buildRealNetwork(enviosLoader.getIcaoCodes(), Paths.get(flightsFile))
                    : buildSyntheticNetwork(enviosLoader.getIcaoCodes());
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf("Red final: %d aeropuertos, %d vuelos%n%n",
                network.getAirportCount(), network.getFlightCount());

        // Resolver los aeropuertos referenciados por los envíos
        List<ShipmentRequest> requests = enviosLoader.resolveAirports(network);
        System.out.printf("Envíos resueltos para Tabu Search: %d%n%n", requests.size());

        // CSV header
        String header = "Rep,Seed,TotalRequests,TotalSuitcases,Delivered,Undelivered," +
                        "LateSuitcases,TotalDelay,CapacityOverflow,WarehouseOverflow," +
                        "Fitness,Semaphore,IterationsRun,Improvements,ExecutionTime(ms)";

        try (PrintWriter pw = new PrintWriter(new FileWriter(outputCsv))) {
            pw.println(header);

            for (int rep = 1; rep <= replicas; rep++) {
                long seed = rep; // seeds 1..N para reproducibilidad

                // Parámetros TS para el experimento E1
                // neighborhoodSize = 50: explora el 10% de los 488 envíos por iteración
                // tabuTenure = 7: tenencia baja para no bloquear movimientos útiles
                // maxIterations = 1000: más iteraciones dentro del límite de 90s
                TabuSearchConfig config = new TabuSearchConfig()
                        .maxIterations(1000)
                        .tabuTenure(7)
                        .neighborhoodSize(50)
                        .maxHops(3)
                        .timeLimitMs(90_000)
                        .seed(seed);

                System.out.printf("Réplica %2d / %d (seed=%d)...%n", rep, replicas, seed);
                TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
                solver.solve();

                String row = rep + "," + solver.getCsvRow(seed);
                pw.println(row);
                pw.flush();

                // Resumen en consola
                System.out.println("    -> " + solver.getCsvRow(seed));
            }

            System.out.println("\nCSV generado: " + outputCsv);
            System.out.println("Cabecera: " + header);

        } catch (IOException e) {
            System.err.println("ERROR al escribir CSV: " + e.getMessage());
        }
    }

    /**
     * Construye la red logística a partir de los archivos REALES del curso:
     *   - aeropuertos: leídos de los _envios_ (icaoCodes ya conocidos)
     *   - vuelos: leídos de planes_vuelo.txt mediante RealFlightLoader
     *
     * @param icaoCodes   códigos ICAO descubiertos en los envíos
     * @param flightsFile ruta al archivo planes_vuelo.txt
     */
    private static LogisticsNetwork buildRealNetwork(java.util.Collection<String> icaoCodes,
                                                     Path flightsFile) throws IOException {
        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        return builder.build(icaoCodes, flightsFile);
    }

    /**
     * Construye la red SINTÉTICA usando {@link RealNetworkBuilder} a partir
     * sólo de los códigos ICAO descubiertos en los envíos. Modo de respaldo.
     */
    private static LogisticsNetwork buildSyntheticNetwork(java.util.Collection<String> icaoCodes) {
        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        return builder.build(icaoCodes);
    }

    // =====================================================================
    //  ESCENARIO E1R - DATOS REALES
    // =====================================================================

    /**
     * Simulación de periodo usando los archivos reales de envíos.
     *
     * @param folder   carpeta con los archivos _envios_XXXX_.txt
     * @param startDay día 0-based relativo a la fecha mínima encontrada en los datos
     * @param numDays  cantidad de días consecutivos a simular
     */
    private static void runPeriodSimulationReal(Path folder, int startDay, int numDays,
                                                 Path flightsFile) {
        System.out.println("=== ESCENARIO E1R: Simulación de periodo con datos REALES ===");
        System.out.printf("Carpeta: %s | Día inicio: %d | Días: %d%n%n",
                folder, startDay, numDays);

        long startTime = System.currentTimeMillis();

        // ----- 1) Cargar envíos reales -----
        EnviosDataLoader loader = new EnviosDataLoader();
        try {
            loader.loadFromFolder(folder, startDay, numDays);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf("%nFecha base: %s | ICAO: %d | Envíos en rango: %d%n%n",
                loader.getBaseDate(), loader.getIcaoCodes().size(), loader.getShipments().size());

        // ----- 2) Construir la red -----
        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        LogisticsNetwork network;
        if (flightsFile != null) {
            network = builder.build(loader.getIcaoCodes(), flightsFile);
        } else {
            System.out.println("(planes_vuelo.txt no indicado — usando vuelos sintéticos)");
            network = builder.build(loader.getIcaoCodes());
        }
        System.out.printf("Red: %d aeropuertos, %d vuelos%n%n",
                network.getAirportCount(), network.getFlightCount());

        // ----- 3) Resolver aeropuertos -----
        List<ShipmentRequest> requests = loader.resolveAirports(network);
        System.out.printf("Envíos resueltos: %d%n%n", requests.size());

        // ----- 4) Configurar y ejecutar Tabu Search -----
        long totalBudgetMs = 60L * 60 * 1000; // 60 minutos
        TabuSearchConfig config = new TabuSearchConfig()
                .maxIterations(500)
                .tabuTenure(15)
                .neighborhoodSize(20)
                .maxHops(3)
                .timeLimitMs(totalBudgetMs)
                .seed(42);

        TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
        solver.solve();
        solver.printReport();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("%nTiempo total E1R: %.2f minutos%n", elapsed / 60000.0);
    }

    // =====================================================================
    //  ESCENARIO E1 - DATOS SINTÉTICOS (original)
    // =====================================================================

    /**
     * E1: Simulación de periodo con datos sintéticos.
     * Simula 3, 5 o 7 días de operaciones con demanda estocástica y cancelaciones.
     * Tiempo objetivo de ejecución: 30-90 minutos.
     */
    private static void runPeriodSimulation(int totalDays, int requestsPerDay) {
        System.out.println("=== ESCENARIO E1: Simulación de periodo (" + totalDays + " días) ===\n");

        NetworkGenerator gen = new NetworkGenerator(42);
        LogisticsNetwork network = gen.createSampleNetwork();
        long startTime = System.currentTimeMillis();

        // Repartir el presupuesto de tiempo entre los días
        long timeBudgetPerDay = (60L * 60 * 1000) / totalDays; // ~60 min totales

        for (int day = 1; day <= totalDays; day++) {
            System.out.println("\n--- Día " + day + " de " + totalDays + " ---");

            // Generar la demanda del día
            List<ShipmentRequest> requests = gen.generateRequests(network, requestsPerDay);
            System.out.println("Solicitudes generadas: " + requests.size());

            // Configurar Tabu Search para este día
            TabuSearchConfig config = new TabuSearchConfig()
                    .maxIterations(500)
                    .tabuTenure(15)
                    .neighborhoodSize(20)
                    .maxHops(3)
                    .timeLimitMs(timeBudgetPerDay)
                    .seed(42 + day);

            // Resolver
            TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
            Solution solution = solver.solve();
            solver.printReport();

            // Simular cancelación aleatoria (10% de probabilidad por día)
            if (day < totalDays && Math.random() < 0.1) {
                List<Flight> flights = network.getFlights();
                Flight cancelled = flights.get((int) (Math.random() * flights.size()));
                System.out.println("*** CANCELACIÓN DE VUELO: " + cancelled.getId() + " ***");
                solution = solver.replanify(solution, cancelled.getId());
                solver.printReport();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("%nTiempo total de simulación: %.1f minutos%n", elapsed / 60000.0);
    }

    // =====================================================================
    //  ESCENARIO E2 - TIEMPO REAL
    // =====================================================================

    /**
     * E2: Operación en tiempo real.
     * Procesa solicitudes entrantes con replanificación rápida (<5 segundos por evento).
     */
    private static void runRealTimeSimulation(int requestCount) {
        System.out.println("=== ESCENARIO E2: Operación en Tiempo Real ===\n");

        NetworkGenerator gen = new NetworkGenerator(42);
        LogisticsNetwork network = gen.createSampleNetwork();
        List<ShipmentRequest> requests = gen.generateRequests(network, requestCount);

        // Configuración rápida para respuesta en tiempo real
        TabuSearchConfig config = new TabuSearchConfig()
                .maxIterations(200)
                .tabuTenure(10)
                .neighborhoodSize(15)
                .maxHops(2)
                .timeLimitMs(5000)  // 5 segundos máximo
                .seed(42);

        // Planificación inicial
        TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
        Solution solution = solver.solve();
        solver.printReport();

        // Simular eventos dinámicos
        System.out.println("\n--- Simulando eventos dinámicos ---");
        List<Flight> flights = network.getFlights();

        for (int event = 1; event <= 3; event++) {
            Flight toCancel = flights.get(event * 7 % flights.size());
            System.out.println("\nEvento " + event + ": Cancelando vuelo " + toCancel.getId());

            long replanStart = System.currentTimeMillis();
            solution = solver.replanify(solution, toCancel.getId());
            long replanTime = System.currentTimeMillis() - replanStart;

            System.out.printf("Tiempo de replanificación: %d ms (límite: 5000 ms) %s%n",
                    replanTime, replanTime < 5000 ? "OK" : "¡EXCEDIDO!");
            solver.printReport();
        }
    }

    // =====================================================================
    //  ESCENARIO E3 - COLAPSO
    // =====================================================================

    /**
     * E3: Simulación hasta el colapso.
     * Aumenta progresivamente la demanda hasta que el sistema se satura.
     * Usa el semáforo (verde/ámbar/rojo) como detector temprano.
     */
    private static void runCollapseSimulation(int baseRequests) {
        System.out.println("=== ESCENARIO E3: Simulación hasta el colapso ===\n");

        NetworkGenerator gen = new NetworkGenerator(42);
        LogisticsNetwork network = gen.createSampleNetwork();

        TabuSearchConfig config = new TabuSearchConfig()
                .maxIterations(300)
                .tabuTenure(12)
                .neighborhoodSize(15)
                .maxHops(3)
                .timeLimitMs(120_000) // 2 minutos por paso
                .seed(42);

        int day = 0;
        String status = "GREEN";

        while (!status.equals("RED") && day < 30) {
            day++;
            // La demanda crece 20% cada día
            List<ShipmentRequest> requests = gen.generateCollapseRequests(network, baseRequests, day);

            System.out.println("\n--- Día de Colapso " + day + " | Solicitudes: " + requests.size() + " ---");

            TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
            Solution solution = solver.solve();
            status = solver.getSemaphoreStatus(solution);

            int totalSuitcases = solution.getTotalSuitcases();
            int lateSuitcases = solution.getLateCount();
            double latePercent = totalSuitcases > 0 ? (100.0 * lateSuitcases / totalSuitcases) : 0;

            System.out.printf("  Maletas: %d | Tarde: %d (%.1f%%) | Semáforo: %s%n",
                    totalSuitcases, lateSuitcases, latePercent, formatSemaphore(status));

            if (status.equals("AMBER")) {
                System.out.println("  ⚠ ADVERTENCIA: ¡Sistema entrando en zona de estrés!");
            }
        }

        if (status.equals("RED")) {
            System.out.println("\n*** COLAPSO DEL SISTEMA detectado en el día " + day + " ***");
            System.out.println("El sistema ya no puede cumplir los plazos de entrega.");
        } else {
            System.out.println("\nSimulación finalizada tras " + day + " días sin colapso.");
        }
    }

    /**
     * Devuelve una cadena con el emoji correspondiente al color del semáforo.
     */
    private static String formatSemaphore(String status) {
        return switch (status) {
            case "GREEN" -> "🟢 VERDE";
            case "AMBER" -> "🟡 ÁMBAR";
            case "RED"   -> "🔴 ROJO";
            default -> status;
        };
    }
}
