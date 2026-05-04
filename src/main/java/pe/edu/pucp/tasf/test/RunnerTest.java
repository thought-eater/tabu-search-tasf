package pe.edu.pucp.tasf.test;

import pe.edu.pucp.tasf.algorithm.TabuSearchConfig;
import pe.edu.pucp.tasf.algorithm.TabuSearchSolver;
import pe.edu.pucp.tasf.io.AirportsLoader;
import pe.edu.pucp.tasf.io.EnviosDataLoader;
import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.LogisticsNetwork;
import pe.edu.pucp.tasf.model.ShipmentRequest;
import pe.edu.pucp.tasf.model.Solution;
import pe.edu.pucp.tasf.util.RealNetworkBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Test de integración para el flujo E1 (datos reales).
 *
 * No usa JUnit. Corre el pipeline completo contra la carpeta test-data/
 * y valida invariantes básicos con afirmaciones PASS/FAIL.
 *
 * Compilar y ejecutar:
 *   javac -d target/classes $(find src -name "*.java")
 *   java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest
 *
 * O desde la raíz del proyecto:
 *   java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest [carpeta]
 *
 * Requiere aeropuertos.txt y planes_vuelo.txt en el directorio de trabajo.
 */
public class RunnerTest {

    // Contadores globales de resultados
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        // La carpeta de test-data se puede pasar como argumento; si no, usa test-data/
        String folder = args.length > 0 ? args[0] : "test-data";

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         RunnerTest – Pipeline E1 (test-data)     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Carpeta de datos: " + folder);
        System.out.println();

        // ── PASO 1: EnviosDataLoader ──────────────────────────────────────────
        section("PASO 1 — EnviosDataLoader");

        EnviosDataLoader loader = new EnviosDataLoader();
        loader.loadFromFolder(Paths.get(folder), 0, 1);   // día 0, sólo 1 día

        assertThat("Fecha base detectada no es null",
                loader.getBaseDate() != null);

        assertThat("Se descubrieron códigos ICAO (> 0)",
                loader.getIcaoCodes().size() > 0);
        info("Códigos ICAO: " + loader.getIcaoCodes());

        assertThat("Se cargaron envíos (> 0)",
                loader.getShipments().size() > 0);
        info("Envíos cargados: " + loader.getShipments().size());

        // ── PASO 2: AirportsLoader + RealNetworkBuilder ───────────────────────
        section("PASO 2 — AirportsLoader + RealNetworkBuilder");

        // Buscar aeropuertos.txt en el directorio actual
        Path airportsFile = Paths.get("aeropuertos.txt");
        assertThat("aeropuertos.txt existe en el directorio actual",
                Files.isRegularFile(airportsFile));

        // Buscar planes_vuelo.txt en el directorio actual
        Path flightsFile = Paths.get("planes_vuelo.txt");
        assertThat("planes_vuelo.txt existe en el directorio actual",
                Files.isRegularFile(flightsFile));

        AirportsLoader airportsLoader = new AirportsLoader();
        airportsLoader.loadFromFile(airportsFile);
        List<Airport> airports = airportsLoader.getAirports();

        assertThat("Se cargaron aeropuertos desde aeropuertos.txt (> 0)",
                airports.size() > 0);
        info("Aeropuertos cargados: " + airports.size());

        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        LogisticsNetwork network = builder.build(airports, flightsFile);

        assertThat("La red tiene aeropuertos (> 0)",
                network.getAirportCount() > 0);
        info("Aeropuertos en red: " + network.getAirportCount());

        assertThat("La red tiene vuelos (> 0)",
                network.getFlightCount() > 0);
        info("Vuelos en red: " + network.getFlightCount());

        // ── PASO 3: resolveAirports ───────────────────────────────────────────
        section("PASO 3 — resolveAirports");

        List<ShipmentRequest> requests = loader.resolveAirports(network);

        assertThat("resolveAirports devuelve envíos (> 0)",
                requests.size() > 0);
        info("Envíos resueltos: " + requests.size());

        // Todos los envíos resueltos deben tener origen y destino en la red
        long orphans = requests.stream()
                .filter(r -> network.getAirport(r.getOrigin().getCode()) == null
                          || network.getAirport(r.getDestination().getCode()) == null)
                .count();
        assertThat("Ningún envío tiene aeropuerto huérfano", orphans == 0);

        // ── PASO 4: TabuSearchSolver ──────────────────────────────────────────
        section("PASO 4 — TabuSearchSolver");

        TabuSearchConfig config = new TabuSearchConfig()
                .maxIterations(50)       // pocas iteraciones para que el test sea rápido
                .tabuTenure(5)
                .neighborhoodSize(10)
                .maxHops(3)
                .timeLimitMs(30_000)     // 30 segundos máximo
                .seed(42);

        TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
        Solution solution = solver.solve();

        assertThat("La solución no es null", solution != null);

        assertThat("La solución cubre todos los envíos resueltos",
                solution.size() == requests.size());
        info("Tamaño de solución: " + solution.size());

        assertThat("El fitness es un número finito >= 0",
                Double.isFinite(solution.getFitness()) && solution.getFitness() >= 0);
        info("Fitness: " + solution.getFitness());

        assertThat("Total de maletas > 0",
                solution.getTotalSuitcases() > 0);
        info("Total maletas: " + solution.getTotalSuitcases());

        assertThat("Maletas tardías no supera el total",
                solution.getLateCount() <= solution.getTotalSuitcases());
        info("Maletas tardías: " + solution.getLateCount()
                + " / " + solution.getTotalSuitcases());

        String semaphore = solver.getSemaphoreStatus(solution);
        assertThat("El semáforo devuelve un valor válido (GREEN/AMBER/RED)",
                semaphore.equals("GREEN") || semaphore.equals("AMBER") || semaphore.equals("RED"));
        info("Semáforo: " + semaphore);

        assertThat("Se ejecutó al menos 1 iteración",
                solver.getIterationsRun() >= 1);
        info("Iteraciones ejecutadas: " + solver.getIterationsRun());

        // ── PASO 5: printReport sin excepción ────────────────────────────────
        section("PASO 5 — printReport");

        try {
            solver.printReport();
            assertThat("printReport() se ejecutó sin excepción", true);
        } catch (Exception e) {
            assertThat("printReport() se ejecutó sin excepción [EXCEPCIÓN: " + e + "]", false);
        }

        // ── RESUMEN ───────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.printf ("  RESULTADO FINAL:  %d PASS  |  %d FAIL%n", passed, failed);
        System.out.println("══════════════════════════════════════════");

        if (failed > 0) {
            System.exit(1);   // señalizar fallo al SO / shell script
        }
    }

    // ── Utilidades de test ────────────────────────────────────────────────────

    private static void section(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 46 - title.length())));
    }

    private static void assertThat(String description, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + description);
            passed++;
        } else {
            System.out.println("  [FAIL] " + description);
            failed++;
        }
    }

    private static void info(String msg) {
        System.out.println("         " + msg);
    }
}

