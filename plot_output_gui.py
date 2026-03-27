#!/usr/bin/env python3
"""GUI para explorar y graficar archivos off_lattice_*.txt generados por Simulation.java."""

from __future__ import annotations

import argparse
import math
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

import matplotlib.pyplot as plt
import numpy as np
import tkinter as tk
from tkinter import messagebox, ttk


@dataclass
class SimulationData:
    path: Path
    n_particles: int
    box_length: float
    eta: float
    scenario: str
    timesteps: np.ndarray
    x: np.ndarray
    y: np.ndarray
    vx: np.ndarray
    vy: np.ndarray
    leader_index: Optional[int]


class ParseError(RuntimeError):
    pass


@dataclass
class CommonPlotSettings:
    start_index: int = 0
    end_index: int = -1
    frame_step: int = 1
    title: str = ""


@dataclass
class OrderPlotSettings(CommonPlotSettings):
    fig_width: float = 8.0
    fig_height: float = 4.5
    line_width: float = 1.4
    y_min: float = 0.0
    y_max: float = 1.05
    grid_alpha: float = 0.25


@dataclass
class TrajectoryPlotSettings(CommonPlotSettings):
    fig_width: float = 6.0
    fig_height: float = 6.0
    max_particles: int = 60
    line_alpha: float = 0.45
    line_width: float = 0.8
    leader_line_width: float = 2.0
    grid_alpha: float = 0.2


@dataclass
class SnapshotPlotSettings(CommonPlotSettings):
    fig_width: float = 6.0
    fig_height: float = 6.0
    quiver_scale: float = 1.0
    quiver_width: float = 0.003
    leader_quiver_width: float = 0.005
    point_size: float = 10.0
    leader_point_size: float = 36.0
    vector_alpha: float = 0.6
    point_alpha: float = 0.7
    grid_alpha: float = 0.2


def resolve_frame_indices(
    total_frames: int,
    start_index: int,
    end_index: int,
    frame_step: int,
) -> np.ndarray:
    if total_frames <= 0:
        raise ValueError("No hay frames para graficar")
    if frame_step <= 0:
        raise ValueError("El paso de frames debe ser >= 1")

    start = max(0, start_index)
    end = (total_frames - 1) if end_index < 0 else min(end_index, total_frames - 1)
    if start > end:
        raise ValueError(
            f"Rango de frames invalido: start={start}, end={end}, total={total_frames}"
        )

    return np.arange(start, end + 1, frame_step)


def discover_output_files(output_dir: Path) -> List[Path]:
    return sorted(output_dir.glob("*.txt"))


def parse_output_file(path: Path) -> SimulationData:
    """Parsea el formato escrito por Simulation.writeState()."""
    with path.open("r", encoding="utf-8") as f:
        first_line = f.readline().strip()
        if not first_line:
            raise ParseError(f"Archivo vacio: {path}")

        try:
            n_particles = int(first_line)
        except ValueError as exc:
            raise ParseError(f"Primera linea invalida en {path}: {first_line!r}") from exc

        meta = f.readline().split()
        if len(meta) < 3:
            raise ParseError(f"Linea de metadata invalida en {path}: {meta}")

        box_length = float(meta[0])
        eta = float(meta[1])
        scenario = meta[2]

        timesteps: List[int] = []
        x_frames: List[np.ndarray] = []
        y_frames: List[np.ndarray] = []
        vx_frames: List[np.ndarray] = []
        vy_frames: List[np.ndarray] = []
        leader_index: Optional[int] = None

        while True:
            line = f.readline()
            if not line:
                break

            line = line.strip()
            if not line:
                continue

            try:
                t = int(line)
            except ValueError as exc:
                raise ParseError(f"Timestep invalido en {path}: {line!r}") from exc

            frame_x = np.empty(n_particles, dtype=float)
            frame_y = np.empty(n_particles, dtype=float)
            frame_vx = np.empty(n_particles, dtype=float)
            frame_vy = np.empty(n_particles, dtype=float)

            for i in range(n_particles):
                particle_line = f.readline()
                if not particle_line:
                    raise ParseError(
                        f"Frame incompleto en {path}: faltan particulas para t={t}"
                    )

                parts = particle_line.split()
                if len(parts) < 5:
                    raise ParseError(
                        f"Linea de particula invalida en {path} (t={t}): {particle_line!r}"
                    )

                frame_x[i] = float(parts[0])
                frame_y[i] = float(parts[1])
                frame_vx[i] = float(parts[2])
                frame_vy[i] = float(parts[3])

                if leader_index is None and int(parts[4]) == 1:
                    leader_index = i

            timesteps.append(t)
            x_frames.append(frame_x)
            y_frames.append(frame_y)
            vx_frames.append(frame_vx)
            vy_frames.append(frame_vy)

    if not timesteps:
        raise ParseError(f"No se encontraron frames en {path}")

    return SimulationData(
        path=path,
        n_particles=n_particles,
        box_length=box_length,
        eta=eta,
        scenario=scenario,
        timesteps=np.array(timesteps, dtype=int),
        x=np.stack(x_frames),
        y=np.stack(y_frames),
        vx=np.stack(vx_frames),
        vy=np.stack(vy_frames),
        leader_index=leader_index,
    )


