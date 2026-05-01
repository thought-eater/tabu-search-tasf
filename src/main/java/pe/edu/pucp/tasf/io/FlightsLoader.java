package pe.edu.pucp.tasf.io;

import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Flight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cargador del archivo planes_vuelo.txt con los horarios reales de Tasf.B2B.
 *
 * Formato esperado (una línea por vuelo):
 *
 *     ORIGEN-DESTINO-HHMM_SAL-HHMM_LLEG-CAPACIDAD
 *
 * Ejemplo:
 *     SKBO-SEQM-03:34-04:21-0300
 *
 * donde:
 *   - ORIGEN, DESTINO : códigos ICAO de 4 letras
 *   - HH:MM            : horas locales de salida y llegada en formato 24h
 *   - CAPACIDAD        : capacidad máxima del vuelo (4 dígitos)
 *
 * Nota: las horas son LOCALES de cada aeropuerto. Para convertir a un eje
 * temporal global (fracción de día desde la fecha base) se usa el offset GMT
 * del aeropuerto origen — proporcionado por {@link AirportsLoader}.
 *
 * El tiempo de tránsito de cada vuelo (transitTime) lo calcula automáticamente
 * la clase {@link Flight} a partir de los continentes de origen y destino
 * (0.5 días intra-continental, 1.0 día inter-continental), conforme al
 * enunciado del curso. Por consistencia con esa decisión, este cargador
 * sólo usa las horas para fijar la salida del vuelo (departureTime).
 */
public class FlightsLoader {

    private final List<Flight> flights = new ArrayList<>();

    /**
     * Lee el archivo de vuelos y construye objetos {@link Flight} usando los
     * {@link Airport} previamente cargados. Las horas se interpretan como
     * locales y se convierten a fracción de día UTC restando el offset GMT
     * del aeropuerto origen.
     *
     * @param file       ruta a planes_vuelo.txt
     * @param airportMap mapa código ICAO → Airport (debe contener todos los aeropuertos referenciados)
     * @param gmtMap     mapa código ICAO → offset GMT (en horas) — para convertir hora local a UTC
     */
    public void loadFromFile(Path file,
                             Map<String, Airport> airportMap,
                             Map<String, Integer> gmtMap) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int flightId = 1;
        int skipped  = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Parseo robusto: separamos por '-' pero las horas también contienen ':'.
            // Formato esperado: ORIG-DEST-HH:MM-HH:MM-CAP
            String[] tok = line.split("-");
            if (tok.length != 5) {
                skipped++;
                continue;
            }
            String orig = tok[0].trim();
            String dest = tok[1].trim();
            String depHHMM = tok[2].trim();
            String arrHHMM = tok[3].trim();
            String capStr  = tok[4].trim();

            Airport oA = airportMap.get(orig);
            Airport dA = airportMap.get(dest);
            if (oA == null || dA == null) {
                // Aeropuerto no presente en la red real cargada
                skipped++;
                continue;
            }

            double depFracLocal = parseHHMM(depHHMM);
            if (depFracLocal < 0) { skipped++; continue; }

            // Convertir hora local a UTC restando el offset GMT del origen.
            // Ej: si origen es Lima (GMT-5), 03:34 local = 08:34 UTC.
            int gmtOrig = gmtMap.getOrDefault(orig, 0);
            double depFracUtc = depFracLocal - gmtOrig / 24.0;
            // Normalizar al rango [0,1) — si se pasa de día, se interpreta como
            // hora del día siguiente
            depFracUtc = ((depFracUtc % 1.0) + 1.0) % 1.0;

            int cap;
            try {
                cap = Integer.parseInt(capStr);
            } catch (NumberFormatException e) {
                skipped++;
                continue;
            }

            String id = "F" + (flightId++);
            flights.add(new Flight(id, oA, dA, cap, depFracUtc));
        }

        System.out.printf("[FlightsLoader] %d vuelos cargados (%d líneas omitidas) desde %s%n",
                flights.size(), skipped, file.getFileName());
    }

    /**
     * Convierte una cadena "HH:MM" a fracción de día (0.0 = 00:00, 0.5 = 12:00).
     * Devuelve -1 si la cadena no tiene el formato esperado.
     */
    private double parseHHMM(String s) {
        try {
            String[] hm = s.split(":");
            if (hm.length != 2) return -1;
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return (h * 60 + m) / (24.0 * 60);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public List<Flight> getFlights() { return flights; }
}
