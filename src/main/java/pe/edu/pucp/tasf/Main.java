package pe.edu.pucp.tasf;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import pe.edu.pucp.tasf.algorithm.TabuSearchConfig;
import pe.edu.pucp.tasf.algorithm.TabuSearchSolver;
import pe.edu.pucp.tasf.io.AirportsLoader;
import pe.edu.pucp.tasf.io.EnviosDataLoader;
import pe.edu.pucp.tasf.model.*;
import pe.edu.pucp.tasf.util.RealNetworkBuilder;

/**
 * Punto de entrada principal del planificador Tabu Search para Tasf.B2B.
 *
 * La red logística siempre se construye a partir de datos reales:
 *   - Aeropuertos : {@code aeropuertos.txt}  (buscado en el directorio actual)
 *   - Vuelos      : {@code planes_vuelo.txt} (buscado en el directorio actual o indicado
 *                   como argumento opcional al final de cada modo)
 *
 * Modos disponibles:
 *
 *   E1  - Simulación de periodo (5 días por defecto).
 *         Lee solicitudes de la carpeta {@code _envios_XXXX_.txt}.
 *         Ejecuta en 30-90 minutos.
 *
 *   E2  - Operación en tiempo real con replanificación (≤5 s/evento).
 *         Lee solicitudes del mismo formato que E1.
 *
 *   E3  - Simulación hasta el colapso.
 *         Parte de los envíos reales y escala la demanda +20%/día.
 *
 *   EXP - Experimentación numérica: N réplicas con seeds 1..N → CSV.
 *
 * Uso:
 *   java pe.edu.pucp.tasf.Main E1  <carpeta_envios> [díaInicio=0] [numDías=5] [planes_vuelo.txt]
 *   java pe.edu.pucp.tasf.Main E2  <carpeta_envios> [planes_vuelo.txt]
 *   java pe.edu.pucp.tasf.Main E3  <carpeta_envios> [planes_vuelo.txt]
 *   java pe.edu.pucp.tasf.Main EXP <carpeta_envios> [réplicas=30] [salida.csv] [planes_vuelo.txt]
 */