def compute_va_over_time(data: SimulationData) -> np.ndarray:
    speed = np.mean(np.sqrt(data.vx[0] ** 2 + data.vy[0] ** 2))
    if speed <= 1e-12:
        return np.zeros_like(data.timesteps, dtype=float)
    sx = np.sum(data.vx, axis=1)
    sy = np.sum(data.vy, axis=1)
    return np.sqrt(sx**2 + sy**2) / (data.n_particles * speed)


def plot_order_parameter(data: SimulationData, settings: OrderPlotSettings) -> None:
    frame_indices = resolve_frame_indices(
        len(data.timesteps),
        settings.start_index,
        settings.end_index,
        settings.frame_step,
    )
    va = compute_va_over_time(data)
    plt.figure(figsize=(settings.fig_width, settings.fig_height))
    plt.plot(data.timesteps[frame_indices], va[frame_indices], linewidth=settings.line_width)
    if settings.y_max > settings.y_min:
        plt.ylim(settings.y_min, settings.y_max)
    plt.xlabel("Tiempo")
    plt.ylabel("v_a")
    default_title = (
        f"Orden global - {data.path.name} (escenario={data.scenario}, eta={data.eta:.2f})"
    )
    plt.title(settings.title or default_title)
    plt.grid(alpha=settings.grid_alpha)
    plt.tight_layout()
    plt.show()


def _sample_particle_indices(data: SimulationData, max_particles: int = 60) -> np.ndarray:
    if data.n_particles <= max_particles:
        return np.arange(data.n_particles)
    step = math.ceil(data.n_particles / max_particles)
    return np.arange(0, data.n_particles, step)


def plot_trajectories(data: SimulationData, settings: TrajectoryPlotSettings) -> None:
    frame_indices = resolve_frame_indices(
        len(data.timesteps),
        settings.start_index,
        settings.end_index,
        settings.frame_step,
    )
    indices = _sample_particle_indices(data, max_particles=settings.max_particles)
    plt.figure(figsize=(settings.fig_width, settings.fig_height))
    for idx in indices:
        if data.leader_index is not None and idx == data.leader_index:
            continue
        plt.plot(
            data.x[frame_indices, idx],
            data.y[frame_indices, idx],
            alpha=settings.line_alpha,
            linewidth=settings.line_width,
            color="tab:blue",
        )

    if data.leader_index is not None:
        li = data.leader_index
        plt.plot(
            data.x[frame_indices, li],
            data.y[frame_indices, li],
            linewidth=settings.leader_line_width,
            color="tab:red",
            label="Lider",
        )
        plt.legend(loc="upper right")

    plt.xlim(0, data.box_length)
    plt.ylim(0, data.box_length)
    plt.gca().set_aspect("equal", adjustable="box")
    plt.xlabel("x")
    plt.ylabel("y")
    default_title = f"Trayectorias (muestra={len(indices)}) - {data.path.name}"
    plt.title(settings.title or default_title)
    plt.grid(alpha=settings.grid_alpha)
    plt.tight_layout()
    plt.show()


