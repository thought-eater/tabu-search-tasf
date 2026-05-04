package pe.edu.pucp.tasf.util;

import pe.edu.pucp.tasf.io.RealFlightLoader;
import pe.edu.pucp.tasf.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Constructor de red logística a partir de aeropuertos y vuelos reales.
 *
 * Método principal:
 *   {@link #build(List, Path)} — recibe aeropuertos ya cargados desde
 *   {@code aeropuertos.txt} (vía {@link pe.edu.pucp.tasf.io.AirportsLoader})
 *   y carga los vuelos desde {@code planes_vuelo.txt}.
 *
 * Método alternativo (sólo para modos que no disponen del archivo de
 * aeropuertos, como EXP cuando se invoca sin él):
 *   {@link #build(Collection, Path)} — deriva los aeropuertos de los
 *   códigos ICAO encontrados en los envíos y carga los vuelos reales.
 *
 * Ya no existe un modo de respaldo sintético: si {@code planes_vuelo.txt}
 * no se puede leer, se lanza una excepción en lugar de generar vuelos al azar.
 */
public class RealNetworkBuilder {

    public RealNetworkBuilder(long seed) {
        // seed no utilizada: eliminado el generador sintético
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye la red logística a partir de una lista de aeropuertos ya
     * cargados (p.ej. desde {@code aeropuertos.txt} con
     * {@link pe.edu.pucp.tasf.io.AirportsLoader}) y del archivo de vuelos.
     *
     * @param airports   aeropuertos a registrar en la red
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     * @throws IOException si el archivo de vuelos no se puede leer
     */
    public LogisticsNetwork build(List<Airport> airports, Path flightsFile) throws IOException {
        LogisticsNetwork network = new LogisticsNetwork();
        for (Airport a : airports) {
            network.addAirport(a);
        }
        RealFlightLoader loader = new RealFlightLoader();
        int loaded = loader.load(flightsFile, network);
        System.out.printf("[RealNetworkBuilder] Aeropuertos: %d | Vuelos reales cargados: %d%n",
                airports.size(), loaded);
        return network;
    }

    /**
     * Construye la red logística derivando los aeropuertos de los códigos ICAO
     * presentes en los envíos. Usado como fallback cuando no se dispone del
     * archivo {@code aeropuertos.txt} completo (p.ej. modo EXP sin ese archivo).
     *
     * @param icaoCodes   códigos ICAO descubiertos en los archivos _envios_
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     * @throws IOException si el archivo de vuelos no se puede leer
     */
    public LogisticsNetwork build(Collection<String> icaoCodes, Path flightsFile) throws IOException {
        LogisticsNetwork network = buildAirportsFromIcao(icaoCodes);
        RealFlightLoader loader = new RealFlightLoader();
        int loaded = loader.load(flightsFile, network);
        System.out.printf("[RealNetworkBuilder] Aeropuertos (ICAO): %d | Vuelos reales cargados: %d%n",
                network.getAirportCount(), loaded);
        return network;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Crea Airport mínimos a partir de códigos ICAO (continente auto-derivado). */
    private LogisticsNetwork buildAirportsFromIcao(Collection<String> icaoCodes) {
        LogisticsNetwork network = new LogisticsNetwork();
        for (String code : icaoCodes) {
            Continent cont = Continent.fromIcao(code);
            Airport a = new Airport(code, code, cont, 600); // capacidad fija por defecto
            network.addAirport(a);
        }
        return network;
    }
}
