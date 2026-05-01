package pe.edu.pucp.tasf.io;

import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Continent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cargador de la tabla real de aeropuertos del enunciado de la PUCP
 * (archivo "c_1inf54_26_1_v1_Aeropuerto_husos_v1_20250818__estudiantes.txt").
 *
 * Formato esperado por línea (ancho fijo):
 *   NN   ICAO   CIUDAD                PAIS            ABRV   GMT   CAP   ...lat/lon...
 *
 * Ejemplo:
 *   01   SKBO   Bogota              Colombia        bogo    -5     430     Latitude: 04° 42'...
 *
 * El archivo viene codificado en UTF-16 (BE o LE con BOM); el cargador detecta
 * automáticamente el encoding. Las líneas que comienzan con "***" o que
 * corresponden a títulos de sección ("America del Sur", "Europa", "Asia") se
 * ignoran. Sólo se procesan las líneas que comienzan con un número de 2 dígitos.
 *
 * El código ICAO determina el continente vía {@link Continent#fromIcao(String)}.
 * El huso horario GMT (offset entero) y la capacidad de almacén se leen
 * directamente del archivo y se almacenan en el {@link Airport} resultante.
 */
public class AirportsLoader {

    /** Aeropuertos cargados, indexados por código ICAO en orden de aparición. */
    private final Map<String, Airport> airports = new LinkedHashMap<>();

    /** Huso horario GMT por aeropuerto, en horas (puede ser negativo). */
    private final Map<String, Integer> gmtByCode = new LinkedHashMap<>();

    /**
     * Lee el archivo de aeropuertos. Detecta automáticamente UTF-16 BE/LE
     * mediante BOM; si no hay BOM, asume UTF-16 BE (formato del archivo
     * entregado por el curso).
     */
    public void loadFromFile(Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        Charset charset = detectCharset(raw);

        // Saltar BOM si está presente
        int offset = 0;
        if (raw.length >= 2) {
            if ((raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) offset = 2; // UTF-16 BE
            else if ((raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) offset = 2; // UTF-16 LE
            else if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                    && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) offset = 3; // UTF-8 BOM
        }

        String content = new String(raw, offset, raw.length - offset, charset);
        try (BufferedReader br = new BufferedReader(
                new java.io.StringReader(content))) {
            String line;
            while ((line = br.readLine()) != null) {
                parseLine(line);
            }
        }
        System.out.printf("[AirportsLoader] %d aeropuertos cargados desde %s%n",
                airports.size(), file.getFileName());
    }

    /**
     * Detección heurística de encoding del archivo de aeropuertos.
     *
     * - Si encuentra un BOM (FE FF, FF FE o EF BB BF) usa el encoding del BOM.
     * - Si no hay BOM, asume UTF-16 BE (es el caso del archivo entregado por
     *   la PUCP, que contiene un byte 0x00 antes de cada carácter ASCII).
     * - Si el archivo es claramente ASCII puro (sin bytes 0x00), usa UTF-8.
     */
    private Charset detectCharset(byte[] data) {
        if (data.length >= 2) {
            if ((data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF)
                return StandardCharsets.UTF_16BE;
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE)
                return StandardCharsets.UTF_16LE;
        }
        // Heurística: si los bytes pares (0,2,4...) son mayoritariamente 0x00,
        // se trata de UTF-16 BE sin BOM. Si los impares son 0x00, es UTF-16 LE.
        int evenZeros = 0, oddZeros = 0, sample = Math.min(data.length, 200);
        for (int i = 0; i < sample; i++) {
            if (data[i] == 0) {
                if ((i & 1) == 0) evenZeros++;
                else oddZeros++;
            }
        }
        if (evenZeros > sample / 4) return StandardCharsets.UTF_16BE;
        if (oddZeros > sample / 4)  return StandardCharsets.UTF_16LE;
        return StandardCharsets.UTF_8;
    }

    /**
     * Procesa una línea del archivo de aeropuertos. El formato es de ancho
     * fijo, por lo que extraemos los campos por posición tras dividir por
     * espacios consecutivos. Sólo aceptamos líneas que comienzan con un
     * número de 2 dígitos (índice 01..30).
     */
    private void parseLine(String line) {
        if (line == null || line.isBlank()) return;
        String trimmed = line.trim();
        // Las líneas válidas comienzan con dos dígitos seguidos de espacios
        if (trimmed.length() < 4) return;
        if (!Character.isDigit(trimmed.charAt(0)) || !Character.isDigit(trimmed.charAt(1))) return;

        // Dividir por espacios consecutivos. Ejemplo de tokens:
        //   [01, SKBO, Bogota, Colombia, bogo, -5, 430, Latitude:, 04°, ...]
        String[] tok = trimmed.split("\\s+");
        if (tok.length < 7) return;

        String icao = tok[1];
        if (icao.length() != 4) return; // sanity check

        // El campo CIUDAD puede tener varias palabras (ej: "Buenos Aires",
        // "Santiago de Chile"). El campo PAIS también ("Arabia Saudita",
        // "Emiratos A.U"). Aprovechamos que GMT siempre es un signo +/- seguido
        // de dígitos, y CAPACIDAD es un entero de 3 dígitos.
        // Buscamos el índice del primer token que parece GMT (^[+-]?\d+$).
        int gmtIdx = -1;
        for (int i = 4; i < tok.length; i++) {
            if (tok[i].matches("[+-]?\\d{1,2}")) {
                // Verificamos que el siguiente token sea un entero de 3 dígitos (capacidad)
                if (i + 1 < tok.length && tok[i + 1].matches("\\d{3,4}")) {
                    gmtIdx = i;
                    break;
                }
            }
        }
        if (gmtIdx < 0) return;

        int gmt;
        int capacity;
        try {
            gmt = Integer.parseInt(tok[gmtIdx]);
            capacity = Integer.parseInt(tok[gmtIdx + 1]);
        } catch (NumberFormatException e) {
            return;
        }

        // Reconstruir el nombre de ciudad: tokens entre [2, gmtIdx-2)
        // (los últimos 2 antes de GMT son [PAIS, ABRV])
        StringBuilder city = new StringBuilder();
        for (int i = 2; i < gmtIdx - 2; i++) {
            if (city.length() > 0) city.append(' ');
            city.append(tok[i]);
        }

        Continent cont = Continent.fromIcao(icao);
        Airport airport = new Airport(icao, city.toString(), cont, capacity);
        airports.put(icao, airport);
        gmtByCode.put(icao, gmt);
    }

    /** Devuelve los aeropuertos cargados, en orden de aparición. */
    public List<Airport> getAirports() {
        return new ArrayList<>(airports.values());
    }

    /** Devuelve el aeropuerto con el código indicado, o {@code null} si no existe. */
    public Airport getAirport(String code) {
        return airports.get(code);
    }

    /** Devuelve el offset GMT (en horas) del aeropuerto indicado. */
    public Integer getGmt(String code) {
        return gmtByCode.get(code);
    }

    /** Mapa completo de huso horario por código ICAO. */
    public Map<String, Integer> getGmtMap() {
        return gmtByCode;
    }
}