def plot_snapshot(
    data: SimulationData,
    timestep_index: int,
    settings: SnapshotPlotSettings,
) -> None:
    timestep_index = max(0, min(timestep_index, len(data.timesteps) - 1))
    x_t = data.x[timestep_index]
    y_t = data.y[timestep_index]
    vx_t = data.vx[timestep_index]
    vy_t = data.vy[timestep_index]

    leader_mask = np.zeros(data.n_particles, dtype=bool)
    if data.leader_index is not None:
        leader_mask[data.leader_index] = True

    plt.figure(figsize=(settings.fig_width, settings.fig_height))
    plt.quiver(
        x_t[~leader_mask],
        y_t[~leader_mask],
        vx_t[~leader_mask],
        vy_t[~leader_mask],
        angles="xy",
        scale_units="xy",
        scale=settings.quiver_scale,
        alpha=settings.vector_alpha,
        color="tab:blue",
        width=settings.quiver_width,
    )
    plt.scatter(
        x_t[~leader_mask],
        y_t[~leader_mask],
        s=settings.point_size,
        color="tab:blue",
        alpha=settings.point_alpha,
    )

    if data.leader_index is not None:
        li = data.leader_index
        plt.quiver(
            [x_t[li]],
            [y_t[li]],
            [vx_t[li]],
            [vy_t[li]],
            angles="xy",
            scale_units="xy",
            scale=settings.quiver_scale,
            color="tab:red",
            width=settings.leader_quiver_width,
        )
        plt.scatter(
            [x_t[li]],
            [y_t[li]],
            s=settings.leader_point_size,
            color="tab:red",
            label="Lider",
        )
        plt.legend(loc="upper right")

    plt.xlim(0, data.box_length)
    plt.ylim(0, data.box_length)
    plt.gca().set_aspect("equal", adjustable="box")
    plt.xlabel("x")
    plt.ylabel("y")
    default_title = f"Snapshot t={data.timesteps[timestep_index]} - {data.path.name}"
    plt.title(settings.title or default_title)
    plt.grid(alpha=settings.grid_alpha)
    plt.tight_layout()
    plt.show()


