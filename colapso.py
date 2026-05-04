#!/usr/bin/env python3
"""
colapso.py — Acumula maletas por fecha desde todos los archivos
_envios_*.txt en _envios_preliminar_/ y muestra el ranking descendente.
"""

import glob
import os
from collections import defaultdict

# Carpeta de envíos (relativa a este script)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FOLDER = os.path.join(SCRIPT_DIR, "_envios_preliminar_")

bags_per_date: dict[str, int] = defaultdict(int)
total_lines = 0

for filepath in sorted(glob.glob(os.path.join(FOLDER, "_envios_*.txt"))):
    with open(filepath, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split("-")
            # parts[1] = fecha (YYYYMMDD), parts[5] = cantidad de maletas
            if len(parts) < 7:
                continue
            date = parts[1]
            try:
                qty = int(parts[5])
            except ValueError:
                continue
            bags_per_date[date] += qty
            total_lines += 1

# Ordenar descendente por cantidad de maletas
sorted_dates = sorted(bags_per_date.items(), key=lambda x: x[1], reverse=True)

# ── Salida ────────────────────────────────────────────────────────────────────
col_w = 12
header = f"{'Fecha':<{col_w}}  {'Maletas':>10}  {'Acum. %':>8}"
print(header)
print("-" * len(header))

total_bags = sum(bags_per_date.values())
acum = 0
for date, qty in sorted_dates:
    acum += qty
    pct = acum / total_bags * 100 if total_bags else 0
    # Formato fecha legible: YYYYMMDD -> YYYY-MM-DD
    d = f"{date[:4]}-{date[4:6]}-{date[6:]}" if len(date) == 8 else date
    print(f"{d:<{col_w}}  {qty:>10,}  {pct:>7.2f}%")

print("-" * len(header))
print(f"{'TOTAL':<{col_w}}  {total_bags:>10,}  {'100.00%':>8}")
print(
    f"\nArchivos procesados : {len(list(glob.glob(os.path.join(FOLDER, '_envios_*.txt'))))}"
)
print(f"Lineas procesadas   : {total_lines:,}")
