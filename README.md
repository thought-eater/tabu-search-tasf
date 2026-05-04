# Tabu Search — Air Cargo Scheduling (TASF)

PUCP 2026-1 · Java 17 · Maven

A **Tabu Search metaheuristic** that solves a multi-airport, multi-flight logistics scheduling problem: given a set of shipment requests (bags that need to travel from one airport to another), find the best assignment of routes through a flight network such that as many bags as possible arrive on time.

---

## Table of Contents

1. [Tabu Search — The Algorithm](#1-tabu-search--the-algorithm)
2. [The Problem Being Solved](#2-the-problem-being-solved)
3. [Data Model](#3-data-model)
4. [Algorithm Implementation](#4-algorithm-implementation)
5. [Simulation Modes](#5-simulation-modes)
6. [Input / Output Formats](#6-input--output-formats)
7. [Build & Run](#7-build--run)
8. [Known Limitations & Design Notes](#8-known-limitations--design-notes)

> **Note:** All simulation modes use the real logistics network loaded from
> `aeropuertos.txt` (airports) and `planes_vuelo.txt` (flights). There is no
> synthetic network generation.

---

## 1. Tabu Search — The Algorithm

### 1.1 Where It Sits Among Metaheuristics

You are already familiar with swarm-based metaheuristics (bees, ants, particles). These maintain a **population** of solutions that communicate to guide the search. Tabu Search (TS) is different: it maintains a **single current solution** and navigates the solution space by making local moves — much closer in spirit to hill climbing or simulated annealing — but it adds an explicit **memory structure** that prevents cycling and forces the search to explore new territory.

| Feature | Bee Algorithm | Simulated Annealing | Tabu Search |
|---|---|---|---|
| Population | Multi-agent (bees) | Single solution | Single solution |
| Escape mechanism | Scouts → random restart | Probabilistic acceptance of worse moves | Forbidden move list (tabu) |
| Memory | None (pheromones are implicit) | None | Explicit short-term memory |
| Intensification | Employed bees exploit best | Cooling schedule | Return-to-best (`intensify`) |
| Diversification | Scout bees explore | High temperature early on | Perturbation (`diversify`) + tabu forcing |

The key insight of TS: **if you remember which moves you just made, you can forbid them for a while, which forces the search away from where it already was**. This is a principled way to escape local optima without randomness.

---

### 1.2 Core Concepts

#### Solution Space and Neighborhoods

A **solution** is one complete assignment of routes to all shipments. The **solution space** is the set of all possible such assignments.

A **neighborhood** `N(s)` of a solution `s` is the set of solutions reachable by applying a single **move** to `s`. In this project, the move type is a **REROUTE**: change the sequence of flights assigned to exactly one shipment, leaving all others unchanged.

```
                 Solution Space
                 ─────────────
    s1  s2  s3  [s4]  s5  s6  s7
                  │
                  │  one REROUTE move changes the route
                  │  of shipment #3 from [F1→F4] to [F2→F5]
                  ▼
    s1  s2  s3  [s4'] s5  s6  s7
```

#### Local Search (without memory)

Naive local search picks the best neighbor at every step:

```
s = initial_solution
loop:
    s_best_neighbor = argmin { fitness(n) | n in N(s) }
    if fitness(s_best_neighbor) >= fitness(s):
        stop  // local optimum — can't improve anymore
    s = s_best_neighbor
```

The problem: it terminates at the first **local optimum**, which is rarely the global optimum. In a large solution space, there are many valleys you can fall into.

#### The Tabu List

Tabu Search removes the stopping condition and instead uses a **tabu list** to record recently visited moves. A move on the tabu list is **forbidden** — even if it would improve the current solution. This forces the search to keep moving, even uphill (accepting worse solutions temporarily):

```
s = initial_solution
best = s
tabu_list = {}

loop until stopping condition:
    candidates = { n in N(s) | move(s→n) not in tabu_list }
    s = argmin { fitness(n) | n in candidates }   // best non-tabu neighbor
    tabu_list.add(move(s_prev → s), tenure=T)     // forbid this move for T iters
    if fitness(s) < fitness(best):
        best = s
return best
```

The **tenure** `T` controls how long a move stays forbidden. A short tenure (e.g., 5 iterations) allows revisiting moves quickly; a long tenure (e.g., 20) forces deeper exploration before returning to previously visited areas.

```
Iteration:  1    2    3    4    5    6    7    8    9    10
Move M1:    ADD  ████████████████████  (tenure=5, expires at iter 6)
Move M2:         ADD  ████████████████████████████ (tenure=7)
Move M3:              ADD  ████████
                                    └─ M1 is now allowed again
```

#### Aspiration Criterion

Sometimes the best available move is tabu — but it would produce a **new global best**. The aspiration criterion overrides the tabu status in this case:

```
if move is tabu:
    if fitness(neighbor) < fitness(best_ever):
        accept it anyway   // aspiration by quality
    else:
        skip it
```

This prevents the tabu list from blocking exceptional improvements.

#### Diversification and Intensification

Two higher-level strategies complement the tabu list:

- **Intensification**: Every `M` iterations, return to the best-known solution and search its neighborhood more carefully. Exploits the best region found so far.
- **Diversification**: Every `N` iterations, randomly perturb the current solution (change many routes at once). Escapes if the search is stuck in a poor region.

```
                      Fitness landscape (lower = better)
      ┌─────────────────────────────────────────────────────┐
  fit │         *              ← local optimum              │
      │        / \                                          │
      │       /   \    tabu forces uphill ─────────►  *    │
      │──────/─────\──────────────────────────────── / \── │
      │             \                               /   \  │
      │              \                             /     \ │ ← global optimum
      │               \                    ───►  *        │
      │                \                  ↑               │
      │                 \   diversify ────┘               │
      │                  \                                 │
      └─────────────────────────────────────────────────────┘
                         iterations ──►
```

#### Stopping Conditions

TS stops when any of these is met:
- Maximum number of iterations reached
- Wall-clock time limit exceeded
- (Optionally) no improvement for a long stretch

---

### 1.3 Pseudocode

```
function TabuSearch(network, requests, config):
    s = greedyInitialSolution(requests, network)
    best = s.copy()
    tabu = {}

    for iter = 1 to maxIterations:
        if wallClock > timeLimit: break

        // --- Neighborhood exploration ---
        sample = randomSample(requests, neighborhoodSize)
        best_candidate = null

        for each shipment i in sample:
            routes = findAllRoutes(origin_i, dest_i, time_i, maxHops)
            for each route r in routes:
                if r == current_route[i]: continue
                if not capacityFeasible(r): continue

                delta = routeFitness(r) - routeFitness(current_route[i])
                move = TabuMove(i, hash(r))
                is_tabu = tabu[move] > iter

                // Aspiration override
                if is_tabu and (s.fitness + delta) >= best.fitness:
                    continue   // blocked: tabu AND not a new best

                if best_candidate == null or delta < best_candidate.delta:
                    best_candidate = Candidate(i, r, delta, move)

        // --- Apply best candidate ---
        if best_candidate != null:
            applyMove(s, best_candidate)
            tabu[best_candidate.move] = iter + tabuTenure
            if s.fitness < best.fitness:
                best = s.copy()

        // --- Meta-strategies ---
        if iter % diversificationInterval == 0:
            perturbRandomly(s, 20% of routes)
        if iter % intensificationInterval == 0:
            s = best.copy()

        purgeExpiredTabuEntries(tabu, iter)

    return best
```

---

## 2. The Problem Being Solved

### 2.1 The Logistics Network

The network is a **directed weighted graph** `G = (N, A)`:
- **Nodes** `N`: airports, each with an ICAO code (e.g., `SPIM` = Lima) and a warehouse capacity
- **Arcs** `A`: scheduled flights, each with a capacity (bags), departure time, and transit time

```
              SPIM (Lima)
             /           \
           F1(cap=100)   F2(cap=80)
           /               \
       SBGR (Sao Paulo)    LEMD (Madrid)
            \               /
           F3(cap=60)   F4(cap=120)
              \         /
              LFPG (Paris)
```

A **route** for a shipment is a sequence of flights `[F_a, F_b, ..., F_z]` such that:
- `F_a.origin = shipment.origin`
- `F_z.destination = shipment.destination`
- Each connecting flight departs after the previous one arrives (respecting transit time)
- Each flight has enough remaining capacity for the shipment's quantity

### 2.2 Shipment Requests

Each request is a tuple `(origin, destination, quantity, creationTime)`:
- `origin`, `destination`: airports
- `quantity`: number of bags
- `creationTime`: fractional day when the request was created (e.g., `0.375` = 09:00)
- `deadline`: **automatically computed** from continent pairing:
  - Same continent → 1 day
  - Different continents → 2 days

### 2.3 Time Representation

All times are expressed as **fractional days** (a `double`):

```
0.0   = midnight (00:00)
0.25  = 06:00
0.5   = noon (12:00)
0.75  = 18:00
1.0   = midnight next day
1.5   = noon next day
```

This unifies single-day and multi-day scheduling on one continuous axis. A shipment created at `0.375` (09:00) with a 1-day deadline must arrive before `1.375` (09:00 the next day).

### 2.4 Continent Mapping

Continents are derived from the ICAO prefix of the airport code:

| First letter(s) | Continent |
|---|---|
| `S, K, C, M, T` | AMERICA |
| `E, L, B` | EUROPE |
| `O, U, V, R, Z, W, Y` | ASIA |
| anything else | ASIA (fallback) |

This matters for two things: (1) computing delivery deadlines, and (2) computing flight transit times (0.5 days intra-continental, 1.0 day inter-continental for synthetic flights).

### 2.5 Objective Function (Fitness)

**Lower is better.** The fitness is a weighted penalty sum over all routes:

```
fitness = Σ for each shipment i:
    qty = requests[i].quantity
    route = routes[i]

    if route is empty (no path found):
        fitness += 5000 × qty        // infeasibility penalty

    else if route arrives late:
        fitness += 1000 × qty        // late-delivery penalty
        fitness += 100 × delay × qty // delay magnitude penalty
```

The three penalty levels create a strict priority hierarchy:

```
Priority 1: minimize infeasible bags  (weight 5000 per bag)
     │
     ▼
Priority 2: minimize late bags        (weight 1000 per bag)
     │
     ▼
Priority 3: minimize total delay      (weight 100 per bag-day)
```

A solution with one infeasible bag is always worse than any solution where all bags have at least some route (even if late).

---

## 3. Data Model

### 3.1 Class Overview

```
pe.edu.pucp.tasf/
│
├── model/
│   ├── Continent          ← enum: AMERICA, ASIA, EUROPE + ICAO→continent map
│   ├── Airport            ← network node (ICAO, warehouse capacity, current stock)
│   ├── Flight             ← network edge (MUTABLE: tracks assignedLoad)
│   ├── LogisticsNetwork   ← graph G=(N,A): adjacency list + DFS route finder
│   ├── ShipmentRequest    ← demand: (origin, dest, qty, creationTime, deadline)
│   ├── RouteAssignment    ← one shipment's assigned flight sequence
│   └── Solution           ← full solution: List<RouteAssignment> + fitness
│
├── algorithm/
│   ├── TabuSearchConfig   ← fluent builder for all TS parameters
│   ├── TabuMove           ← move identity: (shipmentIndex, routeHash)
│   └── TabuSearchSolver   ← core TS: solve(), replanify(), printReport()
│
├── io/
│   ├── EnviosDataLoader   ← parses _envios_XXXX_.txt shipment files
│   ├── AirportsLoader     ← parses aeropuertos.txt (UTF-16, fixed-width)
│   ├── RealFlightLoader   ← parses planes_vuelo.txt flight schedules
│   └── FlightsLoader      ← alternate loader (not used in active code paths)
│
└── util/
    ├── RealNetworkBuilder ← builds network from Airport list + planes_vuelo.txt
    └── test/
        └── RunnerTest.java ← integration smoke test
```

---

### 3.2 `Airport`

```java
Airport {
    String code;             // ICAO (e.g. "SPIM")
    String city;
    Continent continent;     // derived from code prefix
    int warehouseCapacity;   // max bags storable [500, 800]
    int currentStock;        // MUTABLE — reset by resetLoads()
}
```

Airports are **identity-equal by ICAO code** (`equals`/`hashCode` based on `code` only), so they can be used as `Map` keys safely.

---

### 3.3 `Flight` (Mutable — Critical Design Choice)

```java
Flight {
    String id;               // e.g. "F5", "RF12", "F5_d1" (virtual)
    Airport origin;
    Airport destination;
    int capacity;            // max bags this flight can carry
    double departureTime;    // fractional day
    double transitTime;      // duration (0.5 intra, 1.0 inter for synthetic)
    int assignedLoad;        // MUTABLE — how many bags currently assigned
    boolean cancelled;       // MUTABLE — set by replanify()
}
```

**Why mutable?** The solver never copies `Flight` objects — instead, it directly mutates `assignedLoad` on the shared instances. This avoids copying the entire network on every move. The consequence is:

> `LogisticsNetwork.resetLoads()` **must** be called before every `solve()` invocation. Forgetting this causes load state from the previous run to corrupt the new run.

When `assignedLoad > capacity` on a flight used by a route, that `RouteAssignment.isFeasible()` returns `false` and the route incurs the infeasibility penalty.

---

### 3.4 `LogisticsNetwork`

The graph. Internally stored as:
- `LinkedHashMap<String, Airport>` — airports indexed by ICAO code
- `List<Flight>` — all flights
- `Map<String, List<Flight>>` — adjacency list: `outgoing.get("SPIM")` = all flights departing Lima

**Key method — multi-day flight expansion:**

```java
getActiveFlightsFrom(airportCode, minDepartureTime)
```

Returns all non-cancelled flights departing from the given airport at or after `minDepartureTime`. If `minDepartureTime > 0.5`, the network creates **virtual next-day flights** with IDs like `F5_d1` (day 1) or `F5_d2` (day 2). These are ephemeral objects — they exist only for the duration of the route search and are never stored in the network.

```
Original flight F5: SPIM→LEMD, departs 0.25 (06:00)

Shipment arrives at SPIM at time 0.8 (19:12) — too late for today's F5.
→ Virtual flight F5_d1: SPIM→LEMD, departs 1.25 (06:00 next day)
→ Virtual flight F5_d2: SPIM→LEMD, departs 2.25 (06:00 day after)
```

**Key method — route search:**

```java
findRoutes(origin, destination, startTime, maxHops)
```

Depth-first search (DFS) that enumerates **all simple paths** (no airport repeated) from `origin` to `destination` using at most `maxHops` flight legs. Returns a `List<List<Flight>>` — every possible path.

> Warning: complexity is exponential in `maxHops`. The code recommends `maxHops ≤ 3`.

---

### 3.5 `ShipmentRequest`

```java
ShipmentRequest {
    Airport origin;
    Airport destination;
    int quantity;
    double creationTime;  // fractional day (e.g. 0.375 = 09:00)
    double deadline;      // 1.0 intra-continental, 2.0 inter-continental
}
```

`isOverdue(currentTime)` returns true when `(currentTime - creationTime) > deadline`.

---

### 3.6 `RouteAssignment`

Represents the planned itinerary for one `ShipmentRequest`:

```java
RouteAssignment {
    ShipmentRequest request;
    List<Flight> flights;    // ordered sequence of legs
}
```

**Transit time calculation** accumulates both flight duration and waiting time between connections:

```
currentTime = request.creationTime
for each flight f in flights:
    waitTime = max(0, f.departureTime - currentTime)
    currentTime += waitTime + f.transitTime
arrivalTime = currentTime
```

**Feasibility check:**
- `isFeasible()` = no cancelled flights AND no flight where `assignedLoad > capacity`
- `isOnTime()` = `arrivalTime ≤ creationTime + deadline`
- `getDelay()` = `max(0, arrivalTime - (creationTime + deadline))`

**Empty route** (no path found during greedy or TS): `getTotalTransitTime()` returns `999.0`, signals infeasibility, triggers `5000 × qty` penalty.

---

### 3.7 `Solution`

```java
Solution {
    List<RouteAssignment> routes;  // one per ShipmentRequest, same order
}
```

**Fitness computation** (`getFitness()`):

```java
double fitness = 0;
for (RouteAssignment ra : routes) {
    int qty = ra.getRequest().getQuantity();
    if (ra.getFlights().isEmpty()) {
        fitness += 5000 * qty;              // infeasible route
    } else if (!ra.isOnTime()) {
        fitness += 1000 * qty;              // late delivery
        fitness += 100 * ra.getDelay() * qty; // delay penalty
    }
}
```

**`getCapacityOverflow()`** collects unique `Flight` objects across all routes (via a `Set`) and sums `max(0, assignedLoad - capacity)` per flight. This prevents double-counting when multiple shipments share the same flight leg.

**`copy()`** performs a deep copy: new `Solution` with new `RouteAssignment` objects each holding a new `List<Flight>` (flight objects themselves remain shared, they are not copied).

---

## 4. Algorithm Implementation

### 4.1 Initial Solution — Greedy Heuristic

Before the TS loop starts, a greedy solution is built:

```
1. network.resetLoads()     // zero all flight assignedLoads

2. Sort shipments by:
   - deadline ASC  (most urgent first, so they get first pick of capacity)
   - quantity DESC (larger shipments get priority among equal-deadline ones)

3. For each shipment in sorted order:
   routes = findRoutes(origin, dest, creationTime, maxHops)
   best = route with minimum total transit time that is capacity-feasible
   if best exists:
       assign each flight in best (f.assignedLoad += qty)
       add RouteAssignment(shipment, best) to solution
   else:
       add RouteAssignment(shipment, []) to solution  // empty = penalized
```

Urgency-first ordering ensures tight-deadline shipments secure capacity before it fills up. This typically produces a reasonable starting solution that the TS can quickly improve.

---

### 4.2 The REROUTE Move

The only move type implemented is **REROUTE**: change the sequence of flights for exactly one shipment.

```
Before move:
  Shipment #3 (SPIM → LFPG, 50 bags):  [F1, F4]
  Shipment #7 (SPIM → LEMD, 30 bags):  [F2, F8]

After REROUTE(shipment=3, newRoute=[F2, F7]):
  Shipment #3 (SPIM → LFPG, 50 bags):  [F2, F7]  ← changed
  Shipment #7 (SPIM → LEMD, 30 bags):  [F2, F8]  ← unchanged
  (Note: F2 is now shared between shipments 3 and 7)
```

**Move identity (`TabuMove`):**

A move is identified by `(shipmentIndex, routeHash)` where `routeHash` is a polynomial hash over the flight IDs:

```java
int hash = 0;
for (Flight f : route) {
    hash = 31 * hash + f.getId().hashCode();
}
```

Two identical routes for the same shipment produce the same `TabuMove` and will be blocked for `tabuTenure` iterations. The tabu list stores `Map<TabuMove, Integer>` (move → expiry iteration).

---

### 4.3 Neighborhood Exploration (Delta Fitness, In-Place)

Rather than copying the entire solution for each candidate, the solver evaluates delta fitness using **temporary in-place mutations**:

```
for each candidate shipment i (sampled from neighborhoodSize):
    old_route = currentSolution.routes[i]
    all_routes = findRoutes(origin_i, dest_i, time_i, maxHops)

    for each alternative route r:
        if r == old_route: skip

        // Temporarily "undo" old route to check capacity
        for each flight f in old_route: f.unassign(qty)  // f.assignedLoad -= qty

        // Check if new route is feasible
        feasible = all flights in r have canAssign(qty) = true

        // Restore old route (regardless of feasibility)
        for each flight f in old_route: f.assign(qty)    // f.assignedLoad += qty

        if not feasible: skip

        // Compute delta fitness without touching the solution
        delta = routeFitness(r) - routeFitness(old_route)

        // Check tabu + aspiration
        move = TabuMove(i, hash(r))
        if tabu[move] > iteration:
            if (currentFitness + delta) >= bestFitness: skip  // tabu, not an improvement
            // else: aspiration criterion accepts it

        if delta < bestDelta:
            best_candidate = Candidate(i, r, delta, move)
```

This in-place technique avoids `O(n)` solution copies for each of the `neighborhoodSize × routes_per_shipment` candidates, making the inner loop very fast.

**Consequence:** the code is **not thread-safe** — you cannot parallelize this loop because multiple threads would race on `Flight.assignedLoad`.

---

### 4.4 Applying a Move

Once the best candidate is selected:

```java
// 1. Remove old route's load from flights
for (Flight f : oldRoute.getFlights()) f.unassign(qty);

// 2. Add new route's load to flights
for (Flight f : newRoute.getFlights()) f.assign(qty);

// 3. Update solution in-place
currentSolution.setRoute(index, newRoute);
```

No copy of the solution is needed. The current solution state is updated directly.

---

### 4.5 The Main `solve()` Loop

```
solve(network, requests, config):
│
├─ 1. network.resetLoads()
│
├─ 2. s = generateInitialSolution()
│      best = s.copy(); bestFitness = s.getFitness()
│
└─ 3. for iter = 1 to maxIterations:
         │
         ├── if wallClock > timeLimit → break
         │
         ├── candidate = exploreNeighborhood(s, iter)
         │
         ├── if candidate != null:
         │       applyMove(s, candidate)
         │       tabuList[candidate.move] = iter + tabuTenure
         │       if s.fitness < bestFitness:
         │           best = s.copy()
         │           bestFitness = s.fitness
         │
         ├── if iter % diversificationInterval == 0:
         │       diversify(s)          // perturb 20% of routes randomly
         │
         ├── if iter % intensificationInterval == 0:
         │       s = best.copy()       // return to best known
         │       reassignAllLoads(s)
         │
         ├── purgeTabuList(iter)
         │
         └── (log progress every 100 iterations)

     return best
```

**Default configuration parameters:**

| Parameter | Default | Role |
|---|---|---|
| `maxIterations` | 1000 | Hard stop |
| `tabuTenure` | 15 | Move forbidden for 15 iterations |
| `neighborhoodSize` | 20 | Shipments sampled per iteration |
| `maxHops` | 3 | Max flights per route |
| `diversificationInterval` | 100 | Perturb every 100 iterations |
| `intensificationInterval` | 50 | Return to best every 50 iterations |
| `timeLimitMs` | 90 min | Wall-clock cutoff |
| `seed` | 42 | PRNG seed |

---

### 4.6 Diversification

```java
private void diversify(Solution s) {
    network.resetLoads();
    int count = solutionSize / 5;              // perturb 20% of routes
    List<Integer> indices = randomSample(solutionSize, count);
    for (int i : indices) {
        List<List<Flight>> routes = findRoutes(origin_i, dest_i, time_i, maxHops);
        if (!routes.isEmpty()) {
            List<Flight> random = routes.get(rng.nextInt(routes.size())); // NOT best
            s.setRoute(i, new RouteAssignment(request_i, random));
        }
    }
    reassignAllLoads(s);   // rebuild all flight loads from scratch
}
```

Notice: a **random** route is chosen (not the best one). The goal is to escape, not to exploit. After perturbing, `reassignAllLoads()` recomputes all `Flight.assignedLoad` values from scratch by iterating the solution and calling `f.assign(qty)` for every flight in every route.

---

### 4.7 Intensification

```java
private void intensify(Solution s) {
    s = bestSolution.copy();
    reassignAllLoads(s);
}
```

Returns the current solution to the best-known state. The next iteration's neighborhood exploration then searches around the best region.

---

### 4.8 Dynamic Replanning (`replanify`)

Used in E2 mode when a flight cancellation event arrives mid-operation. This must complete within the 5-second real-time budget.

```
replanify(cancelledFlightId):
│
├─ 1. network.cancelFlight(cancelledFlightId)
│      → marks flight.cancelled = true
│
├─ 2. Find affected shipments:
│      routes where any flight in the route == cancelledFlight
│
├─ 3. For each affected shipment:
│      re-route greedily (findBestGreedyRoute)
│
└─ 4. Run focused TS for min(100, maxIterations) iterations
       (same logic as solve(), just fewer iterations)
       return best
```

The greedy re-route in step 3 gives the TS a warm start — affected shipments already have valid (though possibly suboptimal) routes before the TS loop begins.

---

### 4.9 Semaphore Status

Used in E3 collapse simulation:

```
lateRatio = lateShipments / totalShipments

lateRatio ≤ 0.10  →  GREEN  (≤10% late)
lateRatio ≤ 0.30  →  AMBER  (10–30% late)
lateRatio  > 0.30  →  RED    (>30% late — collapse)
```

---

## 5. Simulation Modes

The logistics network is always loaded from the two data files in the working
directory:
- **`aeropuertos.txt`** — 30 real airports (América del Sur, Europa, Asia), UTF-16 format
- **`planes_vuelo.txt`** — scheduled flights between those airports

### 5.1 E1 — Period Simulation (5 days)

**Purpose:** Evaluate the solver over a multi-day period using real shipment data
from `_envios_XXXX_.txt` files. Runs in 30–90 minutes.

**Flow:**
```
build network from aeropuertos.txt + planes_vuelo.txt
load shipments from folder for [startDay, startDay+numDays)

for day = 1 to numDays:
    filter requests for this day
    config: maxIter=500, tenure=15, neighborhood=20, maxHops=3, timeLimit=60min/numDays

    with 10% probability: simulate a random flight cancellation
        → solver.replanify(cancelledFlightId)

    solution = solver.solve(network, dayRequests, config)
    print daily report + semaphore status
```

**Usage:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E1 ./_envios_preliminar_ 0 5
#       └─ data folder    └─ start day 0, 5 days
# optional: add planes_vuelo.txt path as 5th arg
```

---

### 5.2 E2 — Real-Time Replanning

**Purpose:** Verify that replanning after a disruption (flight cancellation)
completes within 5 seconds — a hard operational constraint.

**Flow:**
```
build network from aeropuertos.txt + planes_vuelo.txt
load shipments from folder (day 0)
config: maxIter=200, tenure=10, neighborhood=15, maxHops=2, timeLimit=5s

initial solution = solver.solve(...)

simulate 3 consecutive cancellation events:
    for each event:
        t_start = now()
        newSolution = solver.replanify(cancelledFlightId)
        elapsed = now() - t_start
        assert elapsed <= 5000ms
        print result
```

**Usage:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E2 ./_envios_preliminar_
# optional: add planes_vuelo.txt path as 3rd arg
```

---

### 5.3 E3 — Collapse Simulation

**Purpose:** Find how many days the system can sustain increasing demand before
quality degrades below RED threshold.

**Demand growth model:** starts from real day-0 shipments; each day adds 20% more
requests (cycling through the original list).

**Flow:**
```
build network from aeropuertos.txt + planes_vuelo.txt
load base shipments from folder (day 0)

day = 0; status = GREEN
while status != RED and day < 30:
    scaledRequests = scaleRequests(baseRequests, day)   // × 1.2^day
    config: maxIter=300, tenure=12, neighborhood=15, maxHops=3, timeLimit=2min

    solution = solver.solve(...)
    status = getSemaphoreStatus(solution)
    print: day, requestCount, lateRatio, status

    day++

print: "System collapsed on day X" or "Survived 30 days"
```

**Usage:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E3 ./_envios_preliminar_
# optional: add planes_vuelo.txt path as 3rd arg
```

---

### 5.4 EXP — Batch Experimentation

**Purpose:** Run `N` independent replicas with different seeds to gather
statistical data on solver performance. Output is a CSV for analysis.

**Flow:**
```
// Load data ONCE, build network ONCE
build network from aeropuertos.txt + planes_vuelo.txt
load shipments (day 0)

open output.csv for writing
write CSV header

for seed = 1 to replicas:
    network.resetLoads()           // critical: reset between runs
    config: maxIter=1000, tenure=7, neighborhood=50, maxHops=3,
            timeLimit=90s, seed=seed

    solution = solver.solve(network, requests, config)
    csvRow = solver.getCsvRow(solution, seed, elapsedMs)
    write csvRow to file (flushed immediately)
    print progress
```

**Why flush after each row?** A 30-replica run takes ~45 minutes. Immediate flush
means partial results survive if the process is interrupted.

**CSV columns (15 fields):**

| Column | Description |
|---|---|
| `seed` | Replica number (1..N) |
| `totalRequests` | Number of shipment requests |
| `totalSuitcases` | Total bags across all requests |
| `deliveredOnTime` | Bags delivered on time |
| `deliveredLate` | Bags delivered late |
| `undelivered` | Bags with no route |
| `lateCount` | Number of late shipments |
| `totalDelay` | Cumulative delay in day-bags |
| `capacityOverflow` | Bags on over-capacity flights |
| `fitness` | Final fitness value |
| `iterations` | TS iterations performed |
| `improvementCount` | Times a new best was found |
| `elapsedMs` | Wall-clock time in ms |
| `semaphore` | GREEN / AMBER / RED |
| `initialFitness` | Greedy solution fitness (for comparison) |

**Usage:**
```bash
java -cp target/classes pe.edu.pucp.tasf.Main \
    EXP ./_envios_preliminar_ 30 E1_30_runs_TS.csv
#                              └─ 30 replicas  └─ output file
# optional: add planes_vuelo.txt path as 5th arg
```

---

## 6. Input / Output Formats

### 6.1 Shipment Files (`_envios_XXXX_.txt`)

One file per origin airport. The `XXXX` in the filename is the ICAO code of the origin airport (e.g., `_envios_SPIM_.txt` = shipments from Lima).

**Line format:**
```
ID-YYYYMMDD-HH-MM-DEST_ICAO-QTY-CLIENT_ID
```

**Example:**
```
000000001-20260102-00-47-SUAA-002-0032535
│         │        │  │  │    │   └─ client ID
│         │        │  │  │    └─ quantity: 2 bags
│         │        │  │  └─ destination ICAO: SUAA (Asuncion)
│         │        │  └─ minute: 47
│         │        └─ hour: 00
│         └─ date: 2026-01-02
└─ shipment ID
```

**`creationTime` calculation:**
```
baseDate = minimum date across all shipment files
daysOffset = (date - baseDate).days
creationTime = daysOffset + (hour + minute/60.0) / 24.0
```

So `000000001-20260102-00-47-SUAA-002-0032535` at midnight+47min becomes `creationTime ≈ 0.0326` (47 minutes into day 0 if 20260102 is the base date).

---

### 6.2 Flight Schedule (`planes_vuelo.txt`)

Required file for all modes. Must be present in the working directory or passed
as an explicit path argument.

**Line format:**
```
ORIGIN-DEST-HH:MM-HH:MM-CAPACITY
```

**Example:**
```
SPIM-LEMD-08:00-22:00-150
│    │    │     │     └─ capacity: 150 bags
│    │    │     └─ arrival: 22:00 local time
│    │    └─ departure: 08:00 local time
│    └─ destination: LEMD (Madrid)
└─ origin: SPIM (Lima)
```

Day-wrap is handled: if `arrivalFraction ≤ departureFraction`, the arrival is assumed to be the next day (`arrFrac += 1.0`).

---

### 6.3 Airport File (`aeropuertos.txt`)

UTF-16 fixed-width file with 30 real airports across América del Sur, Europa and Asia.
Parsed by `AirportsLoader`. Must be present in the working directory.

**Line format:**
```
NN   ICAO   CITY                COUNTRY         ABBR  GMT   CAP   ...lat/lon...
```

**Example:**
```
05   SPIM   Lima                Perú            lima    -5     440     Latitude: ...
│    │      └─ city name        └─ country      └─ abbr └─ GMT offset └─ warehouse capacity
│    └─ ICAO code
└─ sequential number
```

The `AirportsLoader` automatically detects UTF-16 BE/LE or UTF-8 via BOM.
Continent is derived from the ICAO prefix via `Continent.fromIcao()`.

---

## 7. Build & Run

### Prerequisites
- Java 17+
- Maven 3.6+
- `aeropuertos.txt` in the working directory (or the project root)
- `planes_vuelo.txt` in the working directory (or passed as last arg)

### Build
```bash
mvn clean compile
```

### Run (All Modes)

**Important:** Always pass `-Dfile.encoding=UTF-8` when running directly — the program uses UTF-8 box-drawing characters and accented text in its console output.

**E1 — Period simulation (5 days, real data):**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E1 ./_envios_preliminar_ 0 5
# optional: add planes_vuelo.txt path as 5th arg
```

**E2 — Real-time replanning:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E2 ./_envios_preliminar_
# optional: add planes_vuelo.txt path as 3rd arg
```

**E3 — Collapse simulation:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main \
    E3 ./_envios_preliminar_
# optional: add planes_vuelo.txt path as 3rd arg
```

**EXP — Batch experimentation (30 replicas):**
```bash
java -cp target/classes pe.edu.pucp.tasf.Main \
    EXP ./_envios_preliminar_ 30 E1_30_runs_TS.csv
# optional: add planes_vuelo.txt path as 5th arg
```

**Integration test:**
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest
# or with real data:
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest _envios_preliminar_/
```
Exits with code `0` on success, `1` on failure.

---

## 8. Known Limitations & Design Notes

### 8.1 Virtual Next-Day Flights (Fixed)

`LogisticsNetwork.getActiveFlightsFrom()` creates ephemeral `Flight` objects with IDs
like `F5_d1` for multi-day routing. Previously these objects were re-created fresh on
every call, so their `assignedLoad` was always 0 — capacity violations on multi-day
legs went undetected.

**This is now fixed.** Virtual flights are cached in a `LinkedHashMap<String, Flight>`
inside `LogisticsNetwork`. The same object is returned on every subsequent call for the
same `(flight, dayOffset)` pair, so `assignedLoad` accumulates correctly. `resetLoads()`
also clears the virtual-flight cache, keeping the lifecycle consistent with real flights.

```
Original flight F5: SPIM→LEMD, departs 0.25 (06:00)

Shipment arrives at SPIM at time 0.8 (19:12) — too late for today's F5.
→ Virtual flight F5_d1: SPIM→LEMD, departs 1.25 (06:00 next day)
   • same object returned on repeated calls  ← NEW
   • assignedLoad tracked correctly          ← NEW
   • cleared by resetLoads()                 ← NEW
```

### 8.2 Warehouse Overflow Not Enforced

`Airport.warehouseCapacity` is modelled and `Solution.getWarehouseOverflow()` exists, but it always returns `0`. The constraint is never enforced in the solver or objective function. The field is infrastructure for a future enhancement.

### 8.3 Tabu Move Hash Collisions

The route hash `31 * a + b` over flight ID hashcodes is compact but can collide — two different routes might produce the same hash, causing a valid move to be incorrectly treated as tabu. Given the typical `neighborhoodSize=20`, this is unlikely to cause significant issues in practice.

### 8.4 `FlightsLoader` vs `RealFlightLoader` Discrepancy

Two classes load the same `planes_vuelo.txt` format:
- `RealFlightLoader` — **actually used** by `Main` and `RealNetworkBuilder`; treats all times as-is (no timezone correction)
- `FlightsLoader` — **not used** in any active code path; applies GMT offset correction to convert local times to UTC

This suggests `FlightsLoader` was an earlier (or planned) implementation. If accurate UTC scheduling matters, `FlightsLoader` + `AirportsLoader` (which provides GMT offsets) is the more correct approach.

### 8.5 Penalties Are Hardcoded in Two Places

The penalty weights (`5000`, `1000`, `100`) are defined in `TabuSearchConfig` as configurable fields, but the actual computation in `Solution.getFitness()` and the solver's local `routeFitness()` uses hardcoded literals. Changing the config values does not change the objective function.

### 8.6 SWAP and SPLIT Moves Not Implemented

Comments in `TabuSearchSolver` note two additional move types:
- **SWAP**: exchange routes between two compatible shipments (same origin/destination pair) — could reduce capacity conflicts
- **SPLIT**: divide a large shipment across multiple routes when no single path has sufficient capacity

Neither is implemented. The solver relies entirely on REROUTE moves.

### 8.7 No JUnit

The test infrastructure (`RunnerTest.java`) lives under `src/main/java/` alongside production code. It is compiled with the main source set and run directly as a main class. This is non-standard but works correctly for a self-contained integration smoke test.
