# Instalar GAMA Suite

## 1. Obtener los instaladores (GitHub Actions)

El workflow **Instaladores** construye y **prueba** todo en máquinas limpias de GitHub:

| Archivo | Para qué |
| --- | --- |
| `GAMA-Suite-Setup-X.Y.Z.exe` | Servidor completo en Windows 10/11 o Windows Server 2019+ (64 bits) |
| `gama-suite_X.Y.Z_amd64.deb` | Servidor completo en Ubuntu 22.04+ o Debian 12+ |
| `gama-terminal.apk` | App GAMA para tabletas de planta (modo kiosco) |
| `gama-movil.apk` | App GAMA para teléfonos de supervisores y gerencia |
| `gama-mdm.apk` | GAMA MDM: administra las tabletas |
| `SHA256SUMS.txt` | Sumas para comprobar que nada se alteró |

Antes de la primera versión configure en GitHub (*Settings → Secrets and variables → Actions*):

1. **Firma de las apps** (obligatoria para publicar; el MDM solo actualiza apps firmadas con la misma clave):

   ```bash
   keytool -genkeypair -v -keystore gama.jks -alias gama -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 gama.jks > gama.jks.b64
   ```

   Secretos: `GAMA_KEYSTORE_BASE64` (contenido de `gama.jks.b64`), `GAMA_KEYSTORE_CLAVE`,
   `GAMA_KEY_ALIAS` (`gama`) y `GAMA_KEY_CLAVE`. Guarde `gama.jks` en un lugar seguro y fuera del
   repositorio: si se pierde, las tabletas no podrán recibir actualizaciones.
2. **Clave pública de licencias** (variable, no secreto): `GAMA_LICENCIA_CLAVE_PUBLICA`, el
   contenido de `claves/publica.hex` generado con `herramientas/licencias.py claves`.

Luego publique una etiqueta: `git tag v1.0.0 && git push origin v1.0.0`. El Release aparece con
todos los archivos. Sin etiqueta, *Actions → Instaladores → Run workflow* produce versiones de prueba
(`0.0.N`) como artefactos.

## 2. Servidor en Windows

1. Ejecute `GAMA-Suite-Setup-X.Y.Z.exe` como administrador.
2. Escriba el nombre de la empresa y el puerto HTTPS (8443 por defecto). Si tiene el archivo de
   licencia, selecciónelo; si no, podrá cargarlo después.
3. El instalador deja todo listo, sin pasos manuales:
   - PostgreSQL 16 propio (servicio **GAMA-PostgreSQL**, solo accesible desde el mismo equipo,
     puerto 5544) con la base `gama` y una contraseña aleatoria.
   - Migraciones, empresa, los 14 roles de plantilla, el catálogo de fallas y el usuario `admin`.
   - Certificado TLS propio y servicio **GAMA-Servidor** con la cuenta de mínimo privilegio
     *LocalService*, que arranca solo con Windows.
   - Regla de firewall solo para redes privadas y de dominio.
   - Tarea programada **GAMA Suite - Respaldo diario** (02:15, cuenta SYSTEM): respaldo completo de la
     base en `C:\ProgramData\GAMA\respaldos\`, conservando los 14 más recientes.
4. La contraseña temporal de `admin` está en `C:\ProgramData\GAMA\acceso-inicial.txt` (solo
   administradores). GAMA pide cambiarla al primer ingreso. Bórrela después.
5. Abra la consola: `https://localhost:8443/consola/`. El navegador avisará que el certificado es
   propio de la planta: es lo esperado. Compruebe que la huella coincida con la del archivo de acceso.

