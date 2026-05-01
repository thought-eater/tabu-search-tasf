package pe.edu.pucp.tasf.util;

import pe.edu.pucp.tasf.io.RealFlightLoader;
import pe.edu.pucp.tasf.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Constructor de red logística a partir de un conjunto de códigos ICAO reales.
 *
 * Si se provee la ruta al archivo planes_vuelo.txt (método {@link #build(Collection, Path)}),
 * los vuelos se cargan desde ese archivo en lugar de generarse sintéticamente.
 * Si no se provee (método {@link #build(Collection)}), se genera una malla sintética
 * como fallback.
 */
public class RealNetworkBuilder {

    private final Random random;

    public RealNetworkBuilder(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Construye la red logística usando vuelos REALES del archivo planes_vuelo.txt.
     * Solo se añaden los vuelos cuyos aeropuertos origen y destino estén en icaoCodes.
     *
     * @param icaoCodes    códigos ICAO de los aeropuertos (de los archivos _envios_)
     * @param flightsFile  ruta a planes_vuelo.txt
     */
    public LogisticsNetwork build(Collection<String> icaoCodes, Path flightsFile) {
        LogisticsNetwork network = buildAirports(icaoCodes);

        try {
            RealFlightLoader loader = new RealFlightLoader();
            int loaded = loader.load(flightsFile, network);
            System.out.printf("[RealNetworkBuilder] Vuelos reales cargados: %d%n", loaded);
        } catch (IOException e) {
            System.err.println("[RealNetworkBuilder] No se pudo leer planes_vuelo.txt: "
                    + e.getMessage() + " — usando vuelos sintéticos como fallback.");
            addSyntheticFlights(network);
        }

        return network;
    }

    /**
     * Construye la red logística usando vuelos SINTÉTICOS (fallback sin planes_vuelo.txt).
     */
    public LogisticsNetwork build(Collection<String> icaoCodes) {
        LogisticsNetwork network = buildAirports(icaoCodes);
        addSyntheticFlights(network);
        return network;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Crea los Airport y los registra en la red. */
    private LogisticsNetwork buildAirports(Collection<String> icaoCodes) {
        LogisticsNetwork network = new LogisticsNetwork();
        for (String code : icaoCodes) {
            Continent cont = Continent.fromIcao(code);
            int capacity = randWarehouseCapacity();
            Airport a = new Airport(code, code, cont, capacity);
            network.addAirport(a);
        }
        return network;
    }

    /** Genera vuelos sintéticos según las reglas del enunciado (malla completa). */
    private void addSyntheticFlights(LogisticsNetwork network) {
        Map<Continent, List<Airport>> byContinent = new EnumMap<>(Continent.class);
        for (Continent c : Continent.values()) byContinent.put(c, new ArrayList<>());
        for (Airport a : network.getAirports()) {
            byContinent.get(a.getContinent()).add(a);
        }

        int flightId = 1;

        // Intra-continentales: 2 frecuencias
        for (List<Airport> group : byContinent.values()) {
            for (Airport o : group) {
                for (Airport d : group) {
                    if (o.equals(d)) continue;
                    for (double dep : new double[]{0.10, 0.55}) {
                        network.addFlight(new Flight(
                                "F" + (flightId++), o, d, randSameContCapacity(), dep));
                    }
                }
            }
        }

        // Inter-continentales: 1 frecuencia garantizada, 2ª con 50%
        List<Continent> continents = Arrays.asList(Continent.values());
        for (Continent oc : continents) {
            for (Continent dc : continents) {
                if (oc == dc) continue;
                List<Airport> origins = byContinent.get(oc);
                List<Airport> dests   = byContinent.get(dc);
                if (origins.isEmpty() || dests.isEmpty()) continue;
                for (Airport o : origins) {
                    for (Airport d : dests) {
                        if (random.nextDouble() > 0.70) continue;
                        double dep1 = 0.05 + random.nextDouble() * 0.25;
                        network.addFlight(new Flight(
                                "F" + (flightId++), o, d, randDiffContCapacity(), dep1));
                        if (random.nextDouble() < 0.50) {
                            double dep2 = 0.40 + random.nextDouble() * 0.30;
                            network.addFlight(new Flight(
                                    "F" + (flightId++), o, d, randDiffContCapacity(), dep2));
                        }
                    }
                }
            }
        }
    }

    private int randWarehouseCapacity()  { return 500 + random.nextInt(301); }
    private int randSameContCapacity()   { return 150 + random.nextInt(101); }
    private int randDiffContCapacity()   { return 150 + random.nextInt(251); }
}
