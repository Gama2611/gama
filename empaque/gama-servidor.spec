# -*- mode: python ; coding: utf-8 -*-
# Ejecutable del servidor de GAMA Suite (carpeta única, Windows y Linux).
#   pyinstaller --noconfirm --clean empaque/gama-servidor.spec
# Resultado: dist/gama-servidor/ con gama-servidor(.exe) y sus bibliotecas.
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_dynamic_libs, collect_submodules

EMPAQUE = Path(SPECPATH)
SERVIDOR = EMPAQUE.parent / "servidor"


def carpeta(origen: Path, destino: str) -> list[tuple[str, str]]:
    """Archivos de una carpeta (sin cachés de Python) para incluirlos tal cual."""
    return [
        (str(p), str(Path(destino) / p.relative_to(origen).parent))
        for p in origen.rglob("*")
        if p.is_file() and "__pycache__" not in p.parts
    ]


datos = carpeta(SERVIDOR / "migraciones", "migraciones") + carpeta(SERVIDOR / "gama" / "consola", "gama/consola")
datos += collect_data_files("psycopg_binary")
ocultos = (
    collect_submodules("gama")
    + collect_submodules("uvicorn")
    + collect_submodules("psycopg")
    + collect_submodules("alembic", filter=lambda nombre: not nombre.startswith("alembic.testing"))
    # Implementación nativa de psycopg: en Windows no hay libpq del sistema, así que es obligatoria.
    + ["psycopg_binary", "psycopg_binary._psycopg", "psycopg_binary.pq", "psycopg_binary.version"]
    + ["sqlalchemy.dialects.postgresql.psycopg", "_cffi_backend"]
)

a = Analysis(
    [str(EMPAQUE / "entrada.py")],
    pathex=[str(SERVIDOR)],
    binaries=collect_dynamic_libs("psycopg_binary"),
    datas=datos,
    hiddenimports=ocultos,
    excludes=["tkinter", "matplotlib", "IPython", "pytest", "PIL"],
    # psycopg es LGPL: se distribuye como archivos .py visibles y reemplazables, no dentro del archivo comprimido.
    module_collection_mode={"psycopg": "py"},
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="gama-servidor",
    console=True,
    icon=str(EMPAQUE / "windows" / "gama.ico") if (EMPAQUE / "windows" / "gama.ico").exists() else None,
    version=str(EMPAQUE / "windows" / "version.txt") if (EMPAQUE / "windows" / "version.txt").exists() else None,
)
coll = COLLECT(exe, a.binaries, a.datas, name="gama-servidor")
