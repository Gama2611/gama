#!/bin/sh
# Construye gama-suite_<versión>_amd64.deb a partir del ejecutable de PyInstaller y los APK.
#   empaque/linux/construir-deb.sh <versión> <carpeta dist/gama-servidor> <carpeta con APK> <salida>
set -eu
VERSION="$1"; DIST="$2"; APKS="$3"; SALIDA="$4"
AQUI=$(cd "$(dirname "$0")" && pwd)
RAIZ=$(mktemp -d)
trap 'rm -rf "$RAIZ"' EXIT

install -d "$RAIZ/DEBIAN" "$RAIZ/opt/gama-suite/apk" "$RAIZ/lib/systemd/system" "$RAIZ/usr/bin"
cp -a "$DIST" "$RAIZ/opt/gama-suite/servidor"
if ls "$APKS"/*.apk >/dev/null 2>&1; then cp "$APKS"/*.apk "$RAIZ/opt/gama-suite/apk/"; fi
for u in gama-suite.service gama-respaldo.service gama-respaldo.timer; do
    install -m 0644 "$AQUI/$u" "$RAIZ/lib/systemd/system/$u"
done
cat >"$RAIZ/usr/bin/gama-servidor" <<'ENVOLTORIO'
#!/bin/sh
# Comandos de administración con la configuración del servicio. Ej.: sudo gama-servidor estado
exec env GAMA_CONFIG=/etc/gama/gama.env /opt/gama-suite/servidor/gama-servidor "$@"
ENVOLTORIO
chmod 0755 "$RAIZ/usr/bin/gama-servidor"
for s in postinst prerm postrm; do install -m 0755 "$AQUI/$s" "$RAIZ/DEBIAN/$s"; done
TAMANO=$(du -sk "$RAIZ/opt" | cut -f1)
cat >"$RAIZ/DEBIAN/control" <<CONTROL
Package: gama-suite
Version: $VERSION
Architecture: amd64
Maintainer: GAMA Suite <soporte@gama-suite.local>
Installed-Size: $TAMANO
Depends: postgresql | postgresql-17 | postgresql-16 | postgresql-15 | postgresql-14, adduser, util-linux
Section: misc
Priority: optional
Homepage: https://127.0.0.1:8443/consola/
Description: GAMA Suite - suite industrial todo en uno
 Servidor de GAMA Suite con todos los departamentos (producción, mantenimiento,
 calidad, mejora continua, logística, compras, ventas y facturación, marketing,
 costos, SST y ambiente, talento, energía, proyectos, gemelo digital, MDM y
 asistente), base de datos PostgreSQL, certificado TLS propio, respaldo diario,
 consola web y las apps Android (GAMA y GAMA MDM).
CONTROL
mkdir -p "$(dirname "$SALIDA")"
dpkg-deb --root-owner-group --build -Zxz "$RAIZ" "$SALIDA"
echo "Paquete: $SALIDA"
