# Componentes de terceros

GAMA Suite usa estos componentes sin modificarlos. Todos permiten uso comercial.

## Servidor (lista completa y versiones en `servidor/requirements.lock`)

| Componente | Licencia | Nota |
| --- | --- | --- |
| FastAPI, Starlette, Uvicorn | MIT / BSD-3-Clause | |
| SQLAlchemy, Alembic | MIT | |
| Pydantic, pydantic-settings | MIT | |
| psycopg 3 | LGPL-3.0 | Se usa como biblioteca sin modificar. Si algún día se modifica, esos cambios deben publicarse. |
| argon2-cffi | MIT | |
| PyJWT | MIT | |
| cryptography | Apache-2.0 o BSD-3-Clause | |
| NumPy, SciPy | BSD-3-Clause | Motor científico (GAMA Ciencia) |
| segno | BSD-3-Clause | Códigos QR de registro de tabletas |
| PostgreSQL | PostgreSQL License | Se distribuye dentro del instalador de Windows; texto en `docs/licencias/PostgreSQL.txt` |

El workflow del servidor rechaza cualquier dependencia GPL o AGPL.

## Instaladores (herramientas de construcción y componentes distribuidos)

| Componente | Licencia | Nota |
| --- | --- | --- |
| PyInstaller | GPL-2.0 con excepción de distribución | La excepción permite distribuir el ejecutable bajo cualquier licencia. psycopg (LGPL) se entrega como archivos `.py` visibles y reemplazables dentro de la carpeta del servidor, como exige la LGPL. |
| Inno Setup | Licencia propia de Inno Setup (uso comercial permitido) | Solo construye el instalador de Windows |
| WinSW | MIT | Ejecuta el servidor como servicio de Windows |
| Runtime de Visual C++ (DLL) | Licencia de redistribución de Microsoft | Junto a los binarios de PostgreSQL (despliegue local permitido) |
| IBM Plex Sans | SIL Open Font License 1.1 | Tipografía de la consola y las apps; texto en `docs/licencias/IBM-Plex-OFL.txt` |

## App Android

| Componente | Licencia |
| --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX, Jetpack Compose, Material 3, íconos Material | Apache-2.0 |
| OkHttp | Apache-2.0 |
| AndroidX WorkManager | Apache-2.0 |
| ZXing Android Embedded (lector de QR de GAMA MDM) | Apache-2.0 |
| IBM Plex Sans | SIL Open Font License 1.1 |
