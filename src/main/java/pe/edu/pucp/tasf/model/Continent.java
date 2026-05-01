package pe.edu.pucp.tasf.model;

/**
 * Enumeración de los continentes donde opera Tasf.B2B.
 *
 * Según el enunciado del problema, la empresa traslada maletas entre aeropuertos
 * de América, Asia y Europa. El tiempo de tránsito entre dos ciudades depende de
 * si están en el mismo continente (0.5 días) o en distinto (1.0 día).
 *
 * Esta enumeración también provee un helper estático para clasificar los códigos
 * ICAO de 4 letras que aparecen en los archivos de entrada reales
 * (_envios_XXXX_.txt), usando el primer carácter del código:
 *
 *   - S*  → Sudamérica (SABE, SBBR, SCEL, SEQM, SGAS, SKBO, SLLP, SPIM, SUAA, SVMI)   → AMERICA
 *   - K*  → USA, C* → Canadá, M* → México, T* → Centroamérica/Caribe                 → AMERICA
 *   - E*  → Europa norte/central (EBCI, EDDI, EHAM, EKCH)                             → EUROPE
 *   - L*  → Europa sur (LATI, LBSF, LDZA, LKPR, LOWW)                                 → EUROPE
 *   - B*  → Islandia/Groenlandia (convenio: EUROPE)                                   → EUROPE
 *   - O*  → Medio Oriente (OAKB, OERK, OJAI, OMDB, OOMS, OPKC, OSDI, OYSN)            → ASIA
 *   - U*  → Ex-URSS (UBBB, UMMS) — para los códigos de este problema corresponden
 *           a Asia Central (Azerbaiyán) y Bielorrusia. Se mapea como ASIA.            → ASIA
 *   - V*  → Asia del Sur / Sudeste (VIDP)                                             → ASIA
 *   - R*, Z*  → Asia oriental                                                         → ASIA
 *   - W*  → Indonesia / Sudeste asiático                                              → ASIA
 *   - Y*  → Oceanía (se agrupa con ASIA a falta de continente específico)             → ASIA
 *   - F*, G*, D*, H*  → África (fuera del alcance real del problema)                  → ASIA (fallback)
 */
public enum Continent {
    AMERICA,
    ASIA,
    EUROPE;

    /**
     * Devuelve el continente correspondiente al código ICAO de 4 letras del aeropuerto.
     *
     * @param icaoCode código ICAO (p. ej. "SPIM", "EDDI", "OMDB")
     * @return continente asignado según la primera letra del código
     */
    public static Continent fromIcao(String icaoCode) {
        if (icaoCode == null || icaoCode.isEmpty()) {
            // Si el código es inválido, devolvemos AMERICA como valor por defecto
            return AMERICA;
        }
        char prefix = Character.toUpperCase(icaoCode.charAt(0));
        return switch (prefix) {
            // América: Sudamérica (S), Estados Unidos (K), Canadá (C), México (M),
            // Centroamérica / Caribe (T)
            case 'S', 'K', 'C', 'M', 'T' -> AMERICA;

            // Europa: prefijos E, L y B (Islandia/Groenlandia)
            case 'E', 'L', 'B' -> EUROPE;

            // Asia (incluye Medio Oriente, Asia Central, Sur y Sudeste asiático)
            case 'O', 'U', 'V', 'R', 'Z', 'W', 'Y' -> ASIA;

            // Para cualquier otro prefijo (por ejemplo África: F, G, D, H) devolvemos
            // ASIA como valor de reserva. El dataset real del problema no contiene
            // aeropuertos africanos, así que este caso es sólo de seguridad.
            default -> ASIA;
        };
    }
}
