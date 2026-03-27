# Plotter GUI para archivos `output/*.txt`

Este script parsea el formato generado por `tp2/src/Simulation.java` y permite elegir por GUI que archivo graficar.

## Que entiende del archivo

El parser asume exactamente esta semantica:

1. Primera linea: `N` (cantidad de particulas).
2. Segunda linea: `L eta scenario`.
3. Luego, por cada frame:
   - una linea con `t` (tiempo entero),
   - `N` lineas con `x y vx vy leaderFlag`.

## Instalacion

```bash
python3 -m pip install -r requirements-plotter.txt
```

## Ejecucion

```bash
python3 plot_output_gui.py
```

Opcionalmente, para apuntar a otra carpeta:

```bash
python3 plot_output_gui.py --output-dir ./output
```

## Graficos disponibles

- `Orden global v_a(t)`: nivel de alineacion en el tiempo.
- `Trayectorias (muestra)`: caminos de una muestra de particulas.
- `Snapshot con velocidades`: quiver del frame seleccionado.

## Parametros configurables desde la GUI

Se agregaron entradas para modificar la mayor parte de parametros sin tocar codigo,
manteniendo como default el comportamiento original.

- `General`: titulo opcional, frame inicial/final y paso entre frames ("numero de pasos").
- `Orden`: ancho/alto de figura, ancho de linea, rango Y y alpha de grilla.
- `Trayectorias`: ancho/alto de figura, maximo de particulas, alpha y anchos de linea.
- `Snapshot`: indice de frame, escala/ancho de quiver, tamano de puntos y alphas.

