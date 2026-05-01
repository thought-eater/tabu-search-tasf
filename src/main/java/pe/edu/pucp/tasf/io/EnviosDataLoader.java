package pe.edu.pucp.tasf.io;

import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.LogisticsNetwork;
import pe.edu.pucp.tasf.model.ShipmentRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Cargador de archivos reales de envíos con el formato usado por Tasf.B2B.
 *
 * Cada archivo se llama _envios_XXXX_.txt donde XXXX es el código ICAO de 4
 * letras del aeropuerto ORIGEN (ese dato no aparece en la línea, se deduce
 * del nombre de archivo).
 *
 * Cada línea del archivo tiene el formato:
 *
 *     ID-FECHA-HH-MM-DESTINO-CANTIDAD-CLIENTE
 *
 * donde:
 *   - ID:       9 dígitos, número secuencial del envío
 *   - FECHA:    8 dígitos YYYYMMDD, fecha de creación del envío
 *   - HH:       2 dígitos, hora (00-23)
 *   - MM:       2 dígitos, minuto (00-59)
 *   - DESTINO:  4 letras, código ICAO del aeropuerto destino
 *   - CANTIDAD: 3 dígitos, cantidad de maletas (001-033 aprox.)
 *   - CLIENTE:  7 dígitos, identificador de cliente/aerolínea (0..32767)
 *
 * Ejemplo:
 *   000000001-20260102-00-47-SUAA-002-0032535
 *   Envío #1, creado el 02/ene/2026 a las 00:47, desde el origen del archivo
 *   hacia SUAA (Montevideo), 2 maletas, cliente 32535.
 *
 * Este cargador convierte cada línea en un {@link ShipmentRequest}. Puesto
 * que el algoritmo Tabu Search trabaja con tiempos relativos expresados como
 * fracción de día (0.0 = inicio del día, 1.0 = fin del primer día), se
 * requiere una fecha de referencia (FECHA_BASE) desde la cual se cuentan los
 * días transcurridos. Se usa la primera fecha detectada en los datos, o la
 * fecha indicada por el usuario.
 */
public class EnviosDataLoader {

    /**
     * Estructura interna para parsear el nombre de archivo y obtener el
     * aeropuerto origen a partir del patrón _envios_XXXX_.txt.
     */
    private static final String PREFIX = "_envios_";
    private static final String SUFFIX = "_.txt";

    /** Formateador de fecha para leer el campo FECHA (YYYYMMDD). */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Fecha de referencia: se calcula como la fecha mínima encontrada entre
     * todas las líneas leídas. Todas las fracciones de día se miden a partir
     * de esta fecha (día 0 = inicio de la FECHA_BASE).
     */
    private LocalDate baseDate;

    /** Lista de todos los envíos leídos, ya convertidos a ShipmentRequest. */
    private final List<ShipmentRequest> shipments = new ArrayList<>();

    /** Conjunto de códigos ICAO vistos (origen o destino) para construir la red. */
    private final Set<String> icaoCodes = new LinkedHashSet<>();

