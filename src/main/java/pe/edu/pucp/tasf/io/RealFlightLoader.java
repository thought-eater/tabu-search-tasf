package pe.edu.pucp.tasf.io;

import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Flight;
import pe.edu.pucp.tasf.model.LogisticsNetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cargador del archivo real de planes de vuelo: planes_vuelo.txt
 *
 * Formato de cada línea (5 campos separados por '-'):
 *   ORIGEN-DESTINO-HH:MM_SALIDA-HH:MM_LLEGADA-CAPACIDAD
 *
 * Ejemplo:
 *   SKBO-SEQM-03:34-04:21-0300
 *   LDZA-SKBO-17:59-23:10-0360   (llegada al día siguiente si llega < sale)
 *
 * El tiempo de salida y llegada se convierte a fracción de día (0.0–1.0),
 * donde 0.0 = medianoche y 1.0 = medianoche siguiente.
 * Si la llegada (en fracción) es menor que la salida, se asume que aterriza
 * al día siguiente (se le suma 1.0).
 *
 * Sólo se cargan vuelos cuyos aeropuertos ORIGEN y DESTINO estén registrados
 * en la {@link LogisticsNetwork} proporcionada.
 */
public class RealFlightLoader {

    /**
     * Lee planes_vuelo.txt e inyecta los vuelos en la red logística dada.
     * Los aeropuertos deben estar ya registrados en la red antes de llamar a este método.
     *
     * @param filePath ruta al archivo planes_vuelo.txt
     * @param network  red logística donde se añadirán los vuelos
     * @return número de vuelos cargados con éxito
     * @throws IOException si no se puede leer el archivo
     */
    public int load(Path filePath, LogisticsNetwork network) throws IOException {
        List<String> skipped = new ArrayList<>();
        int loaded = 0;
        int lineNum = 0;

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNum++;
                // Normalizar: quitar \r y espacios
                line = line.trim().replace("\r", "");
                if (line.isEmpty()) continue;

                String[] parts = line.split("-");
                if (parts.length != 5) {
                    skipped.add("Línea " + lineNum + " (campos=" + parts.length + "): " + line);
                    continue;
                }

                String originCode = parts[0].trim();
                String destCode   = parts[1].trim();
                String depStr     = parts[2].trim();  // HH:MM
                String arrStr     = parts[3].trim();  // HH:MM
                String capStr     = parts[4].trim();  // NNNN (con posible cero izquierdo)

                Airport origin = network.getAirport(originCode);
                Airport dest   = network.getAirport(destCode);

                if (origin == null || dest == null) {
                    // Aeropuerto no registrado en la red: se omite silenciosamente
                    continue;
                }

                double depFrac = parseTimeFraction(depStr);
                double arrFrac = parseTimeFraction(arrStr);
                int capacity;
                try {
                    capacity = Integer.parseInt(capStr);
                } catch (NumberFormatException e) {
                    skipped.add("Línea " + lineNum + " (capacidad inválida): " + line);
                    continue;
                }

                // Si la llegada es antes que la salida en fracción de día,
                // el vuelo aterriza al día siguiente.
                if (arrFrac <= depFrac) {
                    arrFrac += 1.0;
                }

                String flightId = "RF" + (loaded + 1);
                Flight flight = new Flight(flightId, origin, dest, capacity, depFrac, arrFrac);
                network.addFlight(flight);
                loaded++;
            }
        }

        if (!skipped.isEmpty()) {
            System.err.println("[RealFlightLoader] " + skipped.size() + " líneas omitidas:");
            skipped.stream().limit(5).forEach(s -> System.err.println("  " + s));
            if (skipped.size() > 5) System.err.println("  ... y " + (skipped.size() - 5) + " más.");
        }

        return loaded;
    }

    /**
     * Convierte "HH:MM" a fracción de día [0.0, 1.0).
     * Devuelve -1 si el formato es inválido.
     */
    private double parseTimeFraction(String hhmm) {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) return 0.0;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return (h + m / 60.0) / 24.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
