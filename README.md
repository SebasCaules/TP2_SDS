# TP2_SDS - Off-Lattice (TP2)

Repositorio para simular y analizar un modelo off-lattice de partículas auto-propulsadas (estilo Vicsek) con tres escenarios (`A`, `B`, `C`), generar archivos de salida y visualizarlos con:

- una **animación 2D en Java (Swing)**.

## Checklist de uso rapido

- [ ] Ejecutar la simulación Java para generar `output/*.txt`.
- [ ] Abrir un archivo en la animación Java (`AnimationLauncher`).
- [ ] (Opcional) Modificar parámetros de simulación y repetir.

## Estructura del repositorio

```text
TP2_SDS/
├── output/                       # Archivos de salida de simulación
├── tp2/
│   └── src/
│       ├── Simulation.java       # Simulador principal (genera output)
│       └── Animation/
│           ├── AnimationLauncher.java
│           ├── AnimationPanel.java
│           ├── OutputParser.java
│           ├── SimulationData.java
│           ├── Frame.java
│           └── ParticleState.java
└── README.md                     # Documentacion del repositorio
```

## Flujo completo del proyecto

1. `Simulation.java` corre combinaciones de escenario + ruido `eta`.
2. Se escribe un `.txt` por corrida en `output/` (`off_lattice_<escenario>_eta<valor>.txt`).
3. Ese archivo se puede animar en Java (`AnimationLauncher`).

## 1) Simulacion (Java)

### Que implementa

`tp2/src/Simulation.java` modela `N` partículas en un dominio cuadrado `L x L` con condiciones periódicas.

- Partículas con velocidad constante `V`.
- Interacción local con radio `RC`.
- Ruido angular controlado por `eta`.
- Integración por pasos discretos `DT` durante `STEPS`.
- Vecinos acelerados con **Cell Index Method (CIM)**.

### Escenarios

- `A`: sin líder.
- `B`: líder con dirección fija (`leaderFixedTheta`).
- `C`: líder en trayectoria circular de radio `R_CIRCLE`.

### Parámetros principales (en código)

En `Simulation.java`:

- `L = 10.0`
- `DENSITY = 4.0`
- `N = DENSITY * L * L` (actualmente 400)
- `V = 0.03`
- `RC = 1.0`
- `DT = 1.0`
- `STEPS = 2000`
- Barrido de `eta = {0.0, 0.5, ..., 5.0}`
- Escenarios `A`, `B`, `C`

### Compilar y ejecutar simulación

```bash
javac tp2/src/Simulation.java
java -cp tp2/src Simulation
```

Salida esperada: múltiples archivos en `output/` y logs por consola con `va` final por corrida.

## 2) Formato de archivos en `output/`

Cada archivo sigue este contrato:

1. Primera línea: `N`
2. Segunda línea: `L eta scenario`
3. Para cada frame:
   - línea con `t` (entero),
   - `N` líneas de partículas: `x y vx vy leaderFlag`

Ejemplo (esquema):

```text
400
10.0000 0.0000 A
0
x y vx vy 0
...
1
x y vx vy 0
...
```

`leaderFlag = 1` indica líder; `0` partícula normal.

## 3) Animación 2D (Java Swing)

La animación está en `tp2/src/Animation/`.

### Componentes

- `AnimationLauncher.java`: punto de entrada, selector de archivo (`JFileChooser`) y UI.
- `OutputParser.java`: parsea `output/*.txt` a estructuras en memoria.
- `AnimationPanel.java`: render de partículas y vectores de velocidad.
- `SimulationData.java`, `Frame.java`, `ParticleState.java`: modelos de datos.

### Compilar animación

```bash
javac tp2/src/Animation/*.java
```

### Ejecutar animación

Archivo específico:

```bash
java -cp tp2/src Animation.AnimationLauncher output/off_lattice_A_eta0.00.txt
```

Selector de archivo automático:

```bash
java -cp tp2/src Animation.AnimationLauncher
```

### Qué muestra

- Caja de simulación `L x L`.
- Partículas normales en azul.
- Líder en rojo.
- Vectores de velocidad para cada partícula.
- Slider y reproducción temporal automática.

## 4) Resultado físico a observar

Métrica clave: `v_a(t)` (orden global, alineación colectiva).

- `v_a ~ 1`: sistema muy alineado.
- `v_a ~ 0`: sistema desordenado.

Al aumentar `eta`, típicamente baja la alineación promedio.

## 5) Reproducibilidad y cambios rápidos

- La simulación usa `seed = 42` en `Simulation.main`.
- Para cambiar cantidad de corridas o valores de `eta`, editar `Simulation.java`.
- Para cambiar comportamiento del líder, editar la lógica de escenarios en `step()`.

## 6) Troubleshooting

- Si no aparece `output/`, primero ejecutar `Simulation`.
- Si `AnimationLauncher` no encuentra carpeta, ejecutar desde la raíz del repo o pasar ruta explícita.
- Si un `.txt` falla al parsear, revisar que respete el formato descrito arriba.

## 7) Comandos de referencia (copiar/pegar)

```bash
# 1) Simulación
javac tp2/src/Simulation.java
java -cp tp2/src Simulation

# 2) Animación Java
javac tp2/src/Animation/*.java
java -cp tp2/src Animation.AnimationLauncher
```