public class Main {

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // ignorado
        }

        String scenario = args.length > 0 ? args[0].toUpperCase() : "E1";

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Tasf.B2B - Tabu Search Planner      ║");
        System.out.println("║         Equipo 8H - PUCP 2026-1         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        switch (scenario) {
            case "E1" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main E1 <carpeta_envios> [fechaInicio=primerDíaEnDatos] [numDías=5] [planes_vuelo.txt]"
                    );
                    System.out.println(
                        "  fechaInicio: formato yyyymmdd o yyyy-mm-dd  (ej: 20280315 o 2028-03-15)"
                    );
                    return;
                }
                String folder = args[1];
                // Parsear la fecha de inicio (opcional); acepta yyyymmdd o yyyy-mm-dd
                LocalDate startDate = parseFechaArg(
                    args.length > 2 ? args[2] : null
                );
                if (startDate == null && args.length > 2) return; // error ya impreso
                int numDays = args.length > 3 ? Integer.parseInt(args[3]) : 5;
                String flightsF = args.length > 4 ? args[4] : null;
                Path flights = requireFlightsFile(flightsF);
                if (flights == null) return;
                runPeriodSimulation(
                    Paths.get(folder),
                    startDate,
                    numDays,
                    flights
                );
            }
            case "E2" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main E2 <carpeta_envios> [planes_vuelo.txt]"
                    );
                    return;
                }
                String folder = args[1];
                String flightsF = args.length > 2 ? args[2] : null;
                Path flights = requireFlightsFile(flightsF);
                if (flights == null) return;
                runRealTimeSimulation(Paths.get(folder), flights);
            }
            case "E3" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main E3 <carpeta_envios> [planes_vuelo.txt]"
                    );
                    return;
                }
                String folder = args[1];
                String flightsF = args.length > 2 ? args[2] : null;
                Path flights = requireFlightsFile(flightsF);
                if (flights == null) return;
                runCollapseSimulation(Paths.get(folder), flights);
            }
            case "EXP" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main EXP <carpeta_envios> [réplicas=30] [salida.csv] [planes_vuelo.txt]"
                    );
                    return;
                }
                String folder = args[1];
                int replicas = args.length > 2 ? Integer.parseInt(args[2]) : 30;
                String outputCsv =
                    args.length > 3 ? args[3] : "E1_30_runs_TS.csv";
                String flightsF = args.length > 4 ? args[4] : null;
                Path flights = requireFlightsFile(flightsF);
                if (flights == null) return;
                runExperiment(Paths.get(folder), replicas, outputCsv, flights);
            }
            default -> {
                System.out.println(
                    "Uso: java -jar tabu-search-tasf.jar <E1|E2|E3|EXP> [...]"
                );
                System.out.println(
                    "  E1  - Simulación de periodo con datos reales (default 5 días)"
                );
                System.out.println(
                    "  E2  - Operación en tiempo real con replanificación"
                );
                System.out.println("  E3  - Simulación hasta el colapso");
                System.out.println(
                    "  EXP - Experimentación numérica: N réplicas con seeds 1..N → CSV"
                );
            }
        }
    }

    // =========================================================================
    //  RESOLUCIÓN DE ARCHIVOS DE DATOS
    // =========================================================================

    /**
     * Resuelve la ruta al archivo de vuelos: usa la indicada, o busca
     * {@code planes_vuelo.txt} en el directorio de trabajo como fallback.
     * Si no se encuentra ninguno, imprime un error y devuelve {@code null}.
     */
    private static Path requireFlightsFile(String explicit) {
        if (explicit != null) {
            Path p = Paths.get(explicit);
            if (Files.isRegularFile(p)) return p;
            System.err.println(
                "ERROR: planes_vuelo.txt indicado no existe: " + explicit
            );
            return null;
        }
        Path cwd = Paths.get("planes_vuelo.txt");
        if (Files.isRegularFile(cwd)) {
            System.out.println(
                "[Main] Usando planes_vuelo.txt del directorio actual: " +
                    cwd.toAbsolutePath()
            );
            return cwd;
        }
        System.err.println(
            "ERROR: No se encontró planes_vuelo.txt. Indíquelo como último argumento."
        );
        return null;
    }

    /**
     * Parsea un argumento de fecha en formato {@code yyyymmdd} o {@code yyyy-mm-dd}.
     * Devuelve {@code null} si el argumento también es {@code null} (el usuario no lo
     * proporcionó, se usará el inicio de los datos).
     * Imprime un error y devuelve {@code null} si el formato es inválido.
     *
     * @param arg cadena recibida desde la línea de comandos, o null si no se indicó
     * @return la fecha parseada, o null
     */
    private static LocalDate parseFechaArg(String arg) {
        if (arg == null) return null; // no se indicó fecha; usar inicio de datos

        // Normalizar: quitar guiones para manejar ambos formatos (yyyymmdd y yyyy-mm-dd)
        String normalizado = arg.replace("-", "");
        try {
            return LocalDate.parse(
                normalizado,
                DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (DateTimeParseException e) {
            System.err.println(
                "ERROR: Fecha de inicio inválida: '" +
                    arg +
                    "'. Use el formato yyyymmdd o yyyy-mm-dd (ej: 20280315)."
            );
            return null; // señal de error: el llamador debe abortar
        }
    }

    /**
     * Carga el archivo {@code aeropuertos.txt} desde el directorio de trabajo.
     * Devuelve la lista de aeropuertos o lanza RuntimeException si no existe.
     */
    private static List<Airport> loadAirports() {
        Path file = Paths.get("aeropuertos.txt");
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException(
                "No se encontró aeropuertos.txt en el directorio actual: " +
                    Paths.get("").toAbsolutePath()
            );
        }
        AirportsLoader loader = new AirportsLoader();
        try {
            loader.loadFromFile(file);
        } catch (IOException e) {
            throw new RuntimeException(
                "Error al leer aeropuertos.txt: " + e.getMessage(),
                e
            );
        }
        return loader.getAirports();
    }

    /**
     * Construye la red logística a partir de {@code aeropuertos.txt} y
     * {@code planes_vuelo.txt}.
     */
    private static LogisticsNetwork buildNetwork(Path flightsFile)
        throws IOException {
        List<Airport> airports = loadAirports();
        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        return builder.build(airports, flightsFile);
    }

    // =========================================================================
    //  E1 — SIMULACIÓN DE PERIODO (datos reales, 5 días)
    // =========================================================================

    /**
     * E1: Simulación de periodo con datos reales.
     * Lee los envíos de la carpeta indicada para el rango [startDate, startDate+numDays).
     * Si {@code startDate} es {@code null}, comienza desde el primer día con datos.
     * Tiempo objetivo de ejecución: 30-90 minutos.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param startDate   fecha de inicio de la simulación; null = primera fecha en datos
     * @param numDays     número de días consecutivos a simular (mínimo 1, por defecto 5)
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runPeriodSimulation(
        Path folder,
        LocalDate startDate,
        int numDays,
        Path flightsFile
    ) {
        System.out.printf(
            "=== ESCENARIO E1: Simulación de periodo (%d días) ===%n%n",
            numDays
        );
        System.out.printf(
            "Carpeta: %s | Fecha inicio: %s | Días: %d%n%n",
            folder,
            startDate != null ? startDate : "(inicio de datos)",
            numDays
        );

        long wallStart = System.currentTimeMillis();

        // ----- 1) Construir la red -----
        LogisticsNetwork network;
        try {
            network = buildNetwork(flightsFile);
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- 2) Cargar envíos -----
        EnviosDataLoader loader = new EnviosDataLoader();
        try {
            if (startDate != null) {
                // El usuario indicó una fecha explícita: usarla directamente
                loader.loadFromFolder(folder, startDate, numDays);
            } else {
                // Sin fecha explícita: empezar desde el primer día con datos (offset 0)
                loader.loadFromFolder(folder, 0, numDays);
            }
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Fecha base: %s | ICAO: %d | Envíos en rango: %d%n%n",
            loader.getBaseDate(),
            loader.getIcaoCodes().size(),
            loader.getShipments().size()
        );

        // ----- 3) Resolver aeropuertos -----
        // Reemplaza los aeropuertos "fantasma" (solo ICAO) por instancias reales de la red
        List<ShipmentRequest> requests = loader.resolveAirports(network);
        System.out.printf("Envíos resueltos: %d%n%n", requests.size());

        // Fecha efectiva de inicio (puede diferir de startDate si fue null)
        LocalDate fechaEfectiva = (startDate != null)
            ? startDate
            : loader.getBaseDate();

        // ----- 4) Repartir tiempo entre días -----
        long totalBudgetMs = 60L * 60 * 1000; // 60 minutos en total para todos los días
        long timeBudgetPerDay = totalBudgetMs / numDays;

        // ----- 5) Simular día a día -----
        for (int day = 1; day <= numDays; day++) {
            System.out.println("--- Día " + day + " de " + numDays + " ---");

            // Calcular la fecha calendario de este día de simulación
            LocalDate fechaDia = fechaEfectiva.plusDays(day - 1);

            // Calcular el offset absoluto de días desde la fecha base del loader,
            // que es lo que representa Math.floor(creationTime) para cada envío.
            long offsetEsperado =
                fechaDia.toEpochDay() - loader.getBaseDate().toEpochDay();

            // Filtrar únicamente los envíos creados en este día de calendario
            List<ShipmentRequest> dayRequests = requests
                .stream()
                .filter(
                    r ->
                        (long) Math.floor(r.getCreationTime()) == offsetEsperado
                )
                .toList();

            if (dayRequests.isEmpty()) {
                // No debería ocurrir con datos válidos; avisar en lugar de silenciar
                System.out.printf(
                    "  [AVISO] Sin envíos para %s (offset=%d). Saltando.%n",
                    fechaDia,
                    offsetEsperado
                );
                continue;
            }
            System.out.printf(
                "Envíos del día %d (%s): %d%n",
                day,
                fechaDia,
                dayRequests.size()
            );

            TabuSearchConfig config = new TabuSearchConfig()
                .maxIterations(500)
                .tabuTenure(15)
                .maxHops(3)
                .timeLimitMs(timeBudgetPerDay)
                .seed(42 + day);
            // neighborhoodSize usa el default escalado dinámicamente en el solver

            TabuSearchSolver solver = new TabuSearchSolver(
                network,
                dayRequests,
                config
            );
            Solution solution = solver.solve();
            solver.printReport();

            // Simulación de cancelación aleatoria (10% de probabilidad)
            if (day < numDays && Math.random() < 0.1) {
                List<Flight> flights = network.getFlights();
                Flight cancelled = flights.get(
                    (int) (Math.random() * flights.size())
                );
                System.out.println(
                    "*** CANCELACIÓN DE VUELO: " + cancelled.getId() + " ***"
                );
                solution = solver.replanify(solution, cancelled.getId());
                solver.printReport();
            }
        }

        long elapsed = System.currentTimeMillis() - wallStart;
        System.out.printf(
            "%nTiempo total E1: %.2f minutos%n",
            elapsed / 60000.0
        );
    }

    // =========================================================================
    //  E2 — TIEMPO REAL CON REPLANIFICACIÓN
    // =========================================================================

    /**
     * E2: Operación en tiempo real.
     * Carga los envíos del primer día y simula 3 eventos de cancelación,
     * verificando que la replanificación complete en menos de 5 segundos.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runRealTimeSimulation(Path folder, Path flightsFile) {
        System.out.println("=== ESCENARIO E2: Operación en Tiempo Real ===\n");

        // ----- Red -----
        LogisticsNetwork network;
        try {
            network = buildNetwork(flightsFile);
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos (primer día) -----
        EnviosDataLoader loader = new EnviosDataLoader();
        try {
            loader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        List<ShipmentRequest> requests = loader.resolveAirports(network);
        System.out.printf("Envíos cargados: %d%n%n", requests.size());

        // ----- Planificación inicial -----
        TabuSearchConfig config = new TabuSearchConfig()
            .maxIterations(200)
            .tabuTenure(10)
            .neighborhoodSize(15)
            .maxHops(2)
            .timeLimitMs(5000)
            .seed(42);

        TabuSearchSolver solver = new TabuSearchSolver(
            network,
            requests,
            config
        );
        Solution solution = solver.solve();
        solver.printReport();

        // ----- Eventos de cancelación -----
        System.out.println("\n--- Simulando eventos dinámicos ---");
        List<Flight> flights = network.getFlights();

        for (int event = 1; event <= 3; event++) {
            Flight toCancel = flights.get((event * 7) % flights.size());
            System.out.println(
                "\nEvento " + event + ": Cancelando vuelo " + toCancel.getId()
            );

            long replanStart = System.currentTimeMillis();
            solution = solver.replanify(solution, toCancel.getId());
            long replanTime = System.currentTimeMillis() - replanStart;

            System.out.printf(
                "Tiempo de replanificación: %d ms (límite: 5000 ms) %s%n",
                replanTime,
                replanTime < 5000 ? "OK" : "¡EXCEDIDO!"
            );
            solver.printReport();
        }
    }

    // =========================================================================
    //  E3 — SIMULACIÓN HASTA EL COLAPSO
    // =========================================================================

    /**
     * E3: Simulación hasta el colapso.
     * Parte de los envíos reales y duplica la carga progresivamente (+20%/día)
     * hasta que el semáforo alcanza ROJO o se superen 30 días.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runCollapseSimulation(Path folder, Path flightsFile) {
        System.out.println(
            "=== ESCENARIO E3: Simulación hasta el colapso ===\n"
        );

        // ----- Red -----
        LogisticsNetwork network;
        try {
            network = buildNetwork(flightsFile);
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos base (primer día disponible) -----
        EnviosDataLoader loader = new EnviosDataLoader();
        try {
            loader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        List<ShipmentRequest> baseRequests = loader.resolveAirports(network);
        System.out.printf("Envíos base: %d%n%n", baseRequests.size());

        TabuSearchConfig config = new TabuSearchConfig()
            .maxIterations(300)
            .tabuTenure(12)
            .neighborhoodSize(15)
            .maxHops(3)
            .timeLimitMs(120_000)
            .seed(42);

        int day = 0;
        String status = "GREEN";

        while (!status.equals("RED") && day < 30) {
            day++;
            // Escalar la demanda: +20% acumulado por día
            List<ShipmentRequest> scaledRequests = scaleRequests(
                baseRequests,
                day,
                network
            );

            System.out.println(
                "\n--- Día de Colapso " +
                    day +
                    " | Solicitudes: " +
                    scaledRequests.size() +
                    " ---"
            );

            TabuSearchSolver solver = new TabuSearchSolver(
                network,
                scaledRequests,
                config
            );
            Solution solution = solver.solve();
            status = solver.getSemaphoreStatus(solution);

            int totalSuitcases = solution.getTotalSuitcases();
            int lateSuitcases = solution.getLateCount();
            double latePercent =
                totalSuitcases > 0
                    ? ((100.0 * lateSuitcases) / totalSuitcases)
                    : 0;

            System.out.printf(
                "  Maletas: %d | Tarde: %d (%.1f%%) | Semáforo: %s%n",
                totalSuitcases,
                lateSuitcases,
                latePercent,
                formatSemaphore(status)
            );

            if (status.equals("AMBER")) {
                System.out.println(
                    "  ⚠ ADVERTENCIA: ¡Sistema entrando en zona de estrés!"
                );
            }
        }

        if (status.equals("RED")) {
            System.out.println(
                "\n*** COLAPSO DEL SISTEMA detectado en el día " + day + " ***"
            );
            System.out.println(
                "El sistema ya no puede cumplir los plazos de entrega."
            );
        } else {
            System.out.println(
                "\nSimulación finalizada tras " + day + " días sin colapso."
            );
        }
    }

    /**
     * Escala la lista de solicitudes base multiplicando la cantidad de envíos
     * por {@code 1.2^day}. Los nuevos envíos son copias de los originales
     * (circulando si es necesario) con {@code creationTime} ajustado al día 0.
     */
    private static List<ShipmentRequest> scaleRequests(
        List<ShipmentRequest> base,
        int day,
        LogisticsNetwork network
    ) {
        int target = (int) (base.size() * Math.pow(1.2, day));
        List<ShipmentRequest> result = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            ShipmentRequest original = base.get(i % base.size());
            result.add(
                new ShipmentRequest(
                    original.getId() + "_d" + day + "_" + i,
                    original.getOrigin(),
                    original.getDestination(),
                    original.getQuantity(),
                    original.getCreationTime() % 1.0 // normalizar al día 0
                )
            );
        }
        return result;
    }

    // =========================================================================
    //  EXP — EXPERIMENTACIÓN NUMÉRICA (N réplicas, seeds 1..N)
    // =========================================================================

    /**
     * Ejecuta N réplicas independientes del Tabu Search (seeds 1..N).
     * Los datos se cargan una sola vez; solo cambia la semilla entre réplicas.
     *
     * @param folder     carpeta con archivos {@code _envios_XXXX_.txt}
     * @param replicas   número de réplicas
     * @param outputCsv  nombre del archivo CSV de salida
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runExperiment(
        Path folder,
        int replicas,
        String outputCsv,
        Path flightsFile
    ) {
        System.out.println("=== MODO EXP: Experimentación numérica ===");
        System.out.printf("Réplicas: %d | Salida: %s%n%n", replicas, outputCsv);

        // ----- Red -----
        LogisticsNetwork network;
        try {
            network = buildNetwork(flightsFile);
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos -----
        EnviosDataLoader enviosLoader = new EnviosDataLoader();
        try {
            enviosLoader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf(
            "Fecha base: %s | ICAO en envíos: %d | Envíos totales: %d%n%n",
            enviosLoader.getBaseDate(),
            enviosLoader.getIcaoCodes().size(),
            enviosLoader.getShipments().size()
        );

        List<ShipmentRequest> requests = enviosLoader.resolveAirports(network);
        System.out.printf(
            "Envíos resueltos para Tabu Search: %d%n%n",
            requests.size()
        );

        // ----- CSV header -----
        String header =
            "Rep,Seed,TotalRequests,TotalSuitcases,Delivered,Undelivered," +
            "LateSuitcases,TotalDelay,CapacityOverflow,WarehouseOverflow," +
            "Fitness,Semaphore,IterationsRun,Improvements,ExecutionTime(ms)";

        try (PrintWriter pw = new PrintWriter(new FileWriter(outputCsv))) {
            pw.println(header);

            for (int rep = 1; rep <= replicas; rep++) {
                long seed = rep;

                TabuSearchConfig config = new TabuSearchConfig()
                    .maxIterations(1000)
                    .tabuTenure(7)
                    .neighborhoodSize(50)
                    .maxHops(3)
                    .timeLimitMs(90_000)
                    .seed(seed);

                System.out.printf(
                    "Réplica %2d / %d (seed=%d)...%n",
                    rep,
                    replicas,
                    seed
                );
                TabuSearchSolver solver = new TabuSearchSolver(
                    network,
                    requests,
                    config
                );
                solver.solve();

                String row = rep + "," + solver.getCsvRow(seed);
                pw.println(row);
                pw.flush();

                System.out.println("    -> " + solver.getCsvRow(seed));
            }

            System.out.println("\nCSV generado: " + outputCsv);
            System.out.println("Cabecera: " + header);
        } catch (IOException e) {
            System.err.println("ERROR al escribir CSV: " + e.getMessage());
        }
    }

    // =========================================================================
    //  UTILIDADES
    // =========================================================================

    private static String formatSemaphore(String status) {
        return switch (status) {
            case "GREEN" -> "🟢 VERDE";
            case "AMBER" -> "🟡 ÁMBAR";
            case "RED" -> "🔴 ROJO";
            default -> status;
        };
    }
}