    /**
     * Lee TODOS los archivos _envios_XXXX_.txt contenidos en la carpeta indicada.
     *
     * @param folder carpeta que contiene los archivos (p. ej. "_envios_preliminar_")
     * @param startDay día 0-based de inicio del rango a filtrar (inclusivo).
     *                 Ej: 0 = desde el primer día con datos. Use -1 para no filtrar.
     * @param numDays  cantidad de días a leer a partir de startDay. Use -1 para leer todo.
     * @throws IOException si falla la lectura del directorio
     */
    public void loadFromFolder(Path folder, int startDay, int numDays) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IOException("La carpeta no existe o no es un directorio: " + folder);
        }

        // Primer barrido: descubrir la fecha mínima para fijar la referencia
        discoverBaseDate(folder);

        // Calcular fecha de inicio y fin según los parámetros del usuario
        LocalDate rangeStart = (startDay < 0)
                ? baseDate
                : baseDate.plusDays(startDay);
        LocalDate rangeEnd = (numDays < 0)
                ? LocalDate.MAX
                : rangeStart.plusDays(numDays); // exclusivo

        System.out.printf("[EnviosDataLoader] Fecha base = %s, rango = [%s, %s)%n",
                baseDate, rangeStart, rangeEnd);

        // Segundo barrido: leer y filtrar los envíos dentro del rango
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
                    })
                    .sorted()
                    .forEach(f -> loadFile(f, rangeStart, rangeEnd));
        }

        System.out.printf("[EnviosDataLoader] Cargados %d envíos de %d aeropuertos%n",
                shipments.size(), icaoCodes.size());
    }

    /**
     * Primer barrido rápido: detecta la fecha mínima para usarla como día 0 de la
     * simulación. Sólo inspecciona la primera línea de cada archivo; las líneas
     * vienen ordenadas cronológicamente por ID y por fecha.
     */
    private void discoverBaseDate(Path folder) throws IOException {
        LocalDate minDate = null;

        try (Stream<Path> files = Files.list(folder)) {
            List<Path> txtFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
                    })
                    .sorted()
                    .toList();

            for (Path f : txtFiles) {
                try (BufferedReader br = Files.newBufferedReader(f)) {
                    String firstLine = br.readLine();
                    if (firstLine == null) continue;
                    String[] parts = firstLine.trim().split("-");
                    if (parts.length >= 2) {
                        LocalDate d = LocalDate.parse(parts[1], DATE_FMT);
                        if (minDate == null || d.isBefore(minDate)) {
                            minDate = d;
                        }
                    }
                }
            }
        }

        this.baseDate = (minDate != null) ? minDate : LocalDate.of(2026, 1, 2);
    }

    /**
     * Procesa un archivo _envios_XXXX_.txt línea por línea.
     * Sólo se aceptan los envíos cuya fecha esté dentro del rango [rangeStart, rangeEnd).
     */
    private void loadFile(Path file, LocalDate rangeStart, LocalDate rangeEnd) {
        // Extraer el aeropuerto ORIGEN del nombre del archivo
        String fname = file.getFileName().toString();
        String originIcao = fname.substring(PREFIX.length(),
                fname.length() - SUFFIX.length());
        icaoCodes.add(originIcao);

        int count = 0;
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                ShipmentRequest sr = parseLine(line, originIcao, rangeStart, rangeEnd);
                if (sr != null) {
                    shipments.add(sr);
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo " + file + ": " + e.getMessage());
        }

        if (count > 0) {
            System.out.printf("  %s → %d envíos en rango%n", originIcao, count);
        }
    }

    /**
     * Convierte UNA línea del archivo en un ShipmentRequest.
     *
     * Devuelve null si la línea es inválida o cae fuera del rango de fechas.
     */
    private ShipmentRequest parseLine(String line, String originIcao,
                                      LocalDate rangeStart, LocalDate rangeEnd) {
        String[] parts = line.split("-");
        if (parts.length != 7) return null; // línea malformada

        try {
            String id       = parts[0];
            LocalDate date  = LocalDate.parse(parts[1], DATE_FMT);
            int hour        = Integer.parseInt(parts[2]);
            int minute      = Integer.parseInt(parts[3]);
            String destIcao = parts[4];
            int quantity    = Integer.parseInt(parts[5]);
            // parts[6] es el código de cliente; no lo usamos por ahora

            // Filtrar por rango de fechas
            if (date.isBefore(rangeStart) || !date.isBefore(rangeEnd)) {
                return null;
            }

            // Calcular el "tiempo de creación" como fracción de día desde la fecha base.
            // Ejemplo: si baseDate = 2026-01-02 y la línea es del 2026-01-04 a las 06:00,
            //   daysOffset = 2
            //   fracDay    = 6/24 = 0.25
            //   creationTime = 2.25
            long daysOffset = date.toEpochDay() - baseDate.toEpochDay();
            double fracDay = (hour + minute / 60.0) / 24.0;
            double creationTime = daysOffset + fracDay;

            // Registrar los ICAO (origen ya está; destino se añade)
            icaoCodes.add(destIcao);

            // Placeholder: los Airports reales se resolverán luego con resolveAirports().
            // Por ahora almacenamos el ShipmentRequest con objetos temporales.
            // Creamos aeropuertos "fantasma" (solo con el código); luego en el método
            // buildNetwork() se reemplazarán por instancias compartidas.
            Airport tmpOrigin = new Airport(originIcao, originIcao,
                    pe.edu.pucp.tasf.model.Continent.fromIcao(originIcao), 0);
            Airport tmpDest   = new Airport(destIcao, destIcao,
                    pe.edu.pucp.tasf.model.Continent.fromIcao(destIcao), 0);

            return new ShipmentRequest(
                    "E" + id,                // ID visible del envío
                    tmpOrigin,
                    tmpDest,
                    quantity,
                    creationTime
            );
        } catch (Exception e) {
            // Línea corrupta: se ignora
            return null;
        }
    }

    /**
     * Una vez construida la red con aeropuertos reales, reemplaza los objetos
     * Airport "fantasma" de cada ShipmentRequest por las instancias definitivas
     * contenidas en la LogisticsNetwork (para que comparten estado de capacidad).
     *
     * @param network red logística con los Airports ya registrados
     * @return lista de ShipmentRequest listos para usar por el Tabu Search
     */
    public List<ShipmentRequest> resolveAirports(LogisticsNetwork network) {
        List<ShipmentRequest> resolved = new ArrayList<>(shipments.size());
        for (ShipmentRequest sr : shipments) {
            Airport realOrigin = network.getAirport(sr.getOrigin().getCode());
            Airport realDest   = network.getAirport(sr.getDestination().getCode());
            if (realOrigin == null || realDest == null) {
                // Si un aeropuerto no está en la red (no debería pasar si el
                // NetworkBuilder usa el mismo set de códigos), se ignora el envío.
                continue;
            }
            resolved.add(new ShipmentRequest(
                    sr.getId(), realOrigin, realDest,
                    sr.getQuantity(), sr.getCreationTime()));
        }
        return resolved;
    }

    // ============================ Getters ============================

    public List<ShipmentRequest> getShipments() {
        return Collections.unmodifiableList(shipments);
    }

    public Set<String> getIcaoCodes() {
        return Collections.unmodifiableSet(icaoCodes);
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    /** Combina FECHA + HH:MM de una línea en un LocalDateTime (utilidad opcional). */
    public static LocalDateTime toDateTime(LocalDate date, int h, int m) {
        return LocalDateTime.of(date, LocalTime.of(h, m));
    }
}