Datos y configuración: `C:\ProgramData\GAMA\` (base de datos en `pgdata\`, configuración en
`gama.env`, respaldos en `respaldos\`, registros en `logs\`). Copie la carpeta `respaldos\` a otro equipo o
disco con regularidad: un respaldo en el mismo disco no protege de una falla del disco. Desinstalar quita los servicios y **conserva los datos**.
Para actualizar, ejecute el instalador nuevo encima: aplica las migraciones y conserva todo.

Instalación desatendida:

```
GAMA-Suite-Setup-X.Y.Z.exe /VERYSILENT /EMPRESA="Mi Empresa C.A." /PUERTO=8443 /LICENCIA="C:\ruta\licencia.json"
```

## 3. Servidor en Linux

```bash
sudo apt install ./gama-suite_X.Y.Z_amd64.deb            # instala PostgreSQL si hace falta
# o, con el nombre de la empresa:
sudo env GAMA_EMPRESA="Mi Empresa C.A." apt install ./gama-suite_X.Y.Z_amd64.deb
sudo cat /etc/gama/acceso-inicial.txt                     # contraseña temporal de admin
sudo gama-servidor estado                                  # migraciones y huella del certificado
```

Servicio: `systemctl status gama-suite` (usuario de sistema `gama`, con aislamiento de systemd).
Configuración: `/etc/gama/gama.env`. Datos: base PostgreSQL `gama` y `/var/lib/gama-suite/`.
Respaldo diario: temporizador `gama-respaldo.timer` (02:15) en `/var/backups/gama-suite/`, conservando los 14
más recientes. `apt remove` conserva todo; `apt purge` borra configuración y certificado pero **no** la base ni
los respaldos.

## 4. Tabletas con GAMA MDM

1. Restablezca la tableta de fábrica y complete el asistente inicial **sin** agregar cuentas.
2. Active la depuración USB e instale GAMA MDM: `adb install gama-mdm.apk`.
3. Hágalo propietario del dispositivo (una sola vez):
   `adb shell dpm set-device-owner com.gama.mdm/.AdminReceptor`
4. En la consola: *Dispositivos → Registrar tableta*. Escriba el nombre (ej. «Extrusora 3»).
5. En la tableta abra GAMA MDM y escanee el QR. El QR lleva la dirección del servidor, la huella
   de su certificado y un código de un solo uso que vence en 24 horas.
6. En la consola, en la fila de la tableta, toque **Instalar app** y elija `gama-terminal.apk`.
   El MDM descarga el APK del servidor, verifica su huella SHA-256 y lo instala sin intervención.
7. Listo: la tableta queda en modo kiosco con GAMA, conectada al servidor sin escribir nada.

Desde la consola se puede enviar un mensaje a pantalla completa, bloquear, reiniciar, activar o
quitar el kiosco, instalar o actualizar la app y retirar el equipo (invalida su token al instante).
Desde la app GAMA, un administrador puede hacer lo mismo salvo instalar apps y retirar equipos.

## 5. Asistente con IA (opcional)

Sin configuración, el Asistente redacta con plantillas exactas. Para una redacción más natural,
agregue a `gama.env` un modelo compatible con la API de OpenAI. Se recomienda uno **local** (por
ejemplo Ollama en la red de la planta) para que los datos no salgan de la empresa:

```
GAMA_IA_URL=http://servidor-ia.local:11434/v1
GAMA_IA_MODELO=nombre-del-modelo
```

Cada texto de la IA se verifica contra los datos y se descarta si trae una cifra, un nombre o un
código que no estén en ellos.

## 6. Comandos de administración

| Comando | Para qué |
| --- | --- |
| `gama-servidor estado` | Revisa migraciones y muestra la huella del certificado |
| `gama-servidor migrar` | Aplica migraciones pendientes |
| `gama-servidor certificado --renovar` | Certificado nuevo (las tabletas deben registrarse de nuevo) |
| `gama-servidor renombrar-empresa --nombre "..."` | Cambia el nombre de la empresa |
| `gama-servidor respaldar [--carpeta X] [--conservar 14]` | Respaldo completo de la base ahora (formato de pg_dump) |
| `gama-servidor restaurar ARCHIVO --confirmar` | Reemplaza los datos por los del respaldo. Detenga antes el servicio **GAMA-Servidor** / `gama-suite`, restaure, ejecute `migrar` y vuelva a iniciarlo |

En Windows se ejecutan desde `C:\Program Files\GAMA Suite\servidor\` con la variable
`GAMA_CONFIG=C:\ProgramData\GAMA\gama.env`; en Linux, `sudo gama-servidor ...` ya la usa.

## 7. Trabajar desde el PC

La consola web (`https://SERVIDOR:8443/consola/`) no es solo para el administrador: cada usuario entra con su
usuario y ve en **Departamentos** solo lo que su rol permite (por ejemplo, Facturación y cobranza, Costos o
Talento para el personal de oficina). Allí registra, edita, aprueba o anula con nota, consulta el historial de
cada documento y los indicadores del área. Las tabletas y teléfonos usan la app GAMA con los mismos datos.

## 8. Facturación: aviso legal

El módulo de facturación calcula IVA (16 %, 8 %, exento), IGTF (3 % al pagar en divisas) y el equivalente en
bolívares con la tasa BCV indicada, y asigna número de factura y de control correlativos sin saltos. Para usar
esas facturas como documentos fiscales en Venezuela, el sistema debe estar **homologado por el SENIAT** (o
imprimir en imprenta autorizada / máquina fiscal). Hasta entonces, úselas como prefacturas o control interno.