class PlotterApp:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir
        self.cache: dict[Path, SimulationData] = {}

        self.root = tk.Tk()
        self.root.title("Off-Lattice Plotter")
        self.root.geometry("980x680")

        self.files_var = tk.StringVar(value=[])
        self.file_listbox = tk.Listbox(
            self.root,
            listvariable=self.files_var,
            height=20,
            exportselection=False,
            font=("Menlo", 11),
        )
        self.file_listbox.grid(
            row=0,
            column=0,
            rowspan=20,
            sticky="nsew",
            padx=(10, 6),
            pady=10,
        )

        self.root.grid_columnconfigure(0, weight=3)
        self.root.grid_columnconfigure(1, weight=2)
        self.root.grid_rowconfigure(18, weight=1)

        self._init_parameter_vars()

        ttk.Button(self.root, text="Recargar archivos", command=self.reload_files).grid(
            row=0, column=1, sticky="ew", padx=(6, 10), pady=(10, 6)
        )

        ttk.Label(self.root, text="Tipo de grafico:").grid(
            row=1, column=1, sticky="w", padx=(6, 10), pady=(10, 4)
        )
        self.plot_mode = tk.StringVar(value="order")
        for row, (label, value) in enumerate(
            [
                ("Orden global v_a(t)", "order"),
                ("Trayectorias (muestra)", "traj"),
                ("Snapshot con velocidades", "snapshot"),
            ],
            start=2,
        ):
            ttk.Radiobutton(self.root, text=label, variable=self.plot_mode, value=value).grid(
                row=row, column=1, sticky="w", padx=(6, 10), pady=2
            )

        self.params_notebook = ttk.Notebook(self.root)
        self.params_notebook.grid(
            row=5,
            column=1,
            rowspan=12,
            sticky="nsew",
            padx=(6, 10),
            pady=(10, 6),
        )
        self._build_parameter_tabs()

        ttk.Button(self.root, text="Graficar seleccionado", command=self.plot_selected).grid(
            row=17, column=1, sticky="ew", padx=(6, 10), pady=(8, 8)
        )

        self.status_label = ttk.Label(self.root, text="")
        self.status_label.grid(
            row=19,
            column=0,
            columnspan=2,
            sticky="ew",
            padx=10,
            pady=(0, 8),
        )

        self.file_listbox.bind("<<ListboxSelect>>", self.on_file_selected)
        self.reload_files()

    def _init_parameter_vars(self) -> None:
        # Defaults equivalen al comportamiento previo.
        self.custom_title_var = tk.StringVar(value="")
        self.start_idx_var = tk.StringVar(value="0")
        self.end_idx_var = tk.StringVar(value="-1")
        self.frame_step_var = tk.StringVar(value="1")
        self.frame_index_var = tk.StringVar(value="0")

        self.order_fig_w_var = tk.StringVar(value="8")
        self.order_fig_h_var = tk.StringVar(value="4.5")
        self.order_line_width_var = tk.StringVar(value="1.4")
        self.order_y_min_var = tk.StringVar(value="0")
        self.order_y_max_var = tk.StringVar(value="1.05")
        self.order_grid_alpha_var = tk.StringVar(value="0.25")

        self.traj_fig_w_var = tk.StringVar(value="6")
        self.traj_fig_h_var = tk.StringVar(value="6")
        self.traj_max_particles_var = tk.StringVar(value="60")
        self.traj_line_alpha_var = tk.StringVar(value="0.45")
        self.traj_line_width_var = tk.StringVar(value="0.8")
        self.traj_leader_line_width_var = tk.StringVar(value="2.0")
        self.traj_grid_alpha_var = tk.StringVar(value="0.2")

        self.snap_fig_w_var = tk.StringVar(value="6")
        self.snap_fig_h_var = tk.StringVar(value="6")
        self.snap_quiver_scale_var = tk.StringVar(value="1")
        self.snap_quiver_width_var = tk.StringVar(value="0.003")
        self.snap_leader_quiver_width_var = tk.StringVar(value="0.005")
        self.snap_point_size_var = tk.StringVar(value="10")
        self.snap_leader_point_size_var = tk.StringVar(value="36")
        self.snap_vector_alpha_var = tk.StringVar(value="0.6")
        self.snap_point_alpha_var = tk.StringVar(value="0.7")
        self.snap_grid_alpha_var = tk.StringVar(value="0.2")

    def _add_entry(
        self,
        parent: ttk.Frame,
        row: int,
        label: str,
        var: tk.StringVar,
    ) -> None:
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky="w", padx=6, pady=3)
        ttk.Entry(parent, textvariable=var, width=14).grid(
            row=row,
            column=1,
            sticky="ew",
            padx=6,
            pady=3,
        )

    def _build_parameter_tabs(self) -> None:
        general_tab = ttk.Frame(self.params_notebook)
        order_tab = ttk.Frame(self.params_notebook)
        traj_tab = ttk.Frame(self.params_notebook)
        snapshot_tab = ttk.Frame(self.params_notebook)

        self.params_notebook.add(general_tab, text="General")
        self.params_notebook.add(order_tab, text="Orden")
        self.params_notebook.add(traj_tab, text="Trayectorias")
        self.params_notebook.add(snapshot_tab, text="Snapshot")

        for tab in (general_tab, order_tab, traj_tab, snapshot_tab):
            tab.grid_columnconfigure(1, weight=1)

        self._add_entry(general_tab, 0, "Titulo (opcional)", self.custom_title_var)
        self._add_entry(general_tab, 1, "Frame inicial", self.start_idx_var)
        self._add_entry(general_tab, 2, "Frame final (-1 = ultimo)", self.end_idx_var)
        self._add_entry(general_tab, 3, "Paso entre frames", self.frame_step_var)
        self._add_entry(general_tab, 4, "Indice snapshot", self.frame_index_var)

        self._add_entry(order_tab, 0, "Figura ancho", self.order_fig_w_var)
        self._add_entry(order_tab, 1, "Figura alto", self.order_fig_h_var)
        self._add_entry(order_tab, 2, "Ancho de linea", self.order_line_width_var)
        self._add_entry(order_tab, 3, "Y min", self.order_y_min_var)
        self._add_entry(order_tab, 4, "Y max", self.order_y_max_var)
        self._add_entry(order_tab, 5, "Grid alpha", self.order_grid_alpha_var)

        self._add_entry(traj_tab, 0, "Figura ancho", self.traj_fig_w_var)
        self._add_entry(traj_tab, 1, "Figura alto", self.traj_fig_h_var)
        self._add_entry(traj_tab, 2, "Max particulas", self.traj_max_particles_var)
        self._add_entry(traj_tab, 3, "Alpha lineas", self.traj_line_alpha_var)
        self._add_entry(traj_tab, 4, "Ancho lineas", self.traj_line_width_var)
        self._add_entry(traj_tab, 5, "Ancho lider", self.traj_leader_line_width_var)
        self._add_entry(traj_tab, 6, "Grid alpha", self.traj_grid_alpha_var)

        self._add_entry(snapshot_tab, 0, "Figura ancho", self.snap_fig_w_var)
        self._add_entry(snapshot_tab, 1, "Figura alto", self.snap_fig_h_var)
        self._add_entry(snapshot_tab, 2, "Escala quiver", self.snap_quiver_scale_var)
        self._add_entry(snapshot_tab, 3, "Ancho quiver", self.snap_quiver_width_var)
        self._add_entry(
            snapshot_tab,
            4,
            "Ancho quiver lider",
            self.snap_leader_quiver_width_var,
        )
        self._add_entry(snapshot_tab, 5, "Tam punto", self.snap_point_size_var)
        self._add_entry(snapshot_tab, 6, "Tam punto lider", self.snap_leader_point_size_var)
        self._add_entry(snapshot_tab, 7, "Alpha vectores", self.snap_vector_alpha_var)
        self._add_entry(snapshot_tab, 8, "Alpha puntos", self.snap_point_alpha_var)
        self._add_entry(snapshot_tab, 9, "Grid alpha", self.snap_grid_alpha_var)

    def _parse_int(self, raw: str, field_name: str, min_value: Optional[int] = None) -> int:
        try:
            value = int(raw)
        except ValueError as exc:
            raise ValueError(f"{field_name}: debe ser un entero") from exc
        if min_value is not None and value < min_value:
            raise ValueError(f"{field_name}: debe ser >= {min_value}")
        return value

    def _parse_float(self, raw: str, field_name: str, min_value: Optional[float] = None) -> float:
        try:
            value = float(raw)
        except ValueError as exc:
            raise ValueError(f"{field_name}: debe ser un numero") from exc
        if min_value is not None and value < min_value:
            raise ValueError(f"{field_name}: debe ser >= {min_value}")
        return value

    def _common_settings_from_gui(self) -> CommonPlotSettings:
        return CommonPlotSettings(
            start_index=self._parse_int(self.start_idx_var.get(), "Frame inicial"),
            end_index=self._parse_int(self.end_idx_var.get(), "Frame final"),
            frame_step=self._parse_int(
                self.frame_step_var.get(),
                "Paso entre frames",
                min_value=1,
            ),
            title=self.custom_title_var.get().strip(),
        )

    def reload_files(self) -> None:
        files = discover_output_files(self.output_dir)
        names = [p.name for p in files]
        self.files = files
        self.files_var.set(names)
        self.cache.clear()
        if names:
            self.file_listbox.selection_clear(0, tk.END)
            self.file_listbox.selection_set(0)
            self.file_listbox.activate(0)
            self.on_file_selected()
        self.status_label.config(text=f"{len(names)} archivo(s) encontrados en {self.output_dir}")

    def _selected_path(self) -> Optional[Path]:
        selection = self.file_listbox.curselection()
        if not selection:
            return None
        return self.files[selection[0]]

    def _load_data(self, path: Path) -> SimulationData:
        if path not in self.cache:
            self.cache[path] = parse_output_file(path)
        return self.cache[path]

    def on_file_selected(self, _event=None) -> None:
        path = self._selected_path()
        if path is None:
            return
        try:
            data = self._load_data(path)
        except Exception as exc:  # pragma: no cover - GUI error path
            messagebox.showerror("Error de parseo", str(exc))
            return

        max_idx = len(data.timesteps) - 1
        try:
            current_snapshot = self._parse_int(
                self.frame_index_var.get() or "0",
                "Indice snapshot",
            )
        except ValueError:
            current_snapshot = 0
            self.frame_index_var.set("0")
        if current_snapshot > max_idx:
            self.frame_index_var.set(str(max_idx))
        self.status_label.config(
            text=(
                f"{path.name} | N={data.n_particles}, frames={len(data.timesteps)}, "
                f"eta={data.eta:.2f}, escenario={data.scenario}"
            )
        )

    def plot_selected(self) -> None:
        path = self._selected_path()
        if path is None:
            messagebox.showwarning("Sin seleccion", "Primero selecciona un archivo")
            return

        try:
            data = self._load_data(path)
            common = self._common_settings_from_gui()
            mode = self.plot_mode.get()
            if mode == "order":
                settings = OrderPlotSettings(
                    start_index=common.start_index,
                    end_index=common.end_index,
                    frame_step=common.frame_step,
                    title=common.title,
                    fig_width=self._parse_float(
                        self.order_fig_w_var.get(),
                        "Orden figura ancho",
                        min_value=0.1,
                    ),
                    fig_height=self._parse_float(
                        self.order_fig_h_var.get(),
                        "Orden figura alto",
                        min_value=0.1,
                    ),
                    line_width=self._parse_float(
                        self.order_line_width_var.get(),
                        "Orden ancho de linea",
                        min_value=0.0,
                    ),
                    y_min=self._parse_float(self.order_y_min_var.get(), "Orden Y min"),
                    y_max=self._parse_float(self.order_y_max_var.get(), "Orden Y max"),
                    grid_alpha=self._parse_float(
                        self.order_grid_alpha_var.get(),
                        "Orden grid alpha",
                        min_value=0.0,
                    ),
                )
                plot_order_parameter(data, settings)
            elif mode == "traj":
                settings = TrajectoryPlotSettings(
                    start_index=common.start_index,
                    end_index=common.end_index,
                    frame_step=common.frame_step,
                    title=common.title,
                    fig_width=self._parse_float(
                        self.traj_fig_w_var.get(),
                        "Trayectorias figura ancho",
                        min_value=0.1,
                    ),
                    fig_height=self._parse_float(
                        self.traj_fig_h_var.get(),
                        "Trayectorias figura alto",
                        min_value=0.1,
                    ),
                    max_particles=self._parse_int(
                        self.traj_max_particles_var.get(),
                        "Trayectorias max particulas",
                        min_value=1,
                    ),
                    line_alpha=self._parse_float(
                        self.traj_line_alpha_var.get(),
                        "Trayectorias alpha lineas",
                        min_value=0.0,
                    ),
                    line_width=self._parse_float(
                        self.traj_line_width_var.get(),
                        "Trayectorias ancho lineas",
                        min_value=0.0,
                    ),
                    leader_line_width=self._parse_float(
                        self.traj_leader_line_width_var.get(),
                        "Trayectorias ancho lider",
                        min_value=0.0,
                    ),
                    grid_alpha=self._parse_float(
                        self.traj_grid_alpha_var.get(),
                        "Trayectorias grid alpha",
                        min_value=0.0,
                    ),
                )
                plot_trajectories(data, settings)
            else:
                settings = SnapshotPlotSettings(
                    start_index=common.start_index,
                    end_index=common.end_index,
                    frame_step=common.frame_step,
                    title=common.title,
                    fig_width=self._parse_float(
                        self.snap_fig_w_var.get(),
                        "Snapshot figura ancho",
                        min_value=0.1,
                    ),
                    fig_height=self._parse_float(
                        self.snap_fig_h_var.get(),
                        "Snapshot figura alto",
                        min_value=0.1,
                    ),
                    quiver_scale=self._parse_float(
                        self.snap_quiver_scale_var.get(),
                        "Snapshot escala quiver",
                        min_value=0.000001,
                    ),
                    quiver_width=self._parse_float(
                        self.snap_quiver_width_var.get(),
                        "Snapshot ancho quiver",
                        min_value=0.0,
                    ),
                    leader_quiver_width=self._parse_float(
                        self.snap_leader_quiver_width_var.get(),
                        "Snapshot ancho quiver lider",
                        min_value=0.0,
                    ),
                    point_size=self._parse_float(
                        self.snap_point_size_var.get(),
                        "Snapshot tam punto",
                        min_value=0.0,
                    ),
                    leader_point_size=self._parse_float(
                        self.snap_leader_point_size_var.get(),
                        "Snapshot tam punto lider",
                        min_value=0.0,
                    ),
                    vector_alpha=self._parse_float(
                        self.snap_vector_alpha_var.get(),
                        "Snapshot alpha vectores",
                        min_value=0.0,
                    ),
                    point_alpha=self._parse_float(
                        self.snap_point_alpha_var.get(),
                        "Snapshot alpha puntos",
                        min_value=0.0,
                    ),
                    grid_alpha=self._parse_float(
                        self.snap_grid_alpha_var.get(),
                        "Snapshot grid alpha",
                        min_value=0.0,
                    ),
                )
                snapshot_idx = self._parse_int(
                    self.frame_index_var.get(),
                    "Indice snapshot",
                    min_value=0,
                )
                plot_snapshot(data, snapshot_idx, settings)
        except Exception as exc:  # pragma: no cover - GUI error path
            messagebox.showerror("Error", str(exc))

    def run(self) -> None:
        self.root.mainloop()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Abre una GUI para elegir archivos .txt dentro de ./output y graficarlos "
            "(orden global, trayectorias o snapshot)."
        )
    )
    parser.add_argument(
        "--output-dir",
        default="output",
        help="Carpeta con archivos off_lattice_*.txt (default: ./output)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir).expanduser().resolve()

    if not output_dir.exists() or not output_dir.is_dir():
        raise SystemExit(f"Carpeta invalida: {output_dir}")

    app = PlotterApp(output_dir)
    app.run()


if __name__ == "__main__":
    main()

